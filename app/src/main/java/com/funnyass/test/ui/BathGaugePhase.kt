package com.funnyass.test.ui

import kotlin.math.cos
import kotlin.math.sin

/**
 * 仪表盘「相位 → 绘制参数」的纯函数集合。
 *
 * 抽出来的目的：动画连续性是一个**数学不变量**，不该靠抓帧截图去碰运气验证。
 * 这些函数没有任何 Android 依赖，可以直接跑 JVM 单元测试（见 BathGaugePhaseTest）。
 *
 * 背景：IDLE / ERROR 曾经把弧长（288°）当成旋转量写进相位换算，
 * 而相位每个周期要绕一整圈，288° ≠ 360°，导致周期结束时圆环朝向差 72°。
 * 修复后弧仍然旋转，但每个周期完整走满 360°，循环点不再跳变。
 */
internal object BathGaugePhase {

    /** IDLE / ERROR 顶部缺口占整圈的比例（HTML dashoffset 95.5 / 477.5）。 */
    const val GAP_FRACTION = 0.2f

    /** 呼吸 / 脉冲的透明度下限（HTML `opacity:.72 -> 1`、`.55 -> 1`）。 */
    const val ALPHA_BREATHE_MIN = 0.72f
    const val ALPHA_PULSE_MIN = 0.55f

    /** 启动/关阀过渡的局部活动波浪占比。 */
    const val WAVE_SMALL_FRACTION = 0.18f
    const val WAVE_LARGE_FRACTION = 0.78f

    /** 供水波浪呼吸时的最小振幅比例。 */
    const val WAVE_AMPLITUDE_MIN_SCALE = 0.45f

    /** DISCONNECTED 不绘制进度弧，保留一个稳定的兜底角度。 */
    private const val STATIC_ARC_START = -90f + (1f - GAP_FRACTION) * 360f

    /**
     * 弧的起始角度：Canvas 里 -90° 是 12 点钟方向，角度增大为顺时针。
     *
     * - DISCONNECTED：不绘制进度弧，返回固定兜底角度。
     * - 其它状态：用 `360×phase` 走满一整圈，循环点严格无缝；IDLE / ERROR
     *   同时通过 [arcAlphaFor] 保留呼吸效果。
     */
    fun arcStartAngleFor(state: BathGaugeView.GaugeState, phase: Float): Float = when (state) {
        BathGaugeView.GaugeState.DISCONNECTED -> STATIC_ARC_START
        else -> -90f + 360f * phase
    }

    /** 进度弧占整圈的比例。 */
    fun arcWidthFractionFor(state: BathGaugeView.GaugeState): Float = when (state) {
        BathGaugeView.GaugeState.DISCONNECTED -> 0f
        BathGaugeView.GaugeState.IDLE,
        BathGaugeView.GaugeState.ERROR -> 1f - GAP_FRACTION
        BathGaugeView.GaugeState.STARTING -> WAVE_LARGE_FRACTION
        BathGaugeView.GaugeState.BATHING -> 1f
        BathGaugeView.GaugeState.STOPPING -> WAVE_SMALL_FRACTION
        BathGaugeView.GaugeState.SETTLED -> 1f
    }

    /** 当前透明度：IDLE / SETTLED 呼吸，ERROR 脉冲，其余恒为 1。 */
    fun arcAlphaFor(state: BathGaugeView.GaugeState, phase: Float): Float = when (state) {
        BathGaugeView.GaugeState.IDLE,
        BathGaugeView.GaugeState.SETTLED -> breathAlpha(ALPHA_BREATHE_MIN, phase)
        BathGaugeView.GaugeState.ERROR -> breathAlpha(ALPHA_PULSE_MIN, phase)
        else -> 1f
    }

    /** 0..1..0 的呼吸曲线，phase 0 时为最小值，循环点连续。 */
    fun breathAlpha(minAlpha: Float, phase: Float): Float {
        val wave = (1f - cos(phase * 2f * Math.PI.toFloat())) / 2f
        return minAlpha + (1f - minAlpha) * wave
    }

    /** Canvas 角度沿顺时针增加；减去递增相位可让波峰沿顺时针传播。 */
    fun waveOffsetFor(thetaRadians: Float, phase: Float, crests: Float): Float =
        sin(crests * thetaRadians - phase * 2f * Math.PI.toFloat())

    /** 波浪段两端回到圆弧基线，中部保持完整起伏，避免活动段端点跳动。 */
    fun waveEnvelopeFor(segmentProgress: Float): Float =
        sin(segmentProgress.coerceIn(0f, 1f) * Math.PI.toFloat())

    /** 波浪整体振幅平滑地由小变大再变小，循环点保持连续。 */
    fun waveAmplitudeScaleFor(phase: Float): Float =
        breathAlpha(WAVE_AMPLITUDE_MIN_SCALE, phase)

    /** 启动由小变大，供水保持整圈，关阀由大变小。 */
    fun waveFractionFor(state: BathGaugeView.GaugeState, transitionProgress: Float): Float {
        val progress = transitionProgress.coerceIn(0f, 1f)
        return when (state) {
            BathGaugeView.GaugeState.STARTING ->
                WAVE_SMALL_FRACTION + (WAVE_LARGE_FRACTION - WAVE_SMALL_FRACTION) * progress
            BathGaugeView.GaugeState.BATHING -> 1f
            BathGaugeView.GaugeState.STOPPING ->
                WAVE_LARGE_FRACTION + (WAVE_SMALL_FRACTION - WAVE_LARGE_FRACTION) * progress
            else -> 0f
        }
    }
}
