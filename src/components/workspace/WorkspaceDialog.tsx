import clsx from 'clsx'
import { useEffect, useState } from 'react'
import { create } from 'zustand'
import { useSettings } from '../../services/settings.js'
import {
  canPick,
  connectDrive,
  disconnectDrive,
  folderUrl,
  listFolder,
  pickFromDrive,
  resetTokenClient,
  setDriveConfig,
  setMirrorUploads,
  setSyncSessions,
  useDrive,
  type DriveFile,
  type DriveFolderKey,
} from '../../services/drive/drive.js'
import { attachDriveFiles, backupToDrive, syncSessions } from '../../services/drive/sync.js'
import { Button } from '../base/Button.js'
import { Modal } from '../base/Modal.js'
import { showToast } from '../base/toastState.js'
import { DriveIcon } from './DriveIcon.js'
import styles from './Workspace.module.css'

export const useWorkspaceDialog = create(() => ({ open: false }))
export const openWorkspace = () => useWorkspaceDialog.setState({ open: true })
const closeWorkspace = () => useWorkspaceDialog.setState({ open: false })

/** 中英文案：工作区是 Neon 新增的功能，文案就近写在组件里 */
export const useL = () => {
  const zh = useSettings((s) => s.locale) === 'zh-CN'
  return (cn: string, en: string) => (zh ? cn : en)
}

const errText = (e: unknown) => (e instanceof Error ? e.message : String(e))

export const WorkspaceDialog = () => {
  const open = useWorkspaceDialog((s) => s.open)
  return open ? <WorkspaceContent /> : null
}

