package io.github.asadhabib.datausage.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.asadhabib.datausage.R
import io.github.asadhabib.datausage.data.AppUsage
import io.github.asadhabib.datausage.data.DayUsage
import io.github.asadhabib.datausage.data.Fmt
import io.github.asadhabib.datausage.data.Net
import io.github.asadhabib.datausage.data.Period
import io.github.asadhabib.datausage.data.Periods
import io.github.asadhabib.datausage.data.Range
import io.github.asadhabib.datausage.data.UsageAccess
import io.github.asadhabib.datausage.data.UsageSummary
import io.github.asadhabib.datausage.widget.UsageWidgetProvider
import io.github.asadhabib.datausage.widget.WidgetConfigActivity
import io.github.asadhabib.datausage.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private val WifiColor = ChartColors[0]
private val MobileColor = ChartColors[1]
private val dayFmt = DateTimeFormatter.ofPattern("EEE", Periods.locale)
private val dmFmt = DateTimeFormatter.ofPattern("d MMM", Periods.locale)

@Composable
private fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onBack) { Ico(R.drawable.ic_back) } else Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                actions()
            }
        }
        content()
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

private fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(Periods.zone).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    var picker by remember { mutableStateOf(false) }
    Page("Pantau Data", actions = {
        IconButton({ vm.open(Screen.Report) }) { Ico(R.drawable.ic_chart) }
        IconButton({ vm.open(Screen.Widgets) }) { Ico(R.drawable.ic_widgets) }
    }) {
        if (!vm.granted) {
            item { PermissionCard() }
            return@Page
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Period.entries) { p ->
                    FilterChip(
                        selected = vm.period == p,
                        onClick = { if (p == Period.CUSTOM) picker = true else vm.select(p) },
                        label = { Text(p.label) },
                        leadingIcon = if (p == Period.CUSTOM) ({ Ico(R.drawable.ic_calendar, size = 16.dp) }) else null,
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                }
            }
        }
        item { HeroCard(Periods.describe(vm.period, vm.range), vm.summary, vm.net, vm.loading) }
        item { Tiles(vm.summary, vm.net) }
        item { NetSelector(vm.net) { vm.net = it } }
        appList(vm.summary, vm.net) { vm.open(Screen.Detail(it)) }
        item { BackgroundWatch(vm.summary) { vm.open(Screen.Detail(it)) } }
    }

    if (picker) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton({
                    val s = st.selectedStartDateMillis
                    val e = st.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        val sd = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
                        val ed = Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.select(Period.CUSTOM, Range(Periods.startOf(sd), Periods.startOf(ed.plusDays(1))))
                    }
                    picker = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton({ picker = false }) { Text("Batal") } },
        ) { DateRangePicker(st, Modifier.weight(1f)) }
    }
}

@Composable
private fun PermissionCard() {
    val ctx = LocalContext.current
    SCard {
        Ico(R.drawable.ic_shield, MaterialTheme.colorScheme.primary, 36.dp)
        Spacer(Modifier.height(12.dp))
        Text("Izinkan akses penggunaan", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Data Wi-Fi & seluler per aplikasi dibaca dari statistik jaringan sistem. Android mewajibkan izin " +
                "\"Akses penggunaan\". Data tidak pernah keluar dari HP — aplikasi ini tidak punya izin internet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button({ UsageAccess.open(ctx) }, Modifier.fillMaxWidth()) { Text("Buka pengaturan") }
    }
}

@Composable
private fun HeroCard(label: String, s: UsageSummary, net: Net, loading: Boolean) {
    val total = s.of(net).total
    val apps = s.appsBy(net)
    SCard {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.bytes(total), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                apps.take(3).forEachIndexed { i, a -> Legend(ChartColors[i], a.label, Fmt.bytes(a.of(net).total)) }
                val rest = total - apps.take(3).sumOf { it.of(net).total }
                if (apps.size > 3 && rest > 0) Legend(OtherColor, "Lainnya", Fmt.bytes(rest))
            }
            Spacer(Modifier.width(14.dp))
            Donut(apps.take(3).map { it.of(net).total }, total, Modifier.size(120.dp))
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

@Composable
private fun Legend(c: Color, name: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(c))
        Text(name, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun Tiles(s: UsageSummary, net: Net) {
    val t = s.of(net)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(R.drawable.ic_wifi, WifiColor, "Wi-Fi", Fmt.bytes(s.wifi.total))
            Tile(R.drawable.ic_cell, MobileColor, "Data seluler", Fmt.bytes(s.mobile.total))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(R.drawable.ic_up, ChartColors[3], "Upload", Fmt.bytes(t.tx))
            Tile(R.drawable.ic_down, ChartColors[2], "Download", Fmt.bytes(t.rx))
        }
    }
}

@Composable
private fun RowScope.Tile(icon: Int, c: Color, label: String, value: String) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(c.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Ico(icon, c, 18.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    }
}

@Composable
private fun NetSelector(net: Net, onChange: (Net) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        Net.entries.forEach { n ->
            val sel = n == net
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onChange(n) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(n.label, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp) }
        }
    }
}

