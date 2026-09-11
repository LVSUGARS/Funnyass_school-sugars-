package com.funnyass.test.ui

import com.funnyass.test.ui.BathGaugeView.GaugeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 仪表盘相位换算的连续性回归。
 *
 * 背景：IDLE / ERROR 曾经把弧长（288°）当成旋转量写进相位换算，
 * 而相位每个周期要绕一整圈，288° ≠ 360°，导致周期结束时圆环朝向差 72°。
 * 修复后它们仍然旋转，但每周期完整走满 360°。
 *
 * 这里把「循环点必须连续」固化成不变量测试，避免以后再退化。
 * 全部是纯函数，无需设备，跑一次几百毫秒。
 */
class BathGaugePhaseTest {

    private val allStates = GaugeState.entries.toList()

    /** 折算到 [0,360)，用于比较两个角度是否表示同一个屏幕位置。 */
    private fun normalize(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }

    private fun angularDiff(a: Float, b: Float): Float {
        var d = normalize(a) - normalize(b)
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return abs(d)
    }

    @Test
    fun `循环点角度连续 - 任何状态在相位归零时都不应跳变`() {
        for (state in allStates) {
            val atEnd = BathGaugePhase.arcStartAngleFor(state, 0.999f)
            val atStart = BathGaugePhase.arcStartAngleFor(state, 0f)
            val diff = angularDiff(atEnd, atStart)
            assertTrue("$state 在循环点跳了 $diff°（应 < 5°）", diff < 5f)
        }
    }

    @Test
    fun `IDLE 与 ERROR 的弧每周期完整旋转 360 度`() {
        for (state in listOf(GaugeState.IDLE, GaugeState.ERROR)) {
            assertEquals(270.0, normalize(BathGaugePhase.arcStartAngleFor(state, 0f)).toDouble(), 0.01)
            assertEquals(0.0, normalize(BathGaugePhase.arcStartAngleFor(state, 0.25f)).toDouble(), 0.01)
            assertEquals(90.0, normalize(BathGaugePhase.arcStartAngleFor(state, 0.5f)).toDouble(), 0.01)
            assertEquals(180.0, normalize(BathGaugePhase.arcStartAngleFor(state, 0.75f)).toDouble(), 0.01)
        }
    }

    @Test
    fun `流动状态的弧匀速推进 - 包含循环点在内每帧步长一致`() {
        val flowing = allStates - GaugeState.DISCONNECTED
        val frames = 120
        val expected = 360f / frames
        for (state in flowing) {
            var prev = BathGaugePhase.arcStartAngleFor(state, 0f)
            // 多跑一帧，覆盖 phase 从 1 回到 0 的循环点
            for (i in 1..frames) {
                val angle = BathGaugePhase.arcStartAngleFor(state, (i % frames) / frames.toFloat())
                val step = angularDiff(prev, angle)
                assertTrue(
                    "$state 第 $i 帧步长 ${step}° 偏离匀速基准 ${expected}°（循环点不应跳变）",
                    abs(step - expected) < 0.5f
                )
                prev = angle
            }
        }
    }

    @Test
    fun `呼吸透明度在循环点连续`() {
        for (state in listOf(GaugeState.IDLE, GaugeState.SETTLED, GaugeState.ERROR)) {
            val atEnd = BathGaugePhase.arcAlphaFor(state, 0.999f)
            val atStart = BathGaugePhase.arcAlphaFor(state, 0f)
            assertTrue(
                "$state 相位归零时透明度突跳：$atEnd -> $atStart",
                abs(atEnd - atStart) < 0.02f
            )
        }
    }

    @Test
    fun `呼吸透明度确实在区间内摆动`() {
        val min = BathGaugePhase.arcAlphaFor(GaugeState.IDLE, 0f)
        val max = BathGaugePhase.arcAlphaFor(GaugeState.IDLE, 0.5f)
        assertTrue("呼吸谷值应接近 0.72（实际 $min）", abs(min - 0.72f) < 0.01f)
        assertTrue("呼吸峰值应接近 1.0（实际 $max）", abs(max - 1f) < 0.01f)
    }

    @Test
    fun `供水波峰随相位沿 Canvas 顺时针方向推进`() {
        val crests = 7f
        val firstCrest = Math.PI.toFloat() / (2f * crests)
        val nextCrest = Math.PI.toFloat() / crests
        assertEquals(1.0, BathGaugePhase.waveOffsetFor(firstCrest, 0f, crests).toDouble(), 0.01)
        assertEquals(1.0, BathGaugePhase.waveOffsetFor(nextCrest, 0.25f, crests).toDouble(), 0.01)
        assertTrue("Canvas 角度增加代表顺时针，后一个波峰角度应更大", nextCrest > firstCrest)
    }

    @Test
    fun `局部波浪两端平滑收口`() {
        assertEquals(0.0, BathGaugePhase.waveEnvelopeFor(0f).toDouble(), 0.001)
        assertEquals(1.0, BathGaugePhase.waveEnvelopeFor(0.5f).toDouble(), 0.001)
        assertEquals(0.0, BathGaugePhase.waveEnvelopeFor(1f).toDouble(), 0.001)
    }

    @Test
    fun `启动供水关阀的波浪占比符合状态序列`() {
        assertEquals(
            BathGaugePhase.WAVE_SMALL_FRACTION.toDouble(),
            BathGaugePhase.waveFractionFor(GaugeState.STARTING, 0f).toDouble(),
            0.001
        )
        assertEquals(
            BathGaugePhase.WAVE_LARGE_FRACTION.toDouble(),
            BathGaugePhase.waveFractionFor(GaugeState.STARTING, 1f).toDouble(),
            0.001
        )
        assertEquals(1.0, BathGaugePhase.waveFractionFor(GaugeState.BATHING, 0.5f).toDouble(), 0.001)
        assertEquals(
            BathGaugePhase.WAVE_LARGE_FRACTION.toDouble(),
            BathGaugePhase.waveFractionFor(GaugeState.STOPPING, 0f).toDouble(),
            0.001
        )
        assertEquals(
            BathGaugePhase.WAVE_SMALL_FRACTION.toDouble(),
            BathGaugePhase.waveFractionFor(GaugeState.STOPPING, 1f).toDouble(),
            0.001
        )
        assertTrue(
            BathGaugePhase.waveFractionFor(GaugeState.STARTING, 0.25f) <
                BathGaugePhase.waveFractionFor(GaugeState.STARTING, 0.75f)
        )
        assertTrue(
            BathGaugePhase.waveFractionFor(GaugeState.STOPPING, 0.25f) >
                BathGaugePhase.waveFractionFor(GaugeState.STOPPING, 0.75f)
        )
    }

    @Test
    fun `供水波浪振幅由小变大再变小且循环连续`() {
        val atStart = BathGaugePhase.waveAmplitudeScaleFor(0f)
        val atMiddle = BathGaugePhase.waveAmplitudeScaleFor(0.5f)
        val atEnd = BathGaugePhase.waveAmplitudeScaleFor(0.999f)
        assertEquals(BathGaugePhase.WAVE_AMPLITUDE_MIN_SCALE.toDouble(), atStart.toDouble(), 0.001)
        assertEquals(1.0, atMiddle.toDouble(), 0.001)
        assertTrue("振幅循环点不应突跳", abs(atEnd - atStart) < 0.01f)
    }
}