const formatTime = (ts?: string | number | null) => {
  if (!ts) {
    return ''
  }
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const formatSize = (size?: string) => {
  const n = Number(size)
  if (!n) {
    return ''
  }
  return n > 1024 * 1024
    ? `${(n / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(n / 1024))} KB`
}

const WorkspaceContent = () => {
  const L = useL()
  const drive = useDrive()
  const [tab, setTab] = useState<DriveFolderKey>('attachments')
  const [files, setFiles] = useState<DriveFile[] | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [showConfig, setShowConfig] = useState(!drive.clientId)
  const connected = drive.status === 'connected'

  const refresh = async (folder = tab) => {
    if (!connected) {
      return
    }
    setFiles(null)
    try {
      setFiles(await listFolder(folder))
    } catch (e) {
      setFiles([])
      showToast(errText(e))
    }
  }

  useEffect(() => {
    void refresh(tab)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, connected])

  const run = async (key: string, task: () => Promise<unknown>, done?: string) => {
    setBusy(key)
    try {
      await task()
      if (done) {
        showToast(done)
      }
    } catch (e) {
      showToast(errText(e))
    } finally {
      setBusy(null)
    }
  }

  return (
    <Modal
      title={
        <span className={styles.title}>
          <DriveIcon size={20} />
          {L('工作区 · Google Drive', 'Workspace · Google Drive')}
        </span>
      }
      closable
      className={styles.dialog}
      mainClassName={styles.main}
      onClose={closeWorkspace}
    >
      {/* 连接状态 */}
      <div className={styles.statusCard}>
        <span className={clsx(styles.dot, styles[drive.status])} />
        <div className={styles.statusText}>
          <div className={styles.statusTitle}>
            {connected
              ? L('已连接', 'Connected')
              : drive.status === 'expired'
                ? L('授权已过期', 'Authorization expired')
                : drive.status === 'connecting'
                  ? L('正在连接…', 'Connecting…')
                  : L('未连接', 'Not connected')}
            {drive.email ? <span className={styles.email}>{drive.email}</span> : null}
          </div>
          <div className={styles.statusSub}>
            {connected
              ? drive.syncing
                ? L('正在同步…', 'Syncing…')
                : drive.lastSyncAt
                  ? L(
                      `上次同步 ${formatTime(drive.lastSyncAt)}`,
                      `Last synced ${formatTime(drive.lastSyncAt)}`
                    )
                  : L('对话会保存到「我的云端硬盘/Neon」', 'Chats are saved to "My Drive/Neon"')
              : (drive.error ??
                L(
                  'Neon 只能访问它自己建的文件和你手动选中的文件',
                  'Neon only sees files it creates or you pick'
                ))}
          </div>
        </div>
        {connected ? (
          <>
            <a
              className={styles.link}
              href={folderUrl(drive.folders.root)}
              target="_blank"
              rel="noreferrer"
            >
              {L('打开文件夹', 'Open folder')}
            </a>
            <Button variant="outlined" size="s" onClick={() => void disconnectDrive()}>
              {L('断开', 'Disconnect')}
            </Button>
          </>
        ) : (
          <Button
            size="s"
            disabled={!drive.clientId || drive.status === 'connecting'}
            onClick={() => void connectDrive(drive.status !== 'expired')}
          >
            {drive.status === 'expired'
              ? L('重新授权', 'Re-authorize')
              : L('连接 Google Drive', 'Connect')}
          </Button>
        )}
      </div>

      {connected ? (
        <>
          {/* 开关 */}
          <label className={styles.row}>
            <div>
              <div className={styles.rowTitle}>{L('自动同步对话', 'Auto-sync chats')}</div>
              <div className={styles.rowSub}>
                {L(
                  '每个对话存成一个 JSON，删除对话时移到 Drive 回收站',
                  'One JSON per chat; deleted chats go to Drive trash'
                )}
              </div>
            </div>
            <input
              type="checkbox"
              className={styles.switch}
              checked={drive.syncSessions}
              onChange={(e) => setSyncSessions(e.target.checked)}
            />
          </label>
          <label className={styles.row}>
            <div>
              <div className={styles.rowTitle}>
                {L('上传的附件存回 Drive', 'Save uploads to Drive')}
              </div>
              <div className={styles.rowSub}>
                {L(
                  '聊天里添加的文件，同时存一份到 Neon/附件',
                  'Files you attach are copied to Neon/附件'
                )}
              </div>
            </div>
            <input
              type="checkbox"
              className={styles.switch}
              checked={drive.mirrorUploads}
              onChange={(e) => setMirrorUploads(e.target.checked)}
            />
          </label>

          <div className={styles.actions}>
            <Button
              size="s"
              disabled={busy !== null || !canPick()}
              title={
                canPick()
                  ? undefined
                  : L(
                      '需要在下方高级设置里填 API Key 和项目编号',
                      'Needs API key + project number below'
                    )
              }
              onClick={() =>
                void run('pick', async () => {
                  const picked = await pickFromDrive()
                  const n = await attachDriveFiles(picked)
                  if (n) {
                    showToast(L(`已添加 ${n} 个文件到对话`, `Added ${n} file(s)`))
                    closeWorkspace()
                  }
                })
              }
            >
              {L('从 Drive 选择文件', 'Pick from Drive')}
            </Button>
            <Button
              variant="outlined"
              size="s"
              disabled={busy !== null}
              onClick={() =>
                void run('sync', () => syncSessions(true), L('对话已同步', 'Chats synced'))
              }
            >
              {busy === 'sync' ? L('同步中…', 'Syncing…') : L('立即同步全部对话', 'Sync all now')}
            </Button>
            <Button
              variant="outlined"
              size="s"
              disabled={busy !== null}
              onClick={() =>
                void run(
                  'backup',
                  async () => {
                    await backupToDrive()
                    if (tab === 'backups') {
                      await refresh('backups')
                    }
                  },
                  L('完整备份已存到 Neon/备份', 'Backup saved')
                )
              }
            >
              {busy === 'backup'
                ? L('备份中…', 'Backing up…')
                : L('备份完整历史', 'Back up history')}
            </Button>
          </div>

          {/* 文件浏览 */}
          <div className={styles.tabs}>
            {(
              [
                ['attachments', L('附件', 'Attachments')],
                ['sessions', L('对话', 'Chats')],
                ['backups', L('备份', 'Backups')],
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                type="button"
                className={clsx(styles.tab, tab === key && styles.tabActive)}
                onClick={() => setTab(key)}
              >
                {label}
              </button>
            ))}
          </div>
          <div className={styles.list}>
            {files === null ? (
              <div className={styles.empty}>{L('读取中…', 'Loading…')}</div>
            ) : files.length === 0 ? (
              <div className={styles.empty}>{L('这里还没有文件', 'Nothing here yet')}</div>
            ) : (
              files.map((f) => (
                <div key={f.id} className={styles.file}>
                  {f.iconLink ? (
                    <img className={styles.fileIcon} src={f.iconLink} alt="" />
                  ) : (
                    <span className={styles.fileIcon} />
                  )}
                  <div className={styles.fileText}>
                    <div className={styles.fileName}>{f.name}</div>
                    <div className={styles.fileMeta}>
                      {[formatTime(f.modifiedTime), formatSize(f.size)].filter(Boolean).join(' · ')}
                    </div>
                  </div>
                  {tab === 'attachments' ? (
                    <Button
                      variant="outlined"
                      size="s"
                      disabled={busy !== null}
                      onClick={() =>
                        void run(f.id, async () => {
                          if (await attachDriveFiles([f])) {
                            showToast(L('已添加到对话', 'Added to chat'))
                            closeWorkspace()
                          }
                        })
                      }
                    >
                      {L('加到对话', 'Attach')}
                    </Button>
                  ) : null}
                  {f.webViewLink ? (
                    <a
                      className={styles.link}
                      href={f.webViewLink}
                      target="_blank"
                      rel="noreferrer"
                    >
                      {L('查看', 'View')}
                    </a>
                  ) : null}
                </div>
              ))
            )}
          </div>
          {tab === 'backups' ? (
            <div className={styles.hint}>
              {L(
                '恢复备份：在 Drive 下载 ZIP，再到「设置 → 数据管理 → 导入」。',
                'To restore: download a ZIP, then Settings → Data → Import.'
              )}
            </div>
          ) : null}
        </>
      ) : null}

      {/* 高级设置：OAuth 客户端 */}
      <button
        type="button"
        className={styles.configToggle}
        onClick={() => setShowConfig((v) => !v)}
      >
        {showConfig ? '▾' : '▸'} {L('Google Cloud 配置', 'Google Cloud setup')}
      </button>
      {showConfig ? <ConfigForm onSaved={() => setShowConfig(false)} /> : null}
    </Modal>
  )
}

const ConfigForm = ({ onSaved }: { onSaved: () => void }) => {
  const L = useL()
  const drive = useDrive()
  const [clientId, setClientId] = useState(drive.clientId)
  const [apiKey, setApiKey] = useState(drive.apiKey)
  const [appId, setAppId] = useState(drive.appId)
  return (
    <div className={styles.config}>
      <p className={styles.hint}>
        {L(
          'Neon 是纯静态网页，需要你自己的 Google OAuth 客户端。步骤见 README 的「Google Drive 工作区」。授权来源要填 Neon 的访问地址，例如 http://localhost:8090。',
          'Neon is static, so it needs your own Google OAuth client. See README → Google Drive workspace.'
        )}
      </p>
      <label className={styles.field}>
        <span>{L('OAuth 客户端 ID（必填）', 'OAuth client ID (required)')}</span>
        <input
          value={clientId}
          placeholder="xxxx.apps.googleusercontent.com"
          onChange={(e) => setClientId(e.target.value)}
        />
      </label>
      <label className={styles.field}>
        <span>{L('API Key（选填，用于文件选择器）', 'API key (optional, for Picker)')}</span>
        <input value={apiKey} placeholder="AIza…" onChange={(e) => setApiKey(e.target.value)} />
      </label>
      <label className={styles.field}>
        <span>
          {L('项目编号（选填，用于文件选择器）', 'Project number (optional, for Picker)')}
        </span>
        <input
          value={appId}
          placeholder="123456789012"
          onChange={(e) => setAppId(e.target.value)}
        />
      </label>
      <div>
        <Button
          size="s"
          onClick={() => {
            setDriveConfig({ clientId, apiKey, appId })
            resetTokenClient()
            showToast(L('已保存', 'Saved'))
            onSaved()
          }}
        >
          {L('保存', 'Save')}
        </Button>
      </div>
    </div>
  )
}
