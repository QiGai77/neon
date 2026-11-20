import { translate, type TranslationMessage } from '../../i18n/index.js'
import { resizeUploadImage } from '../../utils/resizeUploadImage.js'
import { readMessageEvent } from './parseEvent.js'
import { readServerSentEvents } from './sse.js'
import { GO_PROXY, goSessionHeader, isGoActive, useGo } from '../opencode/go.js'
import type {
  ApiErrorBody,
  CreateMessageRequest,
  FileObject,
  FilePurpose,
  MessageStreamEvent,
} from './types.js'

/** 构建期 API 地址，默认 https://api.deepseek.com；本地开发支持开发服务器代理 */
export const API_ORIGIN = __API_ORIGIN__
/** Anthropic 兼容接口的前缀 */
const ANTHROPIC_BASE_URL = `${API_ORIGIN}/anthropic/v1`
/** 引用 Files API 上传的图片、调用 Anthropic 兼容 Files API 时必须携带 */
const FILES_BETA = 'files-api-2025-04-14'

export type ApiErrorKind =
  /** 非 2xx 响应 */
  | 'http'
  /** 流中返回的错误帧（如等待生成超时），随后连接关闭 */
  | 'stream'
  /** 请求没有到达服务端（断网、跨域被拦截等） */
  | 'network'
  /** 流在 message_stop 之前结束 */
  | 'interrupted'
  /** 响应不符合协议（如非 SSE 响应） */
  | 'protocol'

export class ApiError extends Error {
  readonly localMessage?: TranslationMessage

  constructor(
    readonly kind: ApiErrorKind,
    message: string | TranslationMessage,
    readonly status?: number
  ) {
    super(typeof message === 'string' ? message : translate(message.key, message.params))
    this.name = 'ApiError'
    this.localMessage = typeof message === 'string' ? undefined : message
  }
}

const isErrorBody = (value: unknown): value is ApiErrorBody =>
  typeof value === 'object' &&
  value !== null &&
  'error' in value &&
  typeof (value as ApiErrorBody).error?.message === 'string'

const readErrorResponse = async (
  response: Response,
  kind: 'http' | 'protocol' = 'http',
  fallback = response.statusText
): Promise<ApiError> => {
  const text = await response.text().catch(() => '')
  let message = text || fallback
  try {
    const body: unknown = JSON.parse(text)
    if (isErrorBody(body)) {
      message = body.error.message
    }
  } catch {
    // 非 JSON 响应体直接展示原文
  }
  return new ApiError(kind, message, response.status)
}

export const request = async (url: string, init: RequestInit): Promise<Response> => {
  let response: Response
  try {
    response = await fetch(url, init)
  } catch (error) {
    if (init.signal?.aborted) {
      throw error
    }
    throw new ApiError('network', error instanceof Error ? error.message : String(error))
  }
  if (!response.ok) {
    throw await readErrorResponse(response)
  }
  return response
}

const anthropicHeaders = (apiKey: string, beta: boolean): Record<string, string> => ({
  // DeepSeek 兼容接口的鉴权方式，也可使用 Authorization: Bearer。
  'x-api-key': apiKey,
  // Files API 和通过 file_id 引用图片时必传；纯文本聊天无需携带。
  ...(beta ? { 'anthropic-beta': FILES_BETA } : {}),
})

/** 用 GET /models 校验 API Key（不产生费用） */
export const verifyApiKey = async (apiKey: string, signal?: AbortSignal): Promise<void> => {
  await request(`${API_ORIGIN}/models`, {
    method: 'GET',
    headers: { Authorization: `Bearer ${apiKey}` },
    signal,
  })
}

/**
 * POST /anthropic/v1/files 上传附件（不设过期时间，会话里的历史附件一直可用）。
 * purpose 为 null 时不传该字段（图片）；purpose 必须排在 file 之前。
 */
export const uploadFile = async (
  apiKey: string,
  file: File,
  purpose: FilePurpose | null,
  signal?: AbortSignal
): Promise<FileObject> => {
  if (isGoActive()) {
    throw new ApiError(
      'http',
      'OpenCode Go 模式暂不支持附件，可在侧边栏切回 DeepSeek 后再上传'
    )
  }
  const form = new FormData()
  if (purpose) {
    form.append('purpose', purpose)
  }
  form.append('file', purpose === null ? await resizeUploadImage(file, signal) : file)
  const response = await request(`${ANTHROPIC_BASE_URL}/files`, {
    method: 'POST',
    headers: anthropicHeaders(apiKey, true),
    body: form,
    signal,
  })
  return (await response.json()) as FileObject
}

/** DELETE /anthropic/v1/files/{file_id} */
export const deleteFile = async (apiKey: string, fileId: string): Promise<void> => {
  await request(`${ANTHROPIC_BASE_URL}/files/${encodeURIComponent(fileId)}`, {
    method: 'DELETE',
    headers: anthropicHeaders(apiKey, true),
  })
}

const hasFile = (body: CreateMessageRequest) =>
  body.messages.some(
    (message) =>
      typeof message.content !== 'string' &&
      message.content.some((block) => block.type === 'image' || block.type === 'document')
  )

