/**
 * Google Drive 工作区：纯前端，用 Google Identity Services 拿访问令牌，直接调用 Drive REST API。
 *
 * 权限只申请 drive.file：Neon 只能看到自己创建的文件，以及你在 Google 文件选择器里手动选中的文件，
 * 看不到 Drive 里的其他内容。
 *
 * 目录结构：
 *   我的云端硬盘/Neon/
 *     对话/      每个会话一个 JSON（自动同步）
 *     附件/      聊天里上传的附件副本
 *     备份/      完整历史 ZIP，可在设置里导入恢复
 */
import { create } from 'zustand'

const SCOPE = 'https://www.googleapis.com/auth/drive.file'
const API = 'https://www.googleapis.com/drive/v3'
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3'
const FOLDER_MIME = 'application/vnd.google-apps.folder'

export type DriveFolderKey = 'root' | 'sessions' | 'attachments' | 'backups'
const FOLDER_NAMES: Record<Exclude<DriveFolderKey, 'root'>, string> = {
  sessions: '对话',
  attachments: '附件',
  backups: '备份',
}

export type DriveFile = {
  id: string
  name: string
  mimeType: string
  size?: string
  modifiedTime?: string
  webViewLink?: string
  iconLink?: string
}

type DriveConfig = {
  /** Google Cloud 里创建的「Web 应用」OAuth 客户端 ID */
  clientId: string
  /** 可选：浏览器 API Key，启用「从 Drive 选择文件」（Google Picker） */
  apiKey: string
  /** 可选：Google Cloud 项目编号，Picker 用来授予所选文件的访问权限 */
  appId: string
}

type DriveState = DriveConfig & {
  /** expired：已连接过，但授权过期，需要点一下重新授权（浏览器不允许后台弹窗） */
  status: 'disconnected' | 'connecting' | 'connected' | 'expired' | 'error'
  error: string | null
  email: string | null
  /** 自动把对话同步到 Drive */
  syncSessions: boolean
  /** 上传附件时同时存一份到 Drive */
  mirrorUploads: boolean
  lastSyncAt: number | null
  syncing: boolean
  folders: Partial<Record<DriveFolderKey, string>>
}

const K = {
  clientId: 'neon:drive-client-id',
  apiKey: 'neon:drive-api-key',
  appId: 'neon:drive-app-id',
  syncSessions: 'neon:drive-sync-sessions',
  mirrorUploads: 'neon:drive-mirror-uploads',
  folders: 'neon:drive-folders',
  connected: 'neon:drive-connected',
  email: 'neon:drive-email',
} as const

const read = (key: string) => {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}
const write = (key: string, value: string | null) => {
  try {
    if (value === null) {
      localStorage.removeItem(key)
    } else {
      localStorage.setItem(key, value)
    }
  } catch {
    // 无痕模式等存储不可用时只在内存生效
  }
}

export const useDrive = create<DriveState>(() => ({
  clientId: read(K.clientId) ?? __GOOGLE_CLIENT_ID__,
  apiKey: read(K.apiKey) ?? __GOOGLE_API_KEY__,
  appId: read(K.appId) ?? __GOOGLE_APP_ID__,
  status: 'disconnected',
  error: null,
  email: read(K.email),
  syncSessions: read(K.syncSessions) !== 'false',
  mirrorUploads: read(K.mirrorUploads) !== 'false',
  lastSyncAt: null,
  syncing: false,
  folders: JSON.parse(read(K.folders) ?? '{}') as DriveState['folders'],
}))

export const setDriveConfig = (config: Partial<DriveConfig>) => {
  for (const [key, value] of Object.entries(config) as [keyof DriveConfig, string][]) {
    write(K[key], value.trim() || null)
  }
  useDrive.setState(
    Object.fromEntries(Object.entries(config).map(([k, v]) => [k, (v as string).trim()]))
  )
}

export const setSyncSessions = (on: boolean) => {
  write(K.syncSessions, String(on))
  useDrive.setState({ syncSessions: on })
}

export const setMirrorUploads = (on: boolean) => {
  write(K.mirrorUploads, String(on))
  useDrive.setState({ mirrorUploads: on })
}

export const isDriveConnected = () => useDrive.getState().status === 'connected'
export const canPick = () => {
  const { apiKey, appId } = useDrive.getState()
  return Boolean(apiKey && appId)
}

