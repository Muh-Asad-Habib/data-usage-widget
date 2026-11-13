package io.github.asadhabib.datausage.data

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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

    @Suppress("DEPRECATION")
    fun currentSsid(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        if (!hasLocation(ctx)) return null
        val raw = ctx.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid ?: return null
        val s = raw.removeSurrounding("\"")
        return s.takeUnless { it.isBlank() || it == "<unknown ssid>" || it == "0x" }
    }

    fun record(ctx: Context) {
        val ssid = currentSsid(ctx) ?: return
        synchronized(this) {
            val p = sp(ctx)
            val log = p.getString(KEY, "") ?: ""
            val last = log.substringAfterLast('\n').substringAfter('\t', "")
            if (last == ssid) return
            val lines = (log.lines().filter { it.isNotBlank() } + "${System.currentTimeMillis()}\t$ssid").takeLast(MAX)
            p.edit().putString(KEY, lines.joinToString("\n")).apply()
        }
    }

    private fun entries(ctx: Context): List<Pair<Long, String>> =
        (sp(ctx).getString(KEY, "") ?: "").lines().mapNotNull { l ->
            val i = l.indexOf('\t')
            if (i <= 0) null else l.substring(0, i).toLongOrNull()?.let { it to l.substring(i + 1) }
        }

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

    /** Wakes [Receiver] whenever a Wi-Fi network connects, even if the app is not running. */
    fun watch(ctx: Context) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, Receiver::class.java), flags)
        try { cm.registerNetworkCallback(req, pi) } catch (_: Exception) {}
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) watch(ctx)
            val pending = goAsync()
            Thread {
                try {
                    // The SSID may not be readable right when the network appears.
                    repeat(3) { if (currentSsid(ctx) == null) Thread.sleep(1500) }
                    record(ctx)
                } finally { pending.finish() }
            }.start()
        }
    }
}
