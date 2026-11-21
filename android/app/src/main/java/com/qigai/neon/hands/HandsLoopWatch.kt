package com.qigai.neon.hands

import java.security.MessageDigest

/**
 * 检测 Hands 是否在同一界面陷入死循环（连续 3 次屏幕布局哈希一致）。
 */
class HandsLoopWatch {
    private val recentHashes = ArrayDeque<String>(4)

    fun recordAndCheckStuck(dump: ScreenDump): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(dump.compactText.toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (recentHashes.size >= 3) recentHashes.removeFirst()
        recentHashes.addLast(digest)
        return recentHashes.size == 3 && recentHashes.all { it == digest }
    }

    fun reset() {
        recentHashes.clear()
    }
}
