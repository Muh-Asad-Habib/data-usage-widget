package io.github.asadhabib.datausage.ui

import android.appwidget.AppWidgetManager
import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import io.github.asadhabib.datausage.data.WifiLog
import kotlin.math.roundToInt
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
import io.github.asadhabib.datausage.widget.Widget2x1
import io.github.asadhabib.datausage.widget.Widget2x2
import io.github.asadhabib.datausage.widget.Widget4x1
import io.github.asadhabib.datausage.widget.WidgetConfigActivity
import io.github.asadhabib.datausage.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private val dayFmt = DateTimeFormatter.ofPattern("EEE", Periods.locale)
private val dmFmt = DateTimeFormatter.ofPattern("d MMM", Periods.locale)

@Composable
private fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) RoundButton(R.drawable.ic_back, "Kembali", onBack) else Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
        }
        content()
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun RoundButton(icon: Int, desc: String, onClick: () -> Unit, spin: Boolean = false) {
    val angle = if (spin) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "a").value
    } else 0f
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .semantics { contentDescription = desc }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.rotate(angle)) { Ico(icon, size = 20.dp) } }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Periods.locale)

private fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(Periods.zone).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    var picker by remember { mutableStateOf(false) }
    val sub = when {
        vm.loading -> "Memperbarui…"
        vm.updatedAt > 0 -> "Diperbarui " + Instant.ofEpochMilli(vm.updatedAt).atZone(Periods.zone).format(timeFmt)
        else -> null
    }
    Page("Pantau Data", subtitle = sub, actions = {
        RoundButton(R.drawable.ic_refresh, "Muat ulang", { if (!vm.loading) vm.refresh() }, spin = vm.loading)
        RoundButton(R.drawable.ic_chart, "Laporan", { vm.open(Screen.Report) })
        RoundButton(R.drawable.ic_widgets, "Widget", { vm.open(Screen.Widgets) })
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
                        label = { Text(p.label, Modifier.padding(vertical = 8.dp)) },
                        leadingIcon = if (p == Period.CUSTOM) ({ Ico(R.drawable.ic_calendar, size = 16.dp) }) else null,
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(true, vm.period == p,
                            borderColor = MaterialTheme.colorScheme.outline, selectedBorderColor = Color.Transparent),
                    )
                }
            }
        }
        item { HeroCard(Periods.describe(vm.period, vm.range), vm.summary, vm.net, vm.loading) }
        item { Tiles(vm.summary, vm.net) }
        if (vm.net != Net.MOBILE) item { SsidCard(vm) }
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
    HeroSurface {
        Text(label.uppercase(Periods.locale), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.bytes(total), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 42.sp)
                Text("Total ${net.label.lowercase(Periods.locale)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Box(contentAlignment = Alignment.Center) {
                Donut(apps.take(3).map { it.of(net).total }, total, Modifier.size(104.dp), stroke = 14.dp)
                Text("${apps.size}\napl", fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(14.dp))
        apps.take(3).forEachIndexed { i, a -> Legend(ChartColors[i], a.label, Fmt.bytes(a.of(net).total)) }
        val rest = total - apps.take(3).sumOf { it.of(net).total }
        if (apps.size > 3 && rest > 0) Legend(OtherColor, "Lainnya", Fmt.bytes(rest))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun Legend(c: Color, name: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Text(name, Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun Tiles(s: UsageSummary, net: Net) {
    val t = s.of(net)
    val all = (s.wifi.total + s.mobile.total).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(R.drawable.ic_wifi, WifiColor, "Wi-Fi", Fmt.bytes(s.wifi.total), s.wifi.total.toFloat() / all)
            Tile(R.drawable.ic_cell, MobileColor, "Data seluler", Fmt.bytes(s.mobile.total), s.mobile.total.toFloat() / all)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(R.drawable.ic_down, ChartColors[1], "Download", Fmt.bytes(t.rx), null)
            Tile(R.drawable.ic_up, ChartColors[3], "Upload", Fmt.bytes(t.tx), null)
        }
    }
}

@Composable
private fun RowScope.Tile(icon: Int, c: Color, label: String, value: String, share: Float? = null) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.weight(1f).clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f), shape).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Ico(icon, c, 18.dp)
            }
            Spacer(Modifier.weight(1f))
            if (share != null) Text("${(share * 100).roundToInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c)
        }
        Spacer(Modifier.height(12.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (share != null) { Spacer(Modifier.height(8.dp)); Bar(share, c, 4.dp) }
    }
}

@Composable
private fun SsidCard(vm: MainViewModel) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    SCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(WifiColor.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Ico(R.drawable.ic_wifi, WifiColor, 18.dp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Wi-Fi per jaringan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Berdasarkan nama Wi-Fi (SSID)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            !vm.location -> {
                Text(
                    "Android hanya memberi nama Wi-Fi jika izin lokasi aktif. Lokasi tidak dipakai untuk hal lain dan tidak ada data yang keluar dari HP.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button({
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, Modifier.fillMaxWidth().height(48.dp)) { Text("Izinkan lokasi") }
            }
            else -> {
                if (!vm.locationOn) Hint("Lokasi HP sedang mati, nama Wi-Fi baru tidak bisa dicatat.") {
                    try { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: Exception) {}
                }
                if (Build.VERSION.SDK_INT >= 29 && !WifiLog.hasBackground(ctx)) Hint("Izinkan lokasi \"Sepanjang waktu\" agar perpindahan Wi-Fi tercatat walau aplikasi tertutup.") {
                    bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                val list = vm.ssids
                val max = list.maxOfOrNull { it.bytes }?.coerceAtLeast(1) ?: 1
                if (list.isEmpty()) Text("Belum ada catatan Wi-Fi di periode ini.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.take(8).forEach { u ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.ssid ?: "Belum tercatat", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (u.ssid != null) FontWeight.Medium else FontWeight.Normal,
                                color = if (u.ssid != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Fmt.bytes(u.bytes), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Bar(u.bytes.toFloat() / max, if (u.ssid != null) WifiColor else OtherColor, 4.dp)
                    }
                }
                WifiLog.since(ctx)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Tercatat sejak " + Instant.ofEpochMilli(it).atZone(Periods.zone).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Periods.locale)),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp))
            .background(ChartColors[3].copy(alpha = .12f)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), fontSize = 13.sp)
        Ico(R.drawable.ic_chevron, ChartColors[3], 18.dp)
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
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50))
                    .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onChange(n) },
                contentAlignment = Alignment.Center,
            ) {
                Text(n.label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp,
                    color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f), RoundedCornerShape(20.dp))
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
private fun Bar(f: Float, c: Color = Accent, h: Dp = 6.dp) {
    Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).height(h).clip(RoundedCornerShape(50)).background(c))
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
                Text("Pilih ukuran", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                val sizes = listOf(
                    Triple("2x1", "Mini · total, Wi-Fi, seluler", Widget2x1::class.java),
                    Triple("2x2", "Kotak · + bar rasio", Widget2x2::class.java),
                    Triple("4x1", "Baris · total, Wi-Fi, seluler", Widget4x1::class.java),
                    Triple("4x2", "Lengkap · + daftar aplikasi & donat", UsageWidgetProvider::class.java),
                )
                sizes.forEach { (size, desc, cls) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (mgr.isRequestPinAppWidgetSupported)
                                    mgr.requestPinAppWidget(ComponentName(ctx, cls), null, null)
                            }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(size, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(44.dp))
                        Text(desc, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
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
