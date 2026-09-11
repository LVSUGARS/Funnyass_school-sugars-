package com.funnyass.test.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BathDeviceFilterTest {

    @Test
    fun onlyReturnsBackendRecognizedDevicesInScanOrder() {
        val scanned = listOf("unknown-1", "bath-1", "unknown-2", "bath-2")
        val recognized = listOf("bath-2", "bath-1")

        assertEquals(
            listOf("bath-1", "bath-2"),
            BathActivity.visibleDeviceMacs(scanned, recognized)
        )
    }

    @Test
    fun returnsEmptyListBeforeAnyDeviceIsRecognized() {
        assertEquals(
            emptyList<String>(),
            BathActivity.visibleDeviceMacs(listOf("unknown-1"), emptyList())
        )
    }

    @Test
    fun returnsWaterNamesIgnoringCase() {
        val scanned = listOf("known", "water-upper", "water-mixed", "other")
        val recognized = listOf("known")
        val names = mapOf(
            "water-upper" to "WATER-METER",
            "water-mixed" to "KLCXKJ-WaTeR-2",
            "other" to "Bluetooth Speaker"
        )

        assertEquals(
            listOf("known", "water-upper", "water-mixed"),
            BathActivity.visibleDeviceMacs(scanned, recognized, names)
        )
    }

    /**
     * 已选中的设备归「当前设备」区展示，不能再出现在「附近设备」列表里。
     * 否则每次扫描列表都会重复出现同一台设备。
     */
    @Test
    fun excludesSelectedDeviceFromNearbyList() {
        val scanned = listOf("AA:BB", "CC:DD")
        val recognized = listOf("AA:BB", "CC:DD")

        assertEquals(
            listOf("CC:DD"),
            BathActivity.visibleDeviceMacs(scanned, recognized, emptyMap(), "AA:BB")
        )
    }

    /**
     * 排除选中设备时必须统一大小写：设备上报的 MAC 大小写不保证一致。
     * 这里 scanned 用小写、selected 用大写，仍应把它们视为同一台设备。
     */
    @Test
    fun selectedDeviceExclusionIgnoresCase() {
        val scanned = listOf("aa:bb", "cc:dd")
        // 两台都要"可显示"（被后端识别），否则本来就不会出现在列表里，测不出排除逻辑
        val recognized = listOf("aa:bb", "cc:dd")

        assertEquals(
            listOf("cc:dd"),
            BathActivity.visibleDeviceMacs(scanned, recognized, emptyMap(), "AA:BB")
        )
    }
}
