package com.qigai.neon

import android.app.Application
import androidx.room.Room
import com.qigai.neon.agent.AgentRuntime
import com.qigai.neon.agent.ToolRegistry
import com.qigai.neon.data.EncryptedTokenStore
import com.qigai.neon.data.NeonDatabase
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.provider.DeepSeekClient
import com.qigai.neon.provider.OpenCodeGoClient
import com.qigai.neon.sysfiles.DriveSync
import com.qigai.neon.voice.VoiceInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NeonApp : Application() {
    lateinit var container: NeonContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = NeonContainer(this)
    }
}

class NeonContainer(val app: NeonApp) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db: NeonDatabase = Room.databaseBuilder(app, NeonDatabase::class.java, "neon.db")
        .fallbackToDestructiveMigration()
        .build()
    val tokens = EncryptedTokenStore(app)
    val settings = SettingsRepository(db.settings(), scope)
    val deepseek = DeepSeekClient({ settings.state.value.deepseekBase }, { tokens.get(EncryptedTokenStore.DEEPSEEK) })
    val go = OpenCodeGoClient({ settings.state.value.goBase }, { tokens.get(EncryptedTokenStore.OPENCODE_GO) })
    val tools = ToolRegistry()
    val drive = DriveSync(app)
    val agent = AgentRuntime(db, tools, scope) { cid ->
        val s = settings.state.value
        if (s.driveConnected && s.driveAutoSync) DriveSync.enqueue(app, cid)
    }
    private val deepseekAsr: suspend (ByteArray) -> String = { wav -> deepseek.transcribe(wav) }
    val voice = VoiceInput(app, scope) {
        if (tokens.has(EncryptedTokenStore.DEEPSEEK)) deepseekAsr else null
    }

    init {
        scope.launch { db.messages().markDanglingStopped() }
    }
}
