package com.qigai.neon.connectors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeDeviceTools(private val context: Context) {

    suspend fun readClipboard(): String = withContext(Dispatchers.Main) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return@withContext ""
        if (clip.itemCount == 0) "" else clip.getItemAt(0).coerceToText(context).toString()
    }

    suspend fun writeClipboard(text: String) = withContext(Dispatchers.Main) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Neon", text))
    }

    fun openDeepLink(url: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun shareText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "分享到").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun listRecentNotifications(): String {
        val list = NeonNotificationListenerService.notifications.value
        if (list.isEmpty()) {
            return if (NeonNotificationListenerService.connected.value) "当前无活跃通知" else "通知监听权限未开启"
        }
        return list.joinToString("\n") { "[${it.packageName}] ${it.title}: ${it.text}" }
    }
}
