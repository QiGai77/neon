package com.qigai.neon.guard

/** 动手前: how often Neon stops to ask before acting. */
enum class ApprovalMode(val key: String, val label: String) {
    ALWAYS_ASK("always_ask", "每次都问"),
    IMPORTANT("important", "重要的才问"),
    ALWAYS_ALLOW("always_allow", "总是允许");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: IMPORTANT
    }
}

/** The kinds of action that can be pinned to ask or allow regardless of the mode. */
enum class ActionType(val key: String, val label: String) {
    PAYMENT("payment", "付款"),
    DELETE("delete", "删除文件和数据"),
    SEND("send", "发消息和邮件"),
    HANDS("hands", "操作手机上的 App"),
    OTHER("other", "其他");

    companion object {
        val pinnable = listOf(PAYMENT, DELETE, SEND, HANDS)
    }
}

data class ApprovalSettings(
    val mode: ApprovalMode = ApprovalMode.IMPORTANT,
    /** Per type: true = ask, false = allow. Missing = follow the mode. */
    val overrides: Map<ActionType, Boolean> = DEFAULT_OVERRIDES,
    val biometric: Boolean = true,
) {
    /** What the 这些还是问我 row shows for a type: its pin, or what the mode does for it. */
    fun asks(type: ActionType): Boolean = overrides[type] ?: modeAsks(type)

    private fun modeAsks(type: ActionType) = when (mode) {
        ApprovalMode.ALWAYS_ASK -> true
        ApprovalMode.IMPORTANT -> type != ActionType.HANDS
        ApprovalMode.ALWAYS_ALLOW -> false
    }

    /** One line for the 我的 card, e.g. 总是允许，付款要指纹. */
    fun summary(): String {
        val extra = ActionType.pinnable.filter { overrides[it] == true && !modeAsks(it) }
        val tail = when {
            extra.isEmpty() -> null
            extra.size == 1 && biometric -> extra[0].shortLabel + "要指纹"
            else -> extra.joinToString("、") { it.shortLabel } + "先问"
        }
        return listOfNotNull(mode.label, tail).joinToString("，")
    }

    companion object {
        val DEFAULT_OVERRIDES = mapOf(ActionType.PAYMENT to true)
    }
}

private val ActionType.shortLabel: String
    get() = when (this) {
        ActionType.PAYMENT -> "付款"
        ActionType.DELETE -> "删除"
        ActionType.SEND -> "发消息"
        ActionType.HANDS -> "操作 App"
        ActionType.OTHER -> "其他"
    }

/** Decides whether a step needs the Sentinel sheet. */
object ApprovalPolicy {
    private val deleteWords = listOf("删除", "注销", "清空", "delete", "remove")
    private val sendWords = listOf("发送", "群发", "send")
    private val payWords = listOf("支付", "付款", "转账", "下单", "提交订单", "pay", "checkout", "transfer")

    /** Tools that only change things when the user asked for every step to be confirmed (每次都问). */
    private val writeTools = setOf(
        "drive_write_file", "phone_open_deeplink", "phone_share_text", "hands_open_app", "hands_input",
        "space_cards", "calendar_create_event", "webhook_trigger",
        "web_click", "web_type", "web_fill",
    )
    private val sendTools = setOf("gmail_send_message", "phone_share_text", "webhook_trigger")

    /** Type of a tool call, using any words it carries (a button label, a command). */
    fun typeOf(toolName: String, text: String = "", destructive: Boolean = false): ActionType {
        val t = text.lowercase()
        val acts = toolName.startsWith("hands_") || toolName.startsWith("web_")
        return when {
            payWords.any { t.contains(it) } || Regex("""[¥$€]\s*\d""").containsMatchIn(text) -> ActionType.PAYMENT
            destructive || (acts && deleteWords.any { t.contains(it) }) -> ActionType.DELETE
            toolName in sendTools || (acts && sendWords.any { t.contains(it) }) -> ActionType.SEND
            toolName.startsWith("hands_") -> ActionType.HANDS
            else -> ActionType.OTHER
        }
    }

    /** Whether this tool writes, sends or opens something (asked about in 每次都问). */
    fun isWrite(toolName: String) = toolName in writeTools || toolName.startsWith("mcp_") || toolName.startsWith("localmcp_")

    /**
     * [base] is what the built-in risk rules say. A type pin wins; otherwise the mode:
     * 每次都问 asks for risky steps and every write, 重要的才问 asks for risky steps, 总是允许 asks nothing.
     * A password field stays blocked whatever is set.
     */
    fun shouldAsk(s: ApprovalSettings, type: ActionType, base: RiskLevel, write: Boolean): Boolean {
        if (base == RiskLevel.BLOCKED) return true
        s.overrides[type]?.let { pinned ->
            // HANDS pins apply to every phone step; other pins only when the step really is that kind.
            if (type == ActionType.HANDS || type != ActionType.OTHER) return pinned
        }
        return when (s.mode) {
            ApprovalMode.ALWAYS_ASK -> base == RiskLevel.CONFIRM || write
            ApprovalMode.IMPORTANT -> base == RiskLevel.CONFIRM
            ApprovalMode.ALWAYS_ALLOW -> false
        }
    }

    /** Fingerprint only when the user kept it on; payments always use it then. */
    fun needsBiometric(s: ApprovalSettings, type: ActionType, base: Boolean) =
        s.biometric && (base || type == ActionType.PAYMENT)
}
