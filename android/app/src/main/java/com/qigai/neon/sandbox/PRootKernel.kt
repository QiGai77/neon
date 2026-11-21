package com.qigai.neon.sandbox

import java.io.File

class PRootKernel(private val rootfsManager: RootfsManager) {

    fun buildCommand(shellCommand: String): List<String> {
        val proot = rootfsManager.prootBinary()
        val loader = rootfsManager.loaderBinary()
        val rootfs = rootfsManager.rootfsDir
        val workspace = rootfsManager.workspaceDir
        val shellBin = if (File(rootfs, "bin/bash").exists()) "/bin/bash" else "/bin/sh"

        return listOf(
            proot.absolutePath,
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "${workspace.absolutePath}:/workspace",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/workspace",
            shellBin,
            "-c",
            shellCommand,
        ).also {
            if (loader.exists()) {
                // loader is picked up via PROOT_LOADER env var in ShellExecutor
            }
        }
    }

    fun buildEnv(): Map<String, String> = buildMap {
        put("HOME", "/root")
        put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        put("TERM", "xterm-256color")
        put("LANG", "C.UTF-8")
        put("PROOT_TMP_DIR", rootfsManager.tmpDir.absolutePath)
        val loader = rootfsManager.loaderBinary()
        if (loader.exists()) {
            put("PROOT_LOADER", loader.absolutePath)
        }
    }
}