// ------------------------------------------------------------------ 脚本加载

const scripts = new Map<string, Promise<void>>()
const loadScript = (src: string) => {
  let task = scripts.get(src)
  if (!task) {
    task = new Promise<void>((resolve, reject) => {
      const el = document.createElement('script')
      el.src = src
      el.async = true
      el.onload = () => resolve()
      el.onerror = () => {
        scripts.delete(src)
        reject(new Error(`加载失败：${src}`))
      }
      document.head.appendChild(el)
    })
    scripts.set(src, task)
  }
  return task
}

// ------------------------------------------------------------------ 令牌

type TokenResponse = { access_token?: string; expires_in?: number; error?: string }
type TokenClient = { requestAccessToken: (o?: { prompt?: string; hint?: string }) => void }
type GoogleAccounts = {
  oauth2: {
    initTokenClient: (o: {
      client_id: string
      scope: string
      callback: (r: TokenResponse) => void
      error_callback?: (e: { type: string; message?: string }) => void
    }) => TokenClient
    revoke: (token: string, done?: () => void) => void
  }
}
// oxlint-disable-next-line typescript/no-explicit-any -- Google Picker 没有官方类型
const g = () => (window as unknown as { google: { accounts: GoogleAccounts; picker: any } }).google

let token: { value: string; expiresAt: number } | null = null
let tokenClient: TokenClient | null = null
let pending: { resolve: (t: string) => void; reject: (e: Error) => void } | null = null

const ensureTokenClient = async () => {
  const { clientId } = useDrive.getState()
  if (!clientId) {
    throw new Error('还没有填写 Google OAuth 客户端 ID')
  }
  await loadScript('https://accounts.google.com/gsi/client')
  if (!tokenClient) {
    tokenClient = g().accounts.oauth2.initTokenClient({
      client_id: clientId,
      scope: SCOPE,
      callback: (r) => {
        if (r.access_token) {
          token = { value: r.access_token, expiresAt: Date.now() + (r.expires_in ?? 3600) * 1000 }
          pending?.resolve(r.access_token)
        } else {
          pending?.reject(new Error(r.error ?? '授权失败'))
        }
        pending = null
      },
      error_callback: (e) => {
        pending?.reject(
          new Error(e.type === 'popup_closed' ? '授权窗口被关闭了' : (e.message ?? e.type))
        )
        pending = null
      },
    })
  }
  return tokenClient
}

/** interactive 为 false 时尽量静默续期（之前授权过才会成功） */
const requestToken = async (interactive: boolean): Promise<string> => {
  const client = await ensureTokenClient()
  return new Promise<string>((resolve, reject) => {
    pending = { resolve, reject }
    client.requestAccessToken({
      prompt: interactive ? 'consent' : '',
      hint: useDrive.getState().email ?? undefined,
    })
  })
}

export const hasValidToken = () => Boolean(token && token.expiresAt - Date.now() > 60_000)

const getToken = async (): Promise<string> => {
  if (token && hasValidToken()) {
    return token.value
  }
  return requestToken(false)
}

/** 后台任务用：没有有效令牌时不弹窗，标记为需要重新授权 */
export const ensureBackgroundToken = () => {
  if (hasValidToken()) {
    return true
  }
  const { status } = useDrive.getState()
  if (status === 'connected') {
    useDrive.setState({ status: 'expired' })
  }
  return false
}

// ------------------------------------------------------------------ 请求

const call = async <T>(url: string, init: RequestInit = {}, raw = false): Promise<T> => {
  const send = async (t: string) =>
    fetch(url, { ...init, headers: { ...(init.headers ?? {}), Authorization: `Bearer ${t}` } })
  const res = await send(await getToken())
  if (res.status === 401) {
    token = null
    useDrive.setState({ status: 'expired' })
    throw new Error('Google 授权已过期，请重新授权')
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`Drive 请求失败（${res.status}）${text.slice(0, 200)}`)
  }
  return (raw ? res : res.status === 204 ? undefined : res.json()) as Promise<T>
}

