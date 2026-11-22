package com.qigai.neon.sandbox

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

/** Maps sandbox paths to files on the phone. /workspace is bound to filesDir/workspace; anything else lives in the rootfs. */
class SandboxFiles(private val rootfsDir: () -> File, private val workspaceDir: () -> File) {
    fun host(path: String): File {
        val p = path.removePrefix("sandbox:").removePrefix("file://").trim()
        return when {
            p == "/workspace" -> workspaceDir()
            p.startsWith("/workspace/") -> File(workspaceDir(), p.removePrefix("/workspace/"))
            p.startsWith("/") -> File(rootfsDir(), p.removePrefix("/"))
            else -> File(workspaceDir(), p)
        }
    }

    /** Copies a picked file into /workspace/uploads and returns its sandbox path, name, mime and size. */
    fun importUpload(resolver: ContentResolver, uri: Uri): Upload? = runCatching {
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        name = name.replace(Regex("""[/\\:*?"<>|]"""), "_")
        val mime = resolver.getType(uri) ?: mimeOf(name)
        val dir = File(workspaceDir(), "uploads").apply { mkdirs() }
        var target = File(dir, name)
        var k = 1
        while (target.exists()) {
            val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
            target = File(dir, if (ext.isEmpty()) "$base ($k)" else "$base ($k).$ext"); k++
        }
        resolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        Upload(target.name, "/workspace/uploads/${target.name}", mime, if (size >= 0) size else target.length())
    }.getOrNull()

    data class Upload(val name: String, val path: String, val mime: String, val size: Long)

    companion object {
        fun mimeOf(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
    }
}
