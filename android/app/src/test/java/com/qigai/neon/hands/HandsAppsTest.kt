package com.qigai.neon.hands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HandsAppsTest {
    @Test
    fun `resolves app by Chinese name, alias, and package`() {
        val apps = listOf(
            InstalledApp("微信", "com.tencent.mm"),
            InstalledApp("菜鸟", "com.cainiao.wireless"),
            InstalledApp("高德地图", "com.autonavi.minimap"),
        )

        assertEquals("com.tencent.mm", HandsApps.resolveFromList(apps, "wechat")?.packageName)
        assertEquals("com.cainiao.wireless", HandsApps.resolveFromList(apps, "菜鸟裹裹")?.packageName)
        assertNotNull(HandsApps.resolveFromList(apps, "高德"))
    }
}
