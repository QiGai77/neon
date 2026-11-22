package com.qigai.neon.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.qigai.neon.NeonApp
import com.qigai.neon.sandbox.SandboxFiles
import com.qigai.neon.ui.components.NetImage
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass
import java.io.File

private val CardShape = RoundedCornerShape(20.dp)

/** A markdown link that stands alone: a file in the sandbox becomes a file card, anything else a link card. */
@Composable
fun LinkOrFileCard(url: String, label: String?) {
    if (isSandboxPath(url)) FileCard(url, label) else LinkCard(url, label)
}

fun isSandboxPath(url: String) = url.startsWith("/") || url.startsWith("sandbox:") || url.startsWith("file://")

/** Favicon, title, domain: the way a link lands in a chat. */
@Composable
fun LinkCard(url: String, label: String?) {
    val c = Neon.colors
    val ctx = LocalContext.current
    val host = remember(url) { runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url }
    val title = label?.takeIf { it.isNotBlank() && it != url } ?: host
    Row(
        Modifier.fillMaxWidth().widthIn(max = 420.dp).glass(CardShape)
            .clickable { openUrl(ctx, url) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.bgTile), contentAlignment = Alignment.Center) {
            Icon(Lucide.Globe, null, Modifier.size(18.dp), tint = c.inkTertiary)
            if (!LocalInspectionMode.current) NetImage("https://$host/favicon.ico", Modifier.size(24.dp), ContentScale.Fit)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = NeonType.item(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host, style = NeonType.caption(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Lucide.ArrowUpRight, null, Modifier.size(16.dp), tint = c.inkTertiary)
    }
}

/** A deliverable the agent made in the sandbox: Word, PDF, a table, a picture. Tap opens it, the share icon sends it on. */
@Composable
fun FileCard(path: String, title: String? = null, sizeHint: Long? = null) {
    val c = Neon.colors
    val ctx = LocalContext.current
    val inspect = LocalInspectionMode.current
    val name = path.substringAfterLast('/')
    val file = remember(path) { if (inspect) null else files(ctx)?.host(path) }
    val size = sizeHint ?: file?.takeIf { it.exists() }?.length()
    val kind = fileKind(name)
    if (kind.image && file != null && file.exists()) {
        val bmp = remember(file.path, file.lastModified()) {
            runCatching {
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(file.path, o)?.asImageBitmap()
            }.getOrNull()
        }
        if (bmp != null) {
            Image(
                bmp, title ?: name, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(CardShape)
                    .border(1.dp, c.borderCard, CardShape).clickable { openFile(ctx, path) },
            )
            return
        }
    }
    Row(
        Modifier.fillMaxWidth().widthIn(max = 420.dp).glass(CardShape)
            .clickable { openFile(ctx, path) }.padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(kind.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(kind.icon, null, Modifier.size(22.dp), tint = kind.tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title?.takeIf { it.isNotBlank() } ?: name, style = NeonType.item(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(kind.label, size?.let { humanSize(it) }).joinToString("  "),
                style = NeonType.tnum(NeonType.caption()), maxLines = 1,
            )
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { shareFile(ctx, path) }, contentAlignment = Alignment.Center) {
            Icon(Lucide.Share2, "分享", Modifier.size(17.dp), tint = c.inkSecondary)
        }
    }
}

data class FileKind(val label: String, val icon: ImageVector, val tint: Color, val image: Boolean = false)

fun fileKind(name: String): FileKind {
    return when (name.substringAfterLast('.', "").lowercase()) {
        "doc", "docx", "odt", "rtf" -> FileKind("Word 文档", Lucide.FileText, Color(0xFF2B6BE6))
        "pdf" -> FileKind("PDF", Lucide.FileText, Color(0xFFE5483B))
        "xls", "xlsx", "csv", "tsv", "ods" -> FileKind("表格", Lucide.FileSpreadsheet, Color(0xFF1F9D55))
        "ppt", "pptx", "odp", "key" -> FileKind("演示文稿", Lucide.Presentation, Color(0xFFE07A1F))
        "png", "jpg", "jpeg", "webp", "gif", "heic", "bmp" -> FileKind("图片", Lucide.Image, Color(0xFF9B5DE5), image = true)
        "svg" -> FileKind("图片", Lucide.Image, Color(0xFF9B5DE5))
        "mp4", "mov", "mkv", "webm", "avi" -> FileKind("视频", Lucide.Video, Color(0xFFD63F8C))
        "mp3", "m4a", "wav", "flac", "ogg", "aac" -> FileKind("音频", Lucide.AudioLines, Color(0xFF0FA3B1))
        "zip", "tar", "gz", "tgz", "7z", "rar", "apk" -> FileKind("压缩包", Lucide.FileArchive, Color(0xFF8A8F98))
        "md", "txt", "log" -> FileKind("文本", Lucide.FileText, Color(0xFF8A8F98))
        "html", "htm" -> FileKind("网页", Lucide.Globe, Color(0xFF2B6BE6))
        "json", "yaml", "yml", "toml", "xml" -> FileKind("数据", Lucide.FileJson, Color(0xFF8A8F98))
        "py", "kt", "js", "ts", "java", "c", "cpp", "rs", "go", "sh", "css" -> FileKind("代码", Lucide.FileCode, Color(0xFF8A8F98))
        else -> FileKind("文件", Lucide.FileText, Color(0xFF8A8F98))
    }
}

fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${(b / 1024.0).let { if (it < 10) "%.1f".format(it) else it.toInt().toString() }} KB"
    b < 1024L * 1024 * 1024 -> "${"%.1f".format(b / 1024.0 / 1024.0)} MB"
    else -> "${"%.1f".format(b / 1024.0 / 1024.0 / 1024.0)} GB"
}

private fun files(ctx: Context): SandboxFiles? = (ctx.applicationContext as? NeonApp)?.container?.files

private fun uriFor(ctx: Context, path: String): Pair<Uri, String>? {
    val f: File = files(ctx)?.host(path) ?: return null
    if (!f.exists()) { Toast.makeText(ctx, "文件不在了：${f.name}", Toast.LENGTH_SHORT).show(); return null }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    return uri to SandboxFiles.mimeOf(f.name)
}

fun openFile(ctx: Context, path: String) {
    val (uri, mime) = uriFor(ctx, path) ?: return
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(Intent.createChooser(i, "打开").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) { shareFile(ctx, path) }
}

fun shareFile(ctx: Context, path: String) {
    val (uri, mime) = uriFor(ctx, path) ?: return
    val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
