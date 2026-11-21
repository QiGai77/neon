package com.qigai.neon.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Lucide icons (ISC License, https://lucide.dev) as Compose vectors, stroke 1.75 to match the v0.3 concepts.
 * Generated from lucide-static 0.460.0. Tint with Icon(tint=...); the stroke colour here is a placeholder.
 */
object Lucide {
    private fun lucide(name: String, vararg paths: String, stroke: Float = 1.75f): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val ArrowDown: ImageVector by lazy { lucide("arrow-down", "M12 5v14", "m19 12-7 7-7-7") }
    val ArrowLeft: ImageVector by lazy { lucide("arrow-left", "m12 19-7-7 7-7", "M19 12H5") }
    val ArrowRight: ImageVector by lazy { lucide("arrow-right", "M5 12h14", "m12 5 7 7-7 7") }
    val ArrowUpRight: ImageVector by lazy { lucide("arrow-up-right", "M7 7h10v10", "M7 17 17 7") }
    val ArrowUp: ImageVector by lazy { lucide("arrow-up", "m5 12 7-7 7 7", "M12 19V5") }
    val Bell: ImageVector by lazy { lucide("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val Book: ImageVector by lazy { lucide("book", "M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H19a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H6.5a1 1 0 0 1 0-5H20") }
    val Bookmark: ImageVector by lazy { lucide("bookmark", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z") }
    val Brain: ImageVector by lazy { lucide("brain", "M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z", "M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z", "M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4", "M17.599 6.5a3 3 0 0 0 .399-1.375", "M6.003 5.125A3 3 0 0 0 6.401 6.5", "M3.477 10.896a4 4 0 0 1 .585-.396", "M19.938 10.5a4 4 0 0 1 .585.396", "M6 18a4 4 0 0 1-1.967-.516", "M19.967 17.484A4 4 0 0 1 18 18") }
    val CalendarClock: ImageVector by lazy { lucide("calendar-clock", "M21 7.5V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h3.5", "M16 2v4", "M8 2v4", "M3 10h5", "M17.5 17.5 16 16.3V14", "M10.0 16.0a6.0 6.0 0 1 0 12.0 0a6.0 6.0 0 1 0 -12.0 0") }
    val Calendar: ImageVector by lazy { lucide("calendar", "M8 2v4", "M16 2v4", "M5.0 4.0H19.0a2.0 2.0 0 0 1 2.0 2.0V20.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V6.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M3 10h18") }
    val Check: ImageVector by lazy { lucide("check", "M20 6 9 17l-5-5") }
    val ChevronDown: ImageVector by lazy { lucide("chevron-down", "m6 9 6 6 6-6") }
    val ChevronLeft: ImageVector by lazy { lucide("chevron-left", "m15 18-6-6 6-6") }
    val ChevronRight: ImageVector by lazy { lucide("chevron-right", "m9 18 6-6-6-6") }
    val ChevronUp: ImageVector by lazy { lucide("chevron-up", "m18 15-6-6-6 6") }
    val CircleAlert: ImageVector by lazy { lucide("circle-alert", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0", "M12 8L12 12", "M12 16L12.01 16") }
    val CircleCheck: ImageVector by lazy { lucide("circle-check", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0", "m9 12 2 2 4-4") }
    val CircleDashed: ImageVector by lazy { lucide("circle-dashed", "M10.1 2.182a10 10 0 0 1 3.8 0", "M13.9 21.818a10 10 0 0 1-3.8 0", "M17.609 3.721a10 10 0 0 1 2.69 2.7", "M2.182 13.9a10 10 0 0 1 0-3.8", "M20.279 17.609a10 10 0 0 1-2.7 2.69", "M21.818 10.1a10 10 0 0 1 0 3.8", "M3.721 6.391a10 10 0 0 1 2.7-2.69", "M6.391 20.279a10 10 0 0 1-2.69-2.7") }
    val CircleX: ImageVector by lazy { lucide("circle-x", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0", "m15 9-6 6", "m9 9 6 6") }
    val Cloud: ImageVector by lazy { lucide("cloud", "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z") }
    val Copy: ImageVector by lazy { lucide("copy", "M10.0 8.0H20.0a2.0 2.0 0 0 1 2.0 2.0V20.0a2.0 2.0 0 0 1 -2.0 2.0H10.0a2.0 2.0 0 0 1 -2.0 -2.0V10.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2") }
    val CornerDownLeft: ImageVector by lazy { lucide("corner-down-left", "M9 10L4 15L9 20", "M20 4v7a4 4 0 0 1-4 4H4") }
    val Cpu: ImageVector by lazy { lucide("cpu", "M6.0 4.0H18.0a2.0 2.0 0 0 1 2.0 2.0V18.0a2.0 2.0 0 0 1 -2.0 2.0H6.0a2.0 2.0 0 0 1 -2.0 -2.0V6.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M10.0 9.0H14.0a1.0 1.0 0 0 1 1.0 1.0V14.0a1.0 1.0 0 0 1 -1.0 1.0H10.0a1.0 1.0 0 0 1 -1.0 -1.0V10.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M15 2v2", "M15 20v2", "M2 15h2", "M2 9h2", "M20 15h2", "M20 9h2", "M9 2v2", "M9 20v2") }
    val Download: ImageVector by lazy { lucide("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10L12 15L17 10", "M12 15L12 3") }
    val EllipsisVertical: ImageVector by lazy { lucide("ellipsis-vertical", "M11.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0", "M11.0 5.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0", "M11.0 19.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0") }
    val ExternalLink: ImageVector by lazy { lucide("external-link", "M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6") }
    val FileJson: ImageVector by lazy { lucide("file-json", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v4a2 2 0 0 0 2 2h4", "M10 12a1 1 0 0 0-1 1v1a1 1 0 0 1-1 1 1 1 0 0 1 1 1v1a1 1 0 0 0 1 1", "M14 18a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1 1 1 0 0 1-1-1v-1a1 1 0 0 0-1-1") }
    val FileText: ImageVector by lazy { lucide("file-text", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v4a2 2 0 0 0 2 2h4", "M10 9H8", "M16 13H8", "M16 17H8") }
    val Fingerprint: ImageVector by lazy { lucide("fingerprint", "M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4", "M14 13.12c0 2.38 0 6.38-1 8.88", "M17.29 21.02c.12-.6.43-2.3.5-3.02", "M2 12a10 10 0 0 1 18-6", "M2 16h.01", "M21.8 16c.2-2 .131-5.354 0-6", "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2", "M8.65 22c.21-.66.45-1.32.57-2", "M9 6.8a6 6 0 0 1 9 5.2v2") }
    val Folder: ImageVector by lazy { lucide("folder", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z") }
    val Globe: ImageVector by lazy { lucide("globe", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0", "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20") }
    val Hand: ImageVector by lazy { lucide("hand", "M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2", "M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2", "M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8", "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15") }
    val HardDrive: ImageVector by lazy { lucide("hard-drive", "M22 12L2 12", "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z", "M6 16L6.01 16", "M10 16L10.01 16") }
    val History: ImageVector by lazy { lucide("history", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2") }
    val Info: ImageVector by lazy { lucide("info", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0", "M12 16v-4", "M12 8h.01") }
    val KeyRound: ImageVector by lazy { lucide("key-round", "M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z", "M16.0 7.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0") }
    val Key: ImageVector by lazy { lucide("key", "m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4", "m21 2-9.6 9.6", "M2.0 15.5a5.5 5.5 0 1 0 11.0 0a5.5 5.5 0 1 0 -11.0 0") }
    val Layers: ImageVector by lazy { lucide("layers", "m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z", "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65", "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65") }
    val Link: ImageVector by lazy { lucide("link", "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71") }
    val ListChecks: ImageVector by lazy { lucide("list-checks", "m3 17 2 2 4-4", "m3 7 2 2 4-4", "M13 6h8", "M13 12h8", "M13 18h8") }
    val LoaderCircle: ImageVector by lazy { lucide("loader-circle", "M21 12a9 9 0 1 1-6.219-8.56") }
    val Lock: ImageVector by lazy { lucide("lock", "M5.0 11.0H19.0a2.0 2.0 0 0 1 2.0 2.0V20.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V13.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M7 11V7a5 5 0 0 1 10 0v4") }
    val Mail: ImageVector by lazy { lucide("mail", "M4.0 4.0H20.0a2.0 2.0 0 0 1 2.0 2.0V18.0a2.0 2.0 0 0 1 -2.0 2.0H4.0a2.0 2.0 0 0 1 -2.0 -2.0V6.0a2.0 2.0 0 0 1 2.0 -2.0Z", "m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7") }
    val Menu: ImageVector by lazy { lucide("menu", "M4 12L20 12", "M4 6L20 6", "M4 18L20 18") }
    val MessageSquareText: ImageVector by lazy { lucide("message-square-text", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z", "M13 8H7", "M17 12H7") }
    val MessageSquare: ImageVector by lazy { lucide("message-square", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z") }
    val Mic: ImageVector by lazy { lucide("mic", "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19L12 22") }
    val Minus: ImageVector by lazy { lucide("minus", "M5 12h14") }
    val Monitor: ImageVector by lazy { lucide("monitor", "M4.0 3.0H20.0a2.0 2.0 0 0 1 2.0 2.0V15.0a2.0 2.0 0 0 1 -2.0 2.0H4.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M8 21L16 21", "M12 17L12 21") }
    val Moon: ImageVector by lazy { lucide("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z") }
    val OctagonAlert: ImageVector by lazy { lucide("octagon-alert", "M12 16h.01", "M12 8v4", "M15.312 2a2 2 0 0 1 1.414.586l4.688 4.688A2 2 0 0 1 22 8.688v6.624a2 2 0 0 1-.586 1.414l-4.688 4.688a2 2 0 0 1-1.414.586H8.688a2 2 0 0 1-1.414-.586l-4.688-4.688A2 2 0 0 1 2 15.312V8.688a2 2 0 0 1 .586-1.414l4.688-4.688A2 2 0 0 1 8.688 2z") }
    val Package: ImageVector by lazy { lucide("package", "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z", "M12 22V12", "m3.3 7 7.703 4.734a2 2 0 0 0 1.994 0L20.7 7", "m7.5 4.27 9 5.15") }
    val Palette: ImageVector by lazy { lucide("palette", "M13.0 6.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0", "M17.0 10.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0", "M8.0 7.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0", "M6.0 12.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0", "M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z") }
    val Pause: ImageVector by lazy { lucide("pause", "M15.0 4.0H17.0a1.0 1.0 0 0 1 1.0 1.0V19.0a1.0 1.0 0 0 1 -1.0 1.0H15.0a1.0 1.0 0 0 1 -1.0 -1.0V5.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M7.0 4.0H9.0a1.0 1.0 0 0 1 1.0 1.0V19.0a1.0 1.0 0 0 1 -1.0 1.0H7.0a1.0 1.0 0 0 1 -1.0 -1.0V5.0a1.0 1.0 0 0 1 1.0 -1.0Z") }
    val Pencil: ImageVector by lazy { lucide("pencil", "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4") }
    val Play: ImageVector by lazy { lucide("play", "M6 3L20 12L6 21L6 3Z") }
    val Plug: ImageVector by lazy { lucide("plug", "M12 22v-5", "M9 8V2", "M15 8V2", "M18 8v5a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V8Z") }
    val Plus: ImageVector by lazy { lucide("plus", "M5 12h14", "M12 5v14") }
    val RefreshCw: ImageVector by lazy { lucide("refresh-cw", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5") }
    val RotateCw: ImageVector by lazy { lucide("rotate-cw", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5") }
    val Route: ImageVector by lazy { lucide("route", "M3.0 19.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0", "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15", "M15.0 5.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0") }
    val ScanFace: ImageVector by lazy { lucide("scan-face", "M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M8 14s1.5 2 4 2 4-2 4-2", "M9 9h.01", "M15 9h.01") }
    val Search: ImageVector by lazy { lucide("search", "M3.0 11.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0", "m21 21-4.3-4.3") }
    val Server: ImageVector by lazy { lucide("server", "M4.0 2.0H20.0a2.0 2.0 0 0 1 2.0 2.0V8.0a2.0 2.0 0 0 1 -2.0 2.0H4.0a2.0 2.0 0 0 1 -2.0 -2.0V4.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M4.0 14.0H20.0a2.0 2.0 0 0 1 2.0 2.0V20.0a2.0 2.0 0 0 1 -2.0 2.0H4.0a2.0 2.0 0 0 1 -2.0 -2.0V16.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M6 6L6.01 6", "M6 18L6.01 18") }
    val Settings: ImageVector by lazy { lucide("settings", "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z", "M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0") }
    val ShieldAlert: ImageVector by lazy { lucide("shield-alert", "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z", "M12 8v4", "M12 16h.01") }
    val Shield: ImageVector by lazy { lucide("shield", "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z") }
    val Slash: ImageVector by lazy { lucide("slash", "M22 2 2 22") }
    val SlidersHorizontal: ImageVector by lazy { lucide("sliders-horizontal", "M21 4L14 4", "M10 4L3 4", "M21 12L12 12", "M8 12L3 12", "M21 20L16 20", "M12 20L3 20", "M14 2L14 6", "M8 10L8 14", "M16 18L16 22") }
    val Smartphone: ImageVector by lazy { lucide("smartphone", "M7.0 2.0H17.0a2.0 2.0 0 0 1 2.0 2.0V20.0a2.0 2.0 0 0 1 -2.0 2.0H7.0a2.0 2.0 0 0 1 -2.0 -2.0V4.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M12 18h.01") }
    val Sparkles: ImageVector by lazy { lucide("sparkles", "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z", "M20 3v4", "M22 5h-4", "M4 17v2", "M5 18H3") }
    val SquarePen: ImageVector by lazy { lucide("square-pen", "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7", "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z") }
    val SquareTerminal: ImageVector by lazy { lucide("square-terminal", "m7 11 2-2-2-2", "M11 13h4", "M5.0 3.0H19.0a2.0 2.0 0 0 1 2.0 2.0V19.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z") }
    val Square: ImageVector by lazy { lucide("square", "M5.0 3.0H19.0a2.0 2.0 0 0 1 2.0 2.0V19.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z") }
    val Sun: ImageVector by lazy { lucide("sun", "M8.0 12.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41") }
    val Text: ImageVector by lazy { lucide("text", "M17 6.1H3", "M21 12.1H3", "M15.1 18H3") }
    val Trash2: ImageVector by lazy { lucide("trash-2", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11L10 17", "M14 11L14 17") }
    val Truck: ImageVector by lazy { lucide("truck", "M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2", "M15 18H9", "M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14", "M15.0 18.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0", "M5.0 18.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0") }
    val Upload: ImageVector by lazy { lucide("upload", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M17 8L12 3L7 8", "M12 3L12 15") }
    val UserRound: ImageVector by lazy { lucide("user-round", "M7.0 8.0a5.0 5.0 0 1 0 10.0 0a5.0 5.0 0 1 0 -10.0 0", "M20 21a8 8 0 0 0-16 0") }
    val WandSparkles: ImageVector by lazy { lucide("wand-sparkles", "m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72", "m14 7 3 3", "M5 6v4", "M19 14v4", "M10 2v2", "M7 8H3", "M21 16h-4", "M11 3H9") }
    val WifiOff: ImageVector by lazy { lucide("wifi-off", "M12 20h.01", "M8.5 16.429a5 5 0 0 1 7 0", "M5 12.859a10 10 0 0 1 5.17-2.69", "M19 12.859a10 10 0 0 0-2.007-1.523", "M2 8.82a15 15 0 0 1 4.177-2.643", "M22 8.82a15 15 0 0 0-11.288-3.764", "m2 2 20 20") }
    val Workflow: ImageVector by lazy { lucide("workflow", "M5.0 3.0H9.0a2.0 2.0 0 0 1 2.0 2.0V9.0a2.0 2.0 0 0 1 -2.0 2.0H5.0a2.0 2.0 0 0 1 -2.0 -2.0V5.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M7 11v4a2 2 0 0 0 2 2h4", "M15.0 13.0H19.0a2.0 2.0 0 0 1 2.0 2.0V19.0a2.0 2.0 0 0 1 -2.0 2.0H15.0a2.0 2.0 0 0 1 -2.0 -2.0V15.0a2.0 2.0 0 0 1 2.0 -2.0Z") }
    val X: ImageVector by lazy { lucide("x", "M18 6 6 18", "m6 6 12 12") }
    val Zap: ImageVector by lazy { lucide("zap", "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z") }
}
