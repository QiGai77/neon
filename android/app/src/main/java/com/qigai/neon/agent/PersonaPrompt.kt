package com.qigai.neon.agent

import com.qigai.neon.data.AppSettings

/** Turns 性格 and 关于你 into the tail of the system prompt. Empty fields are left out. */
object PersonaPrompt {
    fun build(s: AppSettings): String = buildString {
        append("你叫").append(s.agentName).append("。")
        if (s.userCall.isNotBlank()) append("称呼用户为").append(s.userCall.trim()).append("。")
        append("语气：").append(s.tone.prompt).append("。")
        s.toneExtra.trim().takeIf { it.isNotEmpty() }?.let { append(it.trimEnd('。')).append("。") }
        val about = about(s)
        if (about.isNotEmpty()) append("\n\n【关于用户】\n").append(about)
    }

    fun about(s: AppSettings): String = buildList {
        s.userName.trim().takeIf { it.isNotEmpty() }?.let { add("名字：$it") }
        s.userCity.trim().takeIf { it.isNotEmpty() }?.let { add("城市：$it") }
        s.userJob.trim().takeIf { it.isNotEmpty() }?.let { add("职业：$it") }
        s.userBio.trim().takeIf { it.isNotEmpty() }?.let { add("背景：$it") }
        s.answerPrefs.takeIf { it.isNotEmpty() }?.let { add("回答时：" + it.joinToString("，")) }
    }.joinToString("\n")

    /** A local sample line for 听起来像这样; no model call. */
    fun sample(name: String, call: String, tone: com.qigai.neon.data.Tone): String {
        val c = call.trim()
        val hi = if (c.isEmpty()) "" else "$c，"
        return when (tone) {
            com.qigai.neon.data.Tone.CRISP -> "${hi}包裹到新街口了，今天 18:00 前送到。要我顺手把账单表也发你吗？"
            com.qigai.neon.data.Tone.GENTLE -> "${hi}包裹已经到新街口啦，今天 18:00 前会送到，不用着急。需要的话，我把账单表也一起发给你。"
            com.qigai.neon.data.Tone.FRIEND -> "${hi}快递到新街口了，晚饭前肯定到。账单表要不要我顺手发你？"
            com.qigai.neon.data.Tone.PRO -> "${hi}顺丰包裹当前在新街口派送中，预计今天 18:00 前送达。本月账单表已生成，可随时发送。"
            com.qigai.neon.data.Tone.WITTY -> "${hi}包裹已经杀到新街口，18:00 前准时登门。账单表也在待命，要不要一起请出来？"
            com.qigai.neon.data.Tone.BRIEF -> "${hi}今天 18:00 前送到。"
        }
    }
}
