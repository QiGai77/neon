package com.qigai.neon.sandbox

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

class TarExtractorTest {

    private object JvmFs : TarExtractor.Fs {
        override fun symlink(target: String, path: String) {
            Files.createSymbolicLink(Paths.get(path), Paths.get(target))
        }
        override fun chmod(path: String, mode: Int) {
            val p = Paths.get(path)
            if (Files.isSymbolicLink(p)) return
            val perms = mutableSetOf<PosixFilePermission>()
            val all = PosixFilePermission.values()
            for (i in 0 until 9) if (mode and (1 shl (8 - i)) != 0) perms += all[i]
            Files.setPosixFilePermissions(p, perms)
        }
        override fun isSymlink(path: String) = Files.isSymbolicLink(Paths.get(path))
    }

    @Test
    fun extractsBundledRootfs() {
        val gz = File("src/main/assets/rootfs-arm64.tar.gz")
        assumeTrue("rootfs asset not prepared", gz.exists())
        TarExtractor.fs = JvmFs
        val dest = Files.createTempDirectory("rootfs").toFile()
        try {
            GZIPInputStream(gz.inputStream().buffered()).use { TarExtractor.extract(it, dest) }
            val sh = Paths.get(dest.path, "bin/sh")
            assertTrue("bin/sh symlink", Files.isSymbolicLink(sh))
            assertTrue("busybox executable", File(dest, "bin/busybox").canExecute())
            assertTrue("etc/apk exists", File(dest, "etc/apk").isDirectory)
            val links = dest.walkTopDown().count { Files.isSymbolicLink(it.toPath()) }
            assertTrue("symlinks created: $links", links > 50)
        } finally {
            TarExtractor.deleteTree(dest)
            TarExtractor.fs = TarExtractor.AndroidFs
        }
    }
}
