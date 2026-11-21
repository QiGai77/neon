package com.qigai.neon.skills

import com.qigai.neon.data.db.HandsTraceEntity
import com.qigai.neon.oauth.EncryptedTokenStore
import com.qigai.neon.provider.ChatTurnMessage
import com.qigai.neon.provider.DeepSeekClient
import com.qigai.neon.provider.OpenCodeGoClient
import com.qigai.neon.provider.StreamChunk
import kotlinx.coroutines.flow.toList

class SkillSynthesizer(
    private val tokenStore: EncryptedTokenStore,
    private val deepSeek: DeepSeekClient,
    private val openCodeGo: OpenCodeGoClient,
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

        val provider = tokenStore.getActiveProvider()
        val model = tokenStore.getActiveModel()
        val flow = if (provider == "deepseek") {
            val key = tokenStore.getDeepSeekKey()
            if (key.isBlank()) return Result.failure(IllegalStateException("请先配置 DeepSeek API Key"))
            deepSeek.streamChat(key, model, "", listOf(ChatTurnMessage("user", prompt)), emptyList(), false)
        } else {
            val key = tokenStore.getOpenCodeGoKey()
            if (key.isBlank()) return Result.failure(IllegalStateException("请先配置 OpenCode Go API Key"))
            openCodeGo.streamChat(key, model, "skill-synth", "", listOf(ChatTurnMessage("user", prompt)), emptyList())
        }

        val sb = StringBuilder()
        val chunks = flow.toList()
        chunks.forEach { c ->
            when (c) {
                is StreamChunk.TextDelta -> sb.append(c.text)
                is StreamChunk.Error -> return Result.failure(IllegalStateException(c.message))
                else -> Unit
            }
        }

        val rawMd = sb.toString()
            .removePrefix("```markdown")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val fileName = "skill_${System.currentTimeMillis()}.SKILL.md"
        return skillRepository.saveSkill(fileName, rawMd)
    }
}
