package com.funnyass.test.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.funnyass.test.R
import com.funnyass.test.data.BaseResponse
import com.funnyass.test.data.UserInfo
import com.funnyass.test.net.Api
import com.funnyass.test.store.Session

class LoginActivity : AppCompatActivity() {
    private lateinit var content: View
    private lateinit var loginScroll: ScrollView
    private lateinit var loginTop: View
    private lateinit var phone: EditText
    private lateinit var code: EditText
    private lateinit var pwd: EditText
    private lateinit var sendBtn: Button
    private lateinit var codeLoginBtn: Button
    private lateinit var pwdLoginBtn: Button

    // 选择页 + 两个输入页
    private lateinit var pickerGroup: View
    private lateinit var pickPwdBtn: Button
    private lateinit var pickCodeBtn: Button
    private lateinit var codeGroup: View
    private lateinit var pwdGroup: View
    private lateinit var loginBack: View
    private lateinit var loginBackText: TextView
    private lateinit var pwdToggle: TextView
    private lateinit var errorCodeTv: TextView
    private lateinit var errorPwdTv: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var countingDown = false
    private var loading = false
    private var passwordVisible = false
    private var formattingPhone = false

    /** 当前所处的登录方式；仅在选择页时为 null。 */
    private var activeMode: LoginMode? = null

    private enum class LoginMode { CODE, PASSWORD }

    /** 输入法可见时占用的屏幕高度；0 表示键盘收起。 */
    private var imeHeight = 0

    /**
     * 键盘完全展开后占用的高度。
     *
     * ime() 的内边距是随键盘动画逐帧增大的，滚动若按动画中途的小值计算，会被
     * content 的总高 clamp 住而滚不到位，所以这里记住见过的最大值当预留下边距的基准。
     */
    private var finalImeHeight = 0

    /** 输入页内容的原始内边距，键盘内边距在其基础上叠加。 */
    private var baseContentTop = 0
    private var baseContentBottom = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        content = findViewById(R.id.login_content)
        loginScroll = findViewById(R.id.login_scroll)
        loginTop = findViewById(R.id.login_top)
        applyWindowInsets()

        phone = findViewById(R.id.phone)
        code = findViewById(R.id.code)
        pwd = findViewById(R.id.pwd)
        sendBtn = findViewById(R.id.send_btn)
        codeLoginBtn = findViewById(R.id.code_login_btn)
        pwdLoginBtn = findViewById(R.id.pwd_login_btn)

        pickerGroup = findViewById(R.id.login_picker_group)
        pickPwdBtn = findViewById(R.id.pick_pwd_btn)
        pickCodeBtn = findViewById(R.id.pick_code_btn)
        codeGroup = findViewById(R.id.login_code_group)
        pwdGroup = findViewById(R.id.login_pwd_group)
        loginBack = findViewById(R.id.login_back)
        loginBackText = findViewById(R.id.login_back_text)
        pwdToggle = findViewById(R.id.pwd_toggle)
        errorCodeTv = findViewById(R.id.login_error_code)
        errorPwdTv = findViewById(R.id.login_error_pwd)

        Session.phone(this)?.takeIf { it.isNotBlank() }?.let {
            val digits = it.filter { ch -> ch.isDigit() }.take(11)
            val formatted = formatPhone(digits)
            phone.setText(formatted)
            phone.setSelection(formatted.length)
        }
        intent.getStringExtra(EXTRA_REASON)?.takeIf { it.isNotBlank() }?.let { toast(it) }

