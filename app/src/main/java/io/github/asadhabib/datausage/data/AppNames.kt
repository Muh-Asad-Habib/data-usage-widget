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
                    (pm.getNameForUid(uid) ?: "UID $uid") to null
                } else {
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(main, 0)).toString()
                    } catch (_: Exception) { main }
                    (if (pkgs.size > 1) "$label +${pkgs.size - 1}" else label) to main
                }
            }
        }
    }

    fun icon(ctx: Context, pkg: String?): Drawable? = try {
        if (pkg == null) null else ctx.packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) { null }
}