const q = (s: string) => s.replace(/\\/g, '\\\\').replace(/'/g, "\\'")

const findFolder = async (name: string, parent?: string): Promise<string | null> => {
  const query = [
    `name='${q(name)}'`,
    `mimeType='${FOLDER_MIME}'`,
    'trashed=false',
    parent ? `'${parent}' in parents` : `'root' in parents`,
  ].join(' and ')
  const res = await call<{ files: DriveFile[] }>(
    `${API}/files?q=${encodeURIComponent(query)}&fields=files(id,name)&pageSize=1`
  )
  return res.files[0]?.id ?? null
}

const createFolder = async (name: string, parent?: string) =>
  (
    await call<DriveFile>(`${API}/files?fields=id`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, mimeType: FOLDER_MIME, parents: parent ? [parent] : undefined }),
    })
  ).id

const folderExists = async (id: string) => {
  try {
    const f = await call<{ trashed: boolean }>(`${API}/files/${id}?fields=trashed`)
    return !f.trashed
  } catch {
    return false
  }
}

/** 找到或建好 Neon 文件夹及子文件夹，ID 缓存在本机 */
export const ensureFolders = async (): Promise<Record<DriveFolderKey, string>> => {
  const cached = useDrive.getState().folders
  if (
    cached.root &&
    cached.sessions &&
    cached.attachments &&
    cached.backups &&
    (await folderExists(cached.root))
  ) {
    return cached as Record<DriveFolderKey, string>
  }
  const root = (await findFolder('Neon')) ?? (await createFolder('Neon'))
  const folders: Record<DriveFolderKey, string> = {
    root,
    sessions: '',
    attachments: '',
    backups: '',
  }
  for (const key of ['sessions', 'attachments', 'backups'] as const) {
    folders[key] =
      (await findFolder(FOLDER_NAMES[key], root)) ?? (await createFolder(FOLDER_NAMES[key], root))
  }
  write(K.folders, JSON.stringify(folders))
  useDrive.setState({ folders })
  return folders
}

/** 新建或覆盖文件（multipart 上传） */
export const putFile = async (
  name: string,
  blob: Blob,
  parent: string,
  existingId?: string,
  appProperties?: Record<string, string>
): Promise<DriveFile> => {
  const boundary = `neon-${Math.random().toString(36).slice(2)}`
  const meta: Record<string, unknown> = { name, mimeType: blob.type || 'application/octet-stream' }
  if (!existingId) {
    meta.parents = [parent]
  }
  if (appProperties) {
    meta.appProperties = appProperties
  }
  const body = new Blob([
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(meta)}\r\n`,
    `--${boundary}\r\nContent-Type: ${meta.mimeType as string}\r\n\r\n`,
    blob,
    `\r\n--${boundary}--`,
  ])
  const url = existingId
    ? `${UPLOAD}/files/${existingId}?uploadType=multipart&fields=id,name,mimeType,modifiedTime,webViewLink`
    : `${UPLOAD}/files?uploadType=multipart&fields=id,name,mimeType,modifiedTime,webViewLink`
  return call<DriveFile>(url, {
    method: existingId ? 'PATCH' : 'POST',
    headers: { 'Content-Type': `multipart/related; boundary=${boundary}` },
    body,
  })
}

export const trashFile = (id: string) =>
  call<unknown>(`${API}/files/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ trashed: true }),
  })

export const listFolder = async (folder: DriveFolderKey): Promise<DriveFile[]> => {
  const folders = await ensureFolders()
  const query = `'${folders[folder]}' in parents and trashed=false`
  const res = await call<{ files: DriveFile[] }>(
    `${API}/files?q=${encodeURIComponent(query)}&orderBy=modifiedTime desc&pageSize=100` +
      '&fields=files(id,name,mimeType,size,modifiedTime,webViewLink,iconLink)'
  )
  return res.files
}

/** Google 文档类文件导出成 DeepSeek 能读的格式，其余直接下载 */
const EXPORTS: Record<string, [string, string]> = {
  'application/vnd.google-apps.document': [
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'docx',
  ],
  'application/vnd.google-apps.spreadsheet': [
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'xlsx',
  ],
  'application/vnd.google-apps.presentation': [
    'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    'pptx',
  ],
}

