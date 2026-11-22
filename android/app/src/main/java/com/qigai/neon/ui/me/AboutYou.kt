package com.qigai.neon.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.MainViewModel
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass
import com.qigai.neon.ui.theme.neonBackdrop

data class AboutState(
    val name: String = "",
    val city: String = "",
    val job: String = "",
    val bio: String = "",
    val prefs: List<String> = emptyList(),
    /** Chips offered: the presets plus anything the user added. */
    val options: List<String> = SettingsRepository.ANSWER_PREF_PRESETS,
)

/** 关于你: what Neon brings into every conversation. Saved on 完成, kept on this phone only. */
@Composable
fun AboutYouRoute(vm: MainViewModel, onBack: () -> Unit, onMemory: () -> Unit) {
    val s by vm.settings.collectAsState()
    val facts by vm.c.memory.items.collectAsState()
    var st by rememberSaveable(s.loaded, stateSaver = AboutSaver) {
        mutableStateOf(AboutState(s.userName, s.userCity, s.userJob, s.userBio, s.answerPrefs, (SettingsRepository.ANSWER_PREF_PRESETS + s.answerPrefs).distinct()))
    }
    AboutYouContent(st, facts.size, onChange = { st = it }, onBack = onBack, onMemory = onMemory, onDone = {
        vm.putSetting(SettingsRepository.USER_NAME, st.name.trim().ifEmpty { null })
        vm.putSetting(SettingsRepository.USER_CITY, st.city.trim().ifEmpty { null })
        vm.putSetting(SettingsRepository.USER_JOB, st.job.trim().ifEmpty { null })
        vm.putSetting(SettingsRepository.USER_BIO, st.bio.trim().ifEmpty { null })
        vm.putSetting(SettingsRepository.ANSWER_PREFS, SettingsRepository.encodeList(st.prefs))
        onBack()
    })
}

private val AboutSaver = androidx.compose.runtime.saveable.listSaver<AboutState, String>(
    save = { listOf(it.name, it.city, it.job, it.bio, SettingsRepository.encodeList(it.prefs), SettingsRepository.encodeList(it.options)) },
    restore = { AboutState(it[0], it[1], it[2], it[3], SettingsRepository.decodeList(it[4]), SettingsRepository.decodeList(it[5])) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutYouContent(
    st: AboutState,
    memoryCount: Int,
    onChange: (AboutState) -> Unit,
    onBack: () -> Unit,
    onMemory: () -> Unit,
    onDone: () -> Unit,
) {
    val c = Neon.colors
    var adding by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding().imePadding()) {
        SubPageBar("关于你", onBack, onDone)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("每次对话前 Neon 都会带上这些，只存在这台手机上", style = NeonType.label().copy(color = c.inkSecondary), modifier = Modifier.padding(horizontal = 6.dp))
            GlassGroup {
                FieldRow("名字", st.name, { onChange(st.copy(name = it.take(24))) }, hint = "怎么称呼你", labelWidth = 68.dp)
                GroupDivider()
                FieldRow("城市", st.city, { onChange(st.copy(city = it.take(24))) }, hint = "所在城市", labelWidth = 68.dp)
                GroupDivider()
                FieldRow("职业", st.job, { onChange(st.copy(job = it.take(24))) }, hint = "做什么的", labelWidth = 68.dp)
            }
            MeSection("背景") { Icon(Lucide.Sparkles, null, Modifier.size(16.dp), tint = c.inkTertiary) }
            Box(Modifier.fillMaxWidth().heightIn(min = 112.dp).glass(RoundedCornerShape(22.dp)).padding(16.dp)) {
                PlainField(
                    st.bio, { onChange(st.copy(bio = it.take(600))) },
                    "在做什么，熟悉什么，平时喜欢什么",
                    NeonType.item().copy(fontSize = 16.sp, lineHeight = 26.sp), Modifier.fillMaxWidth(), minLines = 3,
                )
            }
            MeSection("回答时")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                st.options.forEach { p ->
                    val on = p in st.prefs
                    PickChip(p, on) { onChange(st.copy(prefs = if (on) st.prefs - p else st.prefs + p)) }
                }
                PickChip("添加", false, icon = Lucide.Plus) { adding = true }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).glass(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = onMemory).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Brain, null, Modifier.size(22.dp), tint = c.inkPrimary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Neon 记住的", style = NeonType.item().copy(fontSize = 16.sp))
                    Text(if (memoryCount > 0) "从对话里学到的 $memoryCount 条，可以逐条改或删" else "还没有记住什么，聊多了会慢慢记下", style = NeonType.caption())
                }
                Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = c.inkSecondary)
            }
        }
    }
    if (adding) {
        var text by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = c.bgSurface,
            title = { Text("回答时还要注意", style = NeonType.title()) },
            text = {
                Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(12.dp)) {
                    PlainField(text, { text = it.take(20) }, "比如：先给结论", NeonType.item().copy(fontSize = 16.sp), Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) onChange(st.copy(options = (st.options + t).distinct(), prefs = (st.prefs + t).distinct()))
                    adding = false
                }) { Text("添加", style = NeonType.label().copy(color = pickInk())) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消", style = NeonType.label().copy(color = c.inkSecondary)) } },
        )
    }
}