        phone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (formattingPhone) return
                val digits = s?.toString()?.filter { ch -> ch.isDigit() }?.take(11).orEmpty()
                val formatted = formatPhone(digits)
                if (formatted != s?.toString()) {
                    formattingPhone = true
                    phone.setText(formatted)
                    phone.setSelection(formatted.length)
                    formattingPhone = false
                }
                clearError()
                updateSendEnabled()
            }
        })

        pickPwdBtn.setOnClickListener { showPasswordLogin() }
        pickCodeBtn.setOnClickListener { showCodeLogin() }
        loginBack.setOnClickListener { showPicker() }
        pwdToggle.setOnClickListener { togglePassword() }
        sendBtn.setOnClickListener { sendSms() }
        codeLoginBtn.setOnClickListener { codeLogin() }
        pwdLoginBtn.setOnClickListener { pwdLogin() }

        // 系统返回键：在输入页先退回选择页，符合分步流程的预期。
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeMode != null) showPicker() else finish()
            }
        })

        showPicker()
        updateSendEnabled()
    }

    // ------------------------------------------------------------ 页面切换

    /** 选择页：Logo 在上、两个登录方式按钮贴底。 */
    private fun showPicker() {
        activeMode = null
        clearError()
        setInputVisible(false)
        pickerGroup.visibility = View.VISIBLE
        hideKeyboard()
    }

    private fun showCodeLogin() = showInput(LoginMode.CODE)

    private fun showPasswordLogin() = showInput(LoginMode.PASSWORD)

    private fun showInput(mode: LoginMode) {
        activeMode = mode
        clearError()
        pickerGroup.visibility = View.GONE
        setInputVisible(true)
        // 不自动聚焦手机号：自动弹键盘会把手机号框顶出可视区，用户一进来只看到密码框。
        // 让用户自己点输入框，或从手机号框用键盘「下一项」跳转。
        loginScroll.scrollTo(0, 0)
    }

    private fun setInputVisible(visible: Boolean) {
        loginScroll.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        val codeMode = activeMode == LoginMode.CODE
        codeGroup.visibility = if (codeMode) View.VISIBLE else View.GONE
        pwdGroup.visibility = if (codeMode) View.GONE else View.VISIBLE
    }

    private fun applyWindowInsets() {
        baseContentTop = content.paddingTop
        baseContentBottom = content.paddingBottom
        // 键盘内边距只加在输入页的内容上：选择页不需要键盘处理，也不该被它影响。
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            // targetSdk 37 起 window 默认 edge-to-edge，系统不再自动为输入法调整窗口尺寸，
            // 必须自己把 ime() 内边距让出来，否则键盘会直接盖住输入框和登录按钮。
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // 只用 ime.bottom，不用 isVisible()：Android 11+ 键盘收起时就是 0，
            // 而旧版本上 isVisible() 的取值并不确定，可能误判成可见后多留一大截空白。
            val newImeHeight = ime.bottom
            val imeChanged = newImeHeight != imeHeight
            imeHeight = newImeHeight
            // 只在键盘「本次弹出」内记住最大高度，避免用动画中途的小值预留内边距。
            if (imeHeight == 0) {
                finalImeHeight = 0
            } else if (imeHeight > finalImeHeight) {
                finalImeHeight = imeHeight
            }
            view.setPadding(
                view.paddingLeft,
                baseContentTop,
                view.paddingRight,
                baseContentBottom + finalImeHeight
            )
            if (imeChanged) {
                if (imeHeight > 0) {
                    scrollFocusedFieldIntoView()
                } else {
                    restoreScrollAfterKeyboardHidden()
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    /**
     * 键盘弹出后，把「当前输入框 + 同一模式下的主操作按钮」作为一个整体滚进可视区。
     *
     * 只保证输入框可见是不够的：密码框下方紧跟着登录按钮，输入框贴住键盘上沿时按钮会被
     * 压掉大半，所以按整块计算。用 getLocationInWindow 直接比较窗口坐标，坐标差本身就是
     * 「相对可视区顶部」的位置，不需要换算内边距或滚动偏移。
     */
    private fun scrollFocusedFieldIntoView() {
        val focused = currentFocus ?: return
        if (focused !is EditText) return
        // 必须等这次内边距变化引发的布局跑完再滚：在同一帧里滚，位移会被紧随其后的
        // 重新布局直接吃掉（实测按钮位置纹丝不动）。
        content.doOnLayout {
            if (isFinishing || isDestroyed) return@doOnLayout
            // 键盘内边距加在根布局上，ScrollView 已经被压缩到键盘上方，它自身高度就是可视区。
            val visibleHeight = loginScroll.height
            if (visibleHeight <= 0) return@doOnLayout

            val scrollPos = IntArray(2)
            loginScroll.getLocationInWindow(scrollPos)
            val pos = IntArray(2)
            focused.getLocationInWindow(pos)
            val topInViewport = pos[1] - scrollPos[1]
            var blockBottom = topInViewport + focused.height

            // 当前输入页的主按钮：它才是用户输入完要点的目标，必须一起可见。
            val actionButton = if (activeMode == LoginMode.PASSWORD) pwdLoginBtn else codeLoginBtn
            actionButton.getLocationInWindow(pos)
            val buttonTop = pos[1] - scrollPos[1]
            if (buttonTop > topInViewport) {
                blockBottom = buttonTop + actionButton.height
            }

            val overlap = blockBottom - visibleHeight
            if (overlap > 0) {
                loginScroll.smoothScrollBy(0, overlap + dp(16))
            }
        }
        // 键盘高度是随展开动画逐帧上报的：若此刻拿到的还是中途的小值，滚动量会偏小。
        // 等键盘稳定后再复算一次补上差额（只会继续往下滚，不会回弹）。
        handler.removeCallbacks(imeSettleRunnable)
        handler.postDelayed(imeSettleRunnable, IME_SETTLE_DELAY_MS)
    }

    /** 键盘稳定后再校正一次滚动位置。 */
    private val imeSettleRunnable = Runnable {
        if (!isFinishing && !isDestroyed && imeHeight > 0) scrollFocusedFieldIntoView()
    }

    /**
     * 键盘收起后回到顶部。之前为了避开键盘而滚动的位置会让内容停在半截。
     */
    private fun restoreScrollAfterKeyboardHidden() {
        content.doOnLayout {
            if (isFinishing || isDestroyed) return@doOnLayout
            if (loginScroll.scrollY != 0) loginScroll.smoothScrollTo(0, 0)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(loginTop.windowToken, 0)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacks(imeSettleRunnable)
        super.onDestroy()
    }

    private fun p(): String = phone.text.toString().filter { it.isDigit() }

    private fun formatPhone(digits: String): String {
        return when {
            digits.isEmpty() -> ""
            digits.length <= 3 -> digits
            digits.length <= 7 -> digits.substring(0, 3) + " " + digits.substring(3)
            else -> digits.substring(0, 3) + " " + digits.substring(3, 7) + " " + digits.substring(7)
        }
    }

    private fun togglePassword() {
        passwordVisible = !passwordVisible
        if (passwordVisible) {
            pwd.transformationMethod = null
            pwdToggle.text = "🙈"
        } else {
            pwd.transformationMethod = PasswordTransformationMethod.getInstance()
            pwdToggle.text = "👁"
        }
        pwd.setSelection(pwd.text.length)
    }

    private fun updateSendEnabled() {
        sendBtn.isEnabled = !loading && !countingDown && p().length == 11
        sendBtn.alpha = if (sendBtn.isEnabled) 1f else 0.45f
    }

    /** 错误提示显示在当前输入页对应的位置。 */
    private fun showError(message: String) {
        clearError()
        val target = if (activeMode == LoginMode.PASSWORD) errorPwdTv else errorCodeTv
        target.text = message
    }

    private fun clearError() {
        errorCodeTv.text = ""
        errorPwdTv.text = ""
    }

    private fun sendSms() {
        val phoneNumber = p()
        if (phoneNumber.length != 11) {
            showError("请输入11位手机号")
            return
        }
        showError("")
        setLoading(true)
        Thread {
            try {
                val r: BaseResponse<Any> = Api.sendSms(phoneNumber)
                runOnUiThread {
                    setLoading(false)
                    if (r.errorCode == 0) {
                        toast("验证码已发送")
                        startCountdown()
                    } else {
                        showError(r.errorMessage ?: "发送失败(" + r.errorCode + ")")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    showError("网络错误: " + e.message)
                }
            }
        }.start()
    }

    private fun startCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countingDown = true
        var seconds = 60
        sendBtn.text = seconds.toString() + "s 后重发"
        updateSendEnabled()
        val runnable = object : Runnable {
            override fun run() {
                seconds--
                if (seconds <= 0) {
                    countingDown = false
                    sendBtn.text = "获取验证码"
                    updateSendEnabled()
                } else {
                    sendBtn.text = seconds.toString() + "s 后重发"
                    handler.postDelayed(this, 1000)
                }
            }
        }
        countdownRunnable = runnable
        handler.postDelayed(runnable, 1000)
    }

    private fun codeLogin() {
        val c = code.text.toString().trim()
        if (p().length != 11 || c.isEmpty()) {
            showError("请输入手机号和验证码")
            return
        }
        showError("")
        setLoading(true)
        Thread {
            try {
                val r: BaseResponse<UserInfo> = Api.loginByCode(p(), c)
                runOnUiThread {
                    setLoading(false)
                    handleLogin(r)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    showError("网络错误: " + e.message)
                }
            }
        }.start()
    }

    private fun pwdLogin() {
        val pw = pwd.text.toString()
        if (p().length != 11 || pw.isEmpty()) {
            showError("请输入手机号和密码")
            return
        }
        showError("")
        setLoading(true)
        Thread {
            try {
                val r: BaseResponse<UserInfo> = Api.loginByPassword(p(), pw)
                runOnUiThread {
                    setLoading(false)
                    handleLogin(r)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    showError("网络错误: " + e.message)
                }
            }
        }.start()
    }

    private fun handleLogin(r: BaseResponse<UserInfo>) {
        if (r.errorCode == 0 && r.data != null) {
            Session.saveUser(this, r.data)
            toast("登录成功")
            startActivity(Intent(this, BathActivity::class.java))
            finish()
        } else {
            showError(r.errorMessage ?: "登录失败(" + r.errorCode + ")")
        }
    }

    private fun setLoading(b: Boolean) {
        loading = b
        codeLoginBtn.isEnabled = !b
        pwdLoginBtn.isEnabled = !b
        pickPwdBtn.isEnabled = !b
        pickCodeBtn.isEnabled = !b
        loginBack.isEnabled = !b
        phone.isEnabled = !b
        code.isEnabled = !b
        pwd.isEnabled = !b
        updateSendEnabled()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_REASON = "login_reason"

        /** 键盘展开动画的稳定等待时长，用于在中途高度之外再校正一次滚动。 */
        private const val IME_SETTLE_DELAY_MS = 350L
    }
}
