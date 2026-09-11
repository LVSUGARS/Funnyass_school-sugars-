package com.funnyass.test

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.appcompat.app.AppCompatDelegate
import com.funnyass.test.ui.BathActivity
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class BathVisualCaptureTest {

    @Test
    fun captureDashboardAndSheets() {
        grantBluetoothPermissions()
        ActivityScenario.launch(BathActivity::class.java).use { scenario ->
            capture("dashboard-light")

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.settings_btn).performClick()
                activity.findViewById<TextView>(R.id.settings_phone).text = "138****0000"
                activity.findViewById<TextView>(R.id.settings_wallet).text = "\u00a5--"
            }
            capture("settings-light")

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.settings_close).performClick()
                activity.findViewById<View>(R.id.device_entry_btn).performClick()
            }
            capture("device-picker-light")
        }
    }

    @Test
    fun captureDarkDashboardAndSheets() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        try {
            grantBluetoothPermissions()
            ActivityScenario.launch(BathActivity::class.java).use { scenario ->
                capture("dashboard-dark")

                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.settings_btn).performClick()
                    activity.findViewById<TextView>(R.id.settings_phone).text = "138****0000"
                    activity.findViewById<TextView>(R.id.settings_wallet).text = "\u00a5--"
                }
                capture("settings-dark")

                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.settings_close).performClick()
                    activity.findViewById<View>(R.id.device_entry_btn).performClick()
                }
                capture("device-picker-dark")
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

    private fun grantBluetoothPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT"
        ).forEach { permission ->
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, permission)
        }
    }
}