private fun LazyListScope.appList(s: UsageSummary, net: Net, onClick: (AppUsage) -> Unit) {
    val apps = s.appsBy(net)
    val max = apps.firstOrNull()?.of(net)?.total ?: 1L
    item { SectionTitle("Aplikasi (${apps.size})") }
    if (apps.isEmpty()) item {
        SCard { Text("Belum ada pemakaian di periode ini.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    items(apps, key = { it.uid }) { a -> AppRow(a, net, max, onClick) }
}

@Composable
private fun AppRow(a: AppUsage, net: Net, max: Long, onClick: (AppUsage) -> Unit) {
    val v = a.of(net)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable { onClick(a) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(a.packageName)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Fmt.bytes(v.total), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Bar(v.total.toFloat() / max)
            Spacer(Modifier.height(4.dp))
            Text(
                "Wi-Fi ${Fmt.bytes(a.wifi.total)} · Data ${Fmt.bytes(a.mobile.total)} · ↑${Fmt.bytes(v.tx)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

@Composable
private fun Bar(f: Float, c: Color = Accent) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
    }
}

@Composable
private fun BackgroundWatch(s: UsageSummary, onClick: (AppUsage) -> Unit) {
    val list = s.apps.filter { it.background.tx > 0 }.sortedByDescending { it.background.tx }.take(5)
    SCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ic_shield, ChartColors[3])
            Text("Upload di latar belakang", Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Aplikasi yang mengirim data saat tidak sedang dibuka. Upload besar dari aplikasi yang jarang dipakai patut dicurigai.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Text("Tidak ada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { a ->
            val sus = a.background.tx > a.background.rx && a.background.tx > 20_000_000
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick(a) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(a.packageName, 30.dp)
                Text(a.label, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sus) Text("Periksa ", color = Color(0xFFE5484D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("↑ ${Fmt.bytes(a.background.tx)}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DetailScreen(vm: MainViewModel, a: AppUsage) {
    val ctx = LocalContext.current
    Page(a.label, onBack = { vm.back() }) {
        item {
            SCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.packageName, 52.dp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(Periods.describe(vm.period, vm.range), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text(Fmt.bytes(a.all.total), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
                a.packageName?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(R.drawable.ic_wifi, WifiColor, "Wi-Fi", Fmt.bytes(a.wifi.total))
                    Tile(R.drawable.ic_cell, MobileColor, "Data seluler", Fmt.bytes(a.mobile.total))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(R.drawable.ic_up, ChartColors[3], "Upload", Fmt.bytes(a.all.tx))
                    Tile(R.drawable.ic_down, ChartColors[2], "Download", Fmt.bytes(a.all.rx))
                }
            }
        }
        item {
            SCard {
                Text("Latar depan vs belakang", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(12.dp))
                val tot = a.all.total.coerceAtLeast(1)
                Legend(Accent, "Saat dibuka", Fmt.bytes(a.foreground.total))
                Bar(a.foreground.total.toFloat() / tot)
                Spacer(Modifier.height(10.dp))
                Legend(ChartColors[3], "Latar belakang (↑ ${Fmt.bytes(a.background.tx)})", Fmt.bytes(a.background.total))
                Bar(a.background.total.toFloat() / tot, ChartColors[3])
            }
        }
        item {
            SCard {
                Text("14 hari terakhir", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(12.dp))
                if (vm.detailDays.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                else DayBars(vm.detailDays, labelEvery = 2)
            }
        }
        a.packageName?.let { pkg ->
            item {
                FilledTonalButton({ UsageAccess.openAppSettings(ctx, pkg) }, Modifier.fillMaxWidth()) {
                    Text("Buka info aplikasi (batasi data / izin)")
                }
            }
        }
    }
}

@Composable
private fun DayBars(days: List<DayUsage>, labelEvery: Int = 1, fmt: DateTimeFormatter = dmFmt) {
    val max = (days.maxOfOrNull { it.total } ?: 0L).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Text("Maks ${Fmt.bytes(max)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 6.dp)) {
        val slot = size.width / days.size
        val bw = slot * .55f
        days.forEachIndexed { i, d ->
            val x = i * slot + (slot - bw) / 2
            drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
            val hw = size.height * d.wifi / max
            val hm = size.height * d.mobile / max
            if (hw + hm > 0) {
                drawRoundRect(MobileColor, Offset(x, size.height - hw - hm), Size(bw, hw + hm), CornerRadius(bw / 2))
                if (hw > 0) drawRoundRect(WifiColor, Offset(x, size.height - hw), Size(bw, hw), CornerRadius(bw / 2))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        days.forEachIndexed { i, d ->
            Text(
                if (i % labelEvery == 0) fmt.format(day(d.start)) else "",
                Modifier.weight(1f), fontSize = 10.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(WifiColor))
        Text(" Wi-Fi   ", fontSize = 12.sp)
        Box(Modifier.size(10.dp).clip(CircleShape).background(MobileColor))
        Text(" Data seluler", fontSize = 12.sp)
    }
}

@Composable
fun ReportScreen(vm: MainViewModel) {
    Page("Laporan mingguan", onBack = { vm.back() }) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
            ) {
                (0..3).forEach { i ->
                    val w = vm.weekStart(i).get(WeekFields.ISO.weekOfWeekBasedYear())
                    val sel = i == vm.weekIndex
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { vm.loadWeek(i) }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("W$w", fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }
        val w = vm.week
        if (w == null || vm.weekLoading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (w == null) return@Page
        }
        val total = w.days.sumOf { it.total }
        val counted = w.days.count { it.start <= System.currentTimeMillis() }.coerceAtLeast(1)
        item {
            SCard {
                Text("${dmFmt.format(w.start)} – ${dmFmt.format(w.start.plusDays(6))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.bytes(total), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Rata-rata ${Fmt.bytes(total / counted)} per hari", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                DayBars(w.days, fmt = dayFmt)
            }
        }
        item {
            SCard {
                Text("Pemakaian per jam", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(12.dp))
                Heatmap(w.hourly, w.start)
            }
        }
        val top = w.summary.appsBy(Net.ALL).take(3)
        if (top.isNotEmpty()) {
            item { SectionTitle("Aplikasi teratas") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    top.forEachIndexed { i, a ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
                                .clickable { vm.open(Screen.Detail(a)) }.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(a.packageName, 44.dp, ChartColors[i])
                            Spacer(Modifier.height(8.dp))
                            Text(a.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.bytes(a.all.total), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item { BackgroundWatch(w.summary) { vm.open(Screen.Detail(it)) } }
    }
}

@Composable
private fun Heatmap(m: Array<LongArray>, start: LocalDate) {
    val max = (m.maxOf { r -> r.maxOrNull() ?: 0L }).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row {
        Column(Modifier.width(34.dp)) {
            (0 until 7).forEach {
                Box(Modifier.height(20.dp), contentAlignment = Alignment.CenterStart) {
                    Text(dayFmt.format(start.plusDays(it.toLong())), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Canvas(Modifier.weight(1f).height(140.dp)) {
            val cw = size.width / 24
            val ch = size.height / 7
            for (d in 0 until 7) for (h in 0 until 24) {
                val v = m[d][h]
                val c = if (v <= 0) track else Accent.copy(alpha = (0.15f + 0.85f * v / max).coerceAtMost(1f))
                drawRoundRect(c, Offset(h * cw + 1.5f, d * ch + 1.5f), Size(cw - 3f, ch - 3f), CornerRadius(4f))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 34.dp, top = 4.dp)) {
        listOf("00", "06", "12", "18").forEach {
            Text(it, Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WidgetsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val mgr = remember { AppWidgetManager.getInstance(ctx) }
    val ids = remember(vm.stack.size) { WidgetUpdater.ids(ctx).toList() }
    Page("Widget", onBack = { vm.back() }) {
        item {
            SCard {
                Ico(R.drawable.ic_widgets, MaterialTheme.colorScheme.primary, 32.dp)
                Spacer(Modifier.height(10.dp))
                Text("Widget pemakaian data", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Atur periode, Wi-Fi/data, isi, warna, gradien, gambar galeri dan transparansi.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    {
                        if (mgr.isRequestPinAppWidgetSupported)
                            mgr.requestPinAppWidget(ComponentName(ctx, UsageWidgetProvider::class.java), null, null)
                    },
                    Modifier.fillMaxWidth(),
                ) { Text("Tambah ke layar utama") }
            }
        }
        if (ids.isNotEmpty()) item { SectionTitle("Widget aktif") }
        items(ids) { id ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        ctx.startActivity(
                            Intent(ctx, WidgetConfigActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .putExtra(WidgetConfigActivity.EXTRA_EDIT, true),
                        )
                    }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Widget #$id", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text("Atur", color = MaterialTheme.colorScheme.primary)
                Ico(R.drawable.ic_chevron, MaterialTheme.colorScheme.primary, 18.dp)
            }
        }
    }
}
