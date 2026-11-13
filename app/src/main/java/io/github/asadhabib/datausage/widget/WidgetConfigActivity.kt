package io.github.asadhabib.datausage.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.asadhabib.datausage.R
import io.github.asadhabib.datausage.data.L
import io.github.asadhabib.datausage.data.Net
import io.github.asadhabib.datausage.data.Period
import io.github.asadhabib.datausage.ui.AppTheme
import io.github.asadhabib.datausage.ui.Ico
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private val Swatches = listOf(
    0xFFFFFFFF, 0xFFE4E5EA, 0xFF15171A, 0xFF2B2D33, 0xFF3D7BFF, 0xFF1B3A8C, 0xFF3BBFDB,
    0xFF5BD37F, 0xFFFFA24C, 0xFFFF5F6D, 0xFFA06BFF, 0xFFFFD166,
).map { it.toInt() }

private const val DARK_TEXT = 0xFF15171A.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private enum class Style(val en: String, val id: String) {
    LIGHT("Light", "Terang"), DARK("Dark", "Gelap"), BLUE("Blue", "Biru"), SUNSET("Sunset", "Senja"),
    CUSTOM("Custom color", "Warna kustom"), GRADIENT("Custom gradient", "Gradien kustom"), IMAGE("Image", "Gambar");
    val label get() = L.t(en, id)
}

private fun styleOf(c: WidgetConfig): Style = when (c.bgType) {
    BgType.IMAGE -> Style.IMAGE
    BgType.GRADIENT -> when {
        c.color1 == 0xFF3D7BFF.toInt() && c.color2 == 0xFF1B3A8C.toInt() -> Style.BLUE
        c.color1 == 0xFFFF5F6D.toInt() && c.color2 == 0xFFFFA24C.toInt() -> Style.SUNSET
        else -> Style.GRADIENT
    }
    BgType.SOLID -> when (c.color1) {
        0xFFE4E5EA.toInt() -> Style.LIGHT
        0xFF17171A.toInt() -> Style.DARK
        else -> Style.CUSTOM
    }
}

private fun WidgetConfig.withStyle(s: Style): WidgetConfig = when (s) {
    Style.LIGHT -> copy(bgType = BgType.SOLID, color1 = 0xFFE4E5EA.toInt(), textColor = DARK_TEXT)
    Style.DARK -> copy(bgType = BgType.SOLID, color1 = 0xFF17171A.toInt(), textColor = WHITE)
    Style.BLUE -> copy(bgType = BgType.GRADIENT, color1 = 0xFF3D7BFF.toInt(), color2 = 0xFF1B3A8C.toInt(), textColor = WHITE)
    Style.SUNSET -> copy(bgType = BgType.GRADIENT, color1 = 0xFFFF5F6D.toInt(), color2 = 0xFFFFA24C.toInt(), textColor = WHITE)
    Style.CUSTOM -> copy(bgType = BgType.SOLID, color1 = if (styleOf(this) == Style.CUSTOM) color1 else 0xFF3D7BFF.toInt())
    Style.GRADIENT -> copy(bgType = BgType.GRADIENT, color1 = 0xFFA06BFF.toInt(), color2 = 0xFF3BBFDB.toInt(), textColor = WHITE)
    Style.IMAGE -> copy(bgType = BgType.IMAGE)
}

private val blurNames get() = listOf(L.t("Off", "Mati"), L.t("Low", "Rendah"), L.t("Medium", "Sedang"), L.t("High", "Tinggi"))

