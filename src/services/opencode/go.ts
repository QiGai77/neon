import { create } from 'zustand'

/**
 * OpenCode Go 订阅：用 Go 的 API Key 走 opencode.ai/inference/go 的 Anthropic 兼容接口。
 * opencode.ai 的推理接口不带 CORS，所以经由 Neon 自己的 Cloudflare Worker 转发（/api/opencode/*）。
 */
export const GO_PROXY = __NEON_PROXY__ || 'https://neon.qigai.tech/api/opencode'

export type Provider = 'deepseek' | 'opencode-go'

export type GoWindow = { percent: number; resetsAt: string | null; status?: string }
export type GoUsage = {
  rolling: GoWindow | null
  weekly: GoWindow | null
  monthly: GoWindow | null
  fetchedAt: number
}
export type GoModel = { id: string; name: string; reasoning: boolean; vision: boolean }

type GoState = {
  provider: Provider
  key: string | null
  model: string
  models: GoModel[]
  usage: GoUsage | null
  usageError: string | null
  loading: boolean
}

const K = {
  provider: 'neon:provider',
  key: 'neon:go-key',
  model: 'neon:go-model',
} as const

const read = (k: string) => {
  try {
    return localStorage.getItem(k)
  } catch {
    return null
  }
}
const write = (k: string, v: string | null) => {
  try {
    if (v === null) {
      localStorage.removeItem(k)
    } else {
      localStorage.setItem(k, v)
    }
  } catch {
    // ignore
  }
}

export const DEFAULT_GO_MODEL = 'deepseek-v4.1-flash'

export const useGo = create<GoState>(() => ({
  provider: read(K.provider) === 'opencode-go' && read(K.key) ? 'opencode-go' : 'deepseek',
  key: read(K.key),
  model: read(K.model) || DEFAULT_GO_MODEL,
  models: [],
  usage: null,
  usageError: null,
  loading: false,
}))

export const isGoActive = () => {
  const s = useGo.getState()
  return s.provider === 'opencode-go' && !!s.key
}

export const setProvider = (provider: Provider) => {
  write(K.provider, provider)
  useGo.setState({ provider })
}

export const setGoModel = (model: string) => {
  write(K.model, model)
  useGo.setState({ model })
}

/**
 * 导入订阅：支持直接粘贴 Key，或粘贴 / 选择 OpenCode 的 auth.json
 * （~/.local/share/opencode/auth.json，Windows 在 %USERPROFILE%\.local\share\opencode\auth.json）。
 */
export const parseGoImport = (text: string): string | null => {
  const raw = text.trim()
  if (!raw) {
    return null
  }
  if (raw.startsWith('{')) {
    try {
      const json = JSON.parse(raw) as Record<string, unknown>
      for (const name of ['opencode-go', 'opencode_go', 'opencode', 'go']) {
        const entry = json[name] as { key?: unknown } | undefined
        if (entry && typeof entry.key === 'string' && entry.key.trim()) {
          return entry.key.trim()
        }
      }
      if (typeof json.key === 'string') {
        return json.key.trim()
      }
    } catch {
      return null
    }
    return null
  }
  const m = raw.match(/[A-Za-z0-9_\-.]{20,}/)
  return m ? m[0] : null
}

const auth = (key: string) => ({ Authorization: `Bearer ${key}` })

const readError = async (res: Response) => {
  const text = await res.text().catch(() => '')
  try {
    const body = JSON.parse(text) as { error?: { message?: string } }
    return body.error?.message || text || res.statusText
  } catch {
    return text || res.statusText || String(res.status)
  }
}

const toWindow = (v: unknown): GoWindow | null => {
  if (!v || typeof v !== 'object') {
    return null
  }
  const o = v as Record<string, unknown>
  const percent = Number(o.percent ?? o.usagePercent)
  if (!Number.isFinite(percent)) {
    return null
  }
  let resetsAt = typeof o.resetsAt === 'string' ? o.resetsAt : null
  if (!resetsAt && typeof o.resetInSec === 'number') {
    resetsAt = new Date(Date.now() + o.resetInSec * 1000).toISOString()
  }
  return { percent, resetsAt, status: typeof o.status === 'string' ? o.status : undefined }
}

/** GET /zen/go/v1/usage：5 小时 / 每周 / 每月三个窗口的已用百分比 */
export const fetchGoUsage = async (key: string, signal?: AbortSignal): Promise<GoUsage> => {
  const res = await fetch(`${GO_PROXY}/zen/go/v1/usage`, { headers: auth(key), signal })
  if (!res.ok) {
    throw new Error(res.status === 401 ? 'Key 无效或没有 Go 订阅' : await readError(res))
  }
  const body = (await res.json()) as { usage?: Record<string, unknown> }
  const u = body.usage ?? (body as Record<string, unknown>)
  return {
    rolling: toWindow(u.rolling ?? u.rollingUsage),
    weekly: toWindow(u.weekly ?? u.weeklyUsage),
    monthly: toWindow(u.monthly ?? u.monthlyUsage),
    fetchedAt: Date.now(),
  }
}

/** Go 可用的文本模型（公开列表，不需要 Key） */
export const fetchGoModels = async (): Promise<GoModel[]> => {
  const res = await fetch(`${GO_PROXY}/zen/go/v1/models`)
  if (!res.ok) {
    throw new Error(await readError(res))
  }
  const body = (await res.json()) as {
    data?: {
      id: string
      name?: string
      type?: string
      capabilities?: { reasoning?: { supported?: boolean } }
      modalities?: { input?: string[] }
    }[]
  }
  return (body.data ?? [])
    .filter((m) => !m.type || m.type === 'chat')
    .map((m) => ({
      id: m.id,
      name: m.name ?? m.id,
      reasoning: !!m.capabilities?.reasoning?.supported,
      vision: (m.modalities?.input ?? []).some((x) => x.startsWith('image')),
    }))
}

export const loadGoModels = async () => {
  if (useGo.getState().models.length) {
    return
  }
  try {
    useGo.setState({ models: await fetchGoModels() })
  } catch {
    // 列表拿不到时仍可用默认模型
  }
}

export const refreshGoUsage = async () => {
  const { key } = useGo.getState()
  if (!key) {
    return
  }
  useGo.setState({ loading: true, usageError: null })
  try {
    useGo.setState({ usage: await fetchGoUsage(key), loading: false })
  } catch (e) {
    useGo.setState({ loading: false, usageError: e instanceof Error ? e.message : String(e) })
  }
}

/** 校验并保存 Key（用额度接口校验，不产生费用），成功后切到 Go */
export const importGoKey = async (key: string) => {
  const usage = await fetchGoUsage(key)
  write(K.key, key)
  write(K.provider, 'opencode-go')
  useGo.setState({ key, usage, usageError: null, provider: 'opencode-go' })
}

export const removeGoKey = () => {
  write(K.key, null)
  write(K.provider, 'deepseek')
  useGo.setState({ key: null, usage: null, provider: 'deepseek' })
}

/** 同一个对话用同一个会话 ID，Go 要求带上以便路由和复用缓存 */
export const goSessionHeader = (sessionId?: string) =>
  `ses_neon_${(sessionId || 'default').replace(/[^A-Za-z0-9_-]/g, '').slice(0, 48)}`
