package com.funnyass.test.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从 GitHub Releases 拉取最新版本。
 *
 * 只用标准库 + Gson，不引入 OkHttp/Retrofit 之类依赖。
 */
object UpdateChecker {

    /** 本项目的发布仓库。改这里即可切换更新源。 */
    const val REPO = "LVSUGARS/Funnyass_school-sugars-"

    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val TIMEOUT_MS = 8_000

    /** 更新检查结果。 */
    sealed interface Result {
        /** 有新版本可用。 */
        data class Newer(
            val version: String,
            /** Release 页面地址（浏览器打开）。 */
            val pageUrl: String,
            /** 首选 APK 直链；Release 里没有 .apk 资源时为 null。 */
            val apkUrl: String?
        ) : Result

        /** 已经是最新。 */
        data class UpToDate(val version: String) : Result

        /** 检查失败（网络、限流、无 Release 等），message 供界面直接展示。 */
        data class Failed(val message: String) : Result
    }

    private data class ReleaseDto(
        @SerializedName("tag_name") val tagName: String?,
        @SerializedName("html_url") val htmlUrl: String?,
        @SerializedName("assets") val assets: List<AssetDto>?
    )

    private data class AssetDto(
        @SerializedName("name") val name: String?,
        @SerializedName("browser_download_url") val downloadUrl: String?
    )

    /**
     * 查询最新 Release 并与 [currentVersion] 比较。
     *
     * 该方法会阻塞，调用方必须在工作线程执行。
     */
    fun check(currentVersion: String): Result {
        val conn = try {
            (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RuiZhiXiaoYuan-Android")
            }
        } catch (e: Exception) {
            return Result.Failed("网络不可用")
        }

        return try {
            val code = conn.responseCode
            if (code == 404) {
                // 仓库还没有发布任何 Release
                return Result.Failed("暂未发布版本")
            }
            if (code == 403) {
                return Result.Failed("查询过于频繁，请稍后再试")
            }
            if (code != 200) {
                return Result.Failed("查询失败（HTTP $code）")
            }

            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            val release = Gson().fromJson(body, ReleaseDto::class.java)
            val tag = release?.tagName?.trim().orEmpty()
            if (tag.isEmpty()) return Result.Failed("未获取到版本信息")

            val remote = normalizeVersion(tag)
            val local = normalizeVersion(currentVersion)

            if (compareVersion(remote, local) > 0) {
                val apk = release?.assets
                    ?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }
                    ?.downloadUrl
                Result.Newer(
                    version = remote,
                    pageUrl = release?.htmlUrl ?: "https://github.com/$REPO/releases",
                    apkUrl = apk
                )
            } else {
                Result.UpToDate(local)
            }
        } catch (e: Exception) {
            Result.Failed("检查更新失败")
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    /** 去掉 `v` 前缀和 `release-` 之类修饰，只留版本号本体。 */
    internal fun normalizeVersion(raw: String): String =
        raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('+')
            .substringBefore('-')
            .trim()

    /**
     * 语义化版本比较：返回 >0 表示 [a] 比 [b] 新。
     * 段数不同时缺位按 0 处理（1.0 等于 1.0.0）。非数字段按 0 处理，不抛异常。
     */
    internal fun compareVersion(a: String, b: String): Int {
        val pa = a.split('.')
        val pb = b.split('.')
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val va = pa.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            val vb = pb.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            if (va != vb) return va - vb
        }
        return 0
    }
}
