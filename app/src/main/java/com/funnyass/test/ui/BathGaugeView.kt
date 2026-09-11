package com.funnyass.test.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.funnyass.test.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 自绘圆环仪表盘（纯 Canvas，无第三方依赖）。
 *
 * 视觉依据：`docs/apple-design-preview.html` 的 `.bath-full[data-state=...] .full-gauge`。
 * 结构对应 HTML：track 圆 + value 弧 + orbit 水滴 + 三行中心文本。
 *
 * 实现要点：
 * - 颜色一律在每次 [onDraw] 用 [ContextCompat.getColor] 重新取，深浅色切换后立即正确。
 * - 所有动画都是 `ValueAnimator.ofFloat(0f, 1f)` + `repeatCount = INFINITE` 的相位值，
 *   在 [onDetachedFromWindow] 里全部 cancel，避免泄漏。
 * - 进度弧的发光用 `setShadowLayer`，必须走软件图层才能显示阴影。
 *   代价：`Paint.setShadowLayer` 在硬件加速下不生效，所以本 View 调用了
 *   `setLayerType(LAYER_TYPE_SOFTWARE, null)`；整块 View 每帧要在 CPU 上做一次
 *   模糊 + 位图合成，比 GPU 直绘多花内存与绘制时间。控件只有 ~200dp 且动画简单，
 *   这个代价可以接受；若以后需要更高帧率，可换成 `BlurMaskFilter` 或去掉发光。
 */
class BathGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class GaugeState { DISCONNECTED, IDLE, STARTING, BATHING, STOPPING, SETTLED, ERROR }

    // ---------------------------------------------------------------- 常量

    private companion object {
        /** 控件默认正方形边长（wrap_content 兜底，XML 里显式给 232dp 时不生效）。 */
        const val DEFAULT_SIZE_DP = 220f

        /** 圆环的设计直径（外层控件比它大一圈，用于容纳发光与水波）。 */
        const val GAUGE_DIAMETER_DP = 208f

        /** 按 HTML `.full-track{stroke-width:12}` / `.full-value{stroke-width:15}`。 */
        const val TRACK_STROKE_DP = 12f
        const val VALUE_STROKE_DP = 15f

        /** IDLE / ERROR 的顶部缺口占整圈的比例（HTML dashoffset 95.5 / 477.5）。 */
        const val GAP_FRACTION = BathGaugePhase.GAP_FRACTION

        /** 呼吸 / 流动动画周期，与 HTML keyframes 一致。 */
        const val DUR_BREATHE_IDLE = 2800L
        const val DUR_BREATHE_SETTLED = 2400L
        const val DUR_PULSE_ERROR = 1000L
        const val DUR_FLOW_STARTING = 1100L
        const val DUR_FLOW_BATHING = 1600L
        const val DUR_FLOW_STOPPING = 1100L

        /** 呼吸 / 脉冲的透明度区间（HTML `opacity:.72 -> 1`、`.55 -> 1`）。 */
        const val ALPHA_BREATHE_MIN = BathGaugePhase.ALPHA_BREATHE_MIN
        const val ALPHA_PULSE_MIN = BathGaugePhase.ALPHA_PULSE_MIN

        /** 进度弧发光：半径 6dp，颜色 = 主色 35% 透明（HTML drop-shadow 0 0 10px ... .35）。 */
        const val GLOW_RADIUS_DP = 6f
        const val GLOW_ALPHA = 0.35f

        /** 洗浴中的局部活动波浪：波峰相位独立于整段旋转，才能看见水波向前流动。 */
        const val DUR_WAVE = 1050L

        /** 整圈的波峰数量；局部段按占比显示相同密度的波峰。 */
        const val WAVE_FULL_RING_CRESTS = 12f

        /** 波浪起伏幅度（相对半径也有上限，避免小控件上波形过冲）。 */
        const val WAVE_AMPLITUDE_DP = 7f
        const val WAVE_MAX_AMPLITUDE_RATIO = 0.075f

        /** 构建局部波浪路径的采样段数，越大越平滑。 */
        const val WAVE_SEGMENTS = 260

        /** 活动波浪与剩余平滑轨道之间的圆周间隙。 */
        const val WAVE_TRACK_GAP_DEGREES = 8f


        /** playRefreshAnimation 的按压缩放时长与 halo 外扩时长（HTML .5s / .8s）。 */
        const val DUR_REFRESH_SCALE = 500L
        const val DUR_REFRESH_HALO = 800L
        const val HALO_SCALE_FROM = 0.9f
        const val HALO_SCALE_TO = 1.16f
        const val HALO_ALPHA_FROM = 0.9f
        const val HALO_STROKE_DP = 2f

        /** 中心三行文本字号（sp）：main 36/30/28，label 12 bold，meta 11。 */
        const val MAIN_TEXT_SP = 36f
        const val MAIN_TEXT_SP_BATHING = 30f
        const val MAIN_TEXT_SP_BUSY = 28f
        const val LABEL_TEXT_SP = 12f
        const val META_TEXT_SP = 11f

        /** 行间距（dp）。 */
        const val GAP_MAIN_LABEL_DP = 4f
        const val GAP_LABEL_META_DP = 5f
    }

    // ---------------------------------------------------------------- 状态

    private var gaugeState: GaugeState = GaugeState.DISCONNECTED

    private var mainText: String = "--"
    private var labelText: String = ""
    private var metaText: String? = null

    private var onGaugeClickListener: (() -> Unit)? = null

    /** 当前相位：[0,1) 循环。静态状态下被重置为 0。驱动进度弧的流动/呼吸。 */
    private var phase = 0f

    /** 水波流动的独立相位，不能与 [phase] 共用，否则会互相覆盖。 */
    private var wavePhase = 0f

    /** STARTING / STOPPING 的一次性占比变化进度；BATHING 固定使用整圈。 */
    private var waveTransitionProgress = 0f

    /** 刷新动效进度：-1 表示未在播放，否则 [0,1]。叠加在当前 state 动画之上。 */
    private var refreshScaleProgress = -1f
    private var refreshHaloProgress = -1f

    /** 驱动 [phase] 的呼吸/流动动画（同一时刻只会有一个）。 */
    private var phaseAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null
    private var waveTransitionAnimator: ValueAnimator? = null
    private var refreshScaleAnimator: ValueAnimator? = null
    private var refreshHaloAnimator: ValueAnimator? = null

    // ---------------------------------------------------------------- Paint / 尺寸

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(TRACK_STROKE_DP)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(VALUE_STROKE_DP)
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(HALO_STROKE_DP)
    }

    /** 水波单独用一支 Paint：与进度弧共用会互相覆盖 style / strokeWidth。 */
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val mainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val metaTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        isFakeBoldText = false
    }

    private val arcRect = RectF()
    private val haloRect = RectF()
    private var ringRadius = 0f

    // ---------------------------------------------------------------- 初始化

    init {
        // 点击语义 + 可访问性：本 View 是可点击的。
        isClickable = true
        isFocusable = true
        // 让 setShadowLayer 生效（硬件加速下阴影不显示），代价见类注释。
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // ---------------------------------------------------------------- 公开 API

    /** 设置圆环状态并启动对应动画；会触发 invalidate()。必须在主线程调用。 */
    fun setGaugeState(state: GaugeState) {
        if (gaugeState == state) return
        // 切换状态时保留弧段当前的屏幕角度，避免状态按钮连续点击时出现瞬移。
        val oldAngleFactor = arcAngleFactor(gaugeState)
        val newAngleFactor = arcAngleFactor(state)
        if (oldAngleFactor > 0f && newAngleFactor > 0f && oldAngleFactor != newAngleFactor) {
            phase = (phase * oldAngleFactor / newAngleFactor) % 1f
        }
        gaugeState = state
        waveTransitionProgress = when (state) {
            GaugeState.STARTING, GaugeState.STOPPING -> 0f
            GaugeState.BATHING -> 1f
            else -> 0f
        }
        // 先安全取消旧动画，再按新状态启动，避免多个 ValueAnimator 同时写相位。
        cancelStateAnimators()
        // 保留当前相位：状态切换时从画面上的位置继续，而不是瞬间跳回 12 点钟。
        // orbitPhase 也不能清零，否则 BATHING 的水滴会在切换时跳回起点。
        startAnimatorForCurrentState()
        invalidate()
    }

    /** 设置中心三行文本。meta 传空串或 null 时该行不绘制。必须在主线程调用。 */
    fun setGaugeText(main: String, label: String, meta: String? = null) {
        // BathActivity 的 gaugeTicker 每秒都会调到这里；文字没变时不要重绘，
        // 否则会打断正在进行的呼吸/流动动画的帧节奏（实测表现为待机呼吸忽快忽慢）。
        if (mainText == main && labelText == label && metaText == meta) return
        mainText = main
        labelText = label
        metaText = meta
        invalidate()
    }

    /** 点击圆环时回调（用于触发余额刷新）。设置 null 取消回调。 */
    fun setOnGaugeClickListener(listener: (() -> Unit)?) {
        onGaugeClickListener = listener
    }

    /** 主动播放一次「刷新」按压 + halo 动效（叠加在当前状态上，不改变 state）。 */
    fun playRefreshAnimation() {
        refreshScaleAnimator?.cancel()
        refreshHaloAnimator?.cancel()

        refreshScaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DUR_REFRESH_SCALE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                refreshScaleProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        refreshHaloAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DUR_REFRESH_HALO
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                refreshHaloProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ---------------------------------------------------------------- 测量

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fallback = dp(DEFAULT_SIZE_DP).toInt().coerceAtLeast(1)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var w = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(widthSize, fallback)
            else -> fallback
        }
        var h = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(heightSize, fallback)
            else -> fallback
        }

        // 保持 1:1：只要一侧被父布局定死，另一侧就用 wrap 的边长跟随；两侧都定死则完全尊重父布局。
        if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
            h = w
        } else if (heightMode == MeasureSpec.EXACTLY && widthMode != MeasureSpec.EXACTLY) {
            w = h
        }

        setMeasuredDimension(
            w.coerceAtLeast(1),
            h.coerceAtLeast(1)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val diameter = min(
            (w - paddingLeft - paddingRight).toFloat(),
            (h - paddingTop - paddingBottom).toFloat()
        )
        // 圆环按固定的设计直径绘制，不随控件撑满——控件比圆环大一圈，
        // 多出来的空间给弧的发光和洗浴中的水波，避免被控件边界裁切。
        val gaugeDiameter = min(dp(GAUGE_DIAMETER_DP), diameter)
        ringRadius = (gaugeDiameter - dp(VALUE_STROKE_DP)) / 2f
        if (ringRadius < 0f) ringRadius = 0f
    }

    // ---------------------------------------------------------------- 绘制

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 每帧重新取色：主题/深色模式切换后立刻正确。
        val colorTrack = ContextCompat.getColor(context, R.color.apple_gauge_track)
        val colorText = ContextCompat.getColor(context, R.color.apple_text)
        val colorSecondary = ContextCompat.getColor(context, R.color.apple_secondary)
        val colorBlue = ContextCompat.getColor(context, R.color.apple_blue)
        val colorGreen = ContextCompat.getColor(context, R.color.apple_green)
        val colorRed = ContextCompat.getColor(context, R.color.apple_red)
        val colorAmber = ContextCompat.getColor(context, R.color.apple_amber)
        val stateColor = stateColor(colorTrack, colorBlue, colorGreen, colorRed, colorAmber)

        if (ringRadius <= 0f) return

        // 刷新按压动效：整块仪表盘绕中心缩放（0.96 -> 1.02 -> 1.0）。
        val scale = refreshScaleValue()
        val pivotX = (paddingLeft + (width - paddingLeft - paddingRight) / 2).toFloat()
        val pivotY = (paddingTop + (height - paddingTop - paddingBottom) / 2).toFloat()

        val saveCount = canvas.save()
        canvas.translate(pivotX, pivotY)
        canvas.scale(scale, scale)
        canvas.translate(-pivotX, -pivotY)

        arcRect.set(
            pivotX - ringRadius,
            pivotY - ringRadius,
            pivotX + ringRadius,
            pivotY + ringRadius
        )

        // 启动/供水/关阀：小段变大、整圈流动、大段变小。
        if (gaugeState == GaugeState.STARTING ||
            gaugeState == GaugeState.BATHING ||
            gaugeState == GaugeState.STOPPING
        ) {
            drawWavyRing(
                canvas,
                pivotX,
                pivotY,
                colorTrack,
                colorBlue,
                stateColor,
                colorText,
                colorSecondary
            )
            canvas.restoreToCount(saveCount)
            return
        }

        // 1) 轨道：永远整圈
        trackPaint.color = colorTrack
        trackPaint.strokeWidth = dp(TRACK_STROKE_DP)
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        // 2) 进度弧（DISCONNECTED 不画）
        if (gaugeState != GaugeState.DISCONNECTED) {
            val alpha = arcAlpha()
            arcPaint.color = withAlpha(stateColor, alpha)
            arcPaint.strokeWidth = dp(VALUE_STROKE_DP)
            arcPaint.setShadowLayer(
                dp(GLOW_RADIUS_DP),
                0f,
                0f,
                withAlpha(stateColor, GLOW_ALPHA * alpha)
            )

            val widthFraction = arcWidthFraction()
            val startAngle = arcStartAngle()
            val sweep = 360f * widthFraction
            canvas.drawArc(arcRect, startAngle, sweep, false, arcPaint)

            arcPaint.clearShadowLayer()
        }

        drawRefreshHalo(canvas, pivotX, pivotY, stateColor)
        drawCenterText(canvas, pivotX, pivotY, colorText, colorSecondary, stateColor)
        canvas.restoreToCount(saveCount)
    }

    /** 刷新 halo：半透明圆环从 scale 0.9 扩到 1.16，alpha 0.9 -> 0。 */
    private fun drawRefreshHalo(canvas: Canvas, pivotX: Float, pivotY: Float, stateColor: Int) {
        if (refreshHaloProgress < 0f) return
        val p = refreshHaloProgress.coerceIn(0f, 1f)
        val haloScale = HALO_SCALE_FROM + (HALO_SCALE_TO - HALO_SCALE_FROM) * p
        val haloRadius = ringRadius * haloScale
        haloPaint.color = withAlpha(stateColor, HALO_ALPHA_FROM * (1f - p))
        haloRect.set(
            pivotX - haloRadius,
            pivotY - haloRadius,
            pivotX + haloRadius,
            pivotY + haloRadius
        )
        canvas.drawArc(haloRect, 0f, 360f, false, haloPaint)
    }

    private fun drawCenterText(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        colorText: Int,
        colorSecondary: Int,
        stateColor: Int,
        verticalOffset: Float = 0f
    ) {
        val mainSize = when (gaugeState) {
            GaugeState.BATHING -> MAIN_TEXT_SP_BATHING
            GaugeState.STARTING, GaugeState.STOPPING, GaugeState.ERROR -> MAIN_TEXT_SP_BUSY
            else -> MAIN_TEXT_SP
        }
        mainTextPaint.textSize = sp(mainSize)
        mainTextPaint.color = colorText

        labelTextPaint.textSize = sp(LABEL_TEXT_SP)
        labelTextPaint.color = if (gaugeState == GaugeState.DISCONNECTED) colorSecondary else stateColor

        // Use font metrics for both placement and advancement. Mixing font ascent with glyph
        // bounds caused every following line to reuse almost the same baseline.
        val mainMetrics = mainTextPaint.fontMetrics
        val mainHeight = mainMetrics.bottom - mainMetrics.top

        val labelVisible = labelText.isNotEmpty()
        var labelHeight = 0f
        var labelMetrics: Paint.FontMetrics? = null
        if (labelVisible) {
            labelMetrics = labelTextPaint.fontMetrics
            labelHeight = labelMetrics.bottom - labelMetrics.top
        }

        val metaValue = metaText
        val metaVisible = !metaValue.isNullOrBlank()
        // metaVisible（!isNullOrBlank）已经把 null 排除掉了，所以这里直接抽成非空局部变量。
        val metaLine = if (metaVisible) metaValue else null
        var metaHeight = 0f
        var metaMetrics: Paint.FontMetrics? = null
        if (metaLine != null) {
            metaTextPaint.textSize = sp(META_TEXT_SP)
            metaTextPaint.color = colorSecondary
            metaMetrics = metaTextPaint.fontMetrics
            metaHeight = metaMetrics.bottom - metaMetrics.top
        }

        val gapMainLabel = if (labelVisible) dp(GAP_MAIN_LABEL_DP) else 0f
        val gapLabelMeta = if (labelVisible && metaVisible) dp(GAP_LABEL_META_DP) else 0f
        val blockHeight = mainHeight + gapMainLabel + labelHeight + gapLabelMeta + metaHeight

        var top = centerY - blockHeight / 2f

        val mainBaseline = top - mainMetrics.top
        canvas.drawText(mainText, centerX, mainBaseline, mainTextPaint)
        top += mainHeight + gapMainLabel

        if (labelVisible && labelMetrics != null) {
            val labelBaseline = top - labelMetrics.top
            canvas.drawText(labelText, centerX, labelBaseline, labelTextPaint)
            top += labelHeight + gapLabelMeta
        }

        if (metaLine != null && metaMetrics != null) {
            val metaBaseline = top - metaMetrics.top
            canvas.drawText(metaLine, centerX, metaBaseline, metaTextPaint)
        }
    }

    /**
     * 启动/供水/关阀：平滑轨道与活动波浪段按状态分开绘制。
     *
     * 活动段整体顺时针绕行，段内正弦相位以另一周期推进，形成真正的行波；振幅在
     * 两端收敛到零，让圆弧端点稳定且保持圆润。
     */
    private fun drawWavyRing(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        color: Int,
        glowColor: Int,
        stateColor: Int,
        colorText: Int,
        colorSecondary: Int
    ) {
        if (ringRadius <= 0f) return
        val amplitude = min(dp(WAVE_AMPLITUDE_DP), ringRadius * WAVE_MAX_AMPLITUDE_RATIO)
        val animatedAmplitude = amplitude * BathGaugePhase.waveAmplitudeScaleFor(phase)

        val activeFraction = BathGaugePhase.waveFractionFor(gaugeState, waveTransitionProgress)
        val activeSweep = 360f * activeFraction
        val activeStart = BathGaugePhase.arcStartAngleFor(gaugeState, phase)
        val fullRing = activeFraction >= 0.999f

        fun buildWavyArc(radius: Float, amp: Float): Path {
            val path = Path()
            for (i in 0..WAVE_SEGMENTS) {
                val t = i.toFloat() / WAVE_SEGMENTS
                val theta = Math.toRadians((activeStart + activeSweep * t).toDouble()).toFloat()
                val localTheta = t * 2f * Math.PI.toFloat()
                val crests = WAVE_FULL_RING_CRESTS * activeFraction
                val offset = BathGaugePhase.waveOffsetFor(
                    localTheta,
                    wavePhase,
                    crests
                )
                val envelope = if (fullRing) 1f else BathGaugePhase.waveEnvelopeFor(t)
                val rr = radius + amp * envelope * offset
                val x = centerX + rr * cos(theta)
                val y = centerY + rr * sin(theta)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        // 未活动部分保留为平滑轨道，并在波浪两端留出 Material 风格的间隙。
        trackPaint.color = color
        trackPaint.strokeWidth = dp(TRACK_STROKE_DP)
        if (!fullRing) {
            val trackStart = activeStart + activeSweep + WAVE_TRACK_GAP_DEGREES
            val trackSweep = 360f - activeSweep - WAVE_TRACK_GAP_DEGREES * 2f
            canvas.drawArc(arcRect, trackStart, trackSweep, false, trackPaint)
        }

        // 蓝色活动段整体顺时针旋转，段内波峰也独立顺时针传播。
        wavePaint.color = glowColor
        wavePaint.strokeWidth = dp(VALUE_STROKE_DP)
        wavePaint.setShadowLayer(dp(GLOW_RADIUS_DP), 0f, 0f, withAlpha(glowColor, GLOW_ALPHA))
        canvas.drawPath(buildWavyArc(ringRadius, animatedAmplitude), wavePaint)
        wavePaint.clearShadowLayer()

        drawRefreshHalo(canvas, centerX, centerY, stateColor)
        drawCenterText(canvas, centerX, centerY, colorText, colorSecondary, stateColor)
    }

    // ---------------------------------------------------------------- 状态派生

    private fun stateColor(
        colorTrack: Int,
        colorBlue: Int,
        colorGreen: Int,
        colorRed: Int,
        colorAmber: Int
    ): Int = when (gaugeState) {
        GaugeState.DISCONNECTED -> colorTrack
        GaugeState.IDLE, GaugeState.SETTLED -> colorGreen
        GaugeState.STARTING, GaugeState.BATHING -> colorBlue
        GaugeState.STOPPING -> colorAmber
        GaugeState.ERROR -> colorRed
    }

    /** 进度弧占整圈的比例，换算逻辑见 [BathGaugePhase.arcWidthFractionFor]。 */
    private fun arcWidthFraction(): Float = BathGaugePhase.arcWidthFractionFor(gaugeState)

    /** 起始角度相位对应的圆周比例；所有可见弧都完整旋转一圈。 */
    private fun arcAngleFactor(state: GaugeState): Float = when (state) {
        GaugeState.DISCONNECTED -> 0f
        else -> 1f
    }

    /** 弧的起始角度，换算逻辑见 [BathGaugePhase.arcStartAngleFor]。 */
    private fun arcStartAngle(): Float = BathGaugePhase.arcStartAngleFor(gaugeState, phase)

    /** 当前透明度，换算逻辑见 [BathGaugePhase.arcAlphaFor]。 */
    private fun arcAlpha(): Float = BathGaugePhase.arcAlphaFor(gaugeState, phase)

    private fun refreshScaleValue(): Float {
        val p = refreshScaleProgress
        if (p < 0f) return 1f
        val t = p.coerceIn(0f, 1f)
        return if (t <= 0.5f) {
            0.96f + (1.02f - 0.96f) * (t / 0.5f)
        } else {
            1.02f + (1.0f - 1.02f) * ((t - 0.5f) / 0.5f)
        }
    }

    // ---------------------------------------------------------------- 动画

    private fun cancelStateAnimators() {
        phaseAnimator?.cancel()
        phaseAnimator = null
        waveAnimator?.cancel()
        waveAnimator = null
        waveTransitionAnimator?.cancel()
        waveTransitionAnimator = null
    }

    private fun startAnimatorForCurrentState() {
        // 可见性回调可能在同一帧重复到达；重用正在运行的动画，避免相位被多个
        // ValueAnimator 同时写入而出现抖动或速度突变。
        val phaseRunning = phaseAnimator?.isRunning == true
        val waveRunning = waveAnimator?.isRunning == true
        val transitionNeeded = (gaugeState == GaugeState.STARTING ||
            gaugeState == GaugeState.STOPPING) && waveTransitionProgress < 1f
        val transitionRunning = waveTransitionAnimator?.isRunning == true
        if (gaugeState != GaugeState.DISCONNECTED && phaseRunning &&
            (gaugeState != GaugeState.STARTING && gaugeState != GaugeState.BATHING &&
                gaugeState != GaugeState.STOPPING || waveRunning) &&
            (!transitionNeeded || transitionRunning)
        ) return

        when (gaugeState) {
            GaugeState.DISCONNECTED -> {
                // 无动画
            }
            GaugeState.IDLE -> {
                startPhaseAnimator(DUR_BREATHE_IDLE)
            }
            GaugeState.SETTLED -> {
                startPhaseAnimator(DUR_BREATHE_SETTLED)
            }
            GaugeState.ERROR -> {
                startPhaseAnimator(DUR_PULSE_ERROR)
            }
            GaugeState.STARTING -> {
                startPhaseAnimator(DUR_FLOW_STARTING)
                startWaveAnimator()
                startWaveTransitionAnimator(DUR_FLOW_STARTING)
            }
            GaugeState.BATHING -> {
                startPhaseAnimator(DUR_FLOW_BATHING)
                startWaveAnimator()
            }
            GaugeState.STOPPING -> {
                startPhaseAnimator(DUR_FLOW_STOPPING)
                startWaveAnimator()
                startWaveTransitionAnimator(DUR_FLOW_STOPPING)
            }
        }
    }

    /**
     * 旋转相位始终匀速推进。呼吸缓动由 [BathGaugePhase.arcAlphaFor] 单独计算，
     * 不能对旋转相位使用 ease-in-out，否则每个循环点都会减速到零，肉眼看起来像顿一下。
     */
    private fun startPhaseAnimator(duration: Long) {
        phaseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 活动水波流动使用独立的 [wavePhase]，与进度弧的 [phase] 互不干扰。
     *
     * 周期取整数个波峰通过的时间，保证循环时波形相位无缝衔接（不会出现接缝抖动）。
     */
    private fun startWaveAnimator() {
        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DUR_WAVE
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                wavePhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** 启动/关阀的占比只变化一次，完成后保持终态，不能循环回跳。 */
    private fun startWaveTransitionAnimator(duration: Long) {
        if (waveTransitionProgress >= 1f || waveTransitionAnimator?.isRunning == true) return
        val start = waveTransitionProgress
        waveTransitionAnimator = ValueAnimator.ofFloat(start, 1f).apply {
            this.duration = (duration * (1f - start)).toLong().coerceAtLeast(1L)
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                waveTransitionProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ---------------------------------------------------------------- 生命周期 / 交互

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 从窗口移除时被 cancel 过，重新附加后需要恢复状态动画。
        if (isShown) startAnimatorForCurrentState()
    }

    override fun onDetachedFromWindow() {
        // 必须取消全部动画，避免持有 View / Context 造成内存泄漏。
        cancelStateAnimators()
        refreshScaleAnimator?.cancel()
        refreshScaleAnimator = null
        refreshHaloAnimator?.cancel()
        refreshHaloAnimator = null
        refreshScaleProgress = -1f
        refreshHaloProgress = -1f
        super.onDetachedFromWindow()
    }

    /**
     * GONE/INVISIBLE 时停掉动画省电；重新可见时恢复。
     *
     * 注意 `isAttachedToWindow` 这个判断是必须的：onVisibilityChanged 在 attach 流程里
     * 会先于 View.onAttachedToWindow 被调用（此时 isAttachedToWindow 仍为 false），
     * 若在那里就启动动画，会和 onAttachedToWindow 一起把动画启动两遍。
     */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) {
            if (visibility == VISIBLE) startAnimatorForCurrentState() else cancelStateAnimators()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            if (isShown) startAnimatorForCurrentState()
        } else {
            cancelStateAnimators()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                isPressed = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val inside = isInsideContent(event.x, event.y)
                if (isPressed != inside) isPressed = inside
                return true
            }
            MotionEvent.ACTION_UP -> {
                val wasPressed = isPressed
                isPressed = false
                if (wasPressed && isInsideContent(event.x, event.y)) {
                    performClick()
                    onGaugeClickListener?.invoke()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            performClick()
            onGaugeClickListener?.invoke()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /** 用圆环的包围盒判断，而不是整个 View 的矩形（正方形控件时两者等价）。 */
    private fun isInsideContent(x: Float, y: Float): Boolean {
        val half = ringRadius + dp(VALUE_STROKE_DP) / 2f
        val cx = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= half * half
    }

    // ---------------------------------------------------------------- 工具

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    /**
     * sp -> px。用 density * fontScale 而不是已废弃的 scaledDensity，
     * 也回避了 API 34+ 非线性的 fontScale 换算，这里是保守写法。
     */
    private fun sp(value: Float): Float {
        val metrics = resources.displayMetrics
        return value * metrics.density * resources.configuration.fontScale
    }

    /** 修改颜色 alpha（0..1），不改 RGB，避免硬编码颜色。 */
    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
