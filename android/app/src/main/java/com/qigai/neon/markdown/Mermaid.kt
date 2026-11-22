package com.qigai.neon.markdown

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import kotlinx.serialization.json.JsonPrimitive

/**
 * A ```mermaid block drawn by the bundled mermaid.min.js (assets/web, offline) in a transparent WebView
 * that reports its own height. Tap 源码 to see the text; a diagram that fails to parse falls back to it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidBlock(code: String) {
    val c = Neon.colors
    var height by remember(code) { mutableIntStateOf(0) }
    var failed by remember(code) { mutableStateOf(false) }
    var source by remember(code) { mutableStateOf(false) }
    val dark = c.isDark
    val ink = String.format("#%06X", 0xFFFFFF and c.inkPrimary.toArgb())
    val line = String.format("#%06X", 0xFFFFFF and c.inkSecondary.toArgb())
    val fill = String.format("#%06X", 0xFFFFFF and c.bgTile.toArgb())
    val edge = String.format("#%06X", 0xFFFFFF and c.borderCard.toArgb())
    val accent = String.format("#%06X", 0xFFFFFF and c.lime.toArgb())
    Column(
        Modifier.fillMaxWidth().clip(NeonShapes.item).border(1.dp, c.borderHairline, NeonShapes.item).background(c.bgSurface)
    ) {
        Row(Modifier.fillMaxWidth().background(c.bgSubtle).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("mermaid", style = NeonType.caption().copy(fontFamily = Neon.fonts.code), modifier = Modifier.weight(1f))
            Text(
                if (source || failed) "图" else "源码", style = NeonType.caption(),
                modifier = Modifier.clip(NeonShapes.capsule).clickable(enabled = !failed) { source = !source }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (source || failed) {
            SelectionContainer {
                Text(code, style = NeonType.code(), softWrap = false, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp))
            }
        } else Box(Modifier.fillMaxWidth().heightIn(min = 60.dp).height(if (height > 0) height.dp else 160.dp)) {
            val html = remember(code, dark) {
                """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;padding:0;background:transparent}#d{padding:12px;display:flex;justify-content:center}svg{max-width:100%;height:auto}</style>
<script src="mermaid.min.js"></script></head><body><div id="d"></div><script>
mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'base',fontFamily:'sans-serif',
 themeVariables:{darkMode:$dark,background:'transparent',primaryColor:'$fill',primaryTextColor:'$ink',primaryBorderColor:'$edge',
 lineColor:'$line',secondaryColor:'$fill',tertiaryColor:'$fill',textColor:'$ink',noteBkgColor:'$fill',noteTextColor:'$ink',
 actorBkg:'$fill',actorBorder:'$edge',actorTextColor:'$ink',signalColor:'$line',signalTextColor:'$ink',labelTextColor:'$ink',
 clusterBkg:'transparent',clusterBorder:'$edge',edgeLabelBackground:'transparent',pie1:'$accent'}});
mermaid.render('m',${JsonPrimitive(code)}).then(function(r){document.getElementById('d').innerHTML=r.svg;
 setTimeout(function(){Neon.height(Math.ceil(document.getElementById('d').getBoundingClientRect().height));},30);})
 .catch(function(e){Neon.fail(String(e));});
</script></body></html>"""
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(AColor.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        isVerticalScrollBarEnabled = false
                        addJavascriptInterface(object {
                            @JavascriptInterface fun height(px: Int) { post { height = px.coerceIn(40, 1600) } }
                            @JavascriptInterface fun fail(msg: String) { post { failed = true } }
                        }, "Neon")
                        loadDataWithBaseURL("file:///android_asset/web/", html, "text/html", "utf-8", null)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(if (height > 0) height.dp else 160.dp),
            )
        }
    }
}
