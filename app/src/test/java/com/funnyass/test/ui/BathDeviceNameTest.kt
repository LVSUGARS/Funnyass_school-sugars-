package com.funnyass.test.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「房间 · 设备名」拼接的去重测试。
 *
 * 背景：接口返回的 `devName` 可能本身已包含房间名，例如
 * `roomName="612房"`、`devName="热水表-南铁院-10栋-6层-612房"`。
 * 早先无条件拼成「612房 · 热水表-南铁院-10栋-6层-612房」，
 * 同一个房间名在界面上出现两次。
 */
class BathDeviceNameTest {

    @Test
    fun `设备名已含房间名时不再重复拼接`() {
        assertEquals(
            "热水表-南铁院-10栋-6层-612房",
            BathActivity.joinRoomAndName("612房", "热水表-南铁院-10栋-6层-612房")
        )
    }

    @Test
    fun `设备名不含房间名时正常拼接`() {
        assertEquals(
            "612房 · 热水表",
            BathActivity.joinRoomAndName("612房", "热水表")
        )
    }

    @Test
    fun `缺任一侧时不产生多余分隔符`() {
        assertEquals("612房", BathActivity.joinRoomAndName("612房", null))
        assertEquals("612房", BathActivity.joinRoomAndName("612房", "  "))
        assertEquals("热水表", BathActivity.joinRoomAndName(null, "热水表"))
        assertEquals("", BathActivity.joinRoomAndName(null, null))
    }

    @Test
    fun `两侧空白会被裁掉`() {
        assertEquals(
            "612房 · 热水表",
            BathActivity.joinRoomAndName("  612房 ", " 热水表 ")
        )
    }
}
