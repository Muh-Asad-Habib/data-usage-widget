package io.github.asadhabib.datausage.data

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build

data class SsidUsage(val ssid: String?, val bytes: Long)

/**
 * Android does not keep traffic history per Wi-Fi network for regular apps, so we log every
 * network change ourselves. All Wi-Fi bytes between two log points belong to the earlier SSID.
 */
object WifiLog {
    private const val MAX = 3000
    private const val KEY = "log"

    // Android keeps network stats for about 90 days, so older SSID records can't be matched to traffic anyway.
    private const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000

    private fun sp(ctx: Context) = ctx.getSharedPreferences("wifi_log", Context.MODE_PRIVATE)

    fun hasLocation(ctx: Context) = Demo.on(ctx) ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackground(ctx: Context) = Demo.on(ctx) ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun locationOn(ctx: Context): Boolean {
        if (Demo.on(ctx)) return true
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /** Marks a Wi-Fi connection whose name Android hid (app in background without "All the time" location). */
    const val UNKNOWN = ""

    @Suppress("DEPRECATION")
    private fun wifiNetwork(ctx: Context): Network? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        fun wifi(n: Network?) = n != null && cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return cm.activeNetwork.takeIf(::wifi) ?: cm.allNetworks.firstOrNull(::wifi)
    }

    @Suppress("DEPRECATION")
    fun currentSsid(ctx: Context): String? {
        if (!hasLocation(ctx) || wifiNetwork(ctx) == null) return null
        val raw = ctx.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid ?: return null
        val s = raw.removeSurrounding("\"")
        return s.takeUnless { it.isBlank() || it == "<unknown ssid>" || it == "0x" }?.let(::clean)
    }

    // The log is tab/newline separated; SSIDs are attacker-chosen, so strip control characters.
    private fun clean(ssid: String) = ssid.filterNot { it.isISOControl() }.take(64).takeUnless { it.isBlank() }

    /**
     * Log line: "<millis>\t<ssid>\t<network id>". The network id lets a later foreground read
     * name a connection that was only logged as [UNKNOWN] while the app was in the background.
     */
    fun record(ctx: Context, network: Network? = null) {
        val net = if (hasLocation(ctx)) network ?: wifiNetwork(ctx) else null
        val ssid = if (net != null) currentSsid(ctx) else null
        val id = net?.toString().orEmpty()
        synchronized(this) {
            val p = sp(ctx)
            val log = p.getString(KEY, "") ?: ""
            val lines = prune(log.lines().filter { it.isNotBlank() }).toMutableList()
            if (net != null) {
                val last = lines.lastOrNull()?.split('\t')
                val lastSsid = last?.getOrNull(1)
                val lastId = last?.getOrNull(2).orEmpty()
                val now = System.currentTimeMillis()
                when {
                    ssid != null && lastSsid == UNKNOWN && lastId == id -> lines[lines.lastIndex] = "${last[0]}\t$ssid\t$id"
                    ssid != null && lastSsid == ssid -> if (lastId != id) lines[lines.lastIndex] = "${last[0]}\t$ssid\t$id"
                    ssid != null -> lines += "$now\t$ssid\t$id"
                    lastId != id -> lines += "$now\t$UNKNOWN\t$id"
                }
            }
            val out = lines.takeLast(MAX).joinToString("\n")
            if (out != log) p.edit().putString(KEY, out).commit()
        }
    }

    /** Drops records older than [RETENTION_MS], keeping the one still active at the cutoff. */
    private fun prune(lines: List<String>): List<String> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val firstNew = lines.indexOfFirst { (it.substringBefore('\t').toLongOrNull() ?: 0) >= cutoff }
        val keepFrom = if (firstNew < 0) lines.size - 1 else firstNew - 1
        return if (keepFrom > 0) lines.drop(keepFrom) else lines
    }

    private fun entries(ctx: Context): List<Pair<Long, String>> =
        (sp(ctx).getString(KEY, "") ?: "").lines().mapNotNull { l ->
            val i = l.indexOf('\t')
            if (i <= 0) null else l.substring(0, i).toLongOrNull()?.let { it to l.substring(i + 1).substringBefore('\t') }
        }

    fun isEmpty(ctx: Context) = sp(ctx).getString(KEY, "").isNullOrEmpty()

    fun since(ctx: Context): Long? = if (Demo.on(ctx)) Demo.since() else entries(ctx).firstOrNull()?.first

    /** Wi-Fi usage per SSID in [r]. A null SSID means traffic from before logging started. */
    fun usage(ctx: Context, repo: UsageRepository, r: Range): List<SsidUsage> {
        if (Demo.on(ctx)) return Demo.ssids(r)
        val e = entries(ctx)
        val map = LinkedHashMap<String?, Long>()
        val first = e.firstOrNull()?.first ?: r.end
        if (r.start < first) {
            val b = repo.device(ConnectivityManager.TYPE_WIFI, r.start, minOf(first, r.end)).total
            if (b > 0) map[null] = b
        }
        e.forEachIndexed { i, (t, ssid) ->
            val s = maxOf(t, r.start)
            val end = minOf(e.getOrNull(i + 1)?.first ?: r.end, r.end)
            if (end > s) {
                val b = repo.device(ConnectivityManager.TYPE_WIFI, s, end).total
                if (b > 0) map[ssid] = (map[ssid] ?: 0) + b
            }
        }
        return map.map { SsidUsage(it.key, it.value) }.sortedByDescending { it.bytes }
    }

    private const val JOB_ID = 7301
    private const val JOB_PERIOD_MS = 15L * 60 * 1000

    /**
     * Checks the Wi-Fi network every 15 minutes, even if the app is not running. A PendingIntent
     * network callback can't be used: Android fires it at once for the current network and then
     * drops the registration, so later connections would never be seen.
     */
    fun watch(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, Job::class.java))
            .setPeriodic(JOB_PERIOD_MS)
            .setPersisted(true)
            .build()
        try { js.schedule(job) } catch (_: Exception) {}
    }

    class Job : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {
            Thread {
                try { record(applicationContext) } finally { jobFinished(params, false) }
            }.start()
            return true
        }

        override fun onStopJob(params: JobParameters) = true
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                if (hasLocation(ctx)) watch(ctx)
            }
        }
    }
}
