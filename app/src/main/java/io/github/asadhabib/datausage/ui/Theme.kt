package io.github.asadhabib.datausage.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.asadhabib.datausage.data.AppNames
import io.github.asadhabib.datausage.widget.WidgetRenderer.CELL_COLOR
import io.github.asadhabib.datausage.widget.CHART_COLORS
import io.github.asadhabib.datausage.widget.OTHER_COLOR
import io.github.asadhabib.datausage.widget.WidgetRenderer.WIFI_COLOR

val ChartColors = CHART_COLORS.map { Color(it) }
val OtherColor = Color(OTHER_COLOR)
val Accent = Color(0xFF3D7BFF)
val WifiColor = Color(WIFI_COLOR)
val MobileColor = Color(CELL_COLOR)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // One UI palette: pure black / light grey canvas, flat cards without borders.
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF6E9EFF), onPrimary = Color(0xFF0A1A3F), background = Color.Black,
        surface = Color(0xFF171717), surfaceVariant = Color(0xFF262626), onSurface = Color(0xFFFAFAFA),
        onSurfaceVariant = Color(0xFF9E9E9E), secondaryContainer = Color(0xFF253252), outline = Color(0xFF262626),
        primaryContainer = Color(0xFF171717),
    ) else lightColorScheme(
        primary = Color(0xFF3E91FF), background = Color(0xFFF2F2F2), surface = Color(0xFFFCFCFC),
        surfaceVariant = Color(0xFFEAEAEA), onSurface = Color(0xFF111111), onSurfaceVariant = Color(0xFF737373),
        secondaryContainer = Color(0xFFDDE9FF), outline = Color(0xFFE8E8E8), primaryContainer = Color(0xFFFCFCFC),
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}

@Composable
fun SCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        content = content,
    )
}

/** Main number card; kept flat like One UI's Device care cards. */
@Composable
fun HeroSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(22.dp),
        content = content,
    )
}

@Composable
fun Ico(res: Int, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 22.dp) =
    Icon(painterResource(res), null, Modifier.size(size), tint = tint)

@Composable
fun SectionTitle(text: String, trailing: String? = null) = Row(
    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    if (trailing != null) Text(trailing, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Donut(values: List<Long>, total: Long, modifier: Modifier = Modifier, stroke: Dp = 18.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val sw = stroke.toPx()
        val d = size.minDimension - sw
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        val st = Stroke(sw)
        if (total <= 0) { drawArc(track, 0f, 360f, false, tl, Size(d, d), style = st); return@Canvas }
        val segs = values.mapIndexed { i, v -> v to ChartColors[i % 5] }.toMutableList()
        val rest = total - values.sum()
        if (rest > 0) segs += rest to OtherColor
        val gap = if (segs.size > 1) 3f else 0f
        var a = -90f
        segs.forEach { (v, c) ->
            val sweep = 360f * v / total
            if (sweep > gap) drawArc(c, a + gap / 2, sweep - gap, false, tl, Size(d, d), style = st)
            a += sweep
        }
    }
}

@Composable
fun AppIcon(pkg: String?, size: Dp = 40.dp, fallback: Color = OtherColor) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { AppNames.icon(ctx, pkg)?.let { d: Drawable -> d.toBitmap(96, 96).asImageBitmap() } }
    if (bmp != null) Image(bmp, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)))
    else Box(Modifier.size(size).clip(CircleShape).background(fallback.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * .35f).clip(CircleShape).background(fallback))
    }
}
