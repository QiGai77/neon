package com.qigai.neon.vault

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Lets Playwright in the sandbox drive Neon's own WebView over the Chrome DevTools Protocol.
 * A debuggable WebView listens on the abstract socket webview_devtools_remote_<pid>; this forwards
 * 127.0.0.1:[port] to it, so `chromium.connectOverCDP("http://127.0.0.1:9222")` works from the sandbox.
 * Debugging is on only while the bridge runs. Only Neon's WebView pages are reachable.
 */
class CdpBridge(private val port: Int = 9222) {
    @Volatile private var server: ServerSocket? = null
    val running get() = server?.isClosed == false

    suspend fun start(): String = withContext(Dispatchers.Main) {
        if (running) return@withContext endpoint()
        WebView.setWebContentsDebuggingEnabled(true)
        withContext(Dispatchers.IO) {
            val s = ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"))
            server = s
            thread(name = "cdp-accept", isDaemon = true) {
                while (!s.isClosed) {
                    val client = runCatching { s.accept() }.getOrNull() ?: break
                    thread(name = "cdp-conn", isDaemon = true) { serve(client) }
                }
            }
        }
        endpoint()
    }

    suspend fun stop() {
        withContext(Dispatchers.IO) { runCatching { server?.close() }; server = null }
        withContext(Dispatchers.Main) { WebView.setWebContentsDebuggingEnabled(false) }
    }

    fun endpoint() = "http://127.0.0.1:$port"

    private fun serve(client: Socket) {
        val local = LocalSocket()
        try {
            local.connect(LocalSocketAddress("webview_devtools_remote_${Process.myPid()}", LocalSocketAddress.Namespace.ABSTRACT))
        } catch (e: Exception) {
            runCatching { client.close() }; runCatching { local.close() }
            return
        }
        val a = thread(isDaemon = true) { pump(client.getInputStream(), local.outputStream) ; runCatching { local.shutdownOutput() } }
        pump(local.inputStream, client.getOutputStream())
        runCatching { a.join(1000) }
        runCatching { client.close() }; runCatching { local.close() }
    }

    private fun pump(i: InputStream, o: OutputStream) {
        val buf = ByteArray(16 * 1024)
        try {
            while (true) {
                val n = i.read(buf)
                if (n < 0) break
                o.write(buf, 0, n); o.flush()
            }
        } catch (_: Exception) {}
    }
}
