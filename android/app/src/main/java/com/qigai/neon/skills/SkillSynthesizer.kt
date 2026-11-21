package com.qigai.neon.skills

import com.qigai.neon.data.HandsTraceEntity

class SkillSynthesizer(
    /** One-shot completion with the user's current model; throws on failure. */
    private val complete: suspend (prompt: String) -> String,
    private val skillRepository: SkillRepository,
) {
    suspend fun synthesizeFromTraces(traces: List<HandsTraceEntity>): Result<SkillDocument> {
        if (traces.isEmpty()) {
            return Result.failure(IllegalStateException("当前没有可沉淀的操控轨迹"))
        }

        val traceText = traces.joinToString("\n") { t ->
            "${t.stepIndex}. [${t.engine}] ${t.actionType} ${t.targetPackage} -> ${t.targetLabel} (${t.detail}) [${t.status}]"
        }

        val prompt = """
            请将以下真实的手机操控轨迹提炼为一个标准 SKILL.md 文件（只输出纯 Markdown，不要解释文字）：
            必须包含 YAML frontmatter：
            ---
            name: 简短中文技能名
            command: /english_slug
            description: 一句话描述适用场景
            connectors: [hands]
            risk: 低风险 或 中风险 或 高风险
            ---
            正文包含「触发条件」「执行步骤」「风控注意点」。

            轨迹记录：
            $traceText
        """.trimIndent()

        val rawMd = runCatching { complete(prompt) }.getOrElse { return Result.failure(it) }
            .removePrefix("```markdown")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val fileName = "skill_${System.currentTimeMillis()}.SKILL.md"
        return skillRepository.saveSkill(fileName, rawMd)
    }
}
