package com.funnyass.test.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 按键按压反馈与弹窗进出动画。
 *
 * 统一放在这里，避免各处重复写 ObjectAnimator；所有动画只改 scale/alpha/translation，
 * 不参与任何业务判断。
 */
internal object PressFeedback {

    private const val PRESSED_SCALE = 0.96f
    private const val PRESS_MS = 90L
    private const val RELEASE_MS = 130L

    /** 记录每个 View 上一次旋转的角度，使齿轮每次点击都能接着转。 */
    private val rotationCache = HashMap<View, Float>()

    /** 正在旋转的 View，用于忽略连点（不同 API 版本判断动画进行中的方式不一致，自己记最稳）。 */
    private val spinning = HashSet<View>()

    /**
     * 给按钮加「按下去缩小、松开弹回」的效果。
     *
     * **必须在 `setOnClickListener` 之前调用**，这样 `isPressed` 仍然准确：
     * 手指滑出控件范围再松开时，`performClick()` 不会触发，而 `isPressed` 会是 false，
     * 于是这次不会播放旋转动画——避免"滚动列表时误触发"。
     *
     * @param onPressed 按下（回弹完成）后执行的动作，例如齿轮旋转
     */
    fun attach(view: View, onPressed: (() -> Unit)? = null) {
        view.setOnTouchListener { v, event ->
            // 灰掉的按钮不该有按压反馈
            if (!v.isEnabled) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate()
                    .scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                    .setDuration(PRESS_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(RELEASE_MS)
                        .setInterpolator(OvershootInterpolator(2f))
                        .withEndAction {
                            if (onPressed != null && v.isPressed) onPressed()
                        }
                        .start()
                }
            }
            false // 不消费事件，点击/长按等原有逻辑继续正常工作
        }
    }

    /**
     * 齿轮旋转：每次点击在原有角度上继续转一圈，避免每次都从 0 重来。
     * 旋转进行中时忽略后续点击，连点不会叠加。
     */
    fun spinOnce(view: View, turns: Float = 1f) {
        if (!spinning.add(view)) return
        val from = rotationCache[view] ?: 0f
        val to = from + 360f * turns
        ObjectAnimator.ofFloat(view, View.ROTATION, from, to).apply {
            duration = 520L
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rotationCache[view] = to
                    spinning.remove(view)
                }
            })
            start()
        }
    }
}

/**
 * 底部弹窗的进出场动画。
 *
 * 进：从下方滑入 + 淡入（`OvershootInterpolator` 带一点回弹）；
 * 出：向下滑出 + 淡出，动画结束后再真正 `GONE`，避免闪一下。
 */
internal object SheetAnim {

    private const val ENTER_MS = 260L
    private const val EXIT_MS = 180L
    private const val SLIDE_DP = 48f

    fun show(sheet: View) {
        if (sheet.visibility == View.VISIBLE && sheet.alpha > 0.99f) return
        val distance = SLIDE_DP * sheet.resources.displayMetrics.density
        sheet.animate().cancel()
        sheet.visibility = View.VISIBLE
        sheet.translationY = distance
        sheet.alpha = 0f
        sheet.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(ENTER_MS)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()
    }

    fun hide(sheet: View) {
        if (sheet.visibility != View.VISIBLE) return
        val distance = SLIDE_DP * sheet.resources.displayMetrics.density
        sheet.animate().cancel()
        sheet.animate()
            .translationY(distance)
            .alpha(0f)
            .setDuration(EXIT_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                sheet.visibility = View.GONE
                // 复位，避免下次显示从错误位置开始
                sheet.translationY = 0f
                sheet.alpha = 1f
            }
            .start()
    }

    /** 遮罩层淡入淡出。 */
    fun fadeIn(scrim: View) {
        scrim.animate().cancel()
        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(ENTER_MS).start()
    }

    fun fadeOut(scrim: View, onEnd: (() -> Unit)? = null) {
        if (scrim.visibility != View.VISIBLE) {
            onEnd?.invoke()
            return
        }
        scrim.animate().cancel()
        scrim.animate()
            .alpha(0f)
            .setDuration(EXIT_MS)
            .withEndAction {
                scrim.visibility = View.GONE
                scrim.alpha = 1f
                onEnd?.invoke()
            }
            .start()
    }
}
