package com.funnyass.test.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 洗浴使用记录：每次用水结束后追加一条，供设置页「使用记录」展示。
 *
 * 只记录**展示用**的信息，不参与任何计费、校验或状态判断——
 * 计费完全由服务端按用水量完成。
 */
object UsageLog {
    private const val SP = "qzxy_usage_log"
    private const val KEY_RECORDS = "records"

    /** 最多保留多少条，避免无限增长。 */
    private const val MAX_RECORDS = 50

    data class Record(
        /** 结束时间（毫秒时间戳）。 */
        val endedAtMs: Long,
        /** 本次用水时长（毫秒）。 */
        val durationMs: Long,
        /**
         * 本次花费（元）。服务端钱包差值（更可信）。
         * 无法取得时为 null，界面显示 "--"，不编造金额。
         */
        val costYuan: Double?
    )

    private val gson = Gson()

    private fun sp(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Record> = try {
        val json = sp(ctx).getString(KEY_RECORDS, null) ?: return emptyList()
        val type = object : TypeToken<List<Record>>() {}.type
        gson.fromJson<List<Record>>(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** 追加一条记录；新记录在最前（界面按时间倒序展示）。 */
    fun append(ctx: Context, record: Record) {
        val updated = (listOf(record) + list(ctx)).take(MAX_RECORDS)
        sp(ctx).edit().putString(KEY_RECORDS, gson.toJson(updated)).apply()
    }

    /**
     * 给最近一条记录补上花费。
     *
     * 结算完成后才会拿到刷新后的余额，而记录是在结算收尾时就写下的，
     * 所以花费要回填——若已有花费则不覆盖。
     */
    fun backfillLatestCost(ctx: Context, endedAtMs: Long, costYuan: Double?) {
        if (costYuan == null || costYuan < 0) return
        val records = list(ctx).toMutableList()
        val idx = records.indexOfFirst { it.endedAtMs == endedAtMs }
        if (idx < 0 || records[idx].costYuan != null) return
        records[idx] = records[idx].copy(costYuan = costYuan)
        sp(ctx).edit().putString(KEY_RECORDS, gson.toJson(records)).apply()
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().remove(KEY_RECORDS).apply()
    }
}