class WidgetConfigActivity : ComponentActivity() {
    companion object { const val EXTRA_EDIT = "edit" }

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val initial = WidgetPrefs.load(this, widgetId)
        setContent { AppTheme { ConfigScreen(initial) } }
    }

    private fun save(cfg: WidgetConfig) {
        WidgetPrefs.save(this, widgetId, cfg)
        val app = applicationContext
        val id = widgetId
        Thread { WidgetUpdater.update(app, id) }.start()
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun saveImage(uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1080) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }!!
        val m = max(raw.width, raw.height)
        val bmp = if (m > 1080) {
            val s = 1080f / m
            Bitmap.createScaledBitmap(raw, (raw.width * s).roundToInt(), (raw.height * s).roundToInt(), true).also { if (it !== raw) raw.recycle() }
        } else raw
        WidgetPrefs.imageFile(this, widgetId).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    } catch (_: Exception) { false }

    @Composable
    private fun ConfigScreen(initial: WidgetConfig) {
        var cfg by remember { mutableStateOf(initial) }
        var data by remember { mutableStateOf<WidgetData?>(null) }
        var imgVersion by remember { mutableIntStateOf(0) }
        var dialog by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val (wDp, hDp) = remember { WidgetUpdater.size(this, widgetId) }
        val cs = MaterialTheme.colorScheme

        LaunchedEffect(cfg.period) {
            data = withContext(Dispatchers.IO) { WidgetData.load(this@WidgetConfigActivity, cfg) }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch {
                val ok = withContext(Dispatchers.IO) { saveImage(uri) }
                if (ok) { cfg = cfg.copy(bgType = BgType.IMAGE); imgVersion++ }
            }
        }
        fun pickImage() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        val style = styleOf(cfg)

        Box(Modifier.fillMaxSize().background(cs.background).statusBarsPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text(
                    L.t("Widget settings", "Pengaturan widget"), Modifier.padding(start = 8.dp, top = 20.dp, bottom = 16.dp),
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                )
                // Live preview over a wallpaper-like backdrop
                Box(
                    Modifier.fillMaxWidth().height((hDp + 64).coerceIn(150, 290).dp).clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF7FA6E8), Color(0xFFE6A6A0), Color(0xFF3A4D6B)))),
                    contentAlignment = Alignment.Center,
                ) {
                    val d = data
                    if (d != null) AndroidView(
                        factory = { FrameLayout(it) },
                        modifier = Modifier.size(wDp.coerceAtMost(340).dp, hDp.coerceAtMost(220).dp),
                        update = { frame ->
                            @Suppress("UNUSED_EXPRESSION") imgVersion
                            val rv = WidgetRenderer.build(frame.context, widgetId, cfg, d,
                                wDp.coerceAtMost(340), hDp.coerceAtMost(220), interactive = false)
                            frame.removeAllViews()
                            frame.addView(rv.apply(frame.context, frame),
                                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))

                Group {
                    SwitchRow(L.t("Background", "Latar belakang"), cfg.showBg) { cfg = cfg.copy(showBg = it) }
                    if (cfg.showBg) {
                        Line()
                        ValueRow(L.t("Color", "Warna"), style.label) { dialog = "style" }
                        when (style) {
                            Style.CUSTOM -> Pad { ColorPick(cfg.color1) { cfg = cfg.copy(color1 = it) } }
                            Style.GRADIENT -> Pad {
                                ColorPick(cfg.color1) { cfg = cfg.copy(color1 = it) }
                                ColorPick(cfg.color2) { cfg = cfg.copy(color2 = it) }
                                StepSlider(L.t("Gradient angle", "Sudut gradien"), "${cfg.gradientAngle}°", cfg.gradientAngle, 0..360, 0) { cfg = cfg.copy(gradientAngle = it) }
                            }
                            Style.IMAGE -> {
                                Line()
                                ValueRow(L.t("Image", "Gambar"), L.t("Change", "Ganti")) { pickImage() }
                                Pad { StepSlider(L.t("Darken", "Gelapkan"), "${cfg.dim}%", cfg.dim, 0..80, 0) { cfg = cfg.copy(dim = it) } }
                            }
                            else -> {}
                        }
                        Line()
                        Pad {
                            StepSlider(L.t("Transparency", "Transparansi"), "${100 - cfg.opacity}%", 100 - cfg.opacity, 0..100, 0, checker = true) {
                                cfg = cfg.copy(opacity = 100 - it)
                            }
                        }
                        Line()
                        Pad { StepSlider(L.t("Blur", "Keburaman"), blurNames[cfg.blur], cfg.blur, 0..3, 2) { cfg = cfg.copy(blur = it) } }
                        Line()
                        Pad { StepSlider(L.t("Corner radius", "Sudut membulat"), "${cfg.radius} dp", cfg.radius, 0..40, 0) { cfg = cfg.copy(radius = it) } }
                    }
                }
                Hint(L.t(
                    "Blur gives the widget a frosted-glass look. Android doesn't let widgets blur the wallpaper behind them.",
                    "Keburaman memberi efek kaca buram. Android tidak mengizinkan widget mengaburkan wallpaper di belakangnya.",
                ))

                Group {
                    val tc = when (cfg.textColor) { DARK_TEXT -> L.t("Dark", "Gelap"); WHITE -> L.t("White", "Putih"); else -> L.t("Custom", "Kustom") }
                    ValueRow(L.t("Text color", "Warna teks"), tc) { dialog = "text" }
                    if (cfg.textColor != DARK_TEXT && cfg.textColor != WHITE) Pad { ColorPick(cfg.textColor) { cfg = cfg.copy(textColor = it) } }
                }
                Spacer(Modifier.height(16.dp))

                Group {
                    ValueRow(L.t("Period", "Periode"), cfg.period.label) { dialog = "period" }
                    Line()
                    ValueRow(L.t("Network", "Jaringan"), cfg.net.label) { dialog = "net" }
                    Line()
                    ValueRow(L.t("Title", "Judul"), cfg.title.ifBlank { L.t("Automatic", "Otomatis") }) { dialog = "title" }
                }
                Spacer(Modifier.height(16.dp))

                Group {
                    SwitchRow(L.t("Donut chart", "Diagram donat"), cfg.showDonut) { cfg = cfg.copy(showDonut = it) }
                    Line()
                    SwitchRow(L.t("Top apps", "Aplikasi teratas"), cfg.showApps) { cfg = cfg.copy(showApps = it) }
                    if (cfg.showApps) Pad { StepSlider(L.t("Number of apps", "Jumlah aplikasi"), "${cfg.appCount}", cfg.appCount, 1..5, 3) { cfg = cfg.copy(appCount = it) } }
                    Line()
                    SwitchRow(L.t("Wi-Fi / mobile breakdown", "Rincian Wi-Fi / seluler"), cfg.showSplit) { cfg = cfg.copy(showSplit = it) }
                    Line()
                    SwitchRow(L.t("Upload / download", "Unggah / unduh"), cfg.showUpDown) { cfg = cfg.copy(showUpDown = it) }
                    Line()
                    SwitchRow(L.t("Refresh button", "Tombol refresh"), cfg.showRefresh) { cfg = cfg.copy(showRefresh = it) }
                }
                Hint(L.t("Small sizes always show total, Wi-Fi and mobile data.", "Ukuran kecil selalu menampilkan total, Wi-Fi, dan data seluler."))
                Spacer(Modifier.height(110.dp))
            }

            // One UI floating action pill
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
                    .shadow(10.dp, CircleShape).clip(CircleShape).background(cs.surface).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton(L.t("Cancel", "Batal")) { finish() }
                Box(Modifier.width(1.dp).height(22.dp).background(cs.outlineVariant))
                PillButton(L.t("Save", "Simpan")) { save(cfg) }
            }
        }

        when (dialog) {
            "style" -> Choice(L.t("Color", "Warna"), Style.entries.map { it.label }, style.ordinal, { dialog = null }) { i ->
                val s = Style.entries[i]
                if (s == Style.IMAGE && !WidgetPrefs.imageFile(this, widgetId).exists()) pickImage()
                else cfg = cfg.withStyle(s)
            }
            "text" -> {
                val opts = listOf(DARK_TEXT, WHITE, null)
                val cur = opts.indexOf(cfg.textColor).let { if (it < 0) 2 else it }
                Choice(L.t("Text color", "Warna teks"), listOf(L.t("Dark", "Gelap"), L.t("White", "Putih"), L.t("Custom", "Kustom")), cur, { dialog = null }) { i ->
                    cfg = cfg.copy(textColor = opts[i] ?: if (cur == 2) cfg.textColor else 0xFF3D7BFF.toInt())
                }
            }
            "period" -> {
                val ps = Period.entries.filter { it != Period.CUSTOM }
                Choice(L.t("Period", "Periode"), ps.map { it.label }, ps.indexOf(cfg.period), { dialog = null }) { cfg = cfg.copy(period = ps[it]) }
            }
            "net" -> Choice(L.t("Network", "Jaringan"), Net.entries.map { it.label }, cfg.net.ordinal, { dialog = null }) { cfg = cfg.copy(net = Net.entries[it]) }
            "title" -> {
                var t by remember { mutableStateOf(cfg.title) }
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(L.t("Title", "Judul")) },
                    text = {
                        OutlinedTextField(t, { t = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(L.t("Leave blank for automatic", "Kosongkan untuk otomatis")) })
                    },
                    confirmButton = { TextButton({ cfg = cfg.copy(title = t.trim()); dialog = null }) { Text(L.t("Done", "Selesai")) } },
                    dismissButton = { TextButton({ dialog = null }) { Text(L.t("Cancel", "Batal")) } },
                )
            }
        }
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 6.dp), content = content)

