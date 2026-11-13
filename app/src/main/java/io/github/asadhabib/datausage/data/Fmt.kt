package io.github.asadhabib.datausage.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

object Fmt {
    private val sym = DecimalFormatSymbols(Periods.locale)
    private val one = DecimalFormat("0.#", sym)
    private val two = DecimalFormat("0.##", sym)

    fun bytes(b: Long): String {
        val v = b.toDouble()
        return when {
            b < 1000 -> "$b B"
            v < 1e6 -> "${one.format(v / 1e3)} KB"
            v < 1e9 -> "${one.format(v / 1e6)} MB"
            v < 1e12 -> "${two.format(v / 1e9)} GB"
            else -> "${two.format(v / 1e12)} TB"
        }
    }

    fun percent(part: Long, total: Long) = if (total <= 0) "0%" else "${(part * 100 / total)}%"
}
