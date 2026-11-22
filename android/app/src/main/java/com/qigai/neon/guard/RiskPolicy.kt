package com.qigai.neon.guard

enum class RiskLevel { SAFE, CONFIRM, BLOCKED }

data class RiskAssessment(
    val level: RiskLevel,
    val reasons: List<String>,
    val requireBiometric: Boolean = false,
)

object TapWords {
    private val highRiskKeywords = listOf(
        "支付", "付款", "确认支付", "立即付款", "转账", "确认下单", "提交订单",
        "发送", "群发", "删除", "注销", "清空", "授权", "同意并开通",
        "pay", "checkout", "transfer", "send", "delete", "remove", "confirm payment",
    )

    private val paymentAmountRegex = Regex("""[¥$€]\s*\d+(?:\.\d{1,2})?""")

    fun findMatches(text: String): List<String> {
        val lower = text.lowercase()
        val hits = mutableListOf<String>()
        highRiskKeywords.forEach { kw ->
            if (lower.contains(kw.lowercase())) hits += kw
        }
        paymentAmountRegex.find(text)?.value?.let { hits += "金额：$it" }
        return hits.distinct()
    }
}

object RiskPolicy {
    private val safeToolNames = setOf(
        "hands_dump_screen",
        "hands_screenshot",
        "gmail_list_messages",
        "gmail_read_message",
        "calendar_list_events",
        "drive_list_files",
        "phone_read_clipboard",
        "phone_list_notifications",
        "memory_read",
        "memory_search",
        "memory_save",
        "skill_list",
    )

    private val confirmToolNames = setOf(
        "gmail_send_message",
        "calendar_create_event",
        "drive_write_file",
        "webhook_trigger",
        "phone_share_text",
    )

    fun assessToolCall(
        toolName: String,
        targetText: String = "",
        targetPackage: String = "",
        isPasswordNode: Boolean = false,
        shellCommand: String = "",
    ): RiskAssessment {
        if (isPasswordNode) {
            return RiskAssessment(
                level = RiskLevel.BLOCKED,
                reasons = listOf("密码框需要你自己输入"),
            )
        }

        if (toolName == "sandbox_exec" && shellCommand.isNotBlank()) {
            val shellCheck = ShellGuard.inspect(shellCommand)
            if (shellCheck.isHighRisk) {
                return RiskAssessment(
                    level = RiskLevel.CONFIRM,
                    reasons = shellCheck.reasons,
                    requireBiometric = shellCheck.isDestructive,
                )
            }
            return RiskAssessment(RiskLevel.SAFE, emptyList())
        }

        if (toolName.startsWith("hands_")) {
            val matches = TapWords.findMatches(targetText)
            if (matches.isNotEmpty()) {
                val hasPayment = matches.any { it.contains("支付") || it.contains("付款") || it.contains("转账") || it.startsWith("金额：") || it.contains("pay") }
                return RiskAssessment(
                    level = RiskLevel.CONFIRM,
                    reasons = matches.map { "按钮：$it" } + if (targetPackage.isNotBlank()) listOf("应用：$targetPackage") else emptyList(),
                    requireBiometric = hasPayment,
                )
            }
        }

        if (toolName in safeToolNames) {
            return RiskAssessment(RiskLevel.SAFE, emptyList())
        }

        if (toolName in confirmToolNames || toolName.startsWith("mcp_")) {
            return RiskAssessment(
                level = RiskLevel.CONFIRM,
                reasons = listOf("会写入或发送：$toolName"),
                requireBiometric = toolName == "gmail_send_message",
            )
        }

        return RiskAssessment(RiskLevel.SAFE, emptyList())
    }
}
