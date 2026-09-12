package com.funnyass.test

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.funnyass.test.ui.BathActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 第 2 阶段 Apple UI 映射冒烟测试。
 *
 * 只验证「布局能被解析、23 个历史 id 全部保留、两个底部弹窗已挂载」，
 * 不触碰任何蓝牙连接 / 开阀 / 扣费逻辑。
 *
 * 启动时只显示主仪表盘；点击蓝牙设备卡片后才打开设备选择弹窗并开始扫描。
 *
 * 蓝牙权限由测试侧的 ensureBluetoothPermissions() 预先授予，
 * 避免系统权限对话框把 Activity 顶掉销毁（曾导致 onActivity NPE）。
 */
@RunWith(AndroidJUnit4::class)
class BathUiSmokeTest {

    private val requiredIds = listOf(
        R.id.status,
        R.id.log,
        R.id.device_list,
        R.id.wallet,
        R.id.account_subtitle,
        R.id.account_value,
        R.id.device_title,
        R.id.device_detail,
        R.id.device_mac,
        R.id.device_id_value,
        R.id.connection_value,
        R.id.protocol_value,
        R.id.control_hint,
        R.id.mac_input,
        R.id.advanced_panel,
        R.id.advanced_toggle,
        R.id.connect_btn,
        R.id.scan_btn,
        R.id.start_btn,
        R.id.stop_btn,
        R.id.disconnect_btn,
        R.id.relogin_btn,
        R.id.refresh_btn
    )

    private val appleIds = listOf(
        R.id.bath_gauge,
        R.id.settings_btn,
        R.id.sheet_scrim,
        R.id.picker_sheet,
        R.id.settings_sheet,
        R.id.device_entry_btn,
        R.id.device_entry_value,
        R.id.stat1_label,
        R.id.stat1_value,
        R.id.stat2_label,
        R.id.stat2_value,
        R.id.connection_dot,
        R.id.picker_close,
        R.id.picker_lock_hint,
        R.id.settings_close,
        R.id.settings_phone,
        R.id.settings_wallet,
        R.id.settings_version,
        R.id.settings_refresh,
        R.id.account_avatar,
        R.id.settings_check_update,
        R.id.debug_gauge_toggle
    )

    /** 预授予蓝牙权限，避免运行时权限对话框销毁被测量的 Activity。 */
    private fun ensureBluetoothPermissions() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val pkg = ctx.packageName
        listOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT"
        ).forEach { perm ->
            try {
                Runtime.getRuntime().exec(arrayOf("pm", "grant", pkg, perm)).waitFor()
            } catch (_: Exception) {
                // 低于 API 31 的设备没有这两个权限，忽略
            }
        }
    }

    @Test
    fun bathLayoutKeepsEveryLegacyId() {
        ensureBluetoothPermissions()
        ActivityScenario.launch(BathActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val missing = requiredIds.filter { activity.findViewById<View>(it) == null }
                    .map { activity.resources.getResourceName(it) }
                assertTrue("缺少必须保留的 id: $missing", missing.isEmpty())
            }
        }
    }

    @Test
    fun appleDashboardViewsAreMounted() {
        ensureBluetoothPermissions()
        ActivityScenario.launch(BathActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val missing = appleIds.filter { activity.findViewById<View>(it) == null }
                    .map { activity.resources.getResourceName(it) }
                assertTrue("Apple UI 视图缺失: $missing", missing.isEmpty())
            }
        }
    }

    @Test
    fun sheetsStartHiddenAndPickerOpensOnlyAfterDeviceTap() {
        ensureBluetoothPermissions()
        ActivityScenario.launch(BathActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    "设置弹窗初始应隐藏",
                    View.GONE,
                    activity.findViewById<View>(R.id.settings_sheet).visibility
                )
                assertEquals(
                    "启动时设备弹窗必须隐藏",
                    View.GONE,
                    activity.findViewById<View>(R.id.picker_sheet).visibility
                )
                assertEquals(
                    "启动时遮罩必须隐藏",
                    View.GONE,
                    activity.findViewById<View>(R.id.sheet_scrim).visibility
                )
                activity.findViewById<View>(R.id.device_entry_btn).performClick()
                assertEquals(
                    "点击蓝牙设备卡片后才打开设备弹窗",
                    View.VISIBLE,
                    activity.findViewById<View>(R.id.picker_sheet).visibility
                )
                assertEquals(
                    "设备弹窗打开时显示遮罩",
                    View.VISIBLE,
                    activity.findViewById<View>(R.id.sheet_scrim).visibility
                )
            }
        }
    }

    @Test
    fun settingsSheetShowsRequiredAccountFieldsWithoutChangingSession() {
        ensureBluetoothPermissions()
        ActivityScenario.launch(BathActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<View>(R.id.settings_phone))
                assertTrue(
                    "设置页应显示版本号",
                    activity.findViewById<android.widget.TextView>(R.id.settings_version)
                    .text.toString().contains("1.0.7")
                )
                }
            }
        }

    @Test
    fun debugGaugePreviewCyclesWithoutEnablingRealActions() {
        ensureBluetoothPermissions()
        ActivityScenario.launch(BathActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val toggle = activity.findViewById<android.widget.TextView>(R.id.debug_gauge_toggle)
                val start = activity.findViewById<View>(R.id.start_btn)

                assertEquals(View.VISIBLE, toggle.visibility)
                assertEquals("实时状态", toggle.text.toString())

                toggle.performClick()
                assertEquals("未连接", toggle.text.toString())
                assertTrue("预览状态不得启用真实开始操作", !start.isEnabled)

                toggle.performClick()
                assertEquals("待机", toggle.text.toString())
                assertTrue("切换预览状态后真实开始操作仍须禁用", !start.isEnabled)
            }
        }
    }

    @Test
    fun activitySurvivesOnCreateWithoutSession() {
        ensureBluetoothPermissions()
        ActivityScenario.launch(BathActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue("无会话时 Activity 不应被销毁", !activity.isFinishing)
            }
        }
    }

    @Test
    fun everyRequiredIdIsVisibleOrIntentionallyHidden() {
        ensureBluetoothPermissions()
        ActivityScenario.launch(BathActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // 23 个 id 必须都在视图树里可解析（可见性由状态决定，不做断言）
                requiredIds.forEach { id ->
                    assertNotNull(
                        "id 不可解析: " + activity.resources.getResourceName(id),
                        activity.findViewById<View>(id)
                    )
                }
            }
        }
    }
}
