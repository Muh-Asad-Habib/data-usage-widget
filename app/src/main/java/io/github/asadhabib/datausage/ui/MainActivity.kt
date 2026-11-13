package io.github.asadhabib.datausage.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.asadhabib.datausage.data.AppUsage
import io.github.asadhabib.datausage.data.DayUsage
import io.github.asadhabib.datausage.data.Net
import io.github.asadhabib.datausage.data.Period
import io.github.asadhabib.datausage.data.Periods
import io.github.asadhabib.datausage.data.Range
import io.github.asadhabib.datausage.data.SsidUsage
import io.github.asadhabib.datausage.data.WifiLog
import io.github.asadhabib.datausage.data.UsageAccess
import io.github.asadhabib.datausage.data.UsageRepository
import io.github.asadhabib.datausage.data.UsageSummary
import io.github.asadhabib.datausage.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

sealed interface Screen {
    data object Home : Screen
    data class Detail(val app: AppUsage, val label: String) : Screen
    data object Dashboard : Screen
    data class Apps(val title: String, val summary: UsageSummary, val net: Net) : Screen
    data class Span(val title: String, val r: Range) : Screen
    data object Report : Screen
    data object Widgets : Screen
}

class WeekReport(
    val start: LocalDate,
    val days: List<DayUsage>,
    val hourly: Array<LongArray>,
    val summary: UsageSummary,
)

/** One Monday–Sunday week, one summary per day (future days are empty). */
class DashWeek(val start: LocalDate, val days: List<UsageSummary>)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = UsageRepository(app)

    val stack = mutableStateListOf<Screen>(Screen.Home)
    var granted by mutableStateOf(UsageAccess.granted(app))
    var net by mutableStateOf(Net.ALL)
    var loading by mutableStateOf(false)
    var summary by mutableStateOf(UsageSummary.EMPTY)
    var range by mutableStateOf(Periods.range(Period.TODAY))

    var detailDays by mutableStateOf<List<DayUsage>>(emptyList())

    var weekIndex by mutableStateOf(3)
    var week by mutableStateOf<WeekReport?>(null)
    var weekLoading by mutableStateOf(false)

    var ssids by mutableStateOf<List<SsidUsage>>(emptyList())
    var location by mutableStateOf(WifiLog.hasLocation(app))
    var locationOn by mutableStateOf(WifiLog.locationOn(app))
    var updatedAt by mutableStateOf(0L)

    var dashDay by mutableStateOf(LocalDate.now(Periods.zone))
    var dashWeek by mutableStateOf<DashWeek?>(null)
    var totals by mutableStateOf<List<Pair<Period, Long>>>(emptyList())
    private val weeks = HashMap<LocalDate, DashWeek>()
    private var dashJob: Job? = null

    private var job: Job? = null

    var spanSummary by mutableStateOf(UsageSummary.EMPTY)
    var spanSsids by mutableStateOf<List<SsidUsage>>(emptyList())
    var spanLoading by mutableStateOf(false)
    var spanNet by mutableStateOf(Net.ALL)

    fun openSpan(title: String, r: Range) {
        open(Screen.Span(title, r))
        spanLoading = true
        spanNet = Net.ALL
        val ctx = getApplication<Application>()
        val loc = location
        viewModelScope.launch {
            val (s, w) = withContext(Dispatchers.IO) { repo.summary(r) to (if (loc) WifiLog.usage(ctx, repo, r) else emptyList()) }
            spanSummary = s
            spanSsids = w
            spanLoading = false
        }
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        granted = UsageAccess.granted(ctx)
        location = WifiLog.hasLocation(ctx)
        locationOn = WifiLog.locationOn(ctx)
        if (!granted) return
        val r = Periods.range(Period.TODAY)
        range = r
        job?.cancel()
        loading = true
        val loc = location
        job = viewModelScope.launch {
            val (s, w) = withContext(Dispatchers.IO) {
                if (loc) { WifiLog.watch(ctx); WifiLog.record(ctx) }
                repo.summary(r) to (if (loc) WifiLog.usage(ctx, repo, r) else emptyList())
            }
            summary = s
            ssids = w
            updatedAt = System.currentTimeMillis()
            loading = false
        }
        WidgetUpdater.updateAll(ctx)
    }

    fun openDashboard() {
        weeks.clear()
        open(Screen.Dashboard)
        selectDay(LocalDate.now(Periods.zone))
        viewModelScope.launch {
            totals = withContext(Dispatchers.IO) {
                listOf(Period.LAST_7, Period.THIS_MONTH, Period.LAST_MONTH).map { it to repo.total(Periods.range(it)) }
            }
        }
    }

    fun selectDay(d: LocalDate) {
        val today = LocalDate.now(Periods.zone)
        dashDay = if (d.isAfter(today)) today else d
        val start = dashDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (dashWeek?.start == start) return
        weeks[start]?.let { dashWeek = it; return }
        dashJob?.cancel()
        dashJob = viewModelScope.launch {
            val w = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                DashWeek(start, (0..6).map {
                    val day = start.plusDays(it.toLong())
                    if (day.isAfter(today)) UsageSummary.EMPTY
                    else repo.summary(Range(Periods.startOf(day), minOf(Periods.startOf(day.plusDays(1)), now)))
                })
            }
            weeks[start] = w
            dashWeek = w
        }
    }

    fun open(s: Screen) {
        stack += s
        when (s) {
            is Screen.Detail -> {
                detailDays = emptyList()
                viewModelScope.launch { detailDays = withContext(Dispatchers.IO) { repo.daily(14, s.app.uid) } }
            }
            Screen.Report -> loadWeek(weekIndex)
            else -> {}
        }
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun weekStart(i: Int): LocalDate =
        LocalDate.now(Periods.zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((3 - i).toLong())

    fun loadWeek(i: Int) {
        weekIndex = i
        weekLoading = true
        val start = weekStart(i)
        viewModelScope.launch {
            week = withContext(Dispatchers.IO) {
                val end = start.plusDays(6)
                WeekReport(
                    start,
                    repo.daily(7, null, end),
                    repo.hourly(start, Net.ALL),
                    repo.summary(Range(Periods.startOf(start), minOf(Periods.startOf(end.plusDays(1)), System.currentTimeMillis()))),
                )
            }
            weekLoading = false
        }
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val phonePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Android 9 and older need this to read mobile data totals for the SIM.
        if (Build.VERSION.SDK_INT < 29 && savedInstanceState == null &&
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        ) phonePerm.launch(Manifest.permission.READ_PHONE_STATE)
        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    BackHandler(vm.stack.size > 1) { vm.back() }
                    when (val s = vm.stack.last()) {
                        Screen.Home -> HomeScreen(vm)
                        is Screen.Detail -> DetailScreen(vm, s.app, s.label)
                        Screen.Dashboard -> DashboardScreen(vm)
                        is Screen.Apps -> AppsScreen(vm, s)
                        is Screen.Span -> SpanScreen(vm, s)
                        Screen.Report -> ReportScreen(vm)
                        Screen.Widgets -> WidgetsScreen(vm)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
