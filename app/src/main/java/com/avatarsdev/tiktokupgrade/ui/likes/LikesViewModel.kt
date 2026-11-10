package com.avatarsdev.tiktokupgrade.ui.likes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avatarsdev.tiktokupgrade.TikTokUpgradeApp
import com.avatarsdev.tiktokupgrade.data.AppSettings
import com.avatarsdev.tiktokupgrade.data.db.LikeEntity
import com.avatarsdev.tiktokupgrade.domain.analytics.CsvExporter
import com.avatarsdev.tiktokupgrade.domain.analytics.DayCount
import com.avatarsdev.tiktokupgrade.domain.analytics.LikeAnalytics
import com.avatarsdev.tiktokupgrade.domain.analytics.LikeSummary
import com.avatarsdev.tiktokupgrade.domain.analytics.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** User-picked inclusive range; null = automatic (last 7 days that contain data). */
data class DateRange(val start: LocalDate, val end: LocalDate)

data class Overview(
    val loaded: Boolean = false,
    val total: Int = 0,
    val last7Days: Int = 0,
    val today: Int = 0,
    val peakHour: Int? = null,
    val lastLikeAt: Long? = null,
    val sparkline: List<DayCount> = emptyList(),
)

data class LikesUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val allCount: Int = 0,
    val range: DateRange? = null,
    /** First..last day that has any like in the current data set. */
    val dataRange: DateRange? = null,
    val rangeIsAuto: Boolean = true,
    val query: String = "",
    val order: SortOrder = SortOrder.NEWEST_FIRST,
    val filtered: List<LikeEntity> = emptyList(),
    val daily: List<DayCount> = emptyList(),
    val hourly: IntArray = IntArray(24),
    val summary: LikeSummary = LikeSummary(0, 0, 0.0, null, null, null, null),
    val overview: Overview = Overview(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class LikesViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as TikTokUpgradeApp).container
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val userRange = MutableStateFlow<DateRange?>(null)
    private val query = MutableStateFlow("")
    private val order = MutableStateFlow(SortOrder.NEWEST_FIRST)

    private val likes = container.settings.state
        .map { it.dataSource }
        .distinctUntilChanged()
        .flatMapLatest { container.likes.observe(it) }

    private data class Inputs(val range: DateRange?, val query: String, val order: SortOrder)

    private val inputs = combine(userRange, query, order) { r, q, o -> Inputs(r, q, o) }

    val state: StateFlow<LikesUiState> = combine(container.settings.state, likes, inputs) { settings, all, input ->
        compute(settings, all, input)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LikesUiState())

    private fun compute(settings: AppSettings, all: List<LikeEntity>, input: Inputs): LikesUiState {
        val z = zone
        val dataRange = LikeAnalytics.dataRange(all, z)
        val range = input.range ?: dataRange?.let { (_, last) -> DateRange(last.minusDays(6), last) }
        val filtered = if (range == null) emptyList() else LikeAnalytics.filter(all, range.start, range.end, z, input.query, input.order)
        val daily = if (range == null) emptyList() else LikeAnalytics.daily(filtered, range.start, range.end, z)
        return LikesUiState(
            loaded = true,
            settings = settings,
            allCount = all.size,
            range = range,
            dataRange = dataRange?.let { DateRange(it.first, it.second) },
            rangeIsAuto = input.range == null,
            query = input.query,
            order = input.order,
            filtered = filtered,
            daily = daily,
            hourly = LikeAnalytics.hourly(filtered, z),
            summary = LikeAnalytics.summary(filtered, range?.start, range?.end, z),
            overview = overview(all, z),
        )
    }

    private fun overview(all: List<LikeEntity>, z: ZoneId): Overview {
        if (all.isEmpty()) return Overview(loaded = true)
        val today = LocalDate.now(z)
        val last7 = LikeAnalytics.filter(all, today.minusDays(6), today, z)
        val todayList = LikeAnalytics.filter(all, today, today, z)
        val hours = LikeAnalytics.hourly(all, z)
        return Overview(
            loaded = true,
            total = all.size,
            last7Days = last7.size,
            today = todayList.size,
            peakHour = hours.indices.maxByOrNull { hours[it] },
            lastLikeAt = all.maxOf { it.likedAt },
            sparkline = LikeAnalytics.daily(all, today.minusDays(13), today, z),
        )
    }

    fun setRange(start: LocalDate, end: LocalDate) {
        userRange.value = if (end.isBefore(start)) DateRange(end, start) else DateRange(start, end)
    }

    fun quickRange(days: Long) {
        val today = LocalDate.now(zone)
        userRange.value = DateRange(today.minusDays(days - 1), today)
    }

    fun allTime() {
        state.value.dataRange?.let { userRange.value = it }
    }

    fun autoRange() { userRange.value = null }
    fun setQuery(q: String) { query.value = q }
    fun toggleOrder() {
        order.value = if (order.value == SortOrder.NEWEST_FIRST) SortOrder.OLDEST_FIRST else SortOrder.NEWEST_FIRST
    }

    /** Writes the currently filtered rows (range + search) to [uri] as CSV. */
    fun exportCsv(uri: Uri, onDone: (Result<Int>) -> Unit) {
        val rows = state.value.filtered
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val csv = CsvExporter.toCsv(rows, zone)
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                rows.size
            }
            onDone(result)
        }
    }
}
