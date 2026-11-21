package com.qigai.neon

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 闪退时把堆栈写到本地，下次打开时给用户看，方便复制反馈。 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, err ->
            runCatching {
                val sw = StringWriter()
                err.printStackTrace(PrintWriter(sw))
                val head = "Neon ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                    "${Build.MANUFACTURER} ${Build.MODEL}  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + "  thread=${thread.name}\n\n"
                file(app).writeText(head + sw.toString().take(60_000))
            }
            previous?.uncaughtException(thread, err)
        }
    }

    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(ctx: Context) { file(ctx).delete() }
}
