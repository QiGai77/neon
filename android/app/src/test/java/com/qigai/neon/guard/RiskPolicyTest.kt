package com.qigai.neon.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskPolicyTest {
    @Test
    fun `password node is strictly blocked`() {
        val res = RiskPolicy.assessToolCall("hands_tap", targetText = "输入密码", isPasswordNode = true)
        assertEquals(RiskLevel.BLOCKED, res.level)
    }

    @Test
    fun `payment button triggers confirm with biometric`() {
        val res = RiskPolicy.assessToolCall("hands_tap", targetText = "确认支付 ¥128.00", targetPackage = "com.eg.android.AlipayGphone")
        assertEquals(RiskLevel.CONFIRM, res.level)
        assertTrue(res.requireBiometric)
    }

    @Test
    fun `screen dump is safe`() {
        val res = RiskPolicy.assessToolCall("hands_dump_screen")
        assertEquals(RiskLevel.SAFE, res.level)
    }
}
