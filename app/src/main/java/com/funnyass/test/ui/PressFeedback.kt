package com.funnyass.test.ui

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.funnyass.test.Logger

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

    /** 正在旋转的 View，用于忽略连点。 */
    private val spinning = HashSet<View>()

    /**
     * 给按钮加「按下去缩小、松开弹回」的效果。
     *
     * 只管缩放，**不在这里做旋转之类的附加动作**：
     * 早先版本把旋转挂在 `withEndAction` + `isPressed` 上，真机上缩放正常但旋转始终不触发
     * （`ViewPropertyAnimator.withEndAction` 在触摸手势期间并不可靠）。
     * 现在附加动作交给 `setOnClickListener`——它本身就只在有效点击时触发，更可靠。
     */
    fun attach(view: View) {
        view.setOnTouchListener { v, event ->
            // 灰掉的按钮不该有按压反馈
            if (!v.isEnabled) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                        .setDuration(PRESS_MS)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(RELEASE_MS)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                }
            }
            false // 不消费事件，点击/长按等原有逻辑继续正常工作
        }
    }

    /**
     * 齿轮旋转：每次点击转一圈。
     *
     * 用 `ViewPropertyAnimator.rotationBy`（与缩放同一套机制，实测可靠），
     * 而不是 `ObjectAnimator`——后者在触摸手势期间曾出现完全不生效的情况。
     * `rotationBy` 基于当前角度累加，因此天然接着上次继续转，不需要缓存角度。
     * 旋转进行中忽略连点，避免请求排队。
     */
    fun spinOnce(view: View) {
        if (!spinning.add(view)) {
            Logger.log("spin 跳过：正在旋转中")
            return
        }
        Logger.log("spin 开始 rotation=" + view.rotation)
        view.animate()
            .rotationBy(360f)
            .setDuration(420L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                spinning.remove(view)
                Logger.log("spin 结束 rotation=" + view.rotation)
            }
            .start()
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
