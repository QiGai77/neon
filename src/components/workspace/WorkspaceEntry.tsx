import clsx from 'clsx'
import { useDrive } from '../../services/drive/drive.js'
import { DriveIcon } from './DriveIcon.js'
import { openWorkspace, useL } from './WorkspaceDialog.js'
import styles from './Workspace.module.css'

/** 侧边栏里的工作区入口：显示 Google Drive 连接状态 */
export const WorkspaceEntry = () => {
  const L = useL()
  const status = useDrive((s) => s.status)
  const syncing = useDrive((s) => s.syncing)
  return (
    <button type="button" className={styles.entry} onClick={openWorkspace}>
      <span className={styles.entryIcon}>
        <DriveIcon size={18} />
      </span>
      <span className={styles.entryLabel}>{L('工作区', 'Workspace')}</span>
      <span className={styles.entryStatus}>
        {status === 'connected'
          ? syncing
            ? L('同步中', 'Syncing')
            : 'Drive'
          : status === 'expired'
            ? L('需授权', 'Re-auth')
            : L('未连接', 'Off')}
      </span>
      <span className={clsx(styles.dot, styles[status])} />
    </button>
  )
}
