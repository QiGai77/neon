package com.qigai.neon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SkillParserTest {
    @Test
    fun `parses valid SKILL md frontmatter and body`() {
        val raw = """
            ---
            name: 账单整理
            command: /bills
            description: 解析邮件账单并导出 CSV
            connectors: [gmail, alpine, drive]
            risk: 低风险
            ---
            1. 读取 Gmail
            2. 在沙箱执行 python3
        """.trimIndent()

        val doc = SkillParser.parse("bills.SKILL.md", raw)
        assertNotNull(doc)
        assertEquals("账单整理", doc!!.name)
        assertEquals("/bills", doc.command)
        assertEquals(listOf("gmail", "alpine", "drive"), doc.connectors)
        assertEquals("低风险", doc.risk)
    }
}
