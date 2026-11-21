package com.qigai.neon.hands

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class ShizukuAdbEngine {
    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean =
        isAvailable() && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun requestPermission(requestCode: Int = 1001) {
        if (isAvailable() && !hasPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    suspend fun execShell(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext -1 to "Shizuku 未授权"
        runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val proc = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            val code = proc.waitFor()
            code to (out + err).trim()
        }.getOrElse { e ->
            -1 to (e.message ?: "Shizuku 执行失败")
        }
    }

    suspend fun tap(x: Int, y: Int): Boolean = execShell("input tap $x $y").first == 0

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int = 300): Boolean =
        execShell("input swipe $x1 $y1 $x2 $y2 $ms").first == 0

    suspend fun inputText(text: String): Boolean {
        val escaped = text.replace("'", "'\"'\"'")
        return execShell("input text '$escaped'").first == 0
    }

    suspend fun startApp(packageName: String): Boolean =
        execShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1").first == 0
}