export const downloadAsFile = async (
  file: Pick<DriveFile, 'id' | 'name' | 'mimeType'>
): Promise<File> => {
  const exp = EXPORTS[file.mimeType]
  const url = exp
    ? `${API}/files/${file.id}/export?mimeType=${encodeURIComponent(exp[0])}`
    : `${API}/files/${file.id}?alt=media`
  const res = await call<Response>(url, {}, true)
  const blob = await res.blob()
  const name = exp && !file.name.endsWith(`.${exp[1]}`) ? `${file.name}.${exp[1]}` : file.name
  return new File([blob], name, { type: exp ? exp[0] : blob.type || file.mimeType })
}

// ------------------------------------------------------------------ 连接

const fetchEmail = async () => {
  try {
    const res = await call<{ user?: { emailAddress?: string } }>(
      `${API}/about?fields=user(emailAddress)`
    )
    return res.user?.emailAddress ?? null
  } catch {
    return null
  }
}

export const connectDrive = async (interactive = !read(K.connected)) => {
  useDrive.setState({ status: 'connecting', error: null })
  try {
    await requestToken(interactive)
    await ensureFolders()
    const email = await fetchEmail()
    write(K.connected, 'true')
    write(K.email, email)
    useDrive.setState({ status: 'connected', email })
    return true
  } catch (error) {
    useDrive.setState({
      status: 'error',
      error: error instanceof Error ? error.message : String(error),
    })
    return false
  }
}

/** 页面打开时，如果之前连过，就静默恢复连接（第一次请求时才弹出 Google 授权） */
export const restoreDrive = () => {
  if (read(K.connected) === 'true' && useDrive.getState().clientId) {
    // 令牌只在内存里，刷新页面后需要点一下「重新授权」
    useDrive.setState({ status: 'expired' })
    // 预加载 Google 登录脚本，点击时能立刻弹出授权窗口，不被浏览器拦截
    void loadScript('https://accounts.google.com/gsi/client').catch(() => {})
  }
}

export const disconnectDrive = async () => {
  if (token) {
    try {
      await loadScript('https://accounts.google.com/gsi/client')
      g().accounts.oauth2.revoke(token.value)
    } catch {
      // 撤销失败也照常断开本地状态
    }
  }
  token = null
  tokenClient = null
  write(K.connected, null)
  write(K.email, null)
  useDrive.setState({ status: 'disconnected', email: null, error: null })
}

/** 改了客户端 ID 之后需要重建令牌客户端 */
export const resetTokenClient = () => {
  tokenClient = null
  token = null
}

export const folderUrl = (id?: string) =>
  id ? `https://drive.google.com/drive/folders/${id}` : 'https://drive.google.com/drive/my-drive'

// ------------------------------------------------------------------ 文件选择器

/** 打开 Google 文件选择器；选中的文件会自动授予 Neon 读取权限 */
export const pickFromDrive = async (): Promise<DriveFile[]> => {
  const { apiKey, appId } = useDrive.getState()
  if (!apiKey || !appId) {
    throw new Error('需要先在工作区里填写 API Key 和项目编号才能用文件选择器')
  }
  const t = await getToken()
  await loadScript('https://apis.google.com/js/api.js')
  await new Promise<void>((resolve) =>
    (window as unknown as { gapi: { load: (n: string, cb: () => void) => void } }).gapi.load(
      'picker',
      resolve
    )
  )
  const picker = g().picker
  return new Promise<DriveFile[]>((resolve) => {
    const view = new picker.DocsView(picker.ViewId.DOCS)
      .setIncludeFolders(false)
      .setSelectFolderEnabled(false)
    const builder = new picker.PickerBuilder()
      .addView(view)
      .addView(new picker.DocsUploadView())
      .setOAuthToken(t)
      .setDeveloperKey(apiKey)
      .setAppId(appId)
      .enableFeature(picker.Feature.MULTISELECT_ENABLED)
      .setLocale(document.documentElement.lang === 'en-US' ? 'en' : 'zh-CN')
      .setTitle('从 Google Drive 选择')
      .setCallback(
        (data: { action: string; docs?: { id: string; name: string; mimeType: string }[] }) => {
          if (data.action === picker.Action.PICKED) {
            resolve(
              (data.docs ?? []).map((d) => ({ id: d.id, name: d.name, mimeType: d.mimeType }))
            )
          } else if (data.action === picker.Action.CANCEL) {
            resolve([])
          }
        }
      )
    builder.build().setVisible(true)
  })
}
