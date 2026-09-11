package com.funnyass.test

import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.funnyass.test.ui.LoginActivity
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class LoginVisualCaptureTest {

    @Test
    fun captureLightLoginModes() {
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            // 第一屏：只有这一页有 Logo
            capture("login-picker-light")

            // 第二屏：验证码输入页
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.pick_code_btn).performClick()
                activity.findViewById<EditText>(R.id.phone).setText("138 0000 0000")
                activity.findViewById<EditText>(R.id.code).setText("")
            }
            capture("login-code-light")

            // 第三屏：密码输入页
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.login_back).performClick()
                activity.findViewById<View>(R.id.pick_pwd_btn).performClick()
                activity.findViewById<EditText>(R.id.phone).setText("138 0000 0000")
                activity.findViewById<EditText>(R.id.pwd).setText("")
            }
            capture("login-password-light")
        }
    }

    @Test
    fun captureDarkLogin() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        try {
            ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
                capture("login-picker-dark")

                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.pick_code_btn).performClick()
                    activity.findViewById<EditText>(R.id.phone).setText("138 0000 0000")
                    activity.findViewById<EditText>(R.id.code).setText("")
                }
                capture("login-code-dark")
            }
        } finally {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val directory = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "visual-captures"
        ).apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { stream ->
            instrumentation.uiAutomation.takeScreenshot()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
