package com.qigai.neon.hands

import android.content.Context
import android.content.Intent

data class InstalledApp(
    val label: String,
    val packageName: String,
)

object HandsApps {
    @Volatile
    private var cache: Pair<Long, List<InstalledApp>>? = null

    fun launchable(context: Context): List<InstalledApp> {
        cache?.let { (at, list) ->
            if (System.currentTimeMillis() - at < 60_000L) return list
        }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = runCatching {
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                    if (label.isEmpty()) null else InstalledApp(label, pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
        cache = System.currentTimeMillis() to list
        return list
    }

    fun resolve(context: Context, query: String): InstalledApp? =
        resolveFromList(launchable(context), query)

    fun resolveFromList(apps: List<InstalledApp>, query: String): InstalledApp? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val lower = q.lowercase()
        return apps.firstOrNull { it.label.equals(q, ignoreCase = true) }
            ?: apps.firstOrNull { it.packageName.equals(q, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.lowercase().contains(lower) }
            ?: apps.firstOrNull { lower.contains(it.label.lowercase()) && it.label.length >= 2 }
            ?: aliasPackages(lower)?.let { pkgs -> apps.firstOrNull { it.packageName in pkgs } }
    }

    fun aliasPackages(lower: String): Set<String>? = when (lower) {
        "wechat", "weixin", "微信" -> setOf("com.tencent.mm")
        "alipay", "支付宝" -> setOf("com.eg.android.AlipayGphone")
        "taobao", "淘宝" -> setOf("com.taobao.taobao")
        "cainiao", "菜鸟", "菜鸟裹裹" -> setOf("com.cainiao.wireless")
        "12306", "铁路12306" -> setOf("com.MobileTicket")
        "meituan", "美团" -> setOf("com.sankuai.meituan")
        "jd", "jingdong", "京东" -> setOf("com.jingdong.app.mall")
        "douyin", "抖音", "tiktok" -> setOf("com.ss.android.ugc.aweme", "com.zhiliaoapp.musically")
        "xiaohongshu", "小红书", "rednote" -> setOf("com.xingin.xhs")
        "amap", "gaode", "高德地图", "高德" -> setOf("com.autonavi.minimap")
        "chrome", "谷歌浏览器" -> setOf("com.android.chrome")
        "settings", "设置" -> setOf("com.android.settings")
        else -> null
    }
}
