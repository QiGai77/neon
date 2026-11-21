package com.qigai.neon.hands

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.qigai.neon.data.db.HandsTraceDao
import java.io.File
import java.io.FileOutputStream
import com.qigai.neon.data.db.HandsTraceEntity
import com.qigai.neon.guard.GuardDecision
import com.qigai.neon.guard.PendingGuardRequest
import com.qigai.neon.guard.RiskGate
import com.qigai.neon.guard.RiskLevel
import com.qigai.neon.guard.RiskPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class HandsEngineMode { ACCESSIBILITY, SHIZUKU }

class HandsOperator(
    private val context: Context,
    private val shizuku: ShizukuAdbEngine,
    private val riskGate: RiskGate,
    private val traceDao: HandsTraceDao,
) {
    private val loopWatch = HandsLoopWatch()

    private val _engineMode = MutableStateFlow(HandsEngineMode.ACCESSIBILITY)
    val engineMode: StateFlow<HandsEngineMode> = _engineMode.asStateFlow()

    private val _capsuleState = MutableStateFlow(HandsCapsuleState())
    val capsuleState: StateFlow<HandsCapsuleState> = _capsuleState.asStateFlow()

    private val _stuckWarning = MutableStateFlow(false)
    val stuckWarning: StateFlow<Boolean> = _stuckWarning.asStateFlow()

    private var activeRunId: String = UUID.randomUUID().toString()
    private var stepCounter: Int = 0

    fun setEngineMode(mode: HandsEngineMode) {
        _engineMode.value = mode
        _capsuleState.value = _capsuleState.value.copy(
            engineLabel = if (mode == HandsEngineMode.ACCESSIBILITY) "引擎 A · 无障碍" else "引擎 B · Shizuku",
        )
    }

    fun startRun() {
        activeRunId = UUID.randomUUID().toString()
        stepCounter = 0
        loopWatch.reset()
        _stuckWarning.value = false
        _capsuleState.value = _capsuleState.value.copy(active = true, paused = false, stepIndex = 0, stepText = "准备中")
        syncOverlay()
    }

    fun togglePause() {
        val next = !_capsuleState.value.paused
        _capsuleState.value = _capsuleState.value.copy(paused = next)
        syncOverlay()
    }

    fun stopRun() {
        _capsuleState.value = _capsuleState.value.copy(active = false, paused = false)
        NeonAccessibilityService.instance.value?.hideCapsule()
    }

    suspend fun dumpScreen(): String {
        val svc = NeonAccessibilityService.instance.value
            ?: return "ERROR: Neon 无障碍服务未开启，请引导用户前往系统无障碍设置开启。"
        val dump = svc.dumpScreen()
        val stuck = loopWatch.recordAndCheckStuck(dump)
        _stuckWarning.value = stuck
        return if (stuck) {
            "${dump.compactText}\n\n[HandsLoopWatch 警告] 连续 3 步页面未变化，请换策略或暂停交由人工接管。"
        } else {
            dump.compactText
        }
    }

    suspend fun tapNode(sessionId: String, nodeIndex: Int): String {
        while (_capsuleState.value.paused) delay(250)
        val svc = NeonAccessibilityService.instance.value ?: return "ERROR: 无障碍服务未开启"
        val dump = svc.dumpScreen()
        val node = dump.nodes.firstOrNull { it.index == nodeIndex }
            ?: return "ERROR: 未找到编号 [$nodeIndex] 的控件"

        val assessment = RiskPolicy.assessToolCall(
            toolName = "hands_tap",
            targetText = node.label,
            targetPackage = dump.packageName,
            isPasswordNode = node.isPassword || dump.hasPasswordField,
        )

        if (assessment.level == RiskLevel.BLOCKED) {
            _capsuleState.value = _capsuleState.value.copy(paused = true, stepText = "密码输入已交由人工")
            syncOverlay()
            recordTrace(sessionId, "tap", dump.packageName, node.label, "密码框拦截", "blocked")
            return "BLOCKED: 检测到密码框，已自动暂停并交由用户手动输入。"
        }

        if (assessment.level == RiskLevel.CONFIRM) {
            val decision = riskGate.requestApproval(
                request = PendingGuardRequest(
                    title = "Sentinel 高危操作确认",
                    targetLabel = "点击「${node.label}」",
                    targetPackage = dump.packageName,
                    reasons = assessment.reasons,
                    requireBiometric = assessment.requireBiometric,
                    stepSummary = "第 ${stepCounter + 1} 步 · 点击控件",
                ),
                scopeKey = "hands:${dump.packageName}:${node.label}",
            )
            if (decision == GuardDecision.DENY) {
                recordTrace(sessionId, "tap", dump.packageName, node.label, "用户拒绝放行", "blocked")
                return "DENIED: 用户拒绝点击「${node.label}」，请中止敏感操作。"
            }
        }

        stepCounter++
        _capsuleState.value = _capsuleState.value.copy(
            active = true,
            stepIndex = stepCounter,
            stepText = "点击「${node.label.take(12)}」",
        )
        syncOverlay()

        val ok = if (_engineMode.value == HandsEngineMode.SHIZUKU && shizuku.hasPermission()) {
            shizuku.tap(node.centerX, node.centerY)
        } else {
            val accOk = svc.tap(node.centerX.toFloat(), node.centerY.toFloat())
            if (!accOk && shizuku.hasPermission()) shizuku.tap(node.centerX, node.centerY) else accOk
        }

        recordTrace(
            sessionId,
            "tap",
            dump.packageName,
            node.label,
            "(${node.centerX},${node.centerY})",
            if (ok) "ok" else "failed",
        )
        return if (ok) "OK: 已点击「${node.label}」" else "FAILED: 点击未生效"
    }

    suspend fun inputText(sessionId: String, nodeIndex: Int, text: String): String {
        val svc = NeonAccessibilityService.instance.value ?: return "ERROR: 无障碍服务未开启"
        val dump = svc.dumpScreen()
        val node = dump.nodes.firstOrNull { it.index == nodeIndex }
            ?: return "ERROR: 未找到编号 [$nodeIndex] 的输入框"
        if (node.isPassword) {
            _capsuleState.value = _capsuleState.value.copy(paused = true, stepText = "密码输入已交由人工")
            syncOverlay()
            return "BLOCKED: 密码输入框禁止 AI 自动填充，已暂停等待用户手动输入。"
        }
        val ok = svc.inputText(node.centerX, node.centerY, text)
        stepCounter++
        recordTrace(sessionId, "input", dump.packageName, node.label, "输入 ${text.length} 字", if (ok) "ok" else "failed")
        return if (ok) "OK: 已向「${node.label}」输入文本" else "FAILED: 输入失败"
    }

    suspend fun openApp(sessionId: String, appQuery: String): String {
        val resolvedPkg = HandsApps.resolve(context, appQuery)?.packageName ?: appQuery
        val intent = context.packageManager.getLaunchIntentForPackage(resolvedPkg)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            stepCounter++
            recordTrace(sessionId, "open_app", resolvedPkg, appQuery, "启动应用", "ok")
            "OK: 已打开 $resolvedPkg"
        } else if (shizuku.hasPermission() && shizuku.startApp(resolvedPkg)) {
            stepCounter++
            recordTrace(sessionId, "open_app", resolvedPkg, appQuery, "Shizuku 启动", "ok")
            "OK: 已通过 Shizuku 打开 $resolvedPkg"
        } else {
            "ERROR: 未安装或无法打开应用 $appQuery ($resolvedPkg)"
        }
    }

    fun listInstalledApps(): String {
        val apps = HandsApps.launchable(context)
        if (apps.isEmpty()) return "未获取到可启动应用列表"
        return apps.joinToString("\n") { "${it.label} -> ${it.packageName}" }
    }

    suspend fun swipe(sessionId: String, x1: Float, y1: Float, x2: Float, y2: Float): String {
        val svc = NeonAccessibilityService.instance.value ?: return "ERROR: 无障碍服务未开启"
        stepCounter++
        _capsuleState.value = _capsuleState.value.copy(active = true, stepIndex = stepCounter, stepText = "滑动屏幕")
        syncOverlay()
        val ok = if (_engineMode.value == HandsEngineMode.SHIZUKU && shizuku.hasPermission()) {
            shizuku.swipe(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
        } else {
            svc.swipe(x1, y1, x2, y2)
        }
        recordTrace(sessionId, "swipe", svc.dumpScreen().packageName, "滑动", "(${x1.toInt()},${y1.toInt()})->(${x2.toInt()},${y2.toInt()})", if (ok) "ok" else "failed")
        return if (ok) "OK: 已完成滑动" else "FAILED: 滑动失败"
    }

    suspend fun captureSetOfMarkScreenshot(): String {
        val svc = NeonAccessibilityService.instance.value ?: return "ERROR: 无障碍服务未开启"
        val raw = svc.captureScreenBitmap() ?: return "ERROR: 系统版本低于 Android 11 或截屏失败"
        val dump = svc.dumpScreen()
        val mutable = raw.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#84CC16")
        }
        val tagBgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#141416")
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#A3E635")
            textSize = 26f
            isAntiAlias = true
        }
        dump.nodes.forEach { n ->
            canvas.drawRect(n.bounds, boxPaint)
            val tag = "${n.index}"
            val tx = n.bounds.left.toFloat().coerceAtLeast(4f)
            val ty = n.bounds.top.toFloat().coerceAtLeast(28f)
            canvas.drawRect(tx, ty - 24f, tx + 34f, ty + 4f, tagBgPaint)
            canvas.drawText(tag, tx + 4f, ty - 2f, textPaint)
        }
        val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
        val outFile = File(tmpDir, "latest_som.jpg")
        FileOutputStream(outFile).use { fos ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 82, fos)
        }
        return "OK: Set-of-Mark 截图已保存 (${outFile.absolutePath}, ${outFile.length()} bytes)\n${dump.compactText}"
    }

    fun globalBack(sessionId: String): String {
        val ok = NeonAccessibilityService.instance.value?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
        return if (ok) "OK: 已执行返回" else "ERROR: 无障碍服务未开启"
    }

    private fun syncOverlay() {
        NeonAccessibilityService.instance.value?.showCapsule(
            state = _capsuleState.value,
            onPauseToggle = { togglePause() },
            onStop = { stopRun() },
        )
    }

    private suspend fun recordTrace(
        sessionId: String,
        action: String,
        pkg: String,
        label: String,
        detail: String,
        status: String,
    ) {
        traceDao.insert(
            HandsTraceEntity(
                id = UUID.randomUUID().toString(),
                runId = activeRunId,
                sessionId = sessionId,
                stepIndex = stepCounter,
                engine = if (_engineMode.value == HandsEngineMode.SHIZUKU) "shizuku" else "accessibility",
                targetPackage = pkg,
                actionType = action,
                targetLabel = label,
                detail = detail,
                status = status,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
