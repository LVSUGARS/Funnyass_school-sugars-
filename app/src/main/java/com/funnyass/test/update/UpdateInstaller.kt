package com.funnyass.test.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.funnyass.test.Logger
import java.io.File

/**
 * 应用内下载并安装更新包。
 *
 * 为什么不用浏览器：把 APK 链接丢给浏览器后，Android 10+ 要求**浏览器自己**持有
 * 「安装未知应用」权限，很多浏览器没有该权限，表现就是下载到 100% 后卡住、无法安装。
 * 改成应用内下载 + 应用内安装后，权限只需要授予本应用，用户的成功率高得多。
 */
object UpdateInstaller {

    /** 下载目录：cacheDir/updates（与 res/xml/file_paths.xml 中的声明对应）。 */
    private const val DIR_NAME = "updates"

    /** 进度回报。除 [Failed] 外都会在主线程回调。 */
    interface Listener {
        /** 正在下载，progress 为 0..100；总量未知时为 -1。 */
        fun onProgress(progress: Int)

        /** 下载完成，即将（或已经）拉起系统安装器。 */
        fun onReadyToInstall()

        /** 失败，message 可直接展示给用户。 */
        fun onFailed(message: String)
    }

    private var downloadId: Long = -1L
    private var polling = false

    fun cancel() {
        polling = false
        downloadId = -1L
    }

    /**
     * 下载 [apkUrl] 并在完成后拉起系统安装器。
     *
     * @param positiveButtonText 通知栏上的按钮文案，可不传
     */
    fun start(
        context: Context,
        apkUrl: String,
        version: String,
        listener: Listener,
        positiveButtonText: CharSequence = "安装"
    ) {
        val appContext = context.applicationContext
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            listener.onFailed("系统下载服务不可用")
            return
        }

        val dir = File(appContext.cacheDir, DIR_NAME).apply { mkdirs() }
        // 文件名带版本号，避免与旧包混淆
        val target = File(dir, apkFileName(version))
        if (target.exists() && !target.delete()) {
            Logger.log("更新包旧文件删除失败: " + target.absolutePath)
        }

        val request = try {
            DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("睿智校园 $version")
                setDescription("正在下载更新…")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                // 注意：DownloadManager 不接受应用内部目录作为目标，
                // 只能写外部私有目录（getExternalFilesDir），再经 FileProvider 暴露给安装器。
                setDestinationInExternalFilesDir(
                    appContext,
                    Environment.DIRECTORY_DOWNLOADS,
                    apkFileName(version)
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
        } catch (e: Exception) {
            Logger.log("构造下载请求失败: " + e.message)
            listener.onFailed("无法开始下载")
            return
        }

        downloadId = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            Logger.log("enqueue 下载失败: " + e.message)
            listener.onFailed("无法开始下载")
            return
        }
        Logger.log("开始下载更新 id=" + downloadId + " url=" + apkUrl)

        pollUntilDone(appContext, dm, version, listener, positiveButtonText)
    }

    /** 解析出的 APK 文件名，例如 `睿智校园-1.0.6.apk`。 */
    private fun apkFileName(version: String) = "睿智校园-$version.apk"

    /**
     * 轮询下载状态。
     *
     * 刻意不用 `ACTION_DOWNLOAD_COMPLETE` 广播：Android 13+ 注册系统广播需要指定
     * `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED`，写错会在运行时报错；
     * 轮询只有几行，且不依赖动态接收器的注册规则。
     */
    private fun pollUntilDone(
        context: Context,
        dm: DownloadManager,
        version: String,
        listener: Listener,
        positiveButtonText: CharSequence
    ) {
        polling = true
        val id = downloadId
        Thread {
            val query = DownloadManager.Query().setFilterById(id)
            var lastProgress = -2
            while (polling) {
                val snapshot = try {
                    readStatus(dm, query)
                } catch (e: Exception) {
                    Logger.log("查询下载状态失败: " + e.message)
                    if (polling) listener.onFailed("下载状态查询失败")
                    return@Thread
                }
                if (snapshot == null) {
                    // 记录暂时读不到（例如已被系统清理），稍后重试
                    Thread.sleep(400L)
                    continue
                }

                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Logger.log("更新包下载完成")
                        if (!polling) return@Thread
                        listener.onProgress(100)
                        listener.onReadyToInstall()
                        return@Thread
                    }
                    DownloadManager.STATUS_FAILED -> {
                        Logger.log("更新包下载失败 reason=" + snapshot.reason)
                        if (polling) listener.onFailed("下载失败（原因码 " + snapshot.reason + "）")
                        return@Thread
                    }
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> {
                        val progress = if (snapshot.total > 0) {
                            ((snapshot.downloaded * 100) / snapshot.total).toInt()
                        } else {
                            -1
                        }
                        if (progress != lastProgress) {
                            lastProgress = progress
                            if (polling) listener.onProgress(progress)
                        }
                    }
                }
                Thread.sleep(400L)
            }
        }.start()
    }

    /** 一次下载状态快照。 */
    private class Snapshot(
        val status: Int,
        val downloaded: Long,
        val total: Long,
        val reason: Int
    )

    /** 读一次下载状态；记录不存在时返回 null。 */
    private fun readStatus(dm: DownloadManager, query: DownloadManager.Query): Snapshot? =
        dm.query(query)?.use { c ->
            if (!c.moveToFirst()) return null
            Snapshot(
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloaded = c.getLong(
                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                ),
                total = c.getLong(
                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                ),
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            )
        }

    /** 本应用是否已被允许安装未知来源应用。 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 跳到「安装未知应用」授权页，让用户为本应用开启权限。 */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Logger.log("打开安装权限设置失败: " + e.message)
        }
    }

    /**
     * 拉起系统安装器。
     *
     * 先检查本应用的「安装未知应用」权限：没有权限时系统安装器可能静默失败，
     * 这里改为直接引导用户去授权，避免又出现「点了没反应」。
     */
    fun install(context: Context, version: String): Boolean {
        if (!canInstall(context)) {
            Logger.log("缺少安装未知应用权限，跳转设置")
            openInstallPermissionSettings(context)
            return false
        }

        val file = downloadedApk(context, version)
        if (file == null || !file.exists()) {
            Logger.log("更新包不存在")
            return false
        }

        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Logger.log("已拉起系统安装器")
            true
        } catch (e: Exception) {
            Logger.log("拉起安装器失败: " + e.message)
            false
        }
    }

    /**
     * 下载好的 APK 文件。
     *
     * DownloadManager 写的是外部私有目录，但个别 ROM 也可能落到 cache，
     * 因此两处都找一下。
     */
    private fun downloadedApk(context: Context, version: String): File? {
        val name = apkFileName(version)
        val external = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name)
        if (external.exists()) return external
        val cached = File(File(context.cacheDir, DIR_NAME), name)
        return if (cached.exists()) cached else external
    }
}
