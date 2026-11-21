package com.qigai.neon.oauth

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

data class OAuthCallbackResult(
    val code: String?,
    val state: String?,
    val error: String?,
)

/**
 * 接收 Custom Tabs OAuth 回调：优先使用本机 127.0.0.1 临时回环端口，同时支持 DeepLink (com.qigai.neon://oauth/callback) 兜底。
 */
object LoopbackCallback {
    @Volatile
    private var pendingDeepLink: CompletableDeferred<OAuthCallbackResult>? = null

    fun createLoopbackServer(): Pair<ServerSocket, String> {
        val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val redirectUri = "http://127.0.0.1:${socket.localPort}/oauth/callback"
        return socket to redirectUri
    }

    suspend fun awaitCallback(socket: ServerSocket): OAuthCallbackResult = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<OAuthCallbackResult>()
        pendingDeepLink = deferred
        socket.use { srv ->
            val client = srv.accept()
            client.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine().orEmpty()
                val path = requestLine.split(" ").getOrNull(1).orEmpty()
                val uri = Uri.parse("http://127.0.0.1$path")
                val result = parseUri(uri)
                val html = """
                    <html><body style="font-family:sans-serif;background:#F5F4F0;color:#141416;padding:32px;text-align:center;">
                    <h2>Neon 授权完成</h2>
                    <p>可以关闭此页面并返回 Neon。</p>
                    </body></html>
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n"
                s.getOutputStream().write(resp.toByteArray(StandardCharsets.US_ASCII))
                s.getOutputStream().write(html)
                s.getOutputStream().flush()
                pendingDeepLink = null
                result
            }
        }
    }

    fun deliverDeepLink(uri: Uri) {
        pendingDeepLink?.complete(parseUri(uri))
        pendingDeepLink = null
    }

    fun parseUri(uri: Uri): OAuthCallbackResult = OAuthCallbackResult(
        code = uri.getQueryParameter("code"),
        state = uri.getQueryParameter("state"),
        error = uri.getQueryParameter("error"),
    )
}
