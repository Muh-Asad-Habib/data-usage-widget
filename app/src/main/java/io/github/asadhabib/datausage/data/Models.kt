package io.github.asadhabib.datausage.data

enum class Net(private val en: String, private val idn: String) {
    ALL("All", "Semua"), WIFI("Wi-Fi", "Wi-Fi"), MOBILE("Mobile data", "Data seluler");
    val label get() = L.t(en, idn)
}

data class Traffic(val rx: Long = 0, val tx: Long = 0) {
    val total get() = rx + tx
    operator fun plus(o: Traffic) = Traffic(rx + o.rx, tx + o.tx)
}

data class AppUsage(
    val uid: Int,
    val label: String,
    val packageName: String?,
    val wifi: Traffic = Traffic(),
    val mobile: Traffic = Traffic(),
    val foreground: Traffic = Traffic(),
    val background: Traffic = Traffic(),
) {
    val all get() = wifi + mobile
    fun of(net: Net) = when (net) { Net.ALL -> all; Net.WIFI -> wifi; Net.MOBILE -> mobile }
}

data class UsageSummary(
    val wifi: Traffic,
    val mobile: Traffic,
    val apps: List<AppUsage>,
) {
    val all get() = wifi + mobile
    fun of(net: Net) = when (net) { Net.ALL -> all; Net.WIFI -> wifi; Net.MOBILE -> mobile }
    fun appsBy(net: Net) = apps.filter { it.of(net).total > 0 }.sortedByDescending { it.of(net).total }

    companion object { val EMPTY = UsageSummary(Traffic(), Traffic(), emptyList()) }
}

data class DayUsage(val start: Long, val wifi: Long, val mobile: Long) {
    val total get() = wifi + mobile
    fun of(net: Net) = when (net) { Net.ALL -> total; Net.WIFI -> wifi; Net.MOBILE -> mobile }
}
