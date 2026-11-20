/**
 * 对话 → Drive 同步：会话有变化 4 秒后写入 Neon/对话/<标题>.json；删除会话时把 Drive 上对应文件移到回收站。
 * 另外提供：附件存回 Drive、完整历史备份、从 Drive 把文件加到对话里。
 */
import { showToast } from '../../components/base/toastState.js'
import { ensureApiKey } from '../../components/settings/settingsDialogState.js'
import { exportHistory } from '../chat/backup.js'
import * as db from '../chat/db.js'
import { addDraftFiles } from '../chat/drafts.js'
import { useChatStore } from '../chat/store.js'
import type { Session } from '../chat/types.js'
import { useSettings } from '../settings.js'
import {
  downloadAsFile,
  ensureBackgroundToken,
  ensureFolders,
  isDriveConnected,
  putFile,
  trashFile,
  useDrive,
  type DriveFile,
} from './drive.js'

const MAP_KEY = 'neon:drive-session-map'
type Entry = { fileId: string; syncedAt: number }

const loadMap = (): Record<string, Entry> => {
  try {
    return JSON.parse(localStorage.getItem(MAP_KEY) ?? '{}') as Record<string, Entry>
  } catch {
    return {}
  }
}
const saveMap = (map: Record<string, Entry>) => {
  try {
    localStorage.setItem(MAP_KEY, JSON.stringify(map))
  } catch {
    // 忽略
  }
}

const safeName = (title: string) =>
  (title.trim() || '未命名对话').replace(/[\\/:*?"<>|\n\r]+/g, ' ').slice(0, 80)

const toBlob = (session: Session) =>
  new Blob(
    [
      JSON.stringify(
        { app: 'neon', format: 1, exportedAt: new Date().toISOString(), session },
        null,
        2
      ),
    ],
    { type: 'application/json' }
  )

let queue: Promise<unknown> = Promise.resolve()
const enqueue = <T>(run: () => Promise<T>) => {
  const task = queue.then(run)
  queue = task.catch(() => {})
  return task
}

const uploadSession = async (session: Session, map: Record<string, Entry>) => {
  const folders = await ensureFolders()
  const prev = map[session.id]
  const file = await putFile(
    `${safeName(session.title)}.json`,
    toBlob(session),
    folders.sessions,
    prev?.fileId,
    { neonSessionId: session.id }
  ).catch(async (error: unknown) => {
    // Drive 上的文件被手动删了：重新建一个
    if (prev && String(error).includes('404')) {
      return putFile(
        `${safeName(session.title)}.json`,
        toBlob(session),
        folders.sessions,
        undefined,
        {
          neonSessionId: session.id,
        }
      )
    }
    throw error
  })
  map[session.id] = { fileId: file.id, syncedAt: session.updatedAt }
}

/** 把有变化的已加载会话写到 Drive；full 为 true 时连没打开过的会话也从本机数据库读出来同步 */
export const syncSessions = (full = false) =>
  enqueue(async () => {
    if (!isDriveConnected() || !ensureBackgroundToken()) {
      return
    }
    const { sessions, sessionCache, streaming } = useChatStore.getState()
    const map = loadMap()
    const live = new Set(sessions.map((s) => s.id))
    useDrive.setState({ syncing: true })
    try {
      for (const summary of sessions) {
        if (Object.hasOwn(streaming, summary.id)) {
          continue
        } // 回答生成中，等结束再同步
        if (map[summary.id]?.syncedAt === summary.updatedAt) {
          continue
        }
        let session = Object.hasOwn(sessionCache, summary.id) ? sessionCache[summary.id] : undefined
        if (!session && full) {
          session = await db.loadSession(summary.id)
        }
        if (!session) {
          continue
        }
        await uploadSession({ ...session, ...summary }, map)
        saveMap(map)
      }
      // 本地删掉的会话：Drive 上移到回收站（可恢复）
      for (const id of Object.keys(map)) {
        if (!live.has(id)) {
          await trashFile(map[id].fileId).catch(() => {})
          // oxlint-disable-next-line typescript/no-dynamic-delete
          delete map[id]
          saveMap(map)
        }
      }
      useDrive.setState({ lastSyncAt: Date.now() })
    } catch (error) {
      useDrive.setState({ error: error instanceof Error ? error.message : String(error) })
    } finally {
      useDrive.setState({ syncing: false })
    }
  })

/** App 启动时调用：监听会话变化，防抖后同步 */
export const bindDriveSync = () => {
  let timer: ReturnType<typeof setTimeout> | undefined
  const schedule = () => {
    clearTimeout(timer)
    timer = setTimeout(() => {
      if (useDrive.getState().syncSessions) {
        void syncSessions()
      }
    }, 4000)
  }
  const unsubChat = useChatStore.subscribe((state, prev) => {
    if (state.sessions !== prev.sessions || state.streaming !== prev.streaming) {
      schedule()
    }
  })
  // 重新授权后补一次全量同步
  const unsubDrive = useDrive.subscribe((state, prev) => {
    if (state.status === 'connected' && prev.status !== 'connected' && state.syncSessions) {
      void syncSessions(true)
    }
  })
  return () => {
    clearTimeout(timer)
    unsubChat()
    unsubDrive()
  }
}

/** 聊天里上传的附件，同时存一份到 Neon/附件 */
export const mirrorUploads = (files: File[]) => {
  const { mirrorUploads: on } = useDrive.getState()
  if (!on || !isDriveConnected() || !ensureBackgroundToken()) {
    return
  }
  void enqueue(async () => {
    const folders = await ensureFolders()
    for (const file of files) {
      await putFile(file.name, file, folders.attachments)
    }
  }).catch((error: unknown) => {
    useDrive.setState({ error: error instanceof Error ? error.message : String(error) })
  })
}

/** 完整历史打成 ZIP 存到 Neon/备份，格式和设置里的「导出」一致，可直接导入恢复 */
export const backupToDrive = () =>
  enqueue(async () => {
    const folders = await ensureFolders()
    const { name, blob } = await exportHistory()
    return putFile(name.replace(/^deepseek_data/, 'neon_backup'), blob, folders.backups)
  })

/** 把 Drive 文件加到当前对话的输入框里，和本地上传走同一套流程 */
export const attachDriveFiles = async (files: Pick<DriveFile, 'id' | 'name' | 'mimeType'>[]) => {
  if (files.length === 0 || !ensureApiKey()) {
    return 0
  }
  const downloaded = await Promise.all(files.map(downloadAsFile))
  const sessionId = useChatStore.getState().activeId
  const error = addDraftFiles(sessionId, downloaded, useSettings.getState().apiKey ?? '')
  if (error) {
    showToast(error)
    return 0
  }
  return downloaded.length
}
