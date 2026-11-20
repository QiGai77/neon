declare module '*.module.css' {
  const classes: Record<string, string>
  export default classes
}

declare module '*.css' {}

/** 公开 API 的地址，见 rspack.config.ts 的 createConfig */
declare const __API_ORIGIN__: string
/** Google Drive 工作区默认配置，见 rspack.config.ts */
declare const __GOOGLE_CLIENT_ID__: string
declare const __GOOGLE_API_KEY__: string
declare const __GOOGLE_APP_ID__: string

/** Prism 语言包通过副作用注册到全局 Prism */
declare module 'prismjs/components/*' {}

/** KaTeX 化学公式扩展通过副作用注册到 KaTeX */
declare module 'katex/contrib/mhchem' {}
declare const __NEON_PROXY__: string
