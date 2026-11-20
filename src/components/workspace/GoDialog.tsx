import clsx from 'clsx'
import { useEffect, useRef, useState } from 'react'
import { create } from 'zustand'
import {
  DEFAULT_GO_MODEL,
  importGoKey,
  loadGoModels,
  parseGoImport,
  refreshGoUsage,
  removeGoKey,
  setGoModel,
  setProvider,
  useGo,
  type GoWindow,
} from '../../services/opencode/go.js'
import { Button } from '../base/Button.js'
import { Modal } from '../base/Modal.js'
import { showToast } from '../base/toastState.js'
import { useL } from './WorkspaceDialog.js'
import styles from './Workspace.module.css'

export const useGoDialog = create(() => ({ open: false }))
export const openGo = () => {
  useGoDialog.setState({ open: true })
  void loadGoModels()
  void refreshGoUsage()
}
const closeGo = () => useGoDialog.setState({ open: false })

export const GoDialog = () => {
  const open = useGoDialog((s) => s.open)
  return open ? <GoContent /> : null
}

/** OpenCode 的标志：方框里的光标 */
export const GoIcon = ({ size = 18 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden>
    <rect x="3.5" y="3.5" width="17" height="17" rx="4" stroke="currentColor" strokeWidth="1.6" />
    <path d="M8 9l3 3-3 3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    <path d="M13 15h3.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
  </svg>
)

const resetText = (w: GoWindow | null, zh: boolean) => {
  if (!w?.resetsAt) {
    return ''
  }
  const ms = new Date(w.resetsAt).getTime() - Date.now()
  if (!Number.isFinite(ms) || ms <= 0) {
    return ''
  }
  const h = Math.floor(ms / 3_600_000)
  const m = Math.round((ms % 3_600_000) / 60_000)
  const span = h >= 48 ? `${Math.round(h / 24)}${zh ? ' 天' : 'd'}` : `${h}h ${m}m`
  return zh ? `${span}后重置` : `resets in ${span}`
}

const Meter = ({ label, w }: { label: string; w: GoWindow | null }) => {
  const L = useL()
  const zh = L('zh', 'en') === 'zh'
  const pct = Math.max(0, Math.min(100, w?.percent ?? 0))
  const left = 100 - pct
  return (
    <div className={styles.meter}>
      <div className={styles.meterHead}>
        <span>{label}</span>
        <span className={styles.meterValue}>
          {w ? L(`剩 ${left.toFixed(left < 10 ? 1 : 0)}%`, `${left.toFixed(0)}% left`) : '—'}
        </span>
      </div>
      <div className={styles.meterTrack}>
        <div
          className={clsx(styles.meterFill, pct >= 90 && styles.meterHot)}
          style={{ width: `${pct}%` }}
        />
      </div>
      <div className={styles.meterSub}>{resetText(w, zh)}</div>
    </div>
  )
}

const GoContent = () => {
  const L = useL()
  const go = useGo()
  const [value, setValue] = useState('')
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)
  const active = go.provider === 'opencode-go' && !!go.key

  useEffect(() => {
    if (!go.key) {
      return
    }
    const timer = setInterval(() => void refreshGoUsage(), 60_000)
    return () => clearInterval(timer)
  }, [go.key])

  const doImport = async (text: string) => {
    const key = parseGoImport(text)
    if (!key) {
      showToast(L('没认出 Key，请粘贴 Go 的 API Key 或 auth.json', 'No key found'))
      return
    }
    setBusy(true)
    try {
      await importGoKey(key)
      setValue('')
      showToast(L('已导入 OpenCode Go 订阅', 'OpenCode Go imported'))
    } catch (e) {
      showToast(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  const models = go.models.length
    ? go.models
    : [{ id: go.model || DEFAULT_GO_MODEL, name: go.model || DEFAULT_GO_MODEL, reasoning: true, vision: false }]

  return (
    <Modal
      title={
        <span className={styles.title}>
          <GoIcon size={20} /> OpenCode Go
        </span>
      }
      closable
      className={styles.dialog}
      mainClassName={styles.main}
      onClose={closeGo}
    >
      <div className={styles.statusCard}>
        <span className={clsx(styles.dot, go.key ? styles.connected : undefined)} />
        <div className={styles.statusText}>
          <div className={styles.statusTitle}>
            {go.key ? L('已导入订阅', 'Subscription imported') : L('未导入', 'Not imported')}
            {go.key ? <span className={styles.email}>••••{go.key.slice(-4)}</span> : null}
          </div>
          <div className={styles.statusSub}>
            {go.key
              ? active
                ? L('对话正在使用 OpenCode Go', 'Chats use OpenCode Go')
                : L('对话正在使用 DeepSeek', 'Chats use DeepSeek')
              : L(
                  '粘贴 Go 的 API Key，或导入 OpenCode 的 auth.json',
                  'Paste your Go API key or OpenCode auth.json'
                )}
          </div>
        </div>
        {go.key ? (
          <Button
            size="s"
            variant={active ? 'outlined' : undefined}
            onClick={() => setProvider(active ? 'deepseek' : 'opencode-go')}
          >
            {active ? L('切回 DeepSeek', 'Use DeepSeek') : L('使用 Go', 'Use Go')}
          </Button>
        ) : null}
      </div>

      {go.key ? (
        <>
          <div className={styles.meters}>
            <Meter label={L('5 小时', '5 hours')} w={go.usage?.rolling ?? null} />
            <Meter label={L('本周', 'Week')} w={go.usage?.weekly ?? null} />
            <Meter label={L('本月', 'Month')} w={go.usage?.monthly ?? null} />
          </div>
          <div className={styles.rowSub}>
            {go.usageError
              ? L(`额度查询失败：${go.usageError}`, `Usage failed: ${go.usageError}`)
              : go.usage
                ? L(
                    `更新于 ${new Date(go.usage.fetchedAt).toLocaleTimeString()} · 每分钟自动刷新`,
                    `Updated ${new Date(go.usage.fetchedAt).toLocaleTimeString()}`
                  )
                : L('正在查询额度…', 'Loading usage…')}
          </div>

          <label className={styles.field}>
            {L('模型', 'Model')}
            <select
              className={styles.select}
              value={go.model}
              onChange={(e) => setGoModel(e.target.value)}
            >
              {models.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                  {m.reasoning ? L(' · 可深度思考', ' · reasoning') : ''}
                </option>
              ))}
            </select>
          </label>

          <div className={styles.actions}>
            <Button size="s" variant="outlined" disabled={go.loading} onClick={() => void refreshGoUsage()}>
              {go.loading ? L('查询中…', 'Loading…') : L('刷新额度', 'Refresh')}
            </Button>
            <Button size="s" variant="outlined" onClick={removeGoKey}>
              {L('移除订阅', 'Remove')}
            </Button>
          </div>
          <div className={styles.hint}>
            {L(
              'Go 模式下暂不支持附件和联网搜索；深度思考取决于所选模型。Key 只存在本机浏览器。',
              'Attachments and web search are off in Go mode. Your key stays in this browser.'
            )}
          </div>
        </>
      ) : (
        <div className={styles.config}>
          <label className={styles.field}>
            API Key
            <input
              value={value}
              placeholder="sk-… / oc_sk_…"
              onChange={(e) => setValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  void doImport(value)
                }
              }}
            />
          </label>
          <div className={styles.actions}>
            <Button size="s" disabled={busy || !value.trim()} onClick={() => void doImport(value)}>
              {busy ? L('验证中…', 'Verifying…') : L('导入', 'Import')}
            </Button>
            <Button size="s" variant="outlined" disabled={busy} onClick={() => fileRef.current?.click()}>
              {L('选择 auth.json', 'Choose auth.json')}
            </Button>
            <input
              ref={fileRef}
              type="file"
              accept=".json,application/json"
              hidden
              onChange={(e) => {
                const file = e.target.files?.[0]
                e.target.value = ''
                if (file) {
                  void file.text().then(doImport)
                }
              }}
            />
          </div>
          <div className={styles.hint}>
            {L(
              '在 opencode.ai/console 的 Go 页面复制 API Key；或导入本机 OpenCode 的 auth.json（~/.local/share/opencode/auth.json）。',
              'Copy the key from opencode.ai/console, or import ~/.local/share/opencode/auth.json.'
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}

/** 侧边栏入口：显示 5 小时窗口剩余额度 */
export const GoEntry = () => {
  const L = useL()
  const key = useGo((s) => s.key)
  const provider = useGo((s) => s.provider)
  const usage = useGo((s) => s.usage)
  useEffect(() => {
    if (key) {
      void refreshGoUsage()
    }
  }, [key])
  const left = usage?.rolling ? Math.max(0, 100 - usage.rolling.percent) : null
  return (
    <button type="button" className={styles.entry} onClick={openGo}>
      <span className={styles.entryIcon}>
        <GoIcon />
      </span>
      <span className={styles.entryLabel}>OpenCode Go</span>
      <span className={styles.entryStatus}>
        {key
          ? left !== null
            ? L(`剩 ${left.toFixed(0)}%`, `${left.toFixed(0)}% left`)
            : L('已导入', 'On')
          : L('导入', 'Import')}
      </span>
      <span className={clsx(styles.dot, key && provider === 'opencode-go' && styles.connected)} />
    </button>
  )
}
