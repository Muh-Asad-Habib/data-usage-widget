package io.github.asadhabib.datausage.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.TelephonyManager
import java.time.LocalDate

class UsageRepository(context: Context) {
    private val ctx = context.applicationContext
    private val nsm = ctx.getSystemService(NetworkStatsManager::class.java)

    private val types = listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)

    // Android 9 and older need the SIM subscriber id for mobile stats; Android 10+ accepts null.
    @Suppress("DEPRECATION", "MissingPermission")
    private val subscriber: String? get() = run {
        if (Build.VERSION.SDK_INT >= 29) null
        else try { ctx.getSystemService(TelephonyManager::class.java)?.subscriberId } catch (_: Exception) { null }
    }

    private fun sub(type: Int) = if (type == ConnectivityManager.TYPE_MOBILE) subscriber else null

    fun device(type: Int, start: Long, end: Long): Traffic = try {
        val b = nsm.querySummaryForDevice(type, sub(type), start, end)
        Traffic(b.rxBytes, b.txBytes)
    } catch (_: Exception) { Traffic() }

    fun summary(r: Range): UsageSummary {
        if (r.end <= r.start) return UsageSummary.EMPTY
        data class Acc(var wifi: Traffic = Traffic(), var mobile: Traffic = Traffic(), var fg: Traffic = Traffic(), var bg: Traffic = Traffic())
        val map = HashMap<Int, Acc>()
        for (type in types) {
            val stats = try { nsm.querySummary(type, sub(type), r.start, r.end) } catch (_: Exception) { null } ?: continue
            val b = NetworkStats.Bucket()
            try {
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    val t = Traffic(b.rxBytes, b.txBytes)
                    if (t.total == 0L) continue
                    val a = map.getOrPut(b.uid) { Acc() }
                    if (type == ConnectivityManager.TYPE_WIFI) a.wifi += t else a.mobile += t
                    if (b.state == NetworkStats.Bucket.STATE_FOREGROUND) a.fg += t else a.bg += t
                }
            } finally { stats.close() }
        }
        val apps = map.map { (uid, a) ->
            val (label, pkg) = AppNames.resolve(ctx, uid)
            AppUsage(uid, label, pkg, a.wifi, a.mobile, a.fg, a.bg)
        }
        val wifi = device(ConnectivityManager.TYPE_WIFI, r.start, r.end)
        val mobile = device(ConnectivityManager.TYPE_MOBILE, r.start, r.end)
        return UsageSummary(
            wifi = if (wifi.total > 0) wifi else apps.fold(Traffic()) { s, x -> s + x.wifi },
            mobile = if (mobile.total > 0) mobile else apps.fold(Traffic()) { s, x -> s + x.mobile },
            apps = apps.sortedByDescending { it.all.total },
        )
    }

    /** Daily totals for the device (uid == null) or one app. */
    fun daily(days: Int, uid: Int? = null, endDay: LocalDate = LocalDate.now(Periods.zone)): List<DayUsage> {
        val first = endDay.minusDays((days - 1).toLong())
        val starts = (0..days).map { Periods.startOf(first.plusDays(it.toLong())) }
        if (uid == null) {
            return (0 until days).map { i ->
                DayUsage(
                    starts[i],
                    device(ConnectivityManager.TYPE_WIFI, starts[i], starts[i + 1]).total,
                    device(ConnectivityManager.TYPE_MOBILE, starts[i], starts[i + 1]).total,
                )
            }
        }
        val wifi = LongArray(days)
        val mobile = LongArray(days)
        for (type in types) {
            val stats = try {
                nsm.queryDetailsForUid(type, sub(type), starts.first(), starts.last(), uid)
            } catch (_: Exception) { null } ?: continue
            val b = NetworkStats.Bucket()
            try {
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    val mid = (b.startTimeStamp + b.endTimeStamp) / 2
                    val i = starts.indexOfLast { it <= mid }.coerceIn(0, days - 1)
                    val v = b.rxBytes + b.txBytes
                    if (type == ConnectivityManager.TYPE_WIFI) wifi[i] += v else mobile[i] += v
                }
            } finally { stats.close() }
        }
        return (0 until days).map { DayUsage(starts[it], wifi[it], mobile[it]) }
    }

    /** 7x24 matrix (day, hour) of device bytes starting at [weekStart]. */
    fun hourly(weekStart: LocalDate, net: Net): Array<LongArray> {
        val out = Array(7) { LongArray(24) }
        val now = System.currentTimeMillis()
        for (d in 0 until 7) {
            val base = Periods.startOf(weekStart.plusDays(d.toLong()))
            for (h in 0 until 24) {
                val s = base + h * 3_600_000L
                if (s >= now) return out
                val e = s + 3_600_000L
                out[d][h] = when (net) {
                    Net.WIFI -> device(ConnectivityManager.TYPE_WIFI, s, e).total
                    Net.MOBILE -> device(ConnectivityManager.TYPE_MOBILE, s, e).total
                    Net.ALL -> device(ConnectivityManager.TYPE_WIFI, s, e).total + device(ConnectivityManager.TYPE_MOBILE, s, e).total
                }
            }
        }
        return out
    }
}
