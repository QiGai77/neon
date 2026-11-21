package com.qigai.neon.hands

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class UiNodeSnapshot(
    val index: Int,
    val className: String,
    val text: String,
    val contentDesc: String,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val isPassword: Boolean,
) {
    val label: String get() = text.ifBlank { contentDesc }.ifBlank { className.substringAfterLast('.') }
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()
}

data class ScreenDump(
    val packageName: String,
    val nodes: List<UiNodeSnapshot>,
    val compactText: String,
    val hasPasswordField: Boolean,
)

object ScreenWords {
    fun dump(root: AccessibilityNodeInfo?): ScreenDump {
        if (root == null) {
            return ScreenDump("", emptyList(), "[无障碍根节点不可用]", false)
        }
        val pkg = root.packageName?.toString().orEmpty()
        val nodes = mutableListOf<UiNodeSnapshot>()
        var hasPassword = false

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24 || nodes.size >= 140) return
            if (!node.isVisibleToUser) return

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val clickable = node.isClickable
            val editable = node.isEditable
            val scrollable = node.isScrollable
            val isPwd = node.isPassword
            if (isPwd) hasPassword = true

            if (text.isNotEmpty() || desc.isNotEmpty() || clickable || editable) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    nodes += UiNodeSnapshot(
                        index = nodes.size + 1,
                        className = node.className?.toString().orEmpty(),
                        text = text,
                        contentDesc = desc,
                        bounds = rect,
                        clickable = clickable,
                        editable = editable,
                        scrollable = scrollable,
                        isPassword = isPwd,
                    )
                }
            }

            for (i in 0 until node.childCount) {
                walk(node.getChild(i), depth + 1)
            }
        }

        walk(root, 0)

        val formatted = buildString {
            appendLine("Package: $pkg")
            nodes.forEach { n ->
                val flags = buildList {
                    if (n.clickable) add("tap")
                    if (n.editable) add("input")
                    if (n.scrollable) add("scroll")
                    if (n.isPassword) add("PASSWORD")
                }.joinToString(",")
                appendLine("[${n.index}] ${n.label} (${n.centerX},${n.centerY}) [$flags]")
            }
        }.trim()

        return ScreenDump(pkg, nodes, formatted, hasPassword)
    }
}
