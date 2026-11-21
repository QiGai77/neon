package com.qigai.neon.sandbox

import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * 解压 rootfs 用的 tar 读取器。
 * Android 不允许 App 在私有目录里建硬链接，系统自带 tar 遇到硬链接会直接失败，
 * 这里把硬链接复制成普通文件，符号链接用 Os.symlink 创建，权限按归档里的 mode 设置。
 */
object TarExtractor {

    interface Fs {
        fun symlink(target: String, path: String)
        fun chmod(path: String, mode: Int)
        fun isSymlink(path: String): Boolean
    }

    object AndroidFs : Fs {
        override fun symlink(target: String, path: String) = Os.symlink(target, path)
        override fun chmod(path: String, mode: Int) = Os.chmod(path, mode)
        override fun isSymlink(path: String): Boolean =
            runCatching { android.system.OsConstants.S_ISLNK(Os.lstat(path).st_mode) }.getOrDefault(false)
    }

    var fs: Fs = AndroidFs

    fun extract(input: InputStream, dest: File) {
        val root = dest.canonicalFile
        val header = ByteArray(512)
        var longName: String? = null
        var longLink: String? = null
        var paxPath: String? = null
        var paxLink: String? = null
        val hardLinks = mutableListOf<Pair<File, File>>()
        val modes = mutableListOf<Pair<File, Int>>()

        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break

            val type = header[156].toInt().toChar()
            val size = parseOctal(header, 124, 12)
            val mode = parseOctal(header, 100, 8).toInt()
            var name = longName ?: paxPath ?: headerName(header)
            var link = longLink ?: paxLink ?: cString(header, 157, 100)

            when (type) {
                'L' -> { longName = String(readBytes(input, size), Charsets.UTF_8).trimEnd('\u0000'); continue }
                'K' -> { longLink = String(readBytes(input, size), Charsets.UTF_8).trimEnd('\u0000'); continue }
                'x' -> {
                    val pax = parsePax(readBytes(input, size))
                    paxPath = pax["path"]; paxLink = pax["linkpath"]
                    continue
                }
                'g' -> { skip(input, size); continue }
            }
            longName = null; longLink = null; paxPath = null; paxLink = null

            name = name.removePrefix("./").trimEnd('/')
            if (name.isEmpty()) { skip(input, size); continue }
            if (name.startsWith("/") || name.split('/').any { it == ".." }) {
                skip(input, size); continue
            }
            val out = File(root, name)

            when (type) {
                '5' -> { out.mkdirs(); modes += out to mode }
                '2' -> {
                    out.parentFile?.mkdirs()
                    if (out.exists() || isSymlink(out)) out.delete()
                    fs.symlink(link, out.path)
                }
                '1' -> {
                    hardLinks += File(root, link.removePrefix("./")) to out
                }
                '0', '\u0000', '7' -> {
                    out.parentFile?.mkdirs()
                    if (isSymlink(out)) out.delete()
                    FileOutputStream(out).use { fos -> copyN(input, fos, size) }
                    padding(input, size)
                    modes += out to mode
                    continue
                }
                else -> { /* 设备节点、FIFO 等在沙箱里用不上 */ }
            }
            skip(input, size)
        }

        for ((target, linkFile) in hardLinks) {
            linkFile.parentFile?.mkdirs()
            if (target.isFile) {
                target.copyTo(linkFile, overwrite = true)
                if (target.canExecute()) linkFile.setExecutable(true, false)
            }
        }
        for ((f, m) in modes.asReversed()) {
            runCatching { fs.chmod(f.path, (m and 0x1FF) or if (f.isDirectory) 0x1C0 else 0x100) }
        }
    }

    /** 删除目录树，不跟随符号链接。 */
    fun deleteTree(f: File) {
        if (!f.exists() && !isSymlink(f)) return
        if (!isSymlink(f) && f.isDirectory) f.listFiles()?.forEach { deleteTree(it) }
        f.delete()
    }

    private fun isSymlink(f: File): Boolean = fs.isSymlink(f.path)

    private fun headerName(h: ByteArray): String {
        val name = cString(h, 0, 100)
        val magic = cString(h, 257, 6)
        val prefix = if (magic.startsWith("ustar")) cString(h, 345, 155) else ""
        return if (prefix.isNotEmpty()) "$prefix/$name" else name
    }

    private fun cString(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun parseOctal(b: ByteArray, off: Int, len: Int): Long {
        if (b[off].toInt() and 0x80 != 0) {
            var v = 0L
            for (i in off + 1 until off + len) v = (v shl 8) or (b[i].toLong() and 0xFF)
            return v
        }
        val s = cString(b, off, len).trim()
        return if (s.isEmpty()) 0 else s.toLong(8)
    }

    private fun parsePax(data: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var i = 0
        while (i < data.size) {
            var sp = i
            while (sp < data.size && data[sp] != ' '.code.toByte()) sp++
            if (sp >= data.size) break
            val len = String(data, i, sp - i).toIntOrNull() ?: break
            val rec = String(data, sp + 1, len - (sp - i) - 2, Charsets.UTF_8)
            val eq = rec.indexOf('=')
            if (eq > 0) map[rec.substring(0, eq)] = rec.substring(eq + 1)
            i += len
        }
        return map
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) { if (n == 0) return false else throw IOException("tar 归档被截断") }
            n += r
        }
        return true
    }

    private fun readBytes(input: InputStream, size: Long): ByteArray {
        val b = ByteArray(size.toInt())
        readFully(input, b)
        padding(input, size)
        return b
    }

    private fun copyN(input: InputStream, out: FileOutputStream, size: Long) {
        val buf = ByteArray(1 shl 16)
        var left = size
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) throw IOException("tar 归档被截断")
            out.write(buf, 0, r)
            left -= r
        }
    }

    private fun padding(input: InputStream, size: Long) {
        val pad = (512 - size % 512) % 512
        skipExact(input, pad)
    }

    private fun skip(input: InputStream, size: Long) {
        skipExact(input, size + (512 - size % 512) % 512)
    }

    private fun skipExact(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) throw IOException("tar 归档被截断")
            left -= r
        }
    }
}
