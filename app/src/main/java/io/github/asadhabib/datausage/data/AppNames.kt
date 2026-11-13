package io.github.asadhabib.datausage.data

import android.app.usage.NetworkStats
import android.content.Context
import android.graphics.drawable.Drawable

object AppNames {
    private val cache = HashMap<Int, Pair<String, String?>>()

    @Synchronized
    fun resolve(ctx: Context, uid: Int): Pair<String, String?> = cache.getOrPut(uid) {
        val pm = ctx.packageManager
        when (uid) {
            NetworkStats.Bucket.UID_REMOVED -> L.t("Removed apps", "Aplikasi yang sudah dihapus") to null
            NetworkStats.Bucket.UID_TETHERING -> "Hotspot & tethering" to null
            0 -> L.t("System (root)", "Sistem (root)") to null
            1000 -> L.t("Android System", "Sistem Android") to "android"
            else -> {
                val pkgs = pm.getPackagesForUid(uid).orEmpty()
                val main = pkgs.firstOrNull { p ->
                    try { pm.getLaunchIntentForPackage(p) != null } catch (_: Exception) { false }
                } ?: pkgs.firstOrNull()
                if (main == null) {
                    (system(uid) ?: pm.getNameForUid(uid) ?: "UID $uid") to null
                } else {
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(main, 0)).toString()
                    } catch (_: Exception) { main }
                    (if (pkgs.size > 1) "$label +${pkgs.size - 1}" else label) to main
                }
            }
        }
    }

    /** Android reserved UIDs (AOSP android_filesystem_config.h) that have no package. */
    private fun system(uid: Int): String? = when (uid) {
        1001 -> L.t("Phone service", "Layanan telepon")
        1002 -> "Bluetooth"
        1010 -> L.t("Wi-Fi service", "Layanan Wi-Fi")
        1013 -> L.t("Media server", "Server media")
        1016 -> "VPN"
        1019, 1031 -> L.t("DRM (protected media)", "DRM (media terlindungi)")
        1020 -> L.t("Network discovery (mDNS)", "Penemuan jaringan (mDNS)")
        1021 -> "GPS"
        1029 -> L.t("Mobile network (CLAT)", "Jaringan seluler (CLAT)")
        1051, 1052 -> L.t("DNS resolver", "Resolver DNS")
        1061 -> L.t("System updates (OTA)", "Pembaruan sistem (OTA)")
        1066 -> L.t("Statistics service", "Layanan statistik")
        1073 -> L.t("Network stack", "Stack jaringan")
        else -> if (uid in 1001..9999) L.t("System service", "Layanan sistem") + " ($uid)" else null
    }

    fun icon(ctx: Context, pkg: String?): Drawable? = try {
        if (pkg == null) null else ctx.packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) { if (Demo.on(ctx)) Demo.icon(ctx, pkg) else null }
}
