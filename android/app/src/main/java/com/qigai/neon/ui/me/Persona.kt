package com.qigai.neon.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.MainViewModel
import com.qigai.neon.agent.PersonaPrompt
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.data.Tone
import com.qigai.neon.ui.components.NeonOrb
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass
import com.qigai.neon.ui.theme.neonBackdrop

data class PersonaState(
    val name: String = "Neon",
    val call: String = "",
    val tone: Tone = Tone.CRISP,
    val extra: String = "",
)

/** Neon 的性格: name, how it calls you, tone. Saved on 完成. */
@Composable
fun PersonaRoute(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    var st by rememberSaveable(s.loaded, stateSaver = PersonaSaver) {
        mutableStateOf(PersonaState(s.agentName, s.userCall, s.tone, s.toneExtra))
    }
    PersonaContent(st, onChange = { st = it }, onBack = onBack, onDone = {
        vm.putSetting(SettingsRepository.AGENT_NAME, st.name.trim().ifEmpty { null })
        vm.putSetting(SettingsRepository.USER_CALL, st.call.trim())
        vm.putSetting(SettingsRepository.TONE, st.tone.key)
        vm.putSetting(SettingsRepository.TONE_EXTRA, st.extra.trim().ifEmpty { null })
        onBack()
    })
}

private val PersonaSaver = androidx.compose.runtime.saveable.listSaver<PersonaState, String>(
    save = { listOf(it.name, it.call, it.tone.key, it.extra) },
    restore = { PersonaState(it[0], it[1], Tone.of(it[2]), it[3]) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonaContent(st: PersonaState, onChange: (PersonaState) -> Unit, onBack: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding().imePadding()) {
        SubPageBar("${st.name.ifBlank { "Neon" }} 的性格", onBack, onDone)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { NeonOrb(84.dp) }
            GlassGroup {
                FieldRow("名字", st.name, { onChange(st.copy(name = it.take(16))) }, hint = "Neon", labelWidth = 76.dp)
                GroupDivider()
                FieldRow("怎么叫你", st.call, { onChange(st.copy(call = it.take(16))) }, hint = "不用称呼", labelWidth = 76.dp, clearable = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("老板", "老师", "不用称呼").forEach { q ->
                    val value = if (q == "不用称呼") "" else q
                    PickChip(q, on = false, height = 32.dp) { onChange(st.copy(call = value)) }
                }
            }
            MeSection("语气")
            Tone.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t -> ToneTile(t.label, st.tone == t, Modifier.weight(1f)) { onChange(st.copy(tone = t)) } }
                }
            }
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("再补充几句", style = NeonType.caption())
                Spacer(Modifier.height(4.dp))
                PlainField(st.extra, { onChange(st.copy(extra = it.take(300))) }, "比如：少用感叹号，先给结论再解释", NeonType.item().copy(fontSize = 16.sp, lineHeight = 24.sp), Modifier.fillMaxWidth())
            }
            MeSection("听起来像这样")
            Row(verticalAlignment = Alignment.Bottom) {
                NeonOrb(26.dp)
                Spacer(Modifier.width(10.dp))
                val sample = remember(st.call, st.tone) { PersonaPrompt.sample(st.name, st.call, st.tone) }
                Text(
                    sample, style = NeonType.body(),
                    modifier = Modifier.weight(1f).glass(com.qigai.neon.ui.chat.BubbleShape).padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
