package com.funnyass.test.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BathActivity 内纯函数的 JVM 单元测试（不需要设备）。
 *
 * 这里只覆盖与 Apple 布局文案直接相关的格式化逻辑，
 * 不涉及任何蓝牙 / 网络 / 扣费行为。
 */
class BathTextFormatTest {

    @Test
    fun masksElevenDigitPhone() {
        assertEquals("138****0000", BathActivity.maskPhone("13800000000"))
    }

    @Test
    fun keepsShortPhoneUnchanged() {
        assertEquals("138000", BathActivity.maskPhone("138000"))
        assertEquals("", BathActivity.maskPhone(""))
    }

    @Test
    fun keepsSevenDigitPhoneUnchanged() {
        // 7 位时前 3 后 4 会重叠，因此阈值取 >7，原样返回
        assertEquals("1380000", BathActivity.maskPhone("1380000"))
    }
}
