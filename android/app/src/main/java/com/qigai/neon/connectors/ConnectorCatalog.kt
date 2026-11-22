package com.qigai.neon.connectors

/** How a preset signs in. */
enum class PresetAuth {
    /** Public server, no sign-in. */
    NONE,
    /** OAuth with dynamic client registration, opened in a Custom Tab. */
    OAUTH,
    /** A personal token sent as `Authorization: Bearer …`. */
    TOKEN,
    /** A key that goes into the URL as {key}. */
    URL_KEY,
}

enum class PresetGroup(val label: String) {
    DEV("开发"),
    WORK("办公协作"),
    KNOWLEDGE("搜索与文档"),
    BUSINESS("商业与支付"),
    LOCAL("沙箱本地"),
}

data class ConnectorPreset(
    /** Also the connector id and tool prefix, so letters and digits only. */
    val id: String,
    val name: String,
    val desc: String,
    val group: PresetGroup,
    /** Remote MCP endpoint, or the sandbox command for [PresetGroup.LOCAL]. */
    val target: String,
    val auth: PresetAuth = PresetAuth.NONE,
    /** Where to get the token or key. */
    val keyUrl: String? = null,
    val keyHint: String? = null,
) {
    val local: Boolean get() = group == PresetGroup.LOCAL
    val monogram: String get() = name.first().uppercase()
}

