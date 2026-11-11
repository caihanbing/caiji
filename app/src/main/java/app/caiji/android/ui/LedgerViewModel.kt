package app.caiji.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.caiji.android.CaijiApplication
import app.caiji.android.backup.BackupCodec
import app.caiji.android.data.LedgerEntry
import app.caiji.android.data.LatestRequestGate
import app.caiji.android.data.LedgerRestoreSink
import app.caiji.android.data.MonthTotals
import app.caiji.android.data.LedgerPeriodTotals
import app.caiji.android.data.MonthLedgerTotal
import app.caiji.android.data.PeriodLedgerReport
import app.caiji.android.data.PeriodRankingSnapshot
import app.caiji.android.data.PeriodRankingRequestGate
import app.caiji.android.data.ReportPeriod
import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.io.OutputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as CaijiApplication).ledgerRepository
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()
    private val _entries = MutableStateFlow<List<LedgerEntry>>(emptyList())
    val entries: StateFlow<List<LedgerEntry>> = _entries.asStateFlow()
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    private val _totals = MutableStateFlow(MonthTotals(BigInteger.ZERO, BigInteger.ZERO))
    val totals: StateFlow<MonthTotals> = _totals.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var monthRequest = 0L
    private val _monthlyReports = MutableStateFlow<List<MonthLedgerTotal>>(emptyList())
    val monthlyReports: StateFlow<List<MonthLedgerTotal>> = _monthlyReports.asStateFlow()
    private val _yearlyReports = MutableStateFlow<List<Pair<Int, LedgerPeriodTotals>>>(emptyList())
    val yearlyReports: StateFlow<List<Pair<Int, LedgerPeriodTotals>>> = _yearlyReports.asStateFlow()
    private val _reportLoading = MutableStateFlow(false)
    val reportLoading: StateFlow<Boolean> = _reportLoading.asStateFlow()
    private val _reportError = MutableStateFlow<String?>(null)
    val reportError: StateFlow<String?> = _reportError.asStateFlow()
    private val _periodReport = MutableStateFlow<PeriodLedgerReport?>(null)
    val periodReport: StateFlow<PeriodLedgerReport?> = _periodReport.asStateFlow()
    private val _periodRankingSnapshot = MutableStateFlow<PeriodRankingSnapshot?>(null)
    internal val periodRankingSnapshot: StateFlow<PeriodRankingSnapshot?> = _periodRankingSnapshot.asStateFlow()
    private val _periodRankingLoading = MutableStateFlow(false)
    internal val periodRankingLoading: StateFlow<Boolean> = _periodRankingLoading.asStateFlow()
    private val _periodRankingError = MutableStateFlow<String?>(null)
    internal val periodRankingError: StateFlow<String?> = _periodRankingError.asStateFlow()
    private val reportRequests = LatestRequestGate()
    private val rankingRequests = PeriodRankingRequestGate()

    init { refresh() }

    fun refresh(): Job {
        invalidatePeriodRankings()
        return viewModelScope.launch {
            runCatching {
                repository.initialize()
                _categories.value = repository.categories()
                refreshMonth()
            }.onFailure { _error.value = it.message ?: "读取账单失败" }
        }
    }

    private fun invalidatePeriodRankings() {
        rankingRequests.invalidate()
        _periodRankingSnapshot.value = null
        _periodRankingLoading.value = false
        _periodRankingError.value = null
    }

    internal fun loadPeriodRankingSnapshot(year: Int, monthValue: Int, force: Boolean = false) {
        val start = if (monthValue == 0) LocalDate.of(year, 1, 1) else YearMonth.of(year, monthValue).atDay(1)
        val end = if (monthValue == 0) LocalDate.of(year + 1, 1, 1) else YearMonth.of(year, monthValue).plusMonths(1).atDay(1)
        val period = ReportPeriod(start, end)
        val current = _periodRankingSnapshot.value
        if (!force && current?.let { it.startInclusive == start && it.endExclusive == end } == true) {
            if (rankingRequests.cancelIfDifferent(period)) _periodRankingLoading.value = false
            _periodRankingError.value = null
            return
        }
        if (!force && _periodRankingLoading.value && rankingRequests.isLoading(period)) return
        val request = rankingRequests.begin(period)
        _periodRankingLoading.value = true
        _periodRankingError.value = null
        viewModelScope.launch {
            runCatching { repository.periodRankingSnapshot(start, end) }
                .onSuccess { if (rankingRequests.isCurrent(request)) _periodRankingSnapshot.value = it }
                .onFailure { if (rankingRequests.isCurrent(request)) _periodRankingError.value = it.message ?: "读取账单报告失败" }
            if (rankingRequests.isCurrent(request)) {
                _periodRankingLoading.value = false
                rankingRequests.complete(request)
            }
        }
    }

    fun changeMonth(month: YearMonth) {
        _month.value = month
        viewModelScope.launch {
            runCatching { refreshMonth(month) }
                .onFailure { _error.value = it.message ?: "读取月度账单失败" }
        }
    }

    private suspend fun refreshMonth(month: YearMonth = _month.value) {
        val request = ++monthRequest
        val snapshot = repository.monthSnapshot(month)
        if (request == monthRequest && month == _month.value) {
            _entries.value = snapshot.entries
            _totals.value = snapshot.totals
        }
    }

    fun loadMonthlyReports(year: Int) = viewModelScope.launch {
        val request = reportRequests.begin()
        _monthlyReports.value = emptyList()
        _reportLoading.value = true
        _reportError.value = null
        runCatching { repository.monthlyReports(year) }
            .onSuccess { if (reportRequests.isCurrent(request)) _monthlyReports.value = it }
            .onFailure { if (reportRequests.isCurrent(request)) _reportError.value = it.message ?: "读取月账单失败" }
        if (reportRequests.isCurrent(request)) _reportLoading.value = false
    }

    fun loadBillLists(year: Int) = viewModelScope.launch {
        val request = reportRequests.begin()
        _monthlyReports.value = emptyList()
        _reportLoading.value = true
        _reportError.value = null
        runCatching { repository.monthlyReports(year) to repository.yearlyReports() }
            .onSuccess { (months, years) ->
                if (reportRequests.isCurrent(request)) {
                    _monthlyReports.value = months
                    _yearlyReports.value = years
                }
            }
            .onFailure { if (reportRequests.isCurrent(request)) _reportError.value = it.message ?: "读取账单失败" }
        if (reportRequests.isCurrent(request)) _reportLoading.value = false
    }

    fun loadYearlyReports() = viewModelScope.launch {
        val request = reportRequests.begin()
        _reportLoading.value = true
        _reportError.value = null
        runCatching { repository.yearlyReports() }
            .onSuccess { if (reportRequests.isCurrent(request)) _yearlyReports.value = it }
            .onFailure { if (reportRequests.isCurrent(request)) _reportError.value = it.message ?: "读取年账单失败" }
        if (reportRequests.isCurrent(request)) _reportLoading.value = false
    }

    fun loadPeriodReport(year: Int, monthValue: Int, type: EntryType, categoryId: String? = null) = viewModelScope.launch {
        val request = reportRequests.begin()
        _periodReport.value = null
        _reportLoading.value = true
        _reportError.value = null
        val start = if (monthValue == 0) LocalDate.of(year, 1, 1) else YearMonth.of(year, monthValue).atDay(1)
        val end = if (monthValue == 0) LocalDate.of(year + 1, 1, 1) else YearMonth.of(year, monthValue).plusMonths(1).atDay(1)
        runCatching { repository.periodReport(start, end, type, categoryId) }
            .onSuccess { if (reportRequests.isCurrent(request)) _periodReport.value = it }
            .onFailure { if (reportRequests.isCurrent(request)) _reportError.value = it.message ?: "读取账单报告失败" }
        if (reportRequests.isCurrent(request)) _reportLoading.value = false
    }

    fun addEntry(type: EntryType, fen: Long, categoryId: String, date: LocalDate, note: String?,
        onSaved: () -> Unit, onFailed: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.addEntry(type, fen, categoryId, date, note) }
                .onSuccess { _error.value = null; refresh(); onSaved() }
                .onFailure { _error.value = it.message ?: "保存账单失败"; onFailed() }
        }
    }

    fun updateEntry(entry: LedgerEntry, onSaved: () -> Unit, onFailed: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.updateEntry(entry) }
                .onSuccess { _error.value = null; refresh(); onSaved() }
                .onFailure { _error.value = it.message ?: "保存账单失败"; onFailed() }
        }
    }

    fun deleteEntry(id: String, onDeleted: () -> Unit, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.deleteEntry(id) }
                .onSuccess { _error.value = null; refresh(); onDeleted() }
                .onFailure { _error.value = it.message ?: "删除账单失败"; onFailed() }
        }
    }

    fun saveCategory(type: EntryType, oldId: String?, name: String, icon: String,
        onSaved: () -> Unit, onFailed: () -> Unit) {
        viewModelScope.launch {
            runCatching { if (oldId == null) repository.createCategory(type, name, icon) else repository.renameCategory(oldId, name, icon) }
                .onSuccess { _error.value = null; refresh(); onSaved() }
                .onFailure { _error.value = it.message ?: "保存分类失败"; onFailed() }
        }
    }

    fun setCategoryActive(categoryId: String, active: Boolean, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.setCategoryActive(categoryId, active); _error.value = null; refresh() }
                .onSuccess { onComplete(true) }
                .onFailure { _error.value = it.message ?: "更新分类失败"; onComplete(false) }
        }
    }

    fun reorderCategory(categoryId: String, order: Int, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.reorderCategory(categoryId, order); _error.value = null; refresh() }
                .onSuccess { onComplete(true) }
                .onFailure { _error.value = it.message ?: "更新分类顺序失败"; onComplete(false) }
        }
    }

    suspend fun beginRestoreSession() = repository.beginRestoreSession()

    suspend fun endRestoreSession() = repository.endRestoreSession()

    suspend fun replaceAllForRestore(restore: suspend (LedgerRestoreSink) -> Unit) {
        repository.replaceAllForRestore(restore)
        _error.value = null
        refresh()
    }

    fun clearError() { _error.value = null }

    suspend fun hasExistingData(): Boolean = repository.hasExistingData()

    suspend fun backupData() = repository.backupData()

    suspend fun entryById(id: String): LedgerEntry? = runCatching { repository.entryById(id) }
        .onFailure { _error.value = it.message ?: "读取账单失败" }
        .getOrNull()

    suspend fun exportBackup(password: String, output: OutputStream) {
        repository.withBackupSnapshot { categories, entryCount, streamEntries ->
            BackupCodec().exportTo(password, categories, entryCount, output, streamEntries)
        }
    }
}