/**
 * POST /anthropic/v1/messages（stream: true），逐个产出流式事件，收到 message_stop 时结束。
 * ping、保活注释与无法解析的内容不产出；中止请求时抛出 signal 的 AbortError。
 */
export async function* streamMessage(
  apiKey: string,
  body: CreateMessageRequest,
  signal: AbortSignal,
  sessionId?: string
): AsyncGenerator<MessageStreamEvent> {
  const go = isGoActive()
  const response = go
    ? await request(`${GO_PROXY}/inference/go/anthropic/v1/messages`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${useGo.getState().key ?? ''}`,
          'anthropic-version': '2023-06-01',
          'x-opencode-session': goSessionHeader(sessionId),
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(toGoBody(body)),
        signal,
      })
    : await request(`${ANTHROPIC_BASE_URL}/messages`, {
        method: 'POST',
        headers: {
          ...anthropicHeaders(apiKey, hasFile(body)),
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify({ ...body, stream: true }),
        signal,
      })
  const contentType = response.headers.get('Content-Type') ?? ''
  if (!response.body || !contentType.includes('text/event-stream')) {
    throw await readErrorResponse(response, 'protocol', '')
  }
  for await (const { event: eventName, data } of readServerSentEvents(response.body, signal)) {
    let payload: unknown
    try {
      payload = JSON.parse(data)
    } catch {
      if (eventName !== 'error') {
        continue
      }
    }
    if (isErrorBody(payload)) {
      throw new ApiError('stream', payload.error.message)
    }
    if (eventName === 'error') {
      throw new ApiError('stream', data)
    }
    if (go) {
      payload = normalizeGoEvent(payload, body.title?.enabled ? goTitle(body) : undefined)
    }
    const event = readMessageEvent(payload)
    if (!event) {
      continue
    }
    yield event
    if (event.type === 'message_stop') {
      return
    }
  }
  // 服务端没有提供错误内容时保持为空，不推测断流原因。
  throw new ApiError('interrupted', '')
}

/** Go 走标准 Anthropic Messages：去掉 DeepSeek 专有字段，附件与联网结果转成文字 */
const toGoBody = (body: CreateMessageRequest) => {
  const thinking = body.thinking.type === 'enabled'
  let reminder = ''
  const converted = body.messages
    .filter((message) => {
      if ((message.role as string) === 'latest_reminder') {
        reminder = typeof message.content === 'string' ? message.content : ''
        return false
      }
      return true
    })
    .map((message) => {
      if (typeof message.content === 'string') {
        return message
      }
      const parts: string[] = []
      for (const block of message.content as { type: string; text?: string; title?: string }[]) {
        if (block.type === 'text' && block.text) {
          parts.push(block.text)
        } else if (block.type === 'image') {
          parts.push('[图片附件]')
        } else if (block.type === 'document') {
          parts.push(`[文件附件${block.title ? `：${block.title}` : ''}]`)
        }
      }
      return { role: message.role, content: parts.join('\n\n') }
    })
    .filter((message) => typeof message.content !== 'string' || message.content.trim())
  // 合并相邻同角色消息，保证 user / assistant 交替
  const messages: { role: string; content: string }[] = []
  for (const message of converted) {
    const content = typeof message.content === 'string' ? message.content : ''
    const last = messages.at(-1)
    if (last && last.role === message.role) {
      last.content += `\n\n${content}`
    } else {
      messages.push({ role: message.role, content })
    }
  }
  return {
    model: useGo.getState().model,
    system: `你是 Neon，一个简洁、可靠的 AI 助手。默认用用户使用的语言回答。${
      reminder ? `\n当前信息（日期,星期,位置,平台,语言）：${reminder}` : ''
    }`,
    messages,
    max_tokens: 16384,
    ...(thinking ? { thinking: { type: 'enabled', budget_tokens: 8192 } } : {}),
    stream: true,
  }
}

const STOP_REASONS = ['end_turn', 'max_tokens', 'tool_use', 'refusal']

/** 不同上游的停止原因不完全一致，统一成本应用认识的值 */
const normalizeGoEvent = (payload: unknown, title?: string) => {
  if (
    payload &&
    typeof payload === 'object' &&
    (payload as { type?: string }).type === 'message_delta'
  ) {
    const p = payload as { delta?: { stop_reason?: string | null }; usage?: unknown }
    const reason = p.delta?.stop_reason
    return {
      ...p,
      delta: {
        ...p.delta,
        stop_reason: reason && STOP_REASONS.includes(reason) ? reason : 'end_turn',
        ...(title ? { title } : {}),
      },
    }
  }
  return payload
}

/** Go 不会生成标题，用第一条提问截一段当标题 */
const goTitle = (body: CreateMessageRequest) => {
  const first = body.messages.find((m) => m.role === 'user')
  if (!first) {
    return undefined
  }
  const text =
    typeof first.content === 'string'
      ? first.content
      : (first.content as { type: string; text?: string }[])
          .filter((b) => b.type === 'text')
          .map((b) => b.text ?? '')
          .join(' ')
  const clean = text.replace(/\s+/g, ' ').trim()
  return clean ? (clean.length > 20 ? `${clean.slice(0, 20)}…` : clean) : undefined
}
