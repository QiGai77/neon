package com.qigai.neon.guard

data class ShellInspection(
    val isHighRisk: Boolean,
    val isDestructive: Boolean,
    val reasons: List<String>,
)

object ShellGuard {
    private val destructivePatterns = listOf(
        Regex("""rm\s+-[a-zA-Z]*r[a-zA-Z]*f?\s+(/|/workspace|\*|~)"""),
        Regex("""mkfs\."""),
        Regex("""dd\s+if="""),
        Regex("""chmod\s+-R\s+777"""),
    )

    private val exfiltrationPatterns = listOf(
        Regex("""curl\s+.*(-d|--data|-F|--upload-file)"""),
        Regex("""wget\s+.*--post"""),
        Regex("""printenv|env\b"""),
    )

    fun inspect(command: String): ShellInspection {
        val reasons = mutableListOf<String>()
        var destructive = false

        destructivePatterns.forEach { rx ->
            if (rx.containsMatchIn(command)) {
                destructive = true
                reasons += "会删除或破坏文件"
            }
        }
        exfiltrationPatterns.forEach { rx ->
            if (rx.containsMatchIn(command)) {
                reasons += "可能把数据发到外部"
            }
        }
        if (command.contains("/etc/") || command.contains("../..")) {
            reasons += "访问沙箱外的路径"
        }

        return ShellInspection(
            isHighRisk = reasons.isNotEmpty(),
            isDestructive = destructive,
            reasons = reasons.distinct(),
        )
    }
}
