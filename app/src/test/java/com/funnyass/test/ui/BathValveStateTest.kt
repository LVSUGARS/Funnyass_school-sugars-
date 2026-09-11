package com.funnyass.test.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 水阀状态文案映射的回归测试。
 *
 * 背景：设备被别人占用时 `deviceState==1`，界面会落到仪表盘的 IDLE 分支。
 * 早先 UI 只判断「有没有拉到设备详情」，于是别人正在用水、开始按钮已灰掉，
 * 「水阀」卡却显示「空闲」，与实际状态不符。这里把映射锁死，避免退化。
 */
class BathValveStateTest {

    @Test
    fun `设备空闲显示空闲`() {
        assertEquals("空闲", BathActivity.valveStateLabel(0))
    }

    @Test
    fun `设备被使用中不能显示为空闲`() {
        assertEquals("使用中", BathActivity.valveStateLabel(1))
    }

    @Test
    fun `其余设备状态各有明确文案`() {
        assertEquals("不可中断", BathActivity.valveStateLabel(2))
        assertEquals("有遗留数据", BathActivity.valveStateLabel(3))
        assertEquals("受控模式", BathActivity.valveStateLabel(5))
    }

    @Test
    fun `未知状态不编造含义`() {
        assertEquals("--", BathActivity.valveStateLabel(-1))
        assertEquals("--", BathActivity.valveStateLabel(9))
        assertEquals("--", BathActivity.valveStateLabel(4))
    }
}
