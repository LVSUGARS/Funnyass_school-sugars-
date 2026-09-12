package com.funnyass.test.ui

import android.annotation.SuppressLint
import android.Manifest
import android.animation.ObjectAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.OnBackPressedCallback
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.funnyass.test.CmdServer
import com.funnyass.test.Logger
import com.funnyass.test.R
import com.funnyass.test.ble.BleManager
import com.funnyass.test.ble.FrameUtils
import com.funnyass.test.data.BaseResponse
import com.funnyass.test.data.DeviceInfo
import com.funnyass.test.data.DownRateData
import com.funnyass.test.data.UploadData
import com.funnyass.test.data.UserInfo
import com.funnyass.test.data.WalletData
import com.funnyass.test.net.Api
import com.funnyass.test.net.ApiClient
import com.funnyass.test.store.Session
import com.funnyass.test.store.UsageLog
import com.funnyass.test.update.UpdateChecker
import com.klcxkj.jni.JniUtils

@SuppressLint("MissingPermission")
class BathActivity : AppCompatActivity(), BleManager.Listener, CmdServer.Commands {

    private lateinit var ble: BleManager
    private lateinit var statusTv: TextView
    private lateinit var deviceList: ListView
    private lateinit var walletTv: TextView
    private lateinit var accountSubtitleTv: TextView
    private lateinit var accountValueTv: TextView
    private lateinit var deviceTitleTv: TextView
    private lateinit var deviceDetailTv: TextView
    private lateinit var deviceMacTv: TextView
    private lateinit var deviceIdValueTv: TextView
    private lateinit var connectionValueTv: TextView
    private lateinit var protocolValueTv: TextView
    private lateinit var controlHintTv: TextView
    private lateinit var macInput: EditText
    // 注意：这两行在布局里是整行可点的 LinearLayout，不是 TextView（曾因此 ClassCastException）
    private lateinit var advancedToggle: View
    private lateinit var connectBtn: Button
    // scan_btn / disconnect_btn 在布局里是 TextView（MaterialButton 会覆盖自定义背景）
    private lateinit var scanBtn: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var disconnectBtn: TextView
    // relogin_btn 在布局里是 TextView（MaterialButton 会覆盖自定义背景）
    private lateinit var reloginBtn: TextView

    // ---- Apple 仪表盘新增视图 ----
    private lateinit var gauge: BathGaugeView
    private lateinit var refreshBtn: TextView
    private lateinit var settingsBtn: View
    // ---- 全屏子页面（使用记录 / 诊断日志）----
    private lateinit var usagePage: View
    private lateinit var usagePageListTv: TextView
    private lateinit var diagnosticsPage: View
    private lateinit var diagnosticsPageLogTv: TextView
    private lateinit var settingsSheet: View
    private lateinit var pickerSheet: View
    private lateinit var sheetScrim: View
    private lateinit var connectionDot: View
    private lateinit var deviceEntryBtn: View
    private lateinit var deviceEntryValueTv: TextView
    private lateinit var stat1LabelTv: TextView
    private lateinit var stat1ValueTv: TextView
    private lateinit var stat2LabelTv: TextView
    private lateinit var stat2ValueTv: TextView
    private lateinit var pickerCurrentNameTv: TextView
    private lateinit var pickerCurrentMetaTv: TextView
    private lateinit var pickerLockHint: TextView
    private lateinit var pickerManualToggle: TextView
    private lateinit var pickerManualBody: View
    private lateinit var settingsPhoneTv: TextView
    private lateinit var settingsWalletTv: TextView
    private lateinit var settingsVersionTv: TextView
    private lateinit var settingsRefreshBtn: View
    private lateinit var settingsAvatar: ImageView
    private lateinit var settingsCheckUpdate: View
    private lateinit var settingsClose: View
    private lateinit var pickerClose: View
    private var accountRefreshAnim: ObjectAnimator? = null

    private var user: UserInfo? = null
    private var device: DeviceInfo? = null
    private val devices = LinkedHashMap<String, BluetoothDevice>()
    private val deviceRssi = mutableMapOf<String, Int>()
    private val deviceBleNames = mutableMapOf<String, String>()
    private val deviceInfoCache = LinkedHashMap<String, DeviceInfo>()
    private val deviceInfoFetching = mutableSetOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private var selectedMac: String? = null

    private var protocolType = "20"
    private var randomNumber = ""
    private var devType = 0
    private var a1 = 0
    private var connected = false
    private var pendingRateDate: String? = null
    private var pendingRateMac: String? = null
    private var deviceState = -1
    private var deviceAccountId = 0
    private var startRequestInFlight = false
    private var verifyingStart = false
    private var rollbackInFlight = false
    private var stopRequestInFlight = false
    private var stopAttempts = 0
    private var settlementInFlight = false
    private var collectRequested = false
    private var authRedirected = false
    private var authInvalidPending = false
    @Volatile private var sessionCheckInFlight = false

    // ---- 纯展示派生字段（不参与任何业务判断）----
    private var justSettled = false
    private var startConfirmedAtMs = 0L
    private var lastSettleAmountFen = -1
    private var lastSettleDurationMs = 0L
    private var rateYuanPerHour = 0.0
    private var walletAtStartYuan = Double.NaN

    // ---- 更新检查 ----
    /** 已发现的新版本；非空时版本行可点击跳转下载页。 */
    private var pendingUpdate: UpdateChecker.Result.Newer? = null
    private var checkingUpdate = false

    // ---- 使用记录（仅展示，不参与计费）----
    /** 本次用水结束时间；0 表示本次还没收尾，用于避免重复写记录。 */
    private var usageEndedAtMs = 0L
    private var usageDurationMs = 0L

    private val opHandler = Handler(Looper.getMainLooper())
    private val authInvalidListener: (String) -> Unit = { message ->
        runOnUiThread { handleAuthInvalid(message) }
    }
    private val sessionCheck = object : Runnable {
        override fun run() {
            validateSessionSilently()
            opHandler.postDelayed(this, SESSION_CHECK_INTERVAL_MS)
        }
    }
    private val gaugeTicker = object : Runnable {
        override fun run() {
            refreshGauge()
            if (isOwnActiveSession()) opHandler.postDelayed(this, 1000L)
        }
    }

    private val startAckTimeout = Runnable { verifyTimedOutStart() }
    private val startVerifyTimeout = Runnable {
        if (verifyingStart && pendingRateDate != null) {
            verifyingStart = false
            rollbackPendingStart("设备没有确认开阀，正在退回预扣")
        }
    }
    private val stopAckTimeout = Runnable { handleStopTimeout() }
    private val stopVerifyTimeout = Runnable {
        if (stopRequestInFlight) {
            stopRequestInFlight = false
            stopAttempts = 0
            setStatusText("未确认关阀，请再次点击停止")
            Logger.log("关阀命令与状态查询均无回执")
            updateActionButtons()
        }
    }
    private val settlementTimeout = Runnable {
        if (!settlementInFlight) return@Runnable
        val waitingForCollect = collectRequested
        settlementInFlight = false
        collectRequested = false
        val message = if (waitingForCollect) {
            "已关阀，未收到消费数据，请重试结算"
        } else {
            "已关阀，设备未确认结算写回，请重试"
        }
        Logger.log(message)
        setStatusText(message)
        updateActionButtons()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bath)
        statusTv = findViewById(R.id.status)
        deviceList = findViewById(R.id.device_list)
        walletTv = findViewById(R.id.wallet)
        accountSubtitleTv = findViewById(R.id.account_subtitle)
        accountValueTv = findViewById(R.id.account_value)
        deviceTitleTv = findViewById(R.id.device_title)
        deviceDetailTv = findViewById(R.id.device_detail)
        deviceMacTv = findViewById(R.id.device_mac)
        deviceIdValueTv = findViewById(R.id.device_id_value)
        connectionValueTv = findViewById(R.id.connection_value)
        protocolValueTv = findViewById(R.id.protocol_value)
        controlHintTv = findViewById(R.id.control_hint)
        macInput = findViewById(R.id.mac_input)
        advancedToggle = findViewById(R.id.advanced_toggle)
        usagePage = findViewById(R.id.usage_page)
        usagePageListTv = findViewById(R.id.usage_page_list)
        diagnosticsPage = findViewById(R.id.diagnostics_page)
        diagnosticsPageLogTv = findViewById(R.id.diagnostics_page_log)
        connectBtn = findViewById(R.id.connect_btn)
        scanBtn = findViewById(R.id.scan_btn)
        startBtn = findViewById(R.id.start_btn)
        stopBtn = findViewById(R.id.stop_btn)
        disconnectBtn = findViewById(R.id.disconnect_btn)
        reloginBtn = findViewById(R.id.relogin_btn)

        bindAppleViews()
        setupDeviceList()
        setupAppleInteractions()
        applyWindowInsets()

        user = Session.loadUser(this)
        restorePendingRate()
        ble = BleManager(this)
        ble.listener = this
        ApiClient.setAuthInvalidListener(authInvalidListener)
        Logger.log("user=" + (user?.userId ?: "null") + " 已登录=" + (user != null))
        Logger.log("版本 " + APP_VERSION)
        // 恢复上次选中的设备：以前设备选择只存在内存，重启就丢，用户每次都得重新扫描选一遍。
        if (macInput.text.isNullOrBlank()) {
            Session.selectedDeviceMac(this)?.let { macInput.setText(it) }
        }
        selectedMac = macInput.text.toString().trim().takeIf { it.isNotEmpty() }
        if (selectedMac != null) Logger.log("恢复上次选中设备 selectedMac=" + selectedMac)
        renderUser()
        renderDevice()
        renderConnection()
        applySheetLock()

        // 底部按钮与设置按钮的点击 + 按压反馈统一在 setupAppleInteractions() 里接线
        advancedToggle.visibility = View.VISIBLE
        usagePage.visibility = View.GONE
        diagnosticsPage.visibility = View.GONE
        hideSheets(animate = false)

