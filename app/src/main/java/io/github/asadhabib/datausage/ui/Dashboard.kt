package io.github.asadhabib.datausage.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.asadhabib.datausage.data.Fmt
import io.github.asadhabib.datausage.data.L
import io.github.asadhabib.datausage.data.Net
import io.github.asadhabib.datausage.data.Periods
import io.github.asadhabib.datausage.data.Range
import io.github.asadhabib.datausage.R
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.SelectableDates
import java.time.Instant
import java.time.ZoneOffset
import io.github.asadhabib.datausage.data.UsageSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private const val WEEKS = 104

private fun monday(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now(Periods.zone)
    return when (d) {
        today -> L.t("Today", "Hari ini")
        today.minusDays(1) -> L.t("Yesterday", "Kemarin")
        else -> DateTimeFormatter.ofPattern(if (d.year == today.year) "EEEE, d MMMM" else "d MMMM yyyy", Periods.locale)
            .format(d).replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun SpanRow(label: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = if (value == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (value == null) FontWeight.SemiBold else FontWeight.Normal)
        if (value != null) Text(value, fontWeight = FontWeight.SemiBold)
        Ico(R.drawable.ic_chevron, MaterialTheme.colorScheme.onSurfaceVariant, 18.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePicker(onDismiss: () -> Unit, onPick: (LocalDate, LocalDate) -> Unit) {
    val todayUtc = LocalDate.now(Periods.zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val st = rememberDateRangePickerState(selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
    })
    fun d(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                val s = st.selectedStartDateMillis ?: return@TextButton
                onPick(d(s), d(st.selectedEndDateMillis ?: s))
            }, enabled = st.selectedStartDateMillis != null) { Text("OK") }
        },
        dismissButton = { TextButton(onDismiss) { Text(L.t("Cancel", "Batal")) } },
    ) { DateRangePicker(st, Modifier.weight(1f)) }
}

@Composable
fun SpanScreen(vm: MainViewModel, s: Screen.Span) {
    var showAll by remember { mutableStateOf(false) }
    val sum = vm.spanSummary
    Page(s.title, onBack = { vm.back() }) {
        item { HeroCard(s.title, sum, vm.spanNet, vm.spanLoading, null) }
        if (vm.spanLoading) return@Page
        item { Tiles(sum, vm.spanNet) }
        if (vm.spanNet != Net.MOBILE) item { SsidCard(vm, vm.spanSsids) }
        item { NetSelector(vm.spanNet) { vm.spanNet = it } }
        appList(sum, vm.spanNet, showAll, { showAll = !showAll }) { vm.open(Screen.Detail(it, s.title)) }
        item { BackgroundWatch(sum) { vm.open(Screen.Detail(it, s.title)) } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(vm: MainViewModel) {
    val day = vm.dashDay
    val week = vm.dashWeek?.takeIf { it.start == monday(day) }
    val idx = day.dayOfWeek.value - 1
    val label = dayLabel(day)
    val last = minOf(6, ChronoUnit.DAYS.between(monday(day), LocalDate.now(Periods.zone)).toInt())
    var picker by remember { mutableStateOf(false) }
    if (picker) RangePicker({ picker = false }) { s, e ->
        picker = false
        val title = if (s == e) dayLabel(s) else "${DateTimeFormatter.ofPattern("d MMM", Periods.locale).format(s)} – ${DateTimeFormatter.ofPattern("d MMM yyyy", Periods.locale).format(e)}"
        vm.openSpan(title, Range(Periods.startOf(s), minOf(Periods.startOf(e.plusDays(1)), System.currentTimeMillis())))
    }
    Page(L.t("Dashboard", "Dasbor"), onBack = { vm.back() }) {
        stickyHeader { DateStrip(day) { vm.selectDay(it) } }
        item { Text(label, Modifier.padding(start = 6.dp, top = 6.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (week == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))) }
            return@Page
        }
        val open = { net: Net, title: String -> vm.open(Screen.Apps("$title · $label", week.days[idx], net)) }
        item { UsageCard(L.t("Total", "Total"), Net.ALL, week.days, idx, last, { vm.selectDay(week.start.plusDays(it.toLong())) }, open) }
        item { UsageCard("Wi-Fi", Net.WIFI, week.days, idx, last, { vm.selectDay(week.start.plusDays(it.toLong())) }, open) }
        item { UsageCard(L.t("Mobile data", "Data seluler"), Net.MOBILE, week.days, idx, last, { vm.selectDay(week.start.plusDays(it.toLong())) }, open) }
        if (vm.totals.isNotEmpty()) item {
            SCard {
                Text(L.t("Totals", "Ringkasan"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                vm.totals.forEach { (p, v) ->
                    SpanRow(p.label, Fmt.bytes(v)) { vm.openSpan(p.label, Periods.range(p)) }
                }
                SpanRow(L.t("Pick dates", "Pilih tanggal"), null) { picker = true }
            }
        }
    }
}

@Composable
private fun DateStrip(day: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now(Periods.zone)
    val thisWeek = monday(today)
    fun pageOf(d: LocalDate) = ChronoUnit.WEEKS.between(monday(d), thisWeek).toInt().coerceIn(0, WEEKS - 1)
    val pager = rememberPagerState(pageOf(day)) { WEEKS }
    LaunchedEffect(pager.settledPage) {
        val start = thisWeek.minusWeeks(pager.settledPage.toLong())
        if (monday(day) != start) onPick(start.plusDays((day.dayOfWeek.value - 1).toLong()))
    }
    LaunchedEffect(day) { if (pager.currentPage != pageOf(day)) pager.animateScrollToPage(pageOf(day)) }
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).shadow(6.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(cs.surface)) {
        HorizontalPager(pager, reverseLayout = true) { page ->
            val start = thisWeek.minusWeeks(page.toLong())
            Row(Modifier.fillMaxWidth().padding(6.dp)) {
                (0..6).forEach { i ->
                    val d = start.plusDays(i.toLong())
                    val future = d.isAfter(today)
                    val sel = d == day
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                            .background(if (sel) cs.surfaceVariant else Color.Transparent)
                            .clickable(enabled = !future) { onPick(d) }
                            .padding(vertical = 8.dp).alpha(if (future) .35f else 1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Periods.locale), fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text("${d.dayOfMonth}", fontSize = 17.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageCard(
    title: String, net: Net, days: List<UsageSummary>, idx: Int, last: Int,
    onDay: (Int) -> Unit, onSeeAll: (Net, String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val s = days[idx]
    val total = s.of(net).total
    val top = s.appsBy(net).take(3)
    SCard(Modifier.animateContentSize()) {
        Text(title, fontSize = 15.sp, color = cs.onSurfaceVariant)
        Text(Fmt.bytes(total), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        StackedWeek(days, net, top.map { it.uid }, idx, last, onDay)
        Spacer(Modifier.height(10.dp))
        top.forEachIndexed { i, a -> Legend(ChartColors[i], a.label, Fmt.bytes(a.of(net).total)) }
        val rest = total - top.sumOf { it.of(net).total }
        if (rest > 0) Legend(OtherColor, L.t("Other", "Lainnya"), Fmt.bytes(rest))
        if (top.isEmpty()) Text(L.t("No usage on this day.", "Tidak ada pemakaian di hari ini."), fontSize = 13.sp, color = cs.onSurfaceVariant)
        else Text(
            L.t("See all", "Lihat semua"),
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(50)).clickable { onSeeAll(net, title) }.padding(vertical = 12.dp),
            color = cs.primary, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StackedWeek(days: List<UsageSummary>, net: Net, uids: List<Int>, idx: Int, last: Int, onDay: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val totals = days.map { it.of(net).total }
    val max = (totals.maxOrNull() ?: 0L).coerceAtLeast(1)
    val track = cs.surfaceVariant
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).pointerInput(days) {
            detectTapGestures { o -> onDay((o.x / (size.width / 7f)).toInt().coerceIn(0, last)) }
        },
    ) {
        val slot = size.width / 7
        val bw = slot * .42f
        days.forEachIndexed { i, d ->
            val x = i * slot + (slot - bw) / 2
            val h = size.height * totals[i] / max
            val r = CornerRadius(bw / 2)
            if (i > last) return@forEachIndexed
            if (h < bw) { drawRoundRect(track, Offset(x, size.height - bw), Size(bw, bw), r); return@forEachIndexed }
            val path = Path().apply { addRoundRect(RoundRect(x, size.height - h, x + bw, size.height, r)) }
            clipPath(path) {
                var y = size.height
                uids.forEachIndexed { c, uid ->
                    val v = d.apps.firstOrNull { it.uid == uid }?.of(net)?.total ?: 0L
                    val sh = size.height * v / max
                    drawRect(ChartColors[c], Offset(x, y - sh), Size(bw, sh)); y -= sh
                }
                drawRect(OtherColor, Offset(x, size.height - h), Size(bw, y - (size.height - h)))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        days.indices.forEach { i ->
            val sel = i == idx
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(if (sel) cs.onSurface else Color.Transparent).clickable(enabled = i <= last) { onDay(i) }.alpha(if (i > last) .35f else 1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(DayOfWeek.of(i + 1).getDisplayName(TextStyle.NARROW, Periods.locale), fontSize = 12.sp,
                        color = if (sel) cs.surface else cs.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun AppsScreen(vm: MainViewModel, s: Screen.Apps) {
    var all by remember { mutableStateOf(true) }
    Page(s.title, onBack = { vm.back() }) {
        appList(s.summary, s.net, all, { all = !all }) { vm.open(Screen.Detail(it, s.title.substringAfter(" · "))) }
    }
}