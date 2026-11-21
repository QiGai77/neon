package com.qigai.neon.sandbox

import android.content.Context
import com.qigai.neon.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class MirrorPreset(val label: String, val alpineRepo: String, val pipIndex: String) {
    OFFICIAL(
        "官方源",
        "https://dl-cdn.alpinelinux.org/alpine/v3.21/main\nhttps://dl-cdn.alpinelinux.org/alpine/v3.21/community\n",
        "https://pypi.org/simple",
    ),
    TSINGHUA(
        "清华源",
        "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.21/main\nhttps://mirrors.tuna.tsinghua.edu.cn/alpine/v3.21/community\n",
        "https://pypi.tuna.tsinghua.edu.cn/simple",
    ),
    ALIYUN(
        "阿里源",
        "https://mirrors.aliyun.com/alpine/v3.21/main\nhttps://mirrors.aliyun.com/alpine/v3.21/community\n",
        "https://mirrors.aliyun.com/pypi/simple",
    ),
}

sealed interface SandboxStatus {
    data object Checking : SandboxStatus
    data class Ready(val arch: String, val pythonAvailable: Boolean) : SandboxStatus
    data class Extracting(val detail: String) : SandboxStatus
    data class MissingBundle(val reason: String) : SandboxStatus
    data class Error(val message: String) : SandboxStatus
}

class RootfsManager(private val context: Context) {
    val rootfsDir: File get() = File(context.filesDir, "rootfs")
    val workspaceDir: File get() = File(context.filesDir, "workspace")
    val skillsDir: File get() = File(workspaceDir, "skills")
    val memoryDir: File get() = File(workspaceDir, "memory")
    val tmpDir: File get() = File(context.filesDir, "tmp")

    private val _status = MutableStateFlow<SandboxStatus>(SandboxStatus.Checking)
    val status: StateFlow<SandboxStatus> = _status.asStateFlow()

    private val _mirrorPreset = MutableStateFlow(MirrorPreset.TSINGHUA)
    val mirrorPreset: StateFlow<MirrorPreset> = _mirrorPreset.asStateFlow()

    fun prootBinary(): File = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
    fun loaderBinary(): File = File(context.applicationInfo.nativeLibraryDir, "libproot-loader.so")
    fun busyboxBinary(): File = File(context.applicationInfo.nativeLibraryDir, "libbusybox.so")

    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        workspaceDir.mkdirs()
        skillsDir.mkdirs()
        memoryDir.mkdirs()
        tmpDir.mkdirs()

        val versionFile = File(rootfsDir, ".version")
        val targetVersion = BuildConfig.VERSION_CODE.toString()
        val shFile = File(rootfsDir, "bin/sh")
        if (versionFile.exists() && versionFile.readText().trim() == targetVersion && shFile.exists()) {
            val pyExists = File(rootfsDir, "usr/bin/python3").exists()
            installMcpCli()
            _status.value = SandboxStatus.Ready("arm64", pyExists)
            writeResolvConf()
            return@withContext
        }

        val assetList = runCatching { context.assets.list("")?.toSet().orEmpty() }.getOrDefault(emptySet())
        val matchedAsset = when {
            ASSET_ROOTFS in assetList -> ASSET_ROOTFS
            "rootfs-arm64.tar" in assetList -> "rootfs-arm64.tar"
            else -> null
        }

        if (matchedAsset == null || !prootBinary().exists()) {
            _status.value = SandboxStatus.MissingBundle(
                buildString {
                    if (!prootBinary().exists()) append("缺少 libproot.so；")
                    if (matchedAsset == null) append("缺少 $ASSET_ROOTFS")
                },
            )
            return@withContext
        }

        _status.value = SandboxStatus.Extracting("正在初始化 Linux 环境")
        runCatching {
            val staging = File(context.filesDir, "rootfs.staging")
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()

            val archiveFile = File(tmpDir, matchedAsset)
            context.assets.open(matchedAsset).use { input ->
                FileOutputStream(archiveFile).use { output -> input.copyTo(output) }
            }

            val isGzip = matchedAsset.endsWith(".gz")
            val tarFlag = if (isGzip) "-xzf" else "-xf"
            val bb = busyboxBinary()
            val cmd = if (bb.exists()) {
                listOf(bb.absolutePath, "tar", tarFlag, archiveFile.absolutePath, "-C", staging.absolutePath)
            } else {
                listOf("/system/bin/tar", tarFlag, archiveFile.absolutePath, "-C", staging.absolutePath)
            }
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            archiveFile.delete()
            if (code != 0) error("解压 rootfs 失败 (exit=$code): $out")

            if (rootfsDir.exists()) rootfsDir.deleteRecursively()
            if (!staging.renameTo(rootfsDir)) error("原子切换 rootfs 目录失败")

            File(rootfsDir, ".version").writeText(targetVersion)
            writeResolvConf()
            installMcpCli()
            applyMirrorPreset(_mirrorPreset.value)
            val pyExists = File(rootfsDir, "usr/bin/python3").exists()
            _status.value = SandboxStatus.Ready("arm64", pyExists)
        }.onFailure { err ->
            _status.value = SandboxStatus.Error(err.message ?: "沙箱初始化异常")
        }
    }

    fun applyMirrorPreset(preset: MirrorPreset) {
        _mirrorPreset.value = preset
        runCatching {
            val apkDir = File(rootfsDir, "etc/apk").apply { mkdirs() }
            File(apkDir, "repositories").writeText(preset.alpineRepo)
            val etcDir = File(rootfsDir, "etc").apply { mkdirs() }
            File(etcDir, "pip.conf").writeText("[global]\nindex-url = ${preset.pipIndex}\n")
        }
    }

    private fun installMcpCli() {
        runCatching {
            val binDir = File(rootfsDir, "usr/local/bin").apply { mkdirs() }
            val dst = File(binDir, "neon-mcp-cli")
            context.assets.open("neon-mcp-cli/main.py").use { input ->
                FileOutputStream(dst).use { output -> input.copyTo(output) }
            }
            dst.setExecutable(true, false)
        }
    }

    private fun writeResolvConf() {
        runCatching {
            val etc = File(rootfsDir, "etc").apply { mkdirs() }
            File(etc, "resolv.conf").writeText("nameserver 223.5.5.5\nnameserver 8.8.8.8\n")
        }
    }

    companion object {
        const val ASSET_ROOTFS = "rootfs-arm64.tar.gz"
    }
}