        // 系统返回键：先关全屏子页面，再走默认行为
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSubPageVisible()) {
                    closeSubPage()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        refreshUser()
        CmdServer.start(this)

        // 启动后自动重连上次选中的设备（只有手动断开或切换设备才会替换记忆的设备）。
        // 延后一点：避开 onCreate 期间的权限/适配器初始化，让用户看到界面先出来。
        opHandler.postDelayed({ autoConnectLastDevice() }, 1200L)
    }

    // ==================== Apple UI 绑定与接线 ====================

    private fun bindAppleViews() {
        gauge = findViewById(R.id.bath_gauge)
        refreshBtn = findViewById(R.id.refresh_btn)
        settingsBtn = findViewById(R.id.settings_btn)
        sheetScrim = findViewById(R.id.sheet_scrim)
        pickerSheet = findViewById(R.id.picker_sheet)
        settingsSheet = findViewById(R.id.settings_sheet)
        connectionDot = findViewById(R.id.connection_dot)
        deviceEntryBtn = findViewById(R.id.device_entry_btn)
        deviceEntryValueTv = findViewById(R.id.device_entry_value)
        stat1LabelTv = findViewById(R.id.stat1_label)
        stat1ValueTv = findViewById(R.id.stat1_value)
        stat2LabelTv = findViewById(R.id.stat2_label)
        stat2ValueTv = findViewById(R.id.stat2_value)
        pickerLockHint = findViewById(R.id.picker_lock_hint)
        pickerManualToggle = findViewById(R.id.picker_manual_toggle)
        pickerManualBody = findViewById(R.id.picker_manual_body)
        pickerCurrentNameTv = findViewById(R.id.picker_current_name)
        pickerCurrentMetaTv = findViewById(R.id.picker_current_meta)
        settingsPhoneTv = findViewById(R.id.settings_phone)
        settingsWalletTv = findViewById(R.id.settings_wallet)
        settingsVersionTv = findViewById(R.id.settings_version)
        settingsRefreshBtn = findViewById(R.id.settings_refresh)
        settingsAvatar = findViewById(R.id.account_avatar)
        settingsCheckUpdate = findViewById(R.id.settings_check_update)
        settingsClose = findViewById(R.id.settings_close)
        pickerClose = findViewById(R.id.picker_close)
    }

    private fun setupDeviceList() {
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        deviceList.adapter = adapter
        deviceList.emptyView = findViewById(R.id.device_empty)
        deviceList.setOnItemClickListener { _, _, pos, _ ->
            val mac = visibleDeviceMacs(devices.keys, deviceInfoCache.keys, deviceBleNames)
                .elementAtOrNull(pos) ?: return@setOnItemClickListener
            device = null
            selectedMac = mac
            macInput.setText(mac)
            renderDevice()
            connectDevice(mac)
        }
        deviceList.isNestedScrollingEnabled = true
        deviceList.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    view.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    private fun setupAppleInteractions() {
        // 主布局只保留隐藏的同名兼容节点；诊断入口必须从设置 sheet 内定域查找。
        // 设置页两行改为「跳转到全屏页」（原折叠区空间太小）
        val sheetDiagnostics = settingsSheet.findViewById<View>(R.id.advanced_toggle)
        sheetDiagnostics?.setOnClickListener { showDiagnosticsPage() }
        val sheetUsage = settingsSheet.findViewById<View>(R.id.usage_toggle)
        sheetUsage?.setOnClickListener { showUsagePage() }
        usagePage.findViewById<View>(R.id.usage_back).setOnClickListener { closeSubPage() }
        usagePage.findViewById<View>(R.id.usage_clear).setOnClickListener {
            UsageLog.clear(this)
            renderUsageLog()
            toast("使用记录已清空")
        }
        diagnosticsPage.findViewById<View>(R.id.diagnostics_back).setOnClickListener { closeSubPage() }
        diagnosticsPage.findViewById<View>(R.id.diagnostics_clear).setOnClickListener {
            Logger.clear()
            syncLogText()
            toast("日志已清空")
        }
        gauge.setOnGaugeClickListener {
            gauge.playRefreshAnimation()
            refreshUser()
        }
        // 打开设备列表**只展示**已知设备，绝不自动扫描。
        // 原因：扫描会先断开当前连接（beginScan 里的 ble.disconnect），
        // 于是用户一打开列表，自动连接好的设备就变成「未连接」。
        // 需要找新设备时，由列表里的「扫描」按钮显式触发。
        pickerManualToggle.setOnClickListener {
            val expanded = pickerManualBody.visibility == View.VISIBLE
            pickerManualBody.visibility = if (expanded) View.GONE else View.VISIBLE
            pickerManualToggle.text = if (expanded) "手动输入 MAC  ›" else "手动输入 MAC  ‹"
        }

        // ---- 按压反馈与点击接线 -------------------------------------------
        // 这里同时完成「按压动画」与「点击行为」两件事，放在一处便于核对。
        // 顺序要求：attach 先于 setOnClickListener——这样松开时 isPressed 仍然准确，
        // 手指滑出控件再松开不会误触发（旋转动画依赖这个判断）。
        PressFeedback.attach(settingsBtn) { PressFeedback.spinOnce(settingsBtn) }
        settingsBtn.setOnClickListener { showSettingsSheet() }

        PressFeedback.attach(deviceEntryBtn)
        deviceEntryBtn.setOnClickListener { showDevicePicker() }

        PressFeedback.attach(startBtn)
        startBtn.setOnClickListener { onMainAction() }

        PressFeedback.attach(stopBtn)
        stopBtn.setOnClickListener { onMainAction() }

        PressFeedback.attach(connectBtn)
        connectBtn.setOnClickListener {
            val m = macInput.text.toString().trim()
            if (m.isNotEmpty()) connectDevice(m)
        }

        PressFeedback.attach(scanBtn)
        scanBtn.setOnClickListener { scanDevices() }

        PressFeedback.attach(disconnectBtn)
        disconnectBtn.setOnClickListener { disconnectDev() }

        PressFeedback.attach(reloginBtn)
        reloginBtn.setOnClickListener { requestRelogin() }

        pickerClose.setOnClickListener { hideDevicePicker() }
        settingsClose.setOnClickListener { hideSettingsSheet() }
        sheetScrim.setOnClickListener { hideSheets() }
        settingsRefreshBtn.setOnClickListener {
            animateAccountRefresh()
            refreshUser()
        }
        settingsCheckUpdate.setOnClickListener { checkUpdate() }
        settingsAvatar.setOnClickListener { pickAvatar() }
        refreshBtn.setOnClickListener { refreshUser() }
    }

    /** Main content avoids both system bars; sheets additionally sit above the navigation bar. */
    private fun applyWindowInsets() {
        val content = findViewById<View>(R.id.bath_content) ?: return
        val root = findViewById<View>(R.id.bath_root) ?: return
        val baseTop = content.paddingTop
        val baseBottom = content.paddingBottom
        // 全屏子页面的内边距基线（XML 里的初始值），在监听器外读一次
        val pageBasePaddingTop = usagePage.paddingTop
        val pageBasePaddingBottom = usagePage.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            content.setPadding(
                content.paddingLeft,
                baseTop + bars.top,
                content.paddingRight,
                baseBottom + bars.bottom
            )
            listOf(pickerSheet, settingsSheet).forEach { sheet ->
                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                    ?: return@forEach
                if (params.bottomMargin != bars.bottom) {
                    params.bottomMargin = bars.bottom
                    sheet.layoutParams = params
                }
            }
            // 全屏子页面必须避开系统栏：否则「返回」会落在状态栏区域内，
            // 触摸被系统吃掉，表现为点了没反应（实测 usage_back 中心在 y=112，
            // 而状态栏高 128）。
            listOf(usagePage, diagnosticsPage).forEach { page ->
                page.setPadding(
                    page.paddingLeft,
                    pageBasePaddingTop + bars.top,
                    page.paddingRight,
                    pageBasePaddingBottom + bars.bottom
                )
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun syncLogText() {
        if (!this::diagnosticsPageLogTv.isInitialized) return
        try {
            diagnosticsPageLogTv.text = Logger.getLog().takeLast(8000)
        } catch (_: Exception) {
            // 日志读取失败不影响主流程
        }
    }

    // ==================== 全屏子页面（使用记录 / 诊断日志）====================

    /** 打开使用记录全屏页；设置 sheet 收起，返回时再恢复设置页。 */
    private fun showUsagePage() {
        if (!this::usagePage.isInitialized) return
        prepareForSubPage()
        renderUsageLog()
        usagePage.visibility = View.VISIBLE
    }

    private fun showDiagnosticsPage() {
        if (!this::diagnosticsPage.isInitialized) return
        prepareForSubPage()
        syncLogText()
        diagnosticsPage.visibility = View.VISIBLE
    }

    /**
     * 进入全屏子页面前的准备：立即收起设置 sheet，但**保留遮罩**。
     *
     * 子页面是不透明的全屏层，遮罩被盖在下面看不见；如果这里把遮罩也隐藏了，
     * 返回设置页时它就得重新淡入，会闪一下。
     */
    private fun prepareForSubPage() {
        if (this::settingsSheet.isInitialized) settingsSheet.visibility = View.GONE
        if (this::pickerSheet.isInitialized) pickerSheet.visibility = View.GONE
    }

    /** 从全屏页返回设置页（保持设置 sheet 打开，避免用户再点一次齿轮）。 */
    private fun closeSubPage() {
        if (this::usagePage.isInitialized) usagePage.visibility = View.GONE
        if (this::diagnosticsPage.isInitialized) diagnosticsPage.visibility = View.GONE
        showSettingsSheet()
    }

    /**
     * 收起所有 sheet。
     * 带 [animate] = false 时立即隐藏：用于「A 换成 B」的场景，
     * 否则旧弹窗的缩回动画会和新弹窗的弹出动画叠在一起，看起来乱。
     */
    private fun hideSheets(animate: Boolean = true) {
        if (this::pickerSheet.isInitialized) {
            if (animate) SheetAnim.hide(pickerSheet) else pickerSheet.visibility = View.GONE
        }
        if (this::settingsSheet.isInitialized) {
            if (animate) SheetAnim.hide(settingsSheet) else settingsSheet.visibility = View.GONE
        }
        if (this::sheetScrim.isInitialized) {
            if (animate) SheetAnim.fadeOut(sheetScrim) else sheetScrim.visibility = View.GONE
        }
        // 子页面是全屏覆盖层，收起 sheet 时一并收起，避免残留
        if (this::usagePage.isInitialized) usagePage.visibility = View.GONE
        if (this::diagnosticsPage.isInitialized) diagnosticsPage.visibility = View.GONE
    }

    private fun isSubPageVisible(): Boolean =
        (this::usagePage.isInitialized && usagePage.visibility == View.VISIBLE) ||
            (this::diagnosticsPage.isInitialized && diagnosticsPage.visibility == View.VISIBLE)

    private fun showDevicePicker() {
        if (!this::pickerSheet.isInitialized || !this::sheetScrim.isInitialized) return
        finishSubSheets()
        SheetAnim.fadeIn(sheetScrim)
        SheetAnim.show(pickerSheet)
        constrainSheetHeight(pickerSheet)
        renderPickerCurrent()
        applySheetLock()
    }

    /** 弹窗进场前，把另一个 sheet 直接隐藏掉（不播放缩回动画）。 */
    private fun finishSubSheets() {
        if (this::pickerSheet.isInitialized && pickerSheet.visibility == View.VISIBLE) {
            pickerSheet.visibility = View.GONE
        }
        if (this::settingsSheet.isInitialized && settingsSheet.visibility == View.VISIBLE) {
            settingsSheet.visibility = View.GONE
        }
    }

    private fun hideDevicePicker() {
        if (!this::pickerSheet.isInitialized) return
        SheetAnim.hide(pickerSheet)
        if (this::sheetScrim.isInitialized) SheetAnim.fadeOut(sheetScrim)
    }

    private fun showSettingsSheet() {
        if (!this::settingsSheet.isInitialized || !this::sheetScrim.isInitialized) return
        if (this::pickerSheet.isInitialized) pickerSheet.visibility = View.GONE
        renderSettings()
        syncLogText()
        SheetAnim.fadeIn(sheetScrim)
        SheetAnim.show(settingsSheet)
        constrainSheetHeight(settingsSheet)
    }

    private fun constrainSheetHeight(sheet: View) {
        sheet.post {
            val maximum = resources.displayMetrics.heightPixels * 88 / 100
            if (sheet.height > maximum) {
                sheet.layoutParams = sheet.layoutParams.apply { height = maximum }
            }
        }
    }

    private fun hideSettingsSheet() {
        if (!this::settingsSheet.isInitialized) return
        SheetAnim.hide(settingsSheet)
        if (this::sheetScrim.isInitialized) SheetAnim.fadeOut(sheetScrim)
    }

    private fun renderPickerCurrent() {
        if (!this::pickerCurrentNameTv.isInitialized) return
        // device 为空时用选中设备的缓存兜底，避免「当前设备」区在扫描期间变空
        val d = device ?: selectedMac?.let { deviceInfoCache[it] }
        val hasSelection = !selectedMac.isNullOrBlank()
        val name = d?.devName?.takeIf { it.isNotBlank() }
            ?: when {
                connected -> "已连接洗澡设备"
                hasSelection -> "已选设备（待连接）"
                else -> "尚未选择设备"
            }
        val kind = d?.devTypeName?.takeIf { it.isNotBlank() }.orEmpty()
        val room = d?.roomName?.takeIf { it.isNotBlank() }.orEmpty()
        val head = joinRoomAndName(room, name)
        pickerCurrentNameTv.text = listOf(head, kind).filter { it.isNotBlank() }.joinToString(" · ")
        // 连接状态行（自定义文本）——不要在这里重复 MAC/编号/协议，
        // 那三项由下方的 device_mac / device_id_value / protocol_value 负责，否则会重复显示。
        pickerCurrentMetaTv.text = if (connected) "已连接 · " + signalText() else "未连接"
        deviceMacTv.text = "MAC " + (currentMac() ?: "--")
        deviceIdValueTv.text = if ((d?.devID ?: 0) > 0) d?.devID.toString() else "--"
        protocolValueTv.text = statProtocol()
    }

    private fun renderSettings() {
        if (!this::settingsPhoneTv.isInitialized) return
        val phone = user?.telephone?.trim().orEmpty()
        settingsPhoneTv.text = phone.ifBlank { "--" }
        settingsWalletTv.text = walletDisplay()
        // 有"发现新版本"提示时不要覆盖它，否则用户永远看不到更新入口
        if (pendingUpdate == null && !checkingUpdate) {
            settingsVersionTv.text = "当前版本 " + APP_VERSION
            restoreVersionColor()
        }
    }

    /**
     * 检查更新：从 GitHub Releases 拉最新 tag 与当前版本比较。
     *
     * 发现新版本时，版本行会变成可点击的「发现新版本 vX.Y.Z」，再点一次即打开 Release 页面。
     */
    private fun checkUpdate() {
        if (!this::settingsVersionTv.isInitialized) return
        // 已有可用更新时，这一下点击用于打开下载页
        val pending = pendingUpdate
        if (pending != null) {
            val url = pending.apkUrl ?: pending.pageUrl
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                toast("未找到可用的浏览器")
            }
            return
        }
        if (checkingUpdate) return

        checkingUpdate = true
        settingsVersionTv.text = "检查中…"
        Thread {
            val result = UpdateChecker.check(APP_VERSION)
            runOnUiThread {
                checkingUpdate = false
                if (!this::settingsVersionTv.isInitialized) return@runOnUiThread
                when (result) {
                    is UpdateChecker.Result.Newer -> {
                        pendingUpdate = result
                        settingsVersionTv.text = "发现新版本 " + result.version + "，点此更新"
                        settingsVersionTv.setTextColor(
                            ContextCompat.getColor(this, R.color.apple_blue)
                        )
                    }
                    is UpdateChecker.Result.UpToDate -> {
                        pendingUpdate = null
                        settingsVersionTv.text = "已是最新版本 " + APP_VERSION
                        restoreVersionColor()
                    }
                    is UpdateChecker.Result.Failed -> {
                        pendingUpdate = null
                        settingsVersionTv.text = result.message
                        restoreVersionColor()
                    }
                }
                Logger.log("检查更新: " + settingsVersionTv.text)
                // 非"发现新版本"的结果，短暂提示后回到当前版本
                if (pendingUpdate == null) {
                    opHandler.postDelayed({
                        if (this::settingsVersionTv.isInitialized && pendingUpdate == null) {
                            settingsVersionTv.text = "当前版本 " + APP_VERSION
                            restoreVersionColor()
                        }
                    }, 2_500L)
                }
            }
        }.start()
    }

    private fun restoreVersionColor() {
        if (!this::settingsVersionTv.isInitialized) return
        settingsVersionTv.setTextColor(
            ContextCompat.getColor(this, R.color.apple_secondary)
        )
    }

    private fun animateAccountRefresh() {
        if (!this::settingsRefreshBtn.isInitialized) return
        accountRefreshAnim?.cancel()
        accountRefreshAnim = ObjectAnimator.ofFloat(settingsRefreshBtn, "rotation", 0f, 360f).apply {
            duration = 800L
            start()
        }
    }

    @Deprecated("Avatar picking uses the legacy activity result API on purpose")
    private fun pickAvatar() {
        val intent = Intent(Intent.ACTION_PICK).setType("image/*")
        try {
            startActivityForResult(intent, REQ_PICK_AVATAR)
        } catch (_: Exception) {
            toast("未找到可用的相册应用")
        }
    }

    /** 用水/结算/关阀/回滚期间锁定弹窗内的所有可操作控件。只读 operationLocksDevice()，不新增锁。 */
    private fun applySheetLock() {
        if (!this::deviceList.isInitialized || !this::pickerLockHint.isInitialized) return
        val locked = operationLocksDevice() || rollbackInFlight
        listOf(deviceList, scanBtn, disconnectBtn, macInput, connectBtn).forEach {
            it.isEnabled = !locked
            it.alpha = if (locked) 0.42f else 1f
        }
        pickerLockHint.visibility = if (locked) View.VISIBLE else View.GONE
        if (this::reloginBtn.isInitialized) {
            reloginBtn.isEnabled = !isOwnActiveSession() && !stopRequestInFlight && !startRequestInFlight
            reloginBtn.alpha = if (reloginBtn.isEnabled) 1f else 0.45f
        }
    }

    // ==================== 圆环仪表盘状态映射（只读现有状态字段）====================

    private fun gaugeState(): BathGaugeView.GaugeState = resolveGaugeState(
        connected = connected,
        rollbackInFlight = rollbackInFlight,
        startRequestInFlight = startRequestInFlight,
        stopRequestInFlight = stopRequestInFlight,
        settlementInFlight = settlementInFlight,
        deviceState = deviceState,
        justSettled = justSettled,
        ownActiveSession = isOwnActiveSession()
    )

    private fun canRetrySettlement(): Boolean =
        connected && deviceState == 3 &&
            !settlementInFlight && !stopRequestInFlight && !rollbackInFlight &&
            canSettleLeftoverData()

    private fun refreshGauge() {
        if (!this::gauge.isInitialized) return
        val state = gaugeState()
        gauge.setGaugeState(state)
        when (state) {
            BathGaugeView.GaugeState.DISCONNECTED -> gauge.setGaugeText("--", "未连接", null)
            BathGaugeView.GaugeState.IDLE -> gauge.setGaugeText(walletDisplay(), "余额可用", null)
            BathGaugeView.GaugeState.STARTING ->
                gauge.setGaugeText("启动中", "正在确认设备", null)
            BathGaugeView.GaugeState.STOPPING -> {
                val cleaning = settlementInFlight || deviceState == 3
                gauge.setGaugeText(
                    if (cleaning) "结算中" else "关阀中",
                    if (cleaning) "正在上传消费记录" else "请稍候",
                    null
                )
            }
            BathGaugeView.GaugeState.SETTLED -> gauge.setGaugeText(
                if (lastSettleAmountFen >= 0) money(lastSettleAmountFen) else "--",
                "本次消费",
                null
            )
            BathGaugeView.GaugeState.ERROR -> {
                if (canRetrySettlement()) {
                    // 计费由服务端完成，这里只表示「消费记录还没同步回设备」，
                    // 因此文案要说明影响范围，不能让用户以为扣费出了问题。
                    gauge.setGaugeText("记录待同步", "设备仍存有本次记录", "重连后可重试")
                } else {
                    gauge.setGaugeText("异常", "正在处理", "请稍候")
                }
            }
            BathGaugeView.GaugeState.BATHING -> {
                val elapsed = elapsedBathingMs()
                val used = estimateUsedDisplay(elapsed)
                // 设计稿：大字=已用时长，小字=已用金额，meta=实时余额（起始余额 - 已用）
                val remaining = if (!walletAtStartYuan.isNaN()) {
                    "余额 ¥" + String.format(
                        "%.2f",
                        (walletAtStartYuan - used.removePrefix("¥").toDoubleOrNull().orZero()).coerceAtLeast(0.0)
                    )
                } else {
                    "余额 " + walletDisplay()
                }
                gauge.setGaugeText(formatDuration(elapsed), "已用 " + used, remaining)
            }
        }
        renderStats(state)
        renderDeviceEntry()
        renderStatTexts(state)
        applyChipVisual(state)
        syncMainButton(state)
        applySheetLock()
    }

    private fun renderStats(state: BathGaugeView.GaugeState) {
        val d = device
        when (state) {
            BathGaugeView.GaugeState.DISCONNECTED -> {
                stat1LabelTv.text = "协议"; stat1ValueTv.text = "--"
                stat2LabelTv.text = "水阀"; stat2ValueTv.text = "--"
            }
            BathGaugeView.GaugeState.IDLE -> {
                stat1LabelTv.text = "协议"
                stat1ValueTv.text = if (d != null || connected) statProtocol() else "--"
                stat2LabelTv.text = "水阀"
                // 必须按实际 deviceState 显示：设备被别人占用时 deviceState==1，
                // 但界面会落到 IDLE 分支，早先这里只判断「有没有设备详情」，
                // 于是别人正在用水、按钮已灰掉，水阀却显示「空闲」，与实际不符。
                stat2ValueTv.text = if (connected) valveStateText() else "--"
            }
            BathGaugeView.GaugeState.STARTING -> {
                stat1LabelTv.text = "协议"; stat1ValueTv.text = statProtocol()
                stat2LabelTv.text = "水阀"; stat2ValueTv.text = "--"
            }
            BathGaugeView.GaugeState.BATHING -> {
                stat1LabelTv.text = "水流"; stat1ValueTv.text = "正常"
                stat2LabelTv.text = "剩余"; stat2ValueTv.text = walletDisplay()
            }
            BathGaugeView.GaugeState.STOPPING -> {
                val cleaning = settlementInFlight || deviceState == 3
                stat1LabelTv.text = if (cleaning) "时长" else "水流"
                stat1ValueTv.text = if (cleaning) formatDuration(elapsedBathingMs()) else "--"
                stat2LabelTv.text = if (cleaning) "结算" else "结算"
                stat2ValueTv.text = if (cleaning) "上传中" else "准备中"
            }
            BathGaugeView.GaugeState.SETTLED -> {
                stat1LabelTv.text = "时长"
                stat1ValueTv.text = if (lastSettleDurationMs > 0) formatDuration(lastSettleDurationMs) else "--"
                stat2LabelTv.text = "余额"; stat2ValueTv.text = walletDisplay()
            }
            BathGaugeView.GaugeState.ERROR -> {
                stat1LabelTv.text = "订单"
                stat1ValueTv.text = if (canRetrySettlement()) "待重试" else "处理中"
                stat2LabelTv.text = "余额"; stat2ValueTv.text = walletDisplay()
            }
        }
        if (protocolValueTv.text.isNullOrBlank() || protocolValueTv.text == "--") {
            protocolValueTv.text = statProtocol()
        }
    }

    /** device_detail 在 Apple 布局里是设备副标题；旧逻辑写入的详细文案合并进 stat1/stat2 与弹窗。 */
    private fun renderStatTexts(state: BathGaugeView.GaugeState) {
        if (!this::deviceDetailTv.isInitialized) return
        if (state == BathGaugeView.GaugeState.DISCONNECTED && !connected) {
            // 保留旧提示语义，避免用户失去"为什么不能开始"的信息
            if (deviceDetailTv.text.isNullOrBlank()) deviceDetailTv.text = "连接设备后即可开始"
        }
    }

    private fun renderDeviceEntry() {
        if (!this::deviceEntryValueTv.isInitialized) return
        deviceEntryValueTv.text = if (connected) "已连接 · " + signalText() else "未连接"
    }

    private fun applyChipVisual(state: BathGaugeView.GaugeState) {
        if (!this::statusTv.isInitialized) return
        val (text, colorRes, bgRes) = when (state) {
            BathGaugeView.GaugeState.DISCONNECTED ->
                Triple("未连接", R.color.apple_secondary, R.drawable.bg_apple_pill_blue)
            BathGaugeView.GaugeState.IDLE ->
                Triple("已连接", R.color.apple_green, R.drawable.bg_apple_pill_green)
            BathGaugeView.GaugeState.STARTING ->
                Triple("启动中", R.color.apple_blue, R.drawable.bg_apple_pill_blue)
            BathGaugeView.GaugeState.BATHING ->
                Triple("供水中", R.color.apple_blue, R.drawable.bg_apple_pill_blue)
            BathGaugeView.GaugeState.STOPPING ->
                Triple(
                    if (settlementInFlight || deviceState == 3) "结算中" else "关阀中",
                    R.color.apple_amber,
                    R.drawable.bg_apple_pill_amber
                )
            BathGaugeView.GaugeState.SETTLED ->
                Triple("已完成", R.color.apple_green, R.drawable.bg_apple_pill_green)
            BathGaugeView.GaugeState.ERROR ->
                Triple("异常", R.color.apple_red, R.drawable.bg_apple_pill_red)
        }
        statusTv.text = text
        statusTv.setTextColor(ContextCompat.getColor(this, colorRes))
        // The HTML connection line is dot + plain text, not a status pill.
        statusTv.background = null

        val dotColor = when (state) {
            BathGaugeView.GaugeState.DISCONNECTED -> R.color.apple_secondary
            BathGaugeView.GaugeState.IDLE, BathGaugeView.GaugeState.SETTLED -> R.color.apple_green
            BathGaugeView.GaugeState.STARTING, BathGaugeView.GaugeState.BATHING -> R.color.apple_blue
            BathGaugeView.GaugeState.STOPPING -> R.color.apple_amber
            BathGaugeView.GaugeState.ERROR -> R.color.apple_red
        }
        connectionDot.background?.setTint(ContextCompat.getColor(this, dotColor))
        connectionValueTv.text = text
        connectionValueTv.setTextColor(ContextCompat.getColor(this, R.color.apple_secondary))
    }

    /** 底部主按钮：IDLE → 开始洗澡；BATHING → 停止并结算；SETTLED → 完成；其它禁用。 */
    private fun syncMainButton(state: BathGaugeView.GaugeState) {
        if (!this::startBtn.isInitialized) return
        when (state) {
            BathGaugeView.GaugeState.BATHING -> {
                startBtn.visibility = View.GONE
                stopBtn.visibility = View.VISIBLE
                stopBtn.text = if (stopRequestInFlight) "正在关阀…"
                else if (settlementInFlight) "结算中…" else "停止并结算"
                stopBtn.isEnabled = connected && isOwnActiveSession() &&
                    !stopRequestInFlight && !settlementInFlight
            }
            BathGaugeView.GaugeState.STARTING, BathGaugeView.GaugeState.STOPPING -> {
                startBtn.visibility = View.VISIBLE
                stopBtn.visibility = View.GONE
                startBtn.text = when (state) {
                    BathGaugeView.GaugeState.STARTING -> "正在启动…"
                    else -> "正在关阀…"
                }
                startBtn.isEnabled = false
            }
            BathGaugeView.GaugeState.ERROR -> {
                startBtn.visibility = View.VISIBLE
                stopBtn.visibility = View.GONE
                val canRetry = canRetrySettlement()
                startBtn.text = if (canRetry) "重试结算" else "处理中…"
                startBtn.isEnabled = canRetry
            }
            BathGaugeView.GaugeState.SETTLED -> {
                startBtn.visibility = View.VISIBLE
                stopBtn.visibility = View.GONE
                startBtn.text = "完成"
                startBtn.isEnabled = true
            }
            BathGaugeView.GaugeState.IDLE, BathGaugeView.GaugeState.DISCONNECTED -> {
                startBtn.visibility = View.VISIBLE
                stopBtn.visibility = View.GONE
                startBtn.text = "开始洗澡"
                startBtn.isEnabled = canStartAccordingToBusiness()
            }
        }
        startBtn.alpha = 1f
        stopBtn.alpha = if (stopBtn.isEnabled) 1f else 0.68f
    }

    private fun onMainAction() {
        when (gaugeState()) {
            BathGaugeView.GaugeState.IDLE -> startBath()
            BathGaugeView.GaugeState.BATHING -> stopBath()
            BathGaugeView.GaugeState.SETTLED -> {
                justSettled = false
                lastSettleAmountFen = -1
                lastSettleDurationMs = 0L
                refreshGauge()
                if (connected) ble.send(0x23)
            }
            BathGaugeView.GaugeState.ERROR -> {
                if (canRetrySettlement()) confirmValveClosed(collect = true)
                else toast("当前操作正在处理中，请稍候")
            }
            BathGaugeView.GaugeState.DISCONNECTED -> {
                if (!connected) toast("请先连接洗澡设备")
            }
            else -> toast("当前操作正在进行中，请稍候")
        }
    }

    // ==================== 文本工具 ====================

    private fun currentMac(): String? = device?.realMac?.takeIf { it.isNotBlank() }
        ?: device?.devMac?.takeIf { it.isNotBlank() }
        ?: selectedMac

    private fun statProtocol(): String = protocolType.takeIf { it.isNotBlank() } ?: "--"

    /**
     * 水阀状态短文案，供「水阀」统计卡使用。
     * 取设备上报的 deviceState（0x23 应答），不依赖是否已拉到设备详情。
     */
    private fun valveStateText(): String = valveStateLabel(deviceState)

    /**
     * 当前设备的信号强度（dBm）——**全应用唯一数据源**。
     *
     * 优先级：活动连接的实时读数优先，其次扫描缓存。
     * 之前 [signalText] 用扫描缓存、[onRssiChanged] 用实时值，两者会同时出现在界面上
     * （实测同一台设备同时显示 "-94 dBm" 与 "-48 dBm"），所以统一走这里。
     *
     * 说明：本项目实测机型上 GATT `readRemoteRssi()` 的绝对值与扫描广播值不一致
     * （-90 左右 vs -48），但界面一致性比绝对值更重要，因此只暴露一个来源。
     */
    private fun currentRssi(): Int? = ble.lastRssi() ?: currentMac()?.let { deviceRssi[it] }

    private fun signalText(): String {
        val rssi = currentRssi() ?: return "信号未知"
        return rssi.toString() + " dBm"
    }

    private fun money(fen: Int): String = "¥" + String.format("%.2f", fen / 100.0)

    /**
     * 余额展示：**原样保留服务端返回的精度**，不自行四舍五入。
     *
     * 服务端余额接口返回几位小数（实测如 "1.776"）就显示几位，与官方客户端一致。
     * 早先这里统一规整为两位（1.776 → ¥1.78），会与官方显示不符，且丢掉末尾精度。
     */
    private fun walletDisplay(): String {
        val t = walletTv.text?.toString()?.trim().orEmpty()
        if (t.isBlank() || t == "--") return "--"
        // 已经是 ¥ 开头的视为已格式化（loadWallet 会保留服务端原值），直接使用
        return if (t.startsWith("¥")) t else "¥$t"
    }

    private fun elapsedBathingMs(): Long =
        if (startConfirmedAtMs > 0L) SystemClock.elapsedRealtime() - startConfirmedAtMs else 0L

    /** 粗略估算已用金额：优先用服务端下发的费率（preMoney 分/小时），否则退回 0.10 元/分钟展示。 */
    private fun estimateUsedDisplay(elapsedMs: Long): String {
        val minutes = elapsedMs / 60000.0
        val yuan = if (rateYuanPerHour > 0.0) minutes * (rateYuanPerHour / 60.0) else minutes * 0.10
        return "¥" + String.format("%.2f", yuan)
    }

    /**
     * 从下费率帧里解析单价，仅用于圆环上的"已用金额"展示。
     *
     * 下费率帧 offset 10 起 4 字节小端是预扣单价（单位：分）。设备型号之间的计费单位
     * 不一致，因此这里做两段合理性校验：优先按"分/小时"解释，落在 0.6~600 元/小时
     * 则采用；否则按"分/分钟"解释，落在 0.01~10 元/分钟则采用；都不合理就返回 0，
     * 由调用方退回 0.10 元/分钟的展示估算。
     *
     * 注意：该值只影响 UI 文案，绝不参与任何扣费、校验或状态判断。
     */
    private fun parsePreMoney(downData: String?): Double {
        val hex = downData?.trim().orEmpty()
        if (hex.length < 28) return 0.0
        return try {
            val b = FrameUtils.fromHex(hex.substring(0, 28)) ?: return 0.0
            if (b.size < 14) return 0.0
            val fen = (b[10].toInt() and 0xFF) or
                ((b[11].toInt() and 0xFF) shl 8) or
                ((b[12].toInt() and 0xFF) shl 16) or
                ((b[13].toInt() and 0xFF) shl 24)
            if (fen <= 0) return 0.0
            val perHour = fen / 100.0
            val perMinute = fen / 100.0 * 60.0
            when {
                perHour in 0.6..600.0 -> perHour
                perMinute in 0.01..10.0 -> perMinute
                else -> 0.0
            }
        } catch (_: Exception) {
            0.0
        }
    }

    private fun walletYuan(): Double = walletDisplay().removePrefix("¥").toDoubleOrNull() ?: Double.NaN

    private fun formatDuration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600
        // 超过一小时才显示小时段，否则保持原样的 MM:SS
        return if (h > 0) String.format("%d:%02d:%02d", h, (total % 3600) / 60, total % 60)
        else String.format("%02d:%02d", total / 60, total % 60)
    }

    private fun Double?.orZero(): Double = this ?: 0.0

    private fun canStartAccordingToBusiness(): Boolean {
        val protocolReady = randomNumber.length == 8 && protocolType.length == 2
        return connected && deviceState == 0 && protocolReady &&
            !startRequestInFlight && !stopRequestInFlight && !settlementInFlight &&
            pendingRateDate == null && device != null
    }

    // ==================== 原有业务逻辑（除 UI 呈现外保持不变）====================

    override fun onResume() {
        super.onResume()
        opHandler.removeCallbacks(sessionCheck)
        opHandler.post(sessionCheck)
        opHandler.removeCallbacks(gaugeTicker)
        if (isOwnActiveSession()) opHandler.post(gaugeTicker)
    }

    override fun onPause() {
        opHandler.removeCallbacks(sessionCheck)
        opHandler.removeCallbacks(gaugeTicker)
        super.onPause()
    }

    private fun renderUser() {
        val u = user
        if (u == null) {
            accountSubtitleTv.text = "未登录"
            accountValueTv.text = "账户 --"
            renderSettings()
            return
        }
        val phone = u.telephone?.trim().orEmpty()
        val masked = maskPhone(phone)
        // 部分账号的 alias 存的就是手机号本身，若与账号手机号相同则只显示一行，
        // 避免出现「138****0000 · 138****0000」这种重复。
        val alias = u.alias?.trim().orEmpty()
        val aliasIsPhone = alias.isNotEmpty() && alias.filter { it.isDigit() } == phone.filter { it.isDigit() }
        accountSubtitleTv.text = when {
            masked.isBlank() -> alias.ifBlank { "校园账户已登录" }
            alias.isBlank() || aliasIsPhone -> masked
            else -> alias + " · " + masked
        }
        accountValueTv.text = "账户 " + if (u.accountId > 0) u.accountId else "--"
        renderSettings()
    }

    private fun renderDevice() {
        val d = device
        val hasSelection = !selectedMac.isNullOrBlank()
        deviceTitleTv.text = d?.devName?.takeIf { it.isNotBlank() }
            ?: when {
                connected -> "已连接洗澡设备"
                hasSelection -> "正在读取设备信息…"
                else -> "请选择洗澡设备"
            }
        val room = d?.roomName?.takeIf { it.isNotBlank() }
        val kind = d?.devTypeName?.takeIf { it.isNotBlank() }
        // 用 joinRoomAndName 去重：devTypeName 有时也自带房间名
        deviceDetailTv.text = when {
            room != null && kind != null -> joinRoomAndName(room, kind)
            room != null -> room
            kind != null -> kind
            connected -> "蓝牙设备已就绪"
            hasSelection -> "正在连接…"
            else -> "点击下方蓝牙设备卡片扫描附近设备"
        }
        val mac = currentMac()
        deviceMacTv.text = "MAC " + (mac ?: "--")
        deviceIdValueTv.text = if ((d?.devID ?: 0) > 0) d?.devID.toString() else "--"
        protocolValueTv.text = if (hasSelection) statProtocol() else "--"
        if (deviceMacTv.text == "MAC null") deviceMacTv.text = "MAC --"
        renderPickerCurrent()
        renderDeviceEntry()
    }

    private fun renderConnection() {
        connectionValueTv.text = if (connected) "已连接" else "未连接"
        connectionValueTv.setTextColor(ContextCompat.getColor(this, R.color.apple_secondary))
        val hint = when {
            stopRequestInFlight -> "关阀命令已直接发送；关阀后再进行消费结算"
            settlementInFlight || deviceState == 3 -> "供水已经停止，正在完成消费结算"
            isOwnActiveSession() -> "设备正在供水，点击停止会立即发送关阀命令"
            deviceState == 1 -> "设备正被其他账户使用，已禁止误操作"
            connected && deviceState == 0 && device == null -> "设备状态正常，正在等待设备详情"
            connected && deviceState == 0 -> "设备空闲，可以开始"
            connected -> "正在读取设备状态，请稍候"
            else -> "连接设备后即可开始，停止时会自动结算"
        }
        controlHintTv.text = hint
        renderDevice()
        updateActionButtons()
    }

    private fun setStatusText(text: String) {
        // chip 的最终文案由 applyChipVisual(state) 统一决定；此处保留业务文案供诊断与调试。
        Logger.status(text)
        renderConnection()
    }

    private fun isOwnActiveSession(): Boolean {
        val aid = user?.accountId ?: 0
        return deviceState == 1 && aid > 0 && deviceAccountId == aid
    }

    private fun canSettleLeftoverData(): Boolean {
        val aid = user?.accountId ?: 0
        return deviceAccountId == 0 || (aid > 0 && deviceAccountId == aid)
    }

    private fun operationLocksDevice(): Boolean =
        startRequestInFlight || stopRequestInFlight || settlementInFlight || rollbackInFlight || isOwnActiveSession()

    private fun updateActionButtons() {
        if (!::startBtn.isInitialized) return
        val canChangeDevice = !operationLocksDevice()
        val canReconnectLockedDevice = !connected && !selectedMac.isNullOrBlank() && operationLocksDevice()

        connectBtn.isEnabled = canChangeDevice || canReconnectLockedDevice
        scanBtn.isEnabled = canChangeDevice
        disconnectBtn.isEnabled = connected && canChangeDevice
        reloginBtn.isEnabled = !isOwnActiveSession() && !stopRequestInFlight && !startRequestInFlight
        macInput.isEnabled = canChangeDevice
        advancedToggle.isEnabled = canChangeDevice
        advancedToggle.alpha = if (advancedToggle.isEnabled) 1f else 0.45f

        refreshGauge()
    }

    private fun loadDeviceInfo(mac: String) {
        val u = user ?: return
        if (mac.isBlank()) return
        selectedMac = mac
        rememberSelectedMac(mac)
        Thread {
            try {
                val r: BaseResponse<DeviceInfo> = Api.deviceByMac(u, mac)
                runOnUiThread {
                    if (r.errorCode == 0 && r.data != null) {
                        deviceInfoCache[mac] = r.data
                        device = r.data
                        renderDevice()
                        refreshGauge()
                    } else {
                        Logger.log("设备详情失败: " + r.errorCode + " " + r.errorMessage)
                    }
                    deviceInfoFetching.remove(mac)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    deviceInfoFetching.remove(mac)
                    Logger.log("设备详情异常: " + e.message)
                }
            }
        }.start()
    }

    /**
     * 记住选中的设备。设备选择以前只存在内存（`selectedMac`），重启即丢，
     * 用户每次都要重新扫描再点一遍；这里落到 SharedPreferences，启动时恢复。
     */
    private fun rememberSelectedMac(mac: String) {
        if (mac.isBlank()) return
        Session.saveSelectedDeviceMac(this, mac)
    }

    // ==================== 使用记录 ====================

    /**
     * 每次用水结束时先落一条记录（时长已知、金额待余额刷新后回填）。
     *
     * 金额用**服务端钱包差值**（更可信），而不是本地按费率估算；
     * 差值取不到时退回估算，估算也取不到就显示 "--"，不编造金额。
     */
    private fun recordUsageEnd() {
        if (usageEndedAtMs != 0L) return
        val duration = elapsedBathingMs()
        if (duration <= 0L) return
        usageEndedAtMs = System.currentTimeMillis()
        usageDurationMs = duration
        UsageLog.append(
            this,
            UsageLog.Record(endedAtMs = usageEndedAtMs, durationMs = duration, costYuan = null)
        )
        Logger.log("使用记录已写入 时长=" + duration + "ms，金额待余额刷新后回填")
        renderUsageLog()
    }

    /** 余额刷新后，用「起始余额 - 当前余额」回填本次花费。 */
    private fun backfillUsageCost() {
        if (usageEndedAtMs == 0L) return
        val after = walletYuan()
        val before = walletAtStartYuan
        val cost = if (!before.isNaN() && !after.isNaN() && before >= after) {
            before - after
        } else {
            // 余额差值不可用（未记录起始余额等）时退回按费率估算，纯展示用途
            val minutes = usageDurationMs / 60000.0
            if (rateYuanPerHour > 0.0) minutes * (rateYuanPerHour / 60.0) else null
        }
        UsageLog.backfillLatestCost(this, usageEndedAtMs, cost)
        Logger.log("使用记录花费回填 cost=" + cost)
        renderUsageLog()
    }

    /** 渲染使用记录全屏页。 */
    private fun renderUsageLog() {
        if (!this::usagePageListTv.isInitialized) return
        val records = UsageLog.list(this)
        if (records.isEmpty()) {
            usagePageListTv.text = "暂无使用记录\n\n完成一次洗浴后会自动记录时长与花费。"
            return
        }
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        // 整页展示，最多 50 条（与 UsageLog 的保留上限一致），不再截断
        val body = records.joinToString("\n") { r ->
            val whenText = fmt.format(java.util.Date(r.endedAtMs))
            val duration = formatUsageDuration(r.durationMs)
            val cost = r.costYuan?.let { "¥" + String.format("%.2f", it) } ?: "--"
            "$whenText     $duration     $cost"
        }
        usagePageListTv.text = "共 " + records.size + " 条\n\n" + body
    }

    /**
     * 使用记录的时长展示：不足 1 小时用「X分XX秒」，超过则用「H:MM:SS」。
     * 特意与圆环用的 [formatDuration]（MM:SS）分开命名，避免重载歧义。
     */
    private fun formatUsageDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d分%02d秒", m, s)
    }

    // ==================== 自动连接 ====================

    /**
     * 启动后自动连接上次选中的设备。
     *
     * 只有**手动断开**或**切换设备**才会替换记忆的设备，因此这里直接尝试重连；
     * 连不上时静默保持未连接状态，用户仍可手动扫描。
     */
    private fun autoConnectLastDevice() {
        if (operationLocksDevice()) return
        if (connected || ble.isBusy()) return
        val mac = Session.selectedDeviceMac(this) ?: return
        if (!ble.isEnabled) {
            Logger.log("自动连接跳过：蓝牙未开启")
            return
        }
        Logger.log("自动连接上次设备 " + mac)
        connectDevice(mac)
    }

    private fun formatDeviceInfo(d: DeviceInfo): String {
        val parts = mutableListOf<String>()
        d.roomName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        d.devTypeName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        if (d.devID > 0) parts.add("编号 " + d.devID)
        d.devName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return if (parts.isEmpty()) "已识别设备" else parts.joinToString(" · ")
    }

    private fun deviceListLabel(mac: String): String {
        val bleName = deviceBleNames[mac]?.takeIf { it.isNotBlank() }
        val cached = deviceInfoCache[mac]
        val title = cached?.devName?.takeIf { it.isNotBlank() } ?: bleName ?: "未命名设备"
        val rssi = deviceRssi[mac]
        val rssiText = if (rssi != null) rssi.toString() + " dBm" else "信号未知"
        val room = cached?.roomName?.takeIf { it.isNotBlank() }
        val head = joinRoomAndName(room, title)
        return head + "\n" + rssiText + " · " + mac
    }

    private fun refreshDeviceList() {
        val visibleMacs = visibleDeviceMacs(
            devices.keys, deviceInfoCache.keys, deviceBleNames, selectedMac
        )
        adapter.clear()
        visibleMacs.forEach { mac -> adapter.add(deviceListLabel(mac)) }
        adapter.notifyDataSetChanged()
        renderPickerCurrent()
        setStatusText("已发现 " + visibleMacs.size + " 台洗浴设备")
    }

    private fun fetchDeviceInfoForList(mac: String) {
        val u = user ?: return
        if (deviceInfoCache.containsKey(mac) || !deviceInfoFetching.add(mac)) return
        Thread {
            try {
                val r: BaseResponse<DeviceInfo> = Api.deviceByMac(u, mac)
                runOnUiThread {
                    if (r.errorCode == 0 && r.data != null) {
                        deviceInfoCache[mac] = r.data
                        refreshDeviceList()
                    }
                    deviceInfoFetching.remove(mac)
                }
            } catch (e: Exception) {
                runOnUiThread { deviceInfoFetching.remove(mac) }
            }
        }.start()
    }

    private fun restorePendingRate() {
        val prefs = getSharedPreferences(SAFETY_PREFS, MODE_PRIVATE)
        pendingRateDate = prefs.getString(KEY_PENDING_DATE, null)?.takeIf { it.isNotBlank() }
        pendingRateMac = prefs.getString(KEY_PENDING_MAC, null)?.takeIf { it.isNotBlank() }
        if (pendingRateDate != null) {
            Logger.log("发现上次未确认的预扣记录，将在连接原设备后自动核对")
        }
    }

    private fun persistPendingRate(consumeDate: String, mac: String) {
        pendingRateDate = consumeDate
        pendingRateMac = mac
        getSharedPreferences(SAFETY_PREFS, MODE_PRIVATE).edit()
            .putString(KEY_PENDING_DATE, consumeDate)
            .putString(KEY_PENDING_MAC, mac)
            .apply()
    }

    private fun clearPendingRate(consumeDate: String? = pendingRateDate) {
        if (consumeDate != null && pendingRateDate != null && consumeDate != pendingRateDate) return
        pendingRateDate = null
        pendingRateMac = null
        getSharedPreferences(SAFETY_PREFS, MODE_PRIVATE).edit()
            .remove(KEY_PENDING_DATE)
            .remove(KEY_PENDING_MAC)
            .apply()
    }

    private fun requestRelogin() {
        if (operationLocksDevice()) {
            toast("请先停止供水并等待当前操作完成")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("重新登录")
            .setMessage("将清除本机登录凭据，并返回登录页面。")
            .setNegativeButton("取消", null)
            .setPositiveButton("重新登录") { _, _ -> goToLogin("请重新登录账户") }
            .show()
    }

    private fun handleAuthInvalid(message: String) {
        if (authRedirected) return
        if (isOwnActiveSession() || stopRequestInFlight) {
            authInvalidPending = true
            setStatusText("登录已失效，请先停止供水")
            toast("检测到账号已在其他设备登录；请先关阀")
            return
        }
        goToLogin(message)
    }

    private fun goToLogin(reason: String) {
        if (authRedirected) return
        authRedirected = true
        ApiClient.clearAuthInvalidListener(authInvalidListener)
        ble.stopScan()
        ble.disconnect()
        connected = false
        Session.logout(this, keepPhone = true)
        val intent = Intent(this, LoginActivity::class.java)
            .putExtra(LoginActivity.EXTRA_REASON, reason)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    // ---- CmdServer.Commands ----
    override fun connectDevice(mac: String) {
        if (mac.isEmpty()) { Logger.log("connect 需要 mac"); return }
        val reconnectingLockedDevice = !connected && selectedMac?.equals(mac, ignoreCase = true) == true
        if (operationLocksDevice() && !reconnectingLockedDevice) {
            toast("当前用水或结算尚未结束，不能更换设备")
            return
        }
        deviceState = -1
        deviceAccountId = 0
        randomNumber = ""
        selectedMac = mac
        macInput.setText(mac)
        // 用户选定的设备要跨重启保留：以前只存在内存里，重启就丢。
        rememberSelectedMac(mac)
        val cachedInfo = deviceInfoCache[mac]
        if (cachedInfo != null) {
            device = cachedInfo
            renderDevice()
        } else {
            device = null
            renderDevice()
            loadDeviceInfo(mac)
        }
        Logger.log("连接 " + mac)
        ble.connect(mac)
    }

    override fun scanDevices() {
        showDevicePicker()
        if (!ensurePermissions()) return
        if (!ble.isEnabled) {
            setStatusText("请开启蓝牙")
            try {
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 101)
            } catch (_: Exception) {
                toast("请先在系统设置中开启蓝牙")
            }
            return
        }
        beginScan()
    }

    private fun beginScan() {
        if (operationLocksDevice()) {
            toast("请先停止并结算当前用水")
            return
        }
        // 扫描**不主动断开**当前连接，也不清空 deviceState：
        // 原来这里无条件 ble.disconnect() + connected=false + deviceState=-1，
        // 导致用户一打开设备列表（此前会触发扫描）自动连接好的设备就显示「未连接」、
        // 「水阀」卡片也变空。真正要换设备时，connectDevice() 会断旧连新并重新查 0x23。
        // 注意：也不要清空 selectedMac / macInput / device——
        // 扫描只是刷新「附近设备」，选中设备应当继续留在「当前设备」区。
        restoreSelectedDeviceFromCache()
        devices.clear()
        deviceRssi.clear()
        deviceBleNames.clear()
        adapter.clear()
        renderDevice()
        setStatusText("正在扫描附近设备…")
        ble.startScan()
    }

    /**
     * 用设备详情缓存回填 `device`，保证扫描/重连期间「当前设备」区不空。
     * 缓存里没有对应条目时保持不变（后续 `loadDeviceInfo` 会补上）。
     */
    private fun restoreSelectedDeviceFromCache() {
        val mac = selectedMac ?: return
        deviceInfoCache[mac]?.let { device = it }
    }

    override fun disconnectDev() {
        if (operationLocksDevice()) {
            toast("当前用水或结算尚未结束，不能断开设备")
            return
        }
        ble.disconnect(); connected = false; Logger.status("已断开"); setStatusText("已断开")
        // 用户主动断开 = 明确放弃这台设备，连记住的 MAC 一起清掉；
        // （扫描/刷新列表不会走到这里，所以不会误删选中的设备）
        selectedMac = null
        macInput.text.clear()
        Session.saveSelectedDeviceMac(this, null)
        hideDevicePicker()
    }

    override fun queryDev() {
        Logger.log("手动查询 0x23")
        ble.send(0x23)
    }

    override fun collectDev(randomNumber: String) {
        if (randomNumber.isNotBlank()) this.randomNumber = randomNumber
        Logger.log("手动采集 0x85")
        ble.send(0x85)
    }

    override fun uploadDev(randomNumber: String, xfData: String) {
        if (randomNumber.isNotBlank()) this.randomNumber = randomNumber
        val data = FrameUtils.fromHex(xfData)
        if (data == null) Logger.log("手动上传数据格式错误") else uploadData(data)
    }

    override fun failDev(consumeDate: String) {
        rollbackRate(consumeDate, "正在退回未完成订单的预扣")
    }

    private fun rollbackPendingStart(reason: String) {
        val consumeDate = pendingRateDate ?: return
        opHandler.removeCallbacks(startAckTimeout)
        opHandler.removeCallbacks(startVerifyTimeout)
        startRequestInFlight = false
        verifyingStart = false
        rollbackRate(consumeDate, reason)
    }

    private fun rollbackRate(consumeDate: String, reason: String = "正在退回预扣") {
        val u = user ?: return
        if (consumeDate.isBlank()) return
        if (rollbackInFlight) return
        rollbackInFlight = true
        setStatusText(reason)
        updateActionButtons()
        Thread {
            try {
                val r = Api.failBluetoothOrder(u, consumeDate)
                runOnUiThread {
                    rollbackInFlight = false
                    Logger.log("失败订单回滚: " + r.errorCode + " " + r.errorMessage)
                    if (r.errorCode == 0) {
                        clearPendingRate(consumeDate)
                        deviceState = 0
                        deviceAccountId = 0
                        setStatusText("设备未开阀，预扣已退回")
                        loadWallet()
                    } else {
                        setStatusText("预扣退回失败，请重连原设备核对")
                        toast("为避免重复扣费，已暂停再次启动")
                    }
                    updateActionButtons()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    rollbackInFlight = false
                    setStatusText("预扣退回失败，请重连原设备核对")
                    Logger.log("回滚异常: " + e.message)
                    updateActionButtons()
                }
            }
        }.start()
    }

    override fun startBath() {
        val u = user ?: run { Logger.log("未登录"); return }
        if (!connected) { Logger.log("请先连接设备"); return }
        val mac = selectedMac ?: run { Logger.log("请先选择设备"); return }
        if (startRequestInFlight || stopRequestInFlight || settlementInFlight || rollbackInFlight) {
            toast("上一个操作正在处理中")
            return
        }
        if (pendingRateDate != null) {
            toast("存在待核对预扣，请先连接原设备完成核对")
            return
        }
        if (deviceState != 0 || randomNumber.length != 8) {
            setStatusText("正在确认设备状态…")
            Logger.log("启动被拦截：状态或随机数尚未就绪，重新查询 0x23")
            ble.send(0x23)
            return
        }
        if (device == null) {
            startRequestInFlight = true
            setStatusText("正在读取设备信息…")
            updateActionButtons()
            Logger.log("首次读取设备详情 device/info/mac?mac=" + mac)
            Thread {
                try {
                    val r: BaseResponse<DeviceInfo> = Api.deviceByMac(u, mac)
                    runOnUiThread {
                        if (r.errorCode == 0 && r.data != null && connected && selectedMac == mac && deviceState == 0) {
                            device = r.data
                            renderDevice()
                            doRateOrder(u)
                        } else {
                            startRequestInFlight = false
                            setStatusText("设备信息读取失败，未产生预扣")
                            Logger.log("设备详情失败: " + r.errorCode + " " + r.errorMessage)
                            updateActionButtons()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        startRequestInFlight = false
                        setStatusText("设备信息读取失败，未产生预扣")
                        Logger.log("设备详情异常: " + e.message)
                        updateActionButtons()
                    }
                }
            }.start()
            return
        }
        // 连接时已经取过设备详情，不再在每次启动前重复请求一次。
        startRequestInFlight = true
        setStatusText("准备启动…")
        updateActionButtons()
        doRateOrder(u)
    }

    override fun stopBath() {
        if (!connected) { Logger.log("未连接"); return }
        if (stopRequestInFlight) return
        if (!isOwnActiveSession()) {
            toast(if (deviceState == 1) "设备由其他账户使用，已禁止误关阀" else "当前设备没有你的用水会话")
            return
        }
        stopRequestInFlight = true
        stopAttempts = 1
        setStatusText("正在关阀…")
        Logger.log("直接发送关阀 0x22（第 1 次）")
        updateActionButtons()
        ble.send(0x22)
        opHandler.removeCallbacks(stopAckTimeout)
        opHandler.postDelayed(stopAckTimeout, STOP_ACK_TIMEOUT_MS)
    }

    private fun handleStopTimeout() {
        if (!stopRequestInFlight) return
        if (!connected) {
            stopRequestInFlight = false
            stopAttempts = 0
            setStatusText("连接已断开，无法确认关阀")
            return
        }
        if (stopAttempts < MAX_STOP_ATTEMPTS) {
            stopAttempts++
            setStatusText("关阀响应较慢，正在重试…")
            Logger.log("关阀未回执，重发 0x22（第 " + stopAttempts + " 次）")
            ble.send(0x22)
            opHandler.postDelayed(stopAckTimeout, STOP_ACK_TIMEOUT_MS)
        } else {
            setStatusText("正在核对关阀状态…")
            Logger.log("关阀两次未回执，查询设备 0x23")
            ble.send(0x23)
            opHandler.removeCallbacks(stopVerifyTimeout)
            opHandler.postDelayed(stopVerifyTimeout, STOP_VERIFY_TIMEOUT_MS)
        }
    }

    private fun confirmValveClosed(collect: Boolean) {
        opHandler.removeCallbacks(stopAckTimeout)
        opHandler.removeCallbacks(stopVerifyTimeout)
        stopRequestInFlight = false
        stopAttempts = 0
        deviceState = if (collect) 3 else 0
        if (!collect) {
            settlementInFlight = false
            collectRequested = false
            setStatusText("已关阀")
            if (authInvalidPending) goToLogin("设备已关阀，请重新登录") else loadWallet()
            return
        }
        if (authInvalidPending) {
            settlementInFlight = false
            collectRequested = false
            goToLogin("设备已关阀，请重新登录后完成结算")
            return
        }
        settlementInFlight = true
        setStatusText("已关阀，正在结算…")
        if (!collectRequested) {
            collectRequested = true
            Logger.log("关阀已确认，发送采集 0x85")
            ble.send(0x85)
            opHandler.removeCallbacks(settlementTimeout)
            opHandler.postDelayed(settlementTimeout, SETTLEMENT_DEVICE_TIMEOUT_MS)
        }
        updateActionButtons()
    }

    private fun refreshUser() {
        val u = user ?: return
        if (sessionCheckInFlight || authRedirected) return
        sessionCheckInFlight = true
        Thread {
            try {
                val r: BaseResponse<UserInfo> = Api.userInfo(u)
                if (r.errorCode == 0 && r.data != null) {
                    val refreshed = Session.saveUser(this, r.data)
                    runOnUiThread {
                        user = refreshed
                        renderUser()
                        Logger.log("user/info pid=" + refreshed.projectId + " aid=" + refreshed.accountId)
                        loadWallet()
                        val mac = macInput.text.toString().trim()
                        if (mac.isNotBlank()) loadDeviceInfo(mac)
                    }
                } else {
                    runOnUiThread { Logger.log("user/info 失败 " + r.errorCode + " " + r.errorMessage) }
                }
            } catch (e: Exception) {
                runOnUiThread { Logger.log("user/info 异常 " + e.message) }
            } finally {
                sessionCheckInFlight = false
            }
        }.start()
    }

    private fun validateSessionSilently() {
        val u = user ?: return
        if (sessionCheckInFlight || authRedirected) return
        sessionCheckInFlight = true
        Thread {
            try {
                val r: BaseResponse<UserInfo> = Api.userInfo(u)
                if (r.errorCode == 0 && r.data != null) {
                    val refreshed = Session.saveUser(this, r.data)
                    runOnUiThread {
                        user = refreshed
                        renderUser()
                    }
                }
            } catch (_: Exception) {
                // 静默检查遇到临时网络故障时保留登录态；只响应服务端明确的失效码。
            } finally {
                sessionCheckInFlight = false
            }
        }.start()
    }

    private fun ensurePermissions(): Boolean {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (ble.isEnabled) beginScan()
            else {
                setStatusText("请开启蓝牙")
                try { startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 101) }
                catch (_: Exception) { toast("请先在系统设置中开启蓝牙") }
            }
        } else if (requestCode == 100) {
            setStatusText("需要蓝牙权限")
            toast("允许蓝牙权限后才能扫描洗澡设备")
        }
    }

    @Deprecated("Bluetooth enable result uses the platform activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            if (ble.isEnabled) beginScan() else setStatusText("蓝牙未开启")
            return
        }
        if (requestCode == REQ_PICK_AVATAR && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null && this::settingsAvatar.isInitialized) {
                try {
                    // Local preview only; Session data and upload behavior remain unchanged.
                    settingsAvatar.setImageURI(uri)
                    toast("头像已更新（仅本机显示）")
                } catch (e: Exception) {
                    toast("该图片无法读取")
                }
            }
        }
    }

    private fun loadWallet() {
        val u = user ?: return
        Thread {
            try {
                val r: BaseResponse<WalletData> = Api.wallet(u)
                runOnUiThread {
                    if (r.errorCode == 0 && r.data != null) {
                        val moneyValue = r.data.money?.takeIf { it.isNotBlank() } ?: "--"
                        walletTv.text = if (moneyValue == "--" || moneyValue.startsWith("¥")) moneyValue else "¥$moneyValue"
                        Logger.log("余额 " + r.data.money)
                        renderSettings()
                        refreshGauge()
                        // 余额刚刷新，正是回填本次消费金额的时机（记录在结算收尾时已写入，金额待补）
                        backfillUsageCost()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Logger.log("余额异常 " + e.message) }
            }
        }.start()
    }

    override fun onScanResult(device: BluetoothDevice, rssi: Int, advertisedName: String?) {
        val mac = device.address
        val isNew = !devices.containsKey(mac)
        devices[mac] = device
        deviceRssi[mac] = rssi
        var needRefresh = false
        if (!advertisedName.isNullOrBlank() && advertisedName != deviceBleNames[mac]) {
            deviceBleNames[mac] = advertisedName
            needRefresh = true
        }
        if (isNew) {
            needRefresh = true
            fetchDeviceInfoForList(mac)
        }
        if (needRefresh) refreshDeviceList()
        renderDeviceEntry()
    }

    override fun onStateChanged(state: Int, msg: String) {
        Logger.status(msg)
        when (state) {
            3 -> {
                connected = true
                setStatusText("已连接，正在确认设备状态…")
                Logger.log("发送查询 0x23")
                ble.send(0x23)
            }
            4 -> {
                connected = false
                opHandler.removeCallbacks(startAckTimeout)
                opHandler.removeCallbacks(startVerifyTimeout)
                opHandler.removeCallbacks(stopAckTimeout)
                opHandler.removeCallbacks(stopVerifyTimeout)
                opHandler.removeCallbacks(settlementTimeout)
                collectRequested = false
                if (pendingRateDate != null || isOwnActiveSession() || settlementInFlight) {
                    startRequestInFlight = false
                    verifyingStart = false
                    stopRequestInFlight = false
                    setStatusText("连接中断，请重连原设备核对")
                } else {
                    setStatusText("已断开")
                }
                Logger.status("已断开")
            }
            11 -> setStatusText(
                if (visibleDeviceMacs(devices.keys, deviceInfoCache.keys, deviceBleNames).isEmpty()) {
                    "未发现洗浴设备，请重试"
                } else {
                    "请选择附近洗浴设备"
                }
            )
            else -> setStatusText(msg)
        }
        renderConnection()
    }

    override fun onFrame(cmd: Int, payload: ByteArray) {
        Logger.log("回帧 cmd=0x" + Integer.toHexString(cmd) + " payload=" + FrameUtils.toHex(payload))
        when (cmd) {
            0x23 -> onQueryResp(payload)
            0x21 -> {
                if (payload.isNotEmpty() && (payload[0].toInt() and 0xFF) == 0x80) {
                    if (rollbackInFlight) {
                        Logger.log("回滚过程中收到延迟开阀回执，立即关阀")
                        deviceState = 1
                        deviceAccountId = user?.accountId ?: 0
                        stopRequestInFlight = true
                        stopAttempts = 1
                        setStatusText("检测到延迟开阀，正在立即关闭…")
                        ble.send(0x22)
                        opHandler.postDelayed(stopAckTimeout, STOP_ACK_TIMEOUT_MS)
                    } else {
                        markStartConfirmed()
                    }
                } else {
                    Logger.log("设备拒绝费率，回滚预扣")
                    rollbackPendingStart("设备拒绝启动，正在退回预扣")
                }
            }
            0x22 -> {
                if (payload.isNotEmpty() && (payload[0].toInt() and 0xFF) == 0x80) {
                    Logger.log("设备已确认关阀")
                    confirmValveClosed(collect = true)
                } else if (stopRequestInFlight) {
                    Logger.log("设备拒绝关阀，立即进入重试")
                    opHandler.removeCallbacks(stopAckTimeout)
                    opHandler.post(stopAckTimeout)
                }
            }
            0x85, 0xFB -> onCollectResp(payload)
            0x86, 0xFA -> {
                opHandler.removeCallbacks(settlementTimeout)
                if (payload.isNotEmpty() && (payload[0].toInt() and 0xFF) == 0x80) {
                    Logger.log("存储写回完成")
                    settlementInFlight = false
                    collectRequested = false
                    deviceState = 0
                    deviceAccountId = 0
                    clearPendingRate()
                    // 结算完成：记录本次展示用时，供圆环显示"本次消费"
                    lastSettleDurationMs = elapsedBathingMs()
                    justSettled = true
                    Logger.status("已关阀 · 结算完成")
                    setStatusText("已关阀 · 结算完成")
                    recordUsageEnd()
                    loadWallet()
                    opHandler.postDelayed({ if (connected) ble.send(0x23) }, 500L)
                } else {
                    Logger.log("存储写回失败 payload=" + FrameUtils.toHex(payload))
                    settlementInFlight = false
                    collectRequested = false
                    setStatusText("已关阀，设备记录待重试")
                }
            }
            0xF8, 0x50 -> Logger.log("密钥设置回执")
        }
    }

    override fun onWriteDone(cmd: Int, success: Boolean) {
        if (success) return
        Logger.log("BLE 命令写入失败 cmd=0x" + Integer.toHexString(cmd))
        when (cmd) {
            0x21 -> verifyTimedOutStart()
            0x22 -> if (stopRequestInFlight) {
                opHandler.removeCallbacks(stopAckTimeout)
                opHandler.post(stopAckTimeout)
            }
            0x85, 0x86 -> {
                opHandler.removeCallbacks(settlementTimeout)
                settlementInFlight = false
                collectRequested = false
                setStatusText("已关阀，结算通信失败，请重连重试")
                updateActionButtons()
            }
        }
    }

    /**
     * 活动连接的信号强度更新。
     *
     * 自动重连不经过扫描，扫描缓存里没有 RSSI，此前界面会一直显示「信号未知」；
     * 这里收到实时值后立即刷新两处显示。
     */
    override fun onRssiChanged(rssi: Int) {
        // 不写入 deviceRssi：那是**扫描广播**的缓存，混入 GATT 实时值会让同一台设备
        // 出现两个不同读数。信号统一由 currentRssi() 提供，实时值优先。
        if (!connected) return
        deviceEntryValueTv.text = "已连接 · " + rssi + " dBm"
        if (this::pickerSheet.isInitialized) renderPickerCurrent()
    }

    private fun markStartConfirmed() {
        opHandler.removeCallbacks(startAckTimeout)
        opHandler.removeCallbacks(startVerifyTimeout)
        startRequestInFlight = false
        verifyingStart = false
        rollbackInFlight = false
        justSettled = false
        clearPendingRate()
        deviceState = 1
        deviceAccountId = user?.accountId ?: 0
        startConfirmedAtMs = SystemClock.elapsedRealtime()
        walletAtStartYuan = walletYuan()
        // 新一次用水开始：清掉上一次的收尾状态，否则第二次洗澡不会再写入使用记录
        usageEndedAtMs = 0L
        usageDurationMs = 0L
        opHandler.removeCallbacks(gaugeTicker)
        opHandler.post(gaugeTicker)
        Logger.log("下费率成功，设备开阀供水")
        Logger.status("使用中")
        setStatusText("使用中")
        loadWallet()
    }

    private fun verifyTimedOutStart() {
        if (pendingRateDate == null || verifyingStart || rollbackInFlight) return
        opHandler.removeCallbacks(startAckTimeout)
        if (!connected) {
            startRequestInFlight = false
            setStatusText("连接中断，请重连原设备核对预扣")
            updateActionButtons()
            return
        }
        verifyingStart = true
        setStatusText("启动响应超时，正在核对设备…")
        Logger.log("开阀回执超时，先查询 0x23 再决定是否回滚")
        ble.send(0x23)
        opHandler.removeCallbacks(startVerifyTimeout)
        opHandler.postDelayed(startVerifyTimeout, START_VERIFY_TIMEOUT_MS)
    }

    private fun onQueryResp(p: ByteArray) {
        if (p.size < 31) { Logger.log("应答过短 " + p.size + "B"); return }
        protocolType = String.format("%02X", p[19].toInt() and 0xFF)
        randomNumber = FrameUtils.toHex(p.copyOfRange(24, 28))
        val state = p[28].toInt() and 0xFF
        val accountId = FrameUtils.intAt(p, 9)
        devType = p[29].toInt() and 0xFF
        a1 = p[30].toInt() and 0xFF
        deviceState = state
        deviceAccountId = accountId
        protocolValueTv.text = protocolType
        if (state == 0 && !settlementInFlight && !stopRequestInFlight && pendingRateDate == null) {
            if (!justSettled) startConfirmedAtMs = 0L
            opHandler.removeCallbacks(gaugeTicker)
        }
        Logger.log("协议=" + protocolType + " C1=" + randomNumber + " 状态=" + state +
            " accountId=" + accountId + " type=" + devType + " A1=" + a1)
        Logger.status(stateText(state))

        if (verifyingStart && pendingRateDate != null) {
            verifyingStart = false
            opHandler.removeCallbacks(startVerifyTimeout)
            when {
                state == 1 && isOwnActiveSession() -> markStartConfirmed()
                state == 3 -> {
                    startRequestInFlight = false
                    confirmValveClosed(collect = true)
                }
                else -> rollbackPendingStart("设备确认未开阀，正在退回预扣")
            }
            return
        }

        if (stopRequestInFlight) {
            when (state) {
                0 -> confirmValveClosed(collect = false)
                3 -> confirmValveClosed(collect = true)
                1 -> {
                    opHandler.removeCallbacks(stopVerifyTimeout)
                    stopRequestInFlight = false
                    stopAttempts = 0
                    setStatusText(if (isOwnActiveSession()) "设备仍在供水，请再次点击停止" else "设备账户已变化，已停止操作")
                    updateActionButtons()
                }
                else -> {
                    opHandler.removeCallbacks(stopVerifyTimeout)
                    stopRequestInFlight = false
                    stopAttempts = 0
                    setStatusText(stateText(state))
                    updateActionButtons()
                }
            }
            return
        }

        val samePendingDevice = pendingRateDate != null && pendingRateMac != null &&
            selectedMac?.replace(":", "")?.equals(pendingRateMac?.replace(":", ""), ignoreCase = true) == true
        if (samePendingDevice) {
            when {
                state == 1 && isOwnActiveSession() -> {
                    Logger.log("设备确认上次启动成功，保留有效预扣")
                    markStartConfirmed()
                    return
                }
                state == 3 -> {
                    startRequestInFlight = false
                    confirmValveClosed(collect = true)
                    return
                }
                state != 1 -> {
                    rollbackPendingStart("正在核对并退回上次未完成的预扣")
                    return
                }
                else -> {
                    rollbackPendingStart("设备由其他账户占用，正在退回预扣")
                    return
                }
            }
        }

        when (state) {
            0 -> setStatusText("空闲（可开始）")
            1 -> setStatusText(if (isOwnActiveSession()) "使用中" else "其他账户使用中")
            3 -> {
                if (canSettleLeftoverData()) {
                    confirmValveClosed(collect = true)
                } else {
                    setStatusText("设备有他人遗留数据，已停止操作")
                    Logger.log("状态 3 属于其他账户 accountId=" + deviceAccountId + "，不自动结算")
                    updateActionButtons()
                }
            }
            else -> setStatusText(stateText(state))
        }
        updateActionButtons()
    }

    private fun stateText(s: Int): String = when (s) {
        0 -> "空闲（可开始）"
        1 -> "使用中"
        2 -> "不可中断"
        3 -> "有遗留数据"
        5 -> "受控模式"
        else -> "状态=" + s
    }

    private fun onCollectResp(p: ByteArray) {
        if (p.size < 32) { Logger.log("采集应答过短 " + p.size + "B"); return }
        opHandler.removeCallbacks(settlementTimeout)
        val accountId = FrameUtils.intAt(p, 15)
        var amount = FrameUtils.intAt(p, 28)
        if (amount < 0) amount = -amount
        Logger.log("采集记录 accountId=" + accountId + " 金额(分)=" + amount)
        lastSettleAmountFen = amount
        collectRequested = false
        settlementInFlight = true
        setStatusText("已关阀，正在上传消费记录…")
        uploadData(p)
    }

    private fun doRateOrder(u: UserInfo) {
        val d = device ?: run {
            startRequestInFlight = false
            setStatusText("缺少设备信息，未产生预扣")
            return
        }
        val mac = selectedMac ?: run {
            startRequestInFlight = false
            setStatusText("未选择设备，未产生预扣")
            return
        }
        if (!connected || deviceState != 0 || randomNumber.length != 8) {
            startRequestInFlight = false
            setStatusText("设备状态已变化，未产生预扣")
            updateActionButtons()
            return
        }
        Logger.log("下费率 rateOrder…")
        Thread {
            try {
                val r: BaseResponse<DownRateData> = Api.rateOrder(u, d, devType, a1, protocolType, randomNumber)
                runOnUiThread {
                    if (r.errorCode == 0 && r.data != null) {
                        val down = r.data.downData.orEmpty()
                        val consumeDate = r.data.consumeDate.orEmpty()
                        if (down.isBlank() || consumeDate.isBlank() || FrameUtils.fromHex(down) == null) {
                            startRequestInFlight = false
                            setStatusText("服务器费率数据无效，已停止启动")
                            Logger.log("费率响应缺少 downData 或 consumeDate")
                            updateActionButtons()
                            return@runOnUiThread
                        }
                        if (!connected || selectedMac != mac || deviceState != 0) {
                            // 订单已经创建，但设备状态在网络请求期间发生了变化，必须回滚。
                            persistPendingRate(consumeDate, mac)
                            rollbackPendingStart("设备状态已变化，正在退回预扣")
                            return@runOnUiThread
                        }
                        persistPendingRate(consumeDate, mac)
                        rateYuanPerHour = parsePreMoney(down)
                        Logger.log("下发费率 0x21（已保存预扣回滚凭据）")
                        setStatusText("已创建预扣，等待设备确认开阀…")
                        ble.sendRawHex(0x21, down)
                        opHandler.removeCallbacks(startAckTimeout)
                        opHandler.postDelayed(startAckTimeout, START_ACK_TIMEOUT_MS)
                    } else {
                        startRequestInFlight = false
                        setStatusText("启动订单失败，未产生设备用水")
                        Logger.log("下费率失败: " + r.errorCode + " " + r.errorMessage)
                        updateActionButtons()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    startRequestInFlight = false
                    setStatusText("启动请求失败，未产生设备用水")
                    Logger.log("下费率异常: " + e.message)
                    updateActionButtons()
                }
            }
        }.start()
    }

    private fun uploadData(collectPayload: ByteArray) {
        val u = user ?: return
        val xfData = FrameUtils.toHex(collectPayload)
        Logger.log("上传消费 xfData=" + xfData)
        Thread {
            try {
                val r: BaseResponse<UploadData> = Api.uploadBluetoothData(u, protocolType, randomNumber, xfData)
                runOnUiThread {
                    if (r.errorCode in setOf(0, 206, 209) && r.data != null) {
                        val clData = r.data.clData ?: ""
                        val plain = try { JniUtils.decryptByAES(clData) } catch (e: Exception) { "" }
                        val dataHex = plain.substringAfter("-", "")
                        if (dataHex.isBlank() || FrameUtils.fromHex(dataHex) == null) {
                            finishSettlementLocally("已关阀，已结束本次用水")
                            Logger.log("结算响应无法解密或格式无效")
                            return@runOnUiThread
                        }
                        // 服务器已接收消费数据，此时预扣不再属于“启动失败”订单。
                        clearPendingRate()
                        setStatusText("已关阀，正在完成结算…")
                        Logger.log("写回设备存储 0x86")
                        ble.sendRawHex(0x86, dataHex)
                        opHandler.removeCallbacks(settlementTimeout)
                        opHandler.postDelayed(settlementTimeout, SETTLEMENT_DEVICE_TIMEOUT_MS)
                    } else {
                        Logger.log("上传失败: " + r.errorCode + " " + r.errorMessage + " data=" + r.data)
                        // 计费由服务端按用水量自动完成，消费记录上传只是对账用途。
                        // 上传失败不应让用户卡在“结算失败”，否则余额会一直停在用水中的 0.000。
                        finishSettlementLocally("已关阀，已结束本次用水")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Logger.log("上传异常: " + e.message)
                    finishSettlementLocally("已关阀，已结束本次用水")
                }
            }
        }.start()
    }

    /**
     * 结算的本地收尾：把这次用水当作已结束，并刷新余额。
     *
     * 产品前提：服务端按实际用水量自动扣费，客户端不参与计费。
     * 因此消费记录上传（以及随后的 0x86 写回）只是尽力而为的对账动作，
     * 失败时不能把界面留在“结算失败 / 余额未刷新”的状态——实测用户会以为钱没退，
     * 必须切后台或重开应用才看到真实余额。
     */
    private fun finishSettlementLocally(status: String) {
        opHandler.removeCallbacks(settlementTimeout)
        settlementInFlight = false
        collectRequested = false
        justSettled = true
        lastSettleDurationMs = elapsedBathingMs()
        setStatusText(status)
        updateActionButtons()
        recordUsageEnd()
        loadWallet()
        // 与成功路径一致地刷新一次设备状态：设备若已回到空闲(0)，界面就会停在“已完成”；
        // 若仍报有遗留数据(3)，说明设备侧确实还留着记录，保留 ERROR + 重试入口。
        opHandler.postDelayed({ if (connected) ble.send(0x23) }, 500L)
    }

    override fun onDestroy() {
        opHandler.removeCallbacksAndMessages(null)
        accountRefreshAnim?.cancel()
        accountRefreshAnim = null
        if (::gauge.isInitialized) gauge.setOnGaugeClickListener(null)
        ApiClient.clearAuthInvalidListener(authInvalidListener)
        CmdServer.detach(this)
        if (::ble.isInitialized) {
            ble.stopScan()
            ble.disconnect()
            ble.listener = null
        }
        super.onDestroy()
    }

    companion object {
        private const val APP_VERSION = "1.0.3"

        internal fun resolveGaugeState(
            connected: Boolean,
            rollbackInFlight: Boolean,
            startRequestInFlight: Boolean,
            stopRequestInFlight: Boolean,
            settlementInFlight: Boolean,
            deviceState: Int,
            justSettled: Boolean,
            ownActiveSession: Boolean
        ): BathGaugeView.GaugeState = when {
            !connected -> BathGaugeView.GaugeState.DISCONNECTED
            rollbackInFlight -> BathGaugeView.GaugeState.ERROR
            startRequestInFlight -> BathGaugeView.GaugeState.STARTING
            stopRequestInFlight -> BathGaugeView.GaugeState.STOPPING
            settlementInFlight -> BathGaugeView.GaugeState.STOPPING
            deviceState == 3 -> BathGaugeView.GaugeState.ERROR
            justSettled -> BathGaugeView.GaugeState.SETTLED
            ownActiveSession -> BathGaugeView.GaugeState.BATHING
            else -> BathGaugeView.GaugeState.IDLE
        }

        /**
         * 设备上报的水阀状态 → 「水阀」卡片短文案。抽成纯函数以便单测覆盖。
         *
         * 注意 0/1 的语义：`deviceState==1` 表示设备正被**任意账户**使用（不区分是不是自己），
         * 因此它不能显示成「空闲」。早先 UI 只判断「有没有设备详情」，
         * 导致别人用水、按钮已灰掉时水阀却显示「空闲」。
         */
        internal fun valveStateLabel(deviceState: Int): String = when (deviceState) {
            0 -> "空闲"
            1 -> "使用中"
            2 -> "不可中断"
            3 -> "有遗留数据"
            5 -> "受控模式"
            else -> "--"
        }

        /**
         * 拼接「房间 · 设备名」，避免房间名重复。
         *
         * 实测该接口的 `devName` 本身就可能包含房间名（例如 `roomName="612房"`、
         * `devName="热水表-南铁院-10栋-6层-612房"`），早先无条件拼成
         * 「612房 · 热水表-南铁院-10栋-6层-612房」，同一个房间名出现两次。
         */
        internal fun joinRoomAndName(room: String?, name: String?): String {
            val r = room?.trim().orEmpty()
            val n = name?.trim().orEmpty()
            return when {
                r.isEmpty() -> n
                n.isEmpty() -> r
                n.contains(r) -> n
                else -> r + " · " + n
            }
        }

        /**
         * 手机号打码：138****0000。
         * 仅当长度 > 7 时才打码，避免 7 位时前 3 后 4 发生重叠；
         * 长度不足时原样返回，供 Account 副标题使用。
         */
        internal fun maskPhone(phone: String): String =
            if (phone.length > 7) phone.take(3) + "****" + phone.takeLast(4) else phone

        internal fun visibleDeviceMacs(
            scannedMacs: Collection<String>,
            recognizedMacs: Collection<String>,
            advertisedNames: Map<String, String> = emptyMap(),
            selectedMac: String? = null
        ): List<String> {
            val recognized = recognizedMacs.toHashSet()
            // 已选中的设备在「当前设备」区展示，不应再出现在「附近设备」列表里。
            // 统一转小写比较：设备上报的大小写不保证一致，而 equals(?, ignoreCase) 在
            // selected 为 null 时属于可空扩展，行为不可靠（实测漏排除）。
            val selected = selectedMac?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            return scannedMacs.filter { mac ->
                mac.lowercase() != selected &&
                    (mac in recognized || advertisedNames[mac]?.contains("water", ignoreCase = true) == true)
            }
        }

        private const val SAFETY_PREFS = "bath_safety"
        private const val KEY_PENDING_DATE = "pending_consume_date"
        private const val KEY_PENDING_MAC = "pending_mac"
        private const val REQ_PICK_AVATAR = 202
        private const val START_ACK_TIMEOUT_MS = 6_000L
        private const val START_VERIFY_TIMEOUT_MS = 3_000L
        private const val STOP_ACK_TIMEOUT_MS = 1_600L
        private const val STOP_VERIFY_TIMEOUT_MS = 2_500L
        private const val SETTLEMENT_DEVICE_TIMEOUT_MS = 8_000L
        private const val MAX_STOP_ATTEMPTS = 2
        private const val SESSION_CHECK_INTERVAL_MS = 30_000L
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
