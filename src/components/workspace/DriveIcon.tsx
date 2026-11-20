/** 线性风格的云盘图标，跟随文字颜色，和其他图标保持一致 */
export const DriveIcon = ({ size = 16 }: { size?: number }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={1.7}
    strokeLinejoin="round"
    strokeLinecap="round"
  >
    <path d="M8.6 3.5h6.8l6.1 10.6-3.4 5.9H5.9l-3.4-5.9z" />
    <path d="M8.6 3.5l6.2 10.6H21.5" />
    <path d="M15.4 3.5L9.2 14.1 5.9 20" />
  </svg>
)
