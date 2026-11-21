package com.qigai.neon.hands

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

data class HandsCapsuleState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val stepIndex: Int = 0,
    val maxSteps: Int = 12,
    val stepText: String = "",
    val engineLabel: String = "引擎 A · 无障碍",
)

class HandsCapsuleOverlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var capsuleView: LinearLayout? = null
    private var stepTextView: TextView? = null
    private var pauseBtnView: TextView? = null

    fun showOrUpdate(state: HandsCapsuleState, onPauseToggle: () -> Unit, onStop: () -> Unit) {
        mainHandler.post {
            if (!state.active) {
                hideInternal()
                return@post
            }
            if (capsuleView == null) {
                createCapsule(onPauseToggle, onStop)
            }
            stepTextView?.text = "第 ${state.stepIndex}/${state.maxSteps} 步 · ${state.stepText}"
            pauseBtnView?.text = if (state.paused) "▶ 继续" else "⏸ 接管"
        }
    }

    fun hideCapsule() {
        mainHandler.post { hideInternal() }
    }

    fun showTapRipple(x: Int, y: Int) {
        mainHandler.post {
            val sizePx = (72 * context.resources.displayMetrics.density).toInt()
            val rippleView = object : View(context) {
                private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                    color = Color.parseColor("#F59E0B")
                }
                private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(65, 245, 158, 11)
                }

                override fun onDraw(canvas: Canvas) {
                    val r = width / 2f - 8f
                    canvas.drawCircle(width / 2f, height / 2f, r, fillPaint)
                    canvas.drawCircle(width / 2f, height / 2f, r, strokePaint)
                }
            }
            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x - sizePx / 2
                this.y = y - sizePx / 2
            }
            runCatching {
                wm.addView(rippleView, params)
                mainHandler.postDelayed({ runCatching { wm.removeView(rippleView) } }, 450L)
            }
        }
    }

    private fun createCapsule(onPauseToggle: () -> Unit, onStop: () -> Unit) {
        val density = context.resources.displayMetrics.density
        val padH = (14 * density).toInt()
        val padV = (10 * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141416"))
                cornerRadius = 22 * density
                setStroke((1 * density).toInt(), Color.parseColor("#323236"))
            }
        }

        val stepTv = TextView(context).apply {
            setTextColor(Color.parseColor("#EAEAE6"))
            textSize = 12f
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        val pauseBtn = TextView(context).apply {
            text = "⏸ 接管"
            setTextColor(Color.parseColor("#EAEAE6"))
            textSize = 12f
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#262629"))
                cornerRadius = 12 * density
            }
            setOnClickListener { onPauseToggle() }
        }
        val stopBtn = TextView(context).apply {
            text = "■ 急停"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626"))
                cornerRadius = 12 * density
            }
            setOnClickListener { onStop() }
        }
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((8 * density).toInt(), 1)
        }
        row.addView(pauseBtn)
        row.addView(spacer)
        row.addView(stopBtn)
        root.addView(stepTv)
        root.addView(row)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (42 * density).toInt()
        }

        runCatching {
            wm.addView(root, params)
            capsuleView = root
            stepTextView = stepTv
            pauseBtnView = pauseBtn
        }
    }

    private fun hideInternal() {
        capsuleView?.let { runCatching { wm.removeView(it) } }
        capsuleView = null
        stepTextView = null
        pauseBtnView = null
    }
}
