package io.github.asadhabib.datausage.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Period(val label: String) {
    TODAY("Hari ini"),
    YESTERDAY("Kemarin"),
    LAST_7("7 hari"),
    THIS_MONTH("Bulan ini"),
    LAST_MONTH("Bulan lalu"),
    CUSTOM("Rentang"),
}

data class Range(val start: Long, val end: Long)

object Periods {
    val zone: ZoneId get() = ZoneId.systemDefault()
    val locale = Locale.forLanguageTag("id-ID")

    fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun range(p: Period, customStart: Long = 0, customEnd: Long = 0, now: Long = System.currentTimeMillis()): Range {
        val today = LocalDate.now(zone)
        return when (p) {
            Period.TODAY -> Range(startOf(today), now)
            Period.YESTERDAY -> Range(startOf(today.minusDays(1)), startOf(today))
            Period.LAST_7 -> Range(startOf(today.minusDays(6)), now)
            Period.THIS_MONTH -> Range(startOf(today.withDayOfMonth(1)), now)
            Period.LAST_MONTH -> {
                val first = today.withDayOfMonth(1)
                Range(startOf(first.minusMonths(1)), startOf(first))
            }
            Period.CUSTOM -> {
                if (customStart <= 0 || customEnd <= 0) Range(startOf(today), now)
                else Range(customStart, minOf(customEnd, now))
            }
        }
    }

    private val dm = DateTimeFormatter.ofPattern("d MMM", locale)
    private val my = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    fun describe(p: Period, r: Range): String {
        val s = java.time.Instant.ofEpochMilli(r.start).atZone(zone).toLocalDate()
        val e = java.time.Instant.ofEpochMilli(r.end - 1).atZone(zone).toLocalDate()
        return when (p) {
            Period.TODAY, Period.YESTERDAY -> "${p.label}, ${dm.format(s)}"
            Period.LAST_MONTH, Period.THIS_MONTH -> my.format(s).replaceFirstChar { it.uppercase() }
            else -> if (s == e) dm.format(s) else "${dm.format(s)} – ${dm.format(e)}"
        }
    }
}
