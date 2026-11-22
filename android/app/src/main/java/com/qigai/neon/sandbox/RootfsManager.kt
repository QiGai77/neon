package com.qigai.neon.sandbox

import android.content.Context
import com.qigai.neon.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

enum class MirrorPreset(val label: String, val alpineRepo: String, val pipIndex: String) {
    OFFICIAL(
        "官方源",
        "https://dl-cdn.alpinelinux.org/alpine/v3.22/main\nhttps://dl-cdn.alpinelinux.org/alpine/v3.22/community\n",
        "https://pypi.org/simple",
    ),
    TSINGHUA(
        "清华源",
        "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.22/main\nhttps://mirrors.tuna.tsinghua.edu.cn/alpine/v3.22/community\n",
        "https://pypi.tuna.tsinghua.edu.cn/simple",
    ),
    ALIYUN(
        "阿里源",
        "https://mirrors.aliyun.com/alpine/v3.22/main\nhttps://mirrors.aliyun.com/alpine/v3.22/community\n",
        "https://mirrors.aliyun.com/pypi/simple",
    ),
}

/** A toolchain in the sandbox: bundled ones are present after extraction, the rest install with apk on demand. */
enum class DevEnv(val label: String, val probe: String, val packages: String, val bundled: Boolean) {
    PYTHON("Python", "usr/bin/python3", "python3 py3-pip uv", true),
    NODE("Node.js", "usr/bin/node", "nodejs npm pnpm", true),
    GIT("Git", "usr/bin/git", "git", true),
    CC("C/C++", "usr/bin/gcc", "build-base cmake pkgconf linux-headers", true),
    GO("Go", "usr/bin/go", "go", false),
    RUST("Rust", "usr/bin/cargo", "rust cargo", false),
    JAVA("Java", "usr/lib/jvm/java-21-openjdk/bin/java", "openjdk21-jdk maven", false),
    PHP("PHP", "usr/bin/php83", "php83 php83-phar php83-openssl php83-mbstring composer", false),
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

    private val initLock = Mutex()

    suspend fun ensureInitialized() = initLock.withLock { initialize() }

    private suspend fun initialize() = withContext(Dispatchers.IO) {
        workspaceDir.mkdirs()
        skillsDir.mkdirs()
        memoryDir.mkdirs()
        tmpDir.mkdirs()

        val versionFile = File(rootfsDir, ".version")
        // re-extract only when the bundled rootfs itself changed, so packages the user added survive app updates
        val targetVersion = runCatching { context.assets.open(ASSET_VERSION).bufferedReader().use { it.readText().trim() } }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_CODE.toString()
        val shFile = File(rootfsDir, "bin/sh")
        if (versionFile.exists() && versionFile.readText().trim() == targetVersion && shFile.exists()) {
            val pyExists = File(rootfsDir, "usr/bin/python3").exists()
            installMcpCli()
            writeResolvConf()
            applyMirrorPreset(_mirrorPreset.value)
            _status.value = SandboxStatus.Ready("arm64", pyExists)
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
            TarExtractor.deleteTree(staging)
            staging.mkdirs()

            val isGzip = matchedAsset.endsWith(".gz")
            context.assets.open(matchedAsset).use { raw ->
                val input = if (isGzip) java.util.zip.GZIPInputStream(raw.buffered(1 shl 16)) else raw.buffered(1 shl 16)
                TarExtractor.extract(input, staging)
            }

            TarExtractor.deleteTree(rootfsDir)
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

    fun isInstalled(env: DevEnv): Boolean = File(rootfsDir, env.probe).exists()

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
        const val ASSET_VERSION = "rootfs-arm64.version"
    }
}
