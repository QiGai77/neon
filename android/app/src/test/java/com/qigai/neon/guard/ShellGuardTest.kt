package com.qigai.neon.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellGuardTest {
    @Test
    fun `detects destructive rm rf commands`() {
        val res = ShellGuard.inspect("rm -rf /workspace/*")
        assertTrue(res.isHighRisk)
        assertTrue(res.isDestructive)
    }

    @Test
    fun `allows safe python and ls commands`() {
        val res = ShellGuard.inspect("python3 parse_bills.py --out bills.csv")
        assertFalse(res.isHighRisk)
        assertFalse(res.isDestructive)
    }
}
