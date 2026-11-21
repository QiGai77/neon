package com.qigai.neon

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.qigai.neon.oauth.LoopbackCallback

class MainActivity : FragmentActivity() {
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        val container = (application as NeonApplication).container
        setContent {
            NeonApp(
                container = container,
                sharedText = sharedText,
                onConsumeSharedText = { sharedText = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        val data = intent.data
        if (data != null && data.scheme == "com.qigai.neon" && data.host == "oauth") {
            LoopbackCallback.deliverDeepLink(data)
        }
    }
}