/** Built-in connectors. Every remote endpoint here answered an MCP initialize or an OAuth challenge when it was added. */
object ConnectorCatalog {
    val presets: List<ConnectorPreset> = listOf(
        // 开发
        ConnectorPreset("github", "GitHub", "仓库和协作", PresetGroup.DEV, "https://api.githubcopilot.com/mcp/",
            PresetAuth.TOKEN, "https://github.com/settings/personal-access-tokens/new", "GitHub 个人访问令牌"),
        ConnectorPreset("context7", "Context7", "最新的库文档和代码示例", PresetGroup.DEV, "https://mcp.context7.com/mcp"),
        ConnectorPreset("deepwiki", "DeepWiki", "读懂任意公开 GitHub 仓库", PresetGroup.DEV, "https://mcp.deepwiki.com/mcp"),
        ConnectorPreset("grepapp", "Grep", "在百万个公开仓库里搜代码", PresetGroup.DEV, "https://mcp.grep.app"),
        ConnectorPreset("huggingface", "Hugging Face", "模型和数据集", PresetGroup.DEV, "https://huggingface.co/mcp"),
        ConnectorPreset("sentry", "Sentry", "错误和性能问题", PresetGroup.DEV, "https://mcp.sentry.dev/mcp", PresetAuth.OAUTH),
        ConnectorPreset("vercel", "Vercel", "部署和日志", PresetGroup.DEV, "https://mcp.vercel.com", PresetAuth.OAUTH),
        ConnectorPreset("supabase", "Supabase", "数据库和后端", PresetGroup.DEV, "https://mcp.supabase.com/mcp", PresetAuth.OAUTH),
        ConnectorPreset("cloudflare", "Cloudflare", "Workers 和存储", PresetGroup.DEV, "https://bindings.mcp.cloudflare.com/mcp", PresetAuth.OAUTH),
        ConnectorPreset("netlify", "Netlify", "站点和部署", PresetGroup.DEV, "https://netlify-mcp.netlify.app/mcp", PresetAuth.OAUTH),
        ConnectorPreset("neondb", "Neon Postgres", "Serverless Postgres", PresetGroup.DEV, "https://mcp.neon.tech/mcp", PresetAuth.OAUTH),
        ConnectorPreset("posthog", "PostHog", "产品分析和功能开关", PresetGroup.DEV, "https://mcp.posthog.com/mcp", PresetAuth.OAUTH),
        // 办公协作
        ConnectorPreset("notion", "Notion", "页面和数据库", PresetGroup.WORK, "https://mcp.notion.com/mcp", PresetAuth.OAUTH),
        ConnectorPreset("linear", "Linear", "Issue 和项目", PresetGroup.WORK, "https://mcp.linear.app/mcp", PresetAuth.OAUTH),
        ConnectorPreset("atlassian", "Atlassian", "Jira 和 Confluence", PresetGroup.WORK, "https://mcp.atlassian.com/v1/mcp", PresetAuth.OAUTH),
        ConnectorPreset("canva", "Canva", "设计和模板", PresetGroup.WORK, "https://mcp.canva.com/mcp", PresetAuth.OAUTH),
        ConnectorPreset("airtable", "Airtable", "表格和记录", PresetGroup.WORK, "https://mcp.airtable.com/mcp", PresetAuth.OAUTH),
        ConnectorPreset("monday", "monday.com", "看板和任务", PresetGroup.WORK, "https://mcp.monday.com/mcp", PresetAuth.OAUTH),
        ConnectorPreset("intercom", "Intercom", "客户对话和工单", PresetGroup.WORK, "https://mcp.intercom.com/mcp", PresetAuth.OAUTH),
        ConnectorPreset("webflow", "Webflow", "站点内容和 CMS", PresetGroup.WORK, "https://mcp.webflow.com/mcp", PresetAuth.OAUTH),
        // 搜索与文档
        ConnectorPreset("exa", "Exa", "联网搜索和网页抓取", PresetGroup.KNOWLEDGE, "https://mcp.exa.ai/mcp"),
        ConnectorPreset("mslearn", "Microsoft Learn", "微软官方文档", PresetGroup.KNOWLEDGE, "https://learn.microsoft.com/api/mcp"),
        ConnectorPreset("awsdocs", "AWS 文档", "AWS 官方文档和 API 参考", PresetGroup.KNOWLEDGE, "https://knowledge-mcp.global.api.aws"),
        ConnectorPreset("cfdocs", "Cloudflare 文档", "Cloudflare 开发者文档", PresetGroup.KNOWLEDGE, "https://docs.mcp.cloudflare.com/mcp"),
        ConnectorPreset("tavily", "Tavily", "为 AI 优化的搜索", PresetGroup.KNOWLEDGE, "https://mcp.tavily.com/mcp/", PresetAuth.OAUTH),
        ConnectorPreset("amap", "高德地图", "地点和路线", PresetGroup.KNOWLEDGE, "https://mcp.amap.com/mcp?key={key}",
            PresetAuth.URL_KEY, "https://console.amap.com/dev/key/app", "高德 Web 服务 Key"),
        // 商业与支付
        ConnectorPreset("stripe", "Stripe", "支付和订阅", PresetGroup.BUSINESS, "https://mcp.stripe.com", PresetAuth.OAUTH),
        ConnectorPreset("paypal", "PayPal", "发票和交易", PresetGroup.BUSINESS, "https://mcp.paypal.com/mcp", PresetAuth.OAUTH),
        ConnectorPreset("square", "Square", "订单和付款", PresetGroup.BUSINESS, "https://mcp.squareup.com/mcp", PresetAuth.OAUTH),
        // 沙箱本地
        ConnectorPreset("fs", "文件系统", "读写 /workspace 里的文件", PresetGroup.LOCAL, "npx -y @modelcontextprotocol/server-filesystem /workspace"),
        ConnectorPreset("memory", "知识图谱", "跨对话记住实体和关系", PresetGroup.LOCAL, "npx -y @modelcontextprotocol/server-memory"),
        ConnectorPreset("thinking", "分步推理", "把难题拆成可修正的步骤", PresetGroup.LOCAL, "npx -y @modelcontextprotocol/server-sequential-thinking"),
        ConnectorPreset("fetch", "网页抓取", "把网页转成 Markdown", PresetGroup.LOCAL, "uvx mcp-server-fetch"),
        ConnectorPreset("git", "Git", "提交和分支", PresetGroup.LOCAL, "uvx mcp-server-git"),
        ConnectorPreset("time", "时间", "时区换算和当前时间", PresetGroup.LOCAL, "uvx mcp-server-time --local-timezone Asia/Shanghai"),
    )

    fun byId(id: String) = presets.firstOrNull { it.id == id }
}