@Composable
private fun Pad(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), content = content)

@Composable
private fun Line() = HorizontalDivider(Modifier.padding(horizontal = 22.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))

@Composable
private fun Hint(t: String) =
    Text(t, Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SwitchRow(label: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Switch) { on(!v) }.padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 17.sp)
        Switch(v, null)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 17.sp)
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) =
    Box(Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 36.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

private fun Modifier.fillMaxHeight() = this.then(Modifier.heightIn(min = 56.dp))

@Composable
private fun Checker() {
    val a = Color(0xFFBDBDBD); val b = Color(0xFFFFFFFF)
    Canvas(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).border(1.dp, Color(0x33000000), RoundedCornerShape(5.dp))) {
        val n = 4; val s = size.width / n
        for (x in 0 until n) for (y in 0 until n) drawRect(if ((x + y) % 2 == 0) a else b, Offset(x * s, y * s), Size(s, s))
    }
}

@Composable
private fun StepSlider(label: String, value: String, v: Int, range: IntRange, steps: Int, checker: Boolean = false, on: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 17.sp)
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (checker) { Checker(); Spacer(Modifier.width(12.dp)) }
        Slider(
            v.toFloat(), { on(it.roundToInt()) }, Modifier.weight(1f),
            valueRange = range.first.toFloat()..range.last.toFloat(), steps = steps,
        )
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, dismiss: () -> Unit, pick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton) { pick(i); dismiss() }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(i == selected, null)
                        Text(o, Modifier.padding(start = 12.dp), fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(dismiss) { Text(L.t("Cancel", "Batal")) } },
    )
}

@Composable
private fun ColorPick(color: Int, on: (Int) -> Unit) {
    var hex by remember(color) { mutableStateOf("%06X".format(color and 0xFFFFFF)) }
    Column(Modifier.padding(vertical = 6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            items(Swatches) { c ->
                val sel = (c and 0xFFFFFF) == (color and 0xFFFFFF)
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                        .clickable { on(c) },
                )
            }
        }
        OutlinedTextField(
            hex,
            { s ->
                hex = s.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                if (hex.length == 6) on((0xFF000000 or hex.toLong(16)).toInt())
            },
            Modifier.fillMaxWidth().padding(top = 10.dp),
            label = { Text("Hex") }, prefix = { Text("#") }, singleLine = true,
            leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color))) },
        )
    }
}
