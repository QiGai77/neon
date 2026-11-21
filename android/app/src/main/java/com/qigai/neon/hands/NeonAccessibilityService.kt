package com.qigai.neon.hands

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class NeonAccessibilityService : AccessibilityService() {

    private var overlay: HandsCapsuleOverlay? = null
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance.value = this
        overlay = HandsCapsuleOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Real-time window changes can be observed via rootInActiveWindow on demand
    }

    override fun onInterrupt() {
        overlay?.hideCapsule()
    }

    override fun onDestroy() {
        overlay?.hideCapsule()
        overlay = null
        _instance.value = null
        screenshotExecutor.shutdownNow()
        super.onDestroy()
    }

    fun dumpScreen(): ScreenDump = ScreenWords.dump(rootInActiveWindow)

    fun showCapsule(state: HandsCapsuleState, onPauseToggle: () -> Unit, onStop: () -> Unit) {
        overlay?.showOrUpdate(state, onPauseToggle, onStop)
    }

    fun hideCapsule() {
        overlay?.hideCapsule()
    }

    fun showTapRipple(x: Int, y: Int) {
        overlay?.showTapRipple(x, y)
    }

    suspend fun tap(x: Float, y: Float): Boolean {
        showTapRipple(x.toInt(), y.toInt())
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        return dispatchGestureAsync(GestureDescription.Builder().addStroke(stroke).build())
    }

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 320L): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        return dispatchGestureAsync(GestureDescription.Builder().addStroke(stroke).build())
    }

    fun inputText(targetX: Int, targetY: Int, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findEditableNear(root, targetX, targetY) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (target != null && target.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    suspend fun captureScreenBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { cont ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hw = screenshot.hardwareBuffer
                        val bmp = Bitmap.wrapHardwareBuffer(hw, screenshot.colorSpace)
                        hw.close()
                        if (cont.isActive) cont.resume(bmp)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }
    }

    private fun findEditableNear(node: AccessibilityNodeInfo?, x: Int, y: Int): AccessibilityNodeInfo? {
        if (node == null) return null
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (node.isEditable && rect.contains(x, y)) return node
        for (i in 0 until node.childCount) {
            findEditableNear(node.getChild(i), x, y)?.let { return it }
        }
        return null
    }

    private suspend fun dispatchGestureAsync(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!ok && cont.isActive) cont.resume(false)
        }

    companion object {
        private val _instance = MutableStateFlow<NeonAccessibilityService?>(null)
        val instance: StateFlow<NeonAccessibilityService?> = _instance.asStateFlow()
    }
}
