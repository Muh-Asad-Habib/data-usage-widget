package io.github.asadhabib.datausage.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.asadhabib.datausage.R
import io.github.asadhabib.datausage.data.Net
import io.github.asadhabib.datausage.data.Period
import io.github.asadhabib.datausage.ui.AppTheme
import io.github.asadhabib.datausage.ui.Ico
import io.github.asadhabib.datausage.ui.SCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private val Swatches = listOf(
    0xFFFFFFFF, 0xFFE4E5EA, 0xFF15171A, 0xFF2B2D33, 0xFF3D7BFF, 0xFF1B3A8C, 0xFF3BBFDB,
    0xFF5BD37F, 0xFFFFA24C, 0xFFFF5F6D, 0xFFA06BFF, 0xFFFFD166,
).map { it.toInt() }

private class Preset(val name: String, val apply: (WidgetConfig) -> WidgetConfig)

private val Presets = listOf(
    Preset("Terang") { it.copy(bgType = BgType.SOLID, color1 = 0xFFE4E5EA.toInt(), opacity = 90, dim = 0, textColor = 0xFF15171A.toInt()) },
    Preset("Gelap") { it.copy(bgType = BgType.SOLID, color1 = 0xFF17171A.toInt(), opacity = 92, dim = 0, textColor = 0xFFFFFFFF.toInt()) },
    Preset("Kaca") { it.copy(bgType = BgType.SOLID, color1 = 0xFFFFFFFF.toInt(), opacity = 35, dim = 0, textColor = 0xFFFFFFFF.toInt()) },
    Preset("Biru") { it.copy(bgType = BgType.GRADIENT, color1 = 0xFF3D7BFF.toInt(), color2 = 0xFF1B3A8C.toInt(), opacity = 100, textColor = 0xFFFFFFFF.toInt()) },
    Preset("Sunset") { it.copy(bgType = BgType.GRADIENT, color1 = 0xFFFF5F6D.toInt(), color2 = 0xFFFFA24C.toInt(), opacity = 100, textColor = 0xFFFFFFFF.toInt()) },
    Preset("Transparan") { it.copy(bgType = BgType.SOLID, color1 = 0xFF000000.toInt(), opacity = 0, dim = 0, textColor = 0xFFFFFFFF.toInt()) },
)

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
        val src = ImageDecoder.createSource(contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            val m = max(info.size.width, info.size.height)
            if (m > 1080) {
                val s = 1080f / m
                dec.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
            }
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        WidgetPrefs.imageFile(this, widgetId).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    } catch (_: Exception) { false }

    @Composable
    private fun ConfigScreen(initial: WidgetConfig) {
        var cfg by remember { mutableStateOf(initial) }
        var data by remember { mutableStateOf<WidgetData?>(null) }
        var imgVersion by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val (wDp, hDp) = remember { WidgetUpdater.size(this, widgetId).let { (w, h) -> if (w < 150) 320 to 160 else w to h } }

        LaunchedEffect(cfg.period) {
            data = withContext(Dispatchers.IO) { WidgetData.load(this@WidgetConfigActivity, cfg) }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch {
                val ok = withContext(Dispatchers.IO) { saveImage(uri) }
                if (ok) { cfg = cfg.copy(bgType = BgType.IMAGE); imgVersion++ }
            }
        }

        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ finish() }) { Ico(R.drawable.ic_back) }
                Text("Atur widget", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Button({ save(cfg) }, Modifier.padding(end = 10.dp)) { Text("Simpan") }
            }
            // Preview over a fake wallpaper
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).height((hDp + 40).coerceAtMost(260).dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6C8CD5), Color(0xFFE29587), Color(0xFF2C3E50)))),
                contentAlignment = Alignment.Center,
            ) {
                val d = data
                if (d != null) AndroidView(
                    factory = { FrameLayout(it) },
                    modifier = Modifier.size(wDp.coerceAtMost(360).dp, hDp.coerceAtMost(220).dp),
                    update = { frame ->
                        @Suppress("UNUSED_EXPRESSION") imgVersion
                        val rv = WidgetRenderer.build(frame.context, widgetId, cfg, d,
                            wDp.coerceAtMost(360), hDp.coerceAtMost(220), interactive = false)
                        frame.removeAllViews()
                        frame.addView(rv.apply(frame.context, frame),
                            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    },
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SCard {
                    Label("Gaya cepat")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Presets) { p -> FilledTonalButton({ cfg = p.apply(cfg) }) { Text(p.name) } }
                    }
                }
                SCard {
                    Label("Periode")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Period.entries.filter { it != Period.CUSTOM }) { p ->
                            FilterChip(cfg.period == p, { cfg = cfg.copy(period = p) }, { Text(p.label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Label("Jaringan")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Net.entries.forEach { n -> FilterChip(cfg.net == n, { cfg = cfg.copy(net = n) }, { Text(n.label) }) }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        cfg.title, { cfg = cfg.copy(title = it.take(40)) }, Modifier.fillMaxWidth(),
                        label = { Text("Judul (kosong = otomatis)") }, singleLine = true,
                    )
                }
                SCard {
                    Label("Yang ditampilkan")
                    Toggle("Diagram donat", cfg.showDonut) { cfg = cfg.copy(showDonut = it) }
                    Toggle("Daftar aplikasi teratas", cfg.showApps) { cfg = cfg.copy(showApps = it) }
                    if (cfg.showApps) SliderRow("Jumlah aplikasi", cfg.appCount, 1..5) { cfg = cfg.copy(appCount = it) }
                    Toggle("Rincian Wi-Fi / Data", cfg.showSplit) { cfg = cfg.copy(showSplit = it) }
                    Toggle("Upload / Download", cfg.showUpDown) { cfg = cfg.copy(showUpDown = it) }
                    Toggle("Tombol refresh", cfg.showRefresh) { cfg = cfg.copy(showRefresh = it) }
                }
                SCard {
                    Label("Latar belakang")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(BgType.SOLID to "Warna", BgType.GRADIENT to "Gradien", BgType.IMAGE to "Gambar").forEach { (t, n) ->
                            FilterChip(cfg.bgType == t, {
                                if (t == BgType.IMAGE && !WidgetPrefs.imageFile(this@WidgetConfigActivity, widgetId).exists())
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                else cfg = cfg.copy(bgType = t)
                            }, { Text(n) })
                        }
                    }
                    when (cfg.bgType) {
                        BgType.SOLID -> ColorPick("Warna", cfg.color1) { cfg = cfg.copy(color1 = it) }
                        BgType.GRADIENT -> {
                            ColorPick("Warna 1", cfg.color1) { cfg = cfg.copy(color1 = it) }
                            ColorPick("Warna 2", cfg.color2) { cfg = cfg.copy(color2 = it) }
                            SliderRow("Sudut gradien", cfg.gradientAngle, 0..360, "°") { cfg = cfg.copy(gradientAngle = it) }
                        }
                        BgType.IMAGE -> {
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Ico(R.drawable.ic_image, size = 18.dp)
                                Text("  Ganti gambar dari galeri")
                            }
                            SliderRow("Gelapkan gambar", cfg.dim, 0..80, "%") { cfg = cfg.copy(dim = it) }
                        }
                    }
                    SliderRow("Transparansi (opasitas)", cfg.opacity, 0..100, "%") { cfg = cfg.copy(opacity = it) }
                    SliderRow("Sudut membulat", cfg.radius, 0..40, "dp") { cfg = cfg.copy(radius = it) }
                }
                SCard { ColorPick("Warna teks", cfg.textColor) { cfg = cfg.copy(textColor = it) } }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Label(t: String) =
    Text(t, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))

@Composable
private fun Toggle(label: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { on(!v) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(v, on)
    }
}

@Composable
private fun SliderRow(label: String, v: Int, range: IntRange, unit: String = "", on: (Int) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row {
            Text(label, Modifier.weight(1f), fontSize = 14.sp)
            Text("$v$unit", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Slider(
            v.toFloat(), { on(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (range.last - range.first <= 10) range.last - range.first - 1 else 0,
        )
    }
}

@Composable
private fun ColorPick(label: String, color: Int, on: (Int) -> Unit) {
    var hex by remember(color) { mutableStateOf("%06X".format(color and 0xFFFFFF)) }
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(Swatches) { c ->
                val sel = (c and 0xFFFFFF) == (color and 0xFFFFFF)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(c))
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
            Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Hex (RRGGBB)") }, prefix = { Text("#") }, singleLine = true,
            leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color))) },
        )
    }
}

