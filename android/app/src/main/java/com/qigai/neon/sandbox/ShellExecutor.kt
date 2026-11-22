package com.qigai.neon.sandbox

import com.qigai.neon.guard.GuardDecision
import com.qigai.neon.guard.PendingGuardRequest
import com.qigai.neon.guard.RiskGate
import com.qigai.neon.guard.ShellGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class TerminalLine(
    val text: String,
    val isError: Boolean = false,
    val isCommand: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val blockedByGuard: Boolean = false,
)

class ShellExecutor(
    private val rootfsManager: RootfsManager,
    private val kernel: PRootKernel,
    private val riskGate: RiskGate,
) {
    private val _lines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _latestLine = MutableSharedFlow<TerminalLine>(extraBufferCapacity = 64)
    val latestLine: SharedFlow<TerminalLine> = _latestLine.asSharedFlow()

    fun appendLine(line: TerminalLine) {
        _lines.update { (it + line).takeLast(400) }
        _latestLine.tryEmit(line)
    }

    fun clearHistory() {
        _lines.value = emptyList()
    }

    suspend fun execute(command: String, timeoutSec: Long = 60L): ShellResult {
        val inspection = ShellGuard.inspect(command)
        // 总是允许: sandbox commands never stop to ask, whatever the type pins say.
        val allowAll = riskGate.approval().mode == com.qigai.neon.guard.ApprovalMode.ALWAYS_ALLOW
        if (inspection.isHighRisk && !allowAll) {
            appendLine(TerminalLine("⚠ ShellGuard 拦截: $command", isError = true))
            val decision = riskGate.gate(
                type = if (inspection.isDestructive) com.qigai.neon.guard.ActionType.DELETE else com.qigai.neon.guard.ActionType.OTHER,
                base = com.qigai.neon.guard.RiskLevel.CONFIRM, write = false,
                request = PendingGuardRequest(
                    title = "ShellGuard 已拦截高危命令",
                    targetLabel = command,
                    targetPackage = "sandbox/alpine-arm64",
                    reasons = inspection.reasons,
                    requireBiometric = inspection.isDestructive,
                    stepSummary = "离线沙箱命令执行前检查",
                ),
                scopeKey = "shell:${command.take(40)}",
            )
            if (decision == GuardDecision.DENY) {
                appendLine(TerminalLine("✕ 用户保持拦截，命令未执行", isError = true))
                return ShellResult(-1, "ShellGuard 拦截并拒绝执行", blockedByGuard = true)
            }
        }

        if (rootfsManager.status.value !is SandboxStatus.Ready) {
            rootfsManager.ensureInitialized()
        }
        if (rootfsManager.status.value !is SandboxStatus.Ready) {
            val msg = "离线 Alpine 沙箱尚未就绪：${rootfsManager.status.value}"
            appendLine(TerminalLine(msg, isError = true))
            return ShellResult(-2, msg)
        }

        appendLine(TerminalLine("neon@alpine:/workspace$ $command", isCommand = true))

        return withContext(Dispatchers.IO) {
            runCatching {
                val pb = ProcessBuilder(kernel.buildCommand(command)).redirectErrorStream(true)
                    // nothing can type into the sandbox, so a prompt would hang until the timeout
                    .redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
                pb.environment().remove("LD_PRELOAD")
                pb.environment().putAll(kernel.buildEnv())
                pb.environment().putAll(NON_INTERACTIVE)
                val proc = pb.start()
                val outBuf = StringBuilder()
                val reader = Thread {
                    runCatching {
                        BufferedReader(InputStreamReader(proc.inputStream)).use { r ->
                            var line = r.readLine()
                            while (line != null) {
                                synchronized(outBuf) { outBuf.appendLine(line) }
                                appendLine(TerminalLine(line))
                                line = r.readLine()
                            }
                        }
                    }
                }.apply { isDaemon = true; start() }
                try {
                    val deadline = System.currentTimeMillis() + timeoutSec * 1000
                    while (proc.isAlive && System.currentTimeMillis() < deadline) kotlinx.coroutines.delay(100)
                    if (proc.isAlive) {
                        proc.destroyForcibly()
                        reader.join(500)
                        appendLine(TerminalLine("命令超时 ${timeoutSec}s", isError = true))
                        ShellResult(-3, synchronized(outBuf) { outBuf.toString() } + "\n[TIMEOUT]")
                    } else {
                        reader.join(2000)
                        ShellResult(proc.exitValue(), synchronized(outBuf) { outBuf.toString() }.trim())
                    }
                } finally {
                    // stop pressed or the turn was cancelled: do not leave the process running
                    if (proc.isAlive) proc.destroyForcibly()
                }
            }.getOrElse { err ->
                if (err is kotlinx.coroutines.CancellationException) {
                    appendLine(TerminalLine("已停止", isError = true))
                    throw err
                }
                val msg = err.message ?: "PRoot 启动失败"
                appendLine(TerminalLine("✕ $msg", isError = true))
                ShellResult(-4, msg)
            }
        }
    }

    companion object {
        val NON_INTERACTIVE = mapOf(
            "CI" to "1", "DEBIAN_FRONTEND" to "noninteractive", "GIT_TERMINAL_PROMPT" to "0",
            "PIP_NO_INPUT" to "1", "NPM_CONFIG_YES" to "true", "PAGER" to "cat", "GIT_PAGER" to "cat",
            "PYTHONUNBUFFERED" to "1",
        )
    }
}
