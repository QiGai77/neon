package com.qigai.neon.space

import com.qigai.neon.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** One card on the 空间 page. [data] holds the life card's fields (train number, times, tracking number...). */
@Serializable
data class SpaceCard(val id: String, val type: String, val size: String = "half", val data: Map<String, String> = emptyMap()) {
    val full get() = size == "full"
}

data class CardType(val id: String, val title: String, val life: Boolean, val defaultSize: String, val fields: List<Pair<String, String>> = emptyList())

object SpaceTypes {
    val all = listOf(
        CardType("train", "高铁票", true, "full", listOf("number" to "车次", "from" to "出发站", "to" to "到达站", "date" to "日期", "depart" to "出发时间", "arrive" to "到达时间", "seat" to "座位", "gate" to "检票口")),
        CardType("flight", "航班", true, "full", listOf("airline" to "航空公司", "number" to "航班号", "from" to "出发机场", "to" to "到达机场", "date" to "日期", "depart" to "起飞时间", "arrive" to "到达时间", "gate" to "登机口", "seat" to "座位")),
        CardType("parcel", "快递", true, "half", listOf("carrier" to "快递公司", "number" to "单号", "status" to "状态", "eta" to "预计送达")),
        CardType("nav", "导航", true, "half", listOf("title" to "目的地", "address" to "地址", "eta" to "预计用时")),
        CardType("note", "便签", true, "half", listOf("emoji" to "表情", "title" to "标题", "body" to "内容")),
        CardType("skills", "技能", false, "half"),
        CardType("connectors", "连接器", false, "half"),
        CardType("sandbox", "沙箱", false, "half"),
        CardType("memory", "记忆", false, "half"),
        CardType("tasks", "定时任务", false, "full"),
        CardType("hands", "操作手机", false, "full"),
        CardType("drive", "Google Drive", false, "full"),
        CardType("market", "市场", false, "full"),
    )
    fun of(id: String) = all.firstOrNull { it.id == id }
    val defaults = listOf("skills", "connectors", "sandbox", "memory", "tasks", "drive", "market")
        .map { SpaceCard(it, it, of(it)!!.defaultSize) }
}

class SpaceStore(private val settings: SettingsRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = ListSerializer(SpaceCard.serializer())

    val cards: Flow<List<SpaceCard>> = settings.state.map { parse(it.raw[KEY]) }.distinctUntilChanged()
    fun current(): List<SpaceCard> = parse(settings.state.value.raw[KEY])

    private fun parse(s: String?): List<SpaceCard> =
        if (s == null) SpaceTypes.defaults else runCatching { json.decodeFromString(ser, s) }.getOrDefault(SpaceTypes.defaults)

    private suspend fun save(list: List<SpaceCard>) = settings.put(KEY, json.encodeToString(ser, list))

    suspend fun add(type: String, size: String? = null, data: Map<String, String> = emptyMap(), at: Int? = null): SpaceCard? {
        val t = SpaceTypes.of(type) ?: return null
        val list = current().toMutableList()
        // feature cards are one of a kind
        if (!t.life) list.firstOrNull { it.type == type }?.let { return it }
        val card = SpaceCard(if (t.life) "${type}-" + UUID.randomUUID().toString().take(6) else type, type, size ?: t.defaultSize, data)
        list.add((at ?: if (t.life) 0 else list.size).coerceIn(0, list.size), card)
        save(list)
        return card
    }

    suspend fun update(id: String, size: String? = null, data: Map<String, String>? = null) {
        save(current().map { if (it.id != id) it else it.copy(size = size ?: it.size, data = data?.let { d -> (it.data + d).filterValues { v -> v.isNotBlank() } } ?: it.data) })
    }

    suspend fun remove(id: String) = save(current().filterNot { it.id == id })

    suspend fun move(id: String, to: Int) {
        val list = current().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        val card = list.removeAt(i)
        list.add(to.coerceIn(0, list.size), card)
        save(list)
    }

    suspend fun reset() = settings.put(KEY, null)

    /** The space_cards tool. */
    suspend fun handleTool(args: JsonObject): String {
        fun str(k: String) = (args[k] as? JsonPrimitive)?.contentOrNull
        val data = runCatching { args["data"]?.jsonObject?.mapValues { (_, v) -> v.jsonPrimitive.content } }.getOrNull()
        return when (str("action")) {
            "list" -> current().joinToString("\n") { c -> "${c.id}  ${c.type}  ${c.size}" + if (c.data.isEmpty()) "" else "  " + c.data.entries.joinToString(" ") { "${it.key}=${it.value}" } }
                .ifEmpty { "空间是空的" } + "\n可用类型：" + SpaceTypes.all.joinToString(" ") { it.id }
            "add" -> {
                val type = str("type") ?: return "ERROR: 缺少 type"
                val c = add(type, str("size"), data.orEmpty(), (args["position"] as? JsonPrimitive)?.intOrNull) ?: return "ERROR: 不认识的类型 $type"
                "OK: 已添加 ${c.id}"
            }
            "update" -> { val id = str("id") ?: return "ERROR: 缺少 id"; update(id, str("size"), data); "OK: 已更新 $id" }
            "remove" -> { val id = str("id") ?: return "ERROR: 缺少 id"; remove(id); "OK: 已删除 $id" }
            "move" -> {
                val id = str("id") ?: return "ERROR: 缺少 id"
                move(id, (args["position"] as? JsonPrimitive)?.intOrNull ?: 0); "OK: 已移动 $id"
            }
            else -> "ERROR: action 只能是 list add update remove move"
        }
    }

    companion object { const val KEY = "space_cards" }
}
