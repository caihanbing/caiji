package app.caiji.android.ui

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.caiji.android.data.RoomSalaryRepository
import app.caiji.android.data.SalaryDatabase
import app.caiji.android.data.LedgerEntry
import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiTheme
import app.caiji.android.ui.theme.CaijiPalette
import app.caiji.android.ui.theme.CaijiThemeId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SalaryScreensTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: SalaryDatabase
    private lateinit var repository: RoomSalaryRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SalaryDatabase::class.java).build()
        repository = RoomSalaryRepository(database)
        runBlocking { repository.initialize() }
    }
    @After fun tearDown() { database.close() }

    private fun enterSalaryAmount(field: String, amount: String, replace: Boolean = true) {
        compose.onNodeWithTag("salary-field-$field", useUnmergedTree = true).performScrollTo().performClick()
        if (replace) repeat(32) { compose.onNodeWithTag("salary-key-⌫").performClick() }
        amount.forEach { compose.onNodeWithTag("salary-key-$it").performClick() }
    }

    private fun assertContainsPaletteColor(tag: String, expected: Color) {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        val red = (expected.red * 255).toInt()
        val green = (expected.green * 255).toInt()
        val blue = (expected.blue * 255).toInt()
        val found = (0 until pixels.height).any { y -> (0 until pixels.width).any { x ->
            val pixel = pixels[x, y]
            pixel.alpha > 0.5f && kotlin.math.abs((pixel.red * 255).toInt() - red) <= 8 &&
                kotlin.math.abs((pixel.green * 255).toInt() - green) <= 8 &&
                kotlin.math.abs((pixel.blue * 255).toInt() - blue) <= 8
        } }
        assertTrue("$tag should render with the expected palette color", found)
    }

    private fun assertCenterPaletteColor(tag: String, expected: Color) {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        val pixel = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue("$tag center should use the expected palette color",
            kotlin.math.abs(pixel.red - expected.red) < 0.04f &&
                kotlin.math.abs(pixel.green - expected.green) < 0.04f &&
                kotlin.math.abs(pixel.blue - expected.blue) < 0.04f)
    }

    @Test fun savesSalaryAndShowsItsIndependentSummary() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("还没有工资记录").assertIsDisplayed()
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-income-type-builtin-1").performScrollTo().performClick()
        compose.onNodeWithTag("salary-income-type-builtin-1").assertContentDescriptionEquals("工资（交行 到账时间），已选择")
        compose.onNodeWithTag("salary-key-1").performClick()
        compose.onNodeWithTag("salary-key-0").performClick()
        compose.onNodeWithTag("salary-key-0").performClick()
        compose.onNodeWithTag("salary-active-amount").assertTextContains("100.00")
        compose.onNodeWithTag("salary-key-save").assertIsEnabled().performClick()
        compose.onAllNodesWithText("收入性质").assertCountEquals(0)
        compose.onAllNodesWithText("收入项管理").assertCountEquals(0)
        compose.onNodeWithTag("salary-net-total", useUnmergedTree = true).assertTextContains("100.00", substring = true)
        compose.onNodeWithText("本年纯收入").assertIsDisplayed()
    }

    @Test fun salaryStartsAsCashWithoutSelectionAndSwitchingClearsOnlyIncomeType() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-cash-tab").assertIsDisplayed()
        compose.onAllNodesWithTag("salary-active-amount").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-key-save").assertCountEquals(0)
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        compose.onNodeWithTag("salary-active-amount").assertTextContains("0.00")
        compose.onNodeWithTag("salary-key-1").performClick()
        compose.onNodeWithTag("salary-income-noncash-tab").performClick()
        compose.onAllNodesWithTag("salary-key-save").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-active-amount").assertCountEquals(0)
        compose.onNodeWithTag("salary-income-type-builtin-4").performClick()
        compose.onNodeWithTag("salary-key-save").assertIsEnabled()
        compose.onNodeWithTag("salary-income-noncash-tab").assertIsSelected()
        compose.onNodeWithTag("salary-cash-tab").assertIsNotSelected()
    }

    @Test fun salaryAmountDisplayMovesIntoKeypadDockAndFollowsActiveDeduction() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        compose.onNodeWithTag("salary-active-amount-label", useUnmergedTree = true).assertTextContains("收入")
        compose.onNodeWithTag("salary-input-dock").assertIsDisplayed()
        compose.onNodeWithTag("salary-field-养老保险", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("salary-active-amount-label", useUnmergedTree = true).assertTextContains("养老保险")
        compose.onNodeWithTag("salary-key-1").performClick()
        compose.onNodeWithTag("salary-active-amount").assertTextEquals("1.00")
    }

    @Test fun unselectedNewSalaryHidesTheWholeAmountSummaryAndKeypadPanel() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onAllNodesWithTag("salary-input-dock").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-editor-net").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-deductions-toggle").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-key-1").assertCountEquals(0)
    }

    @Test fun salaryHeaderBackWithEnteredAmountConfirmsAndPreservesCancelledDraft() {
        val enteredDraft = java.util.concurrent.atomic.AtomicReference<SalaryDraft?>(null)
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {},
            onDraftSnapshotChange = { value, _ -> if (value != null) enteredDraft.set(value) }) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        enterSalaryAmount("收入", "1", replace = false)
        assertEquals("1", enteredDraft.get()?.gross)
        compose.onNodeWithTag("salary-active-amount").assertTextEquals("1.00")
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithText("放弃本次收入？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("salary-active-amount").assertTextEquals("1.00")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("salary-discard-dialog").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
    }

    @Test fun salaryHeaderBackWithEmptyNewDraftReturnsWithoutConfirmation() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onAllNodesWithText("放弃本次收入？").assertCountEquals(0)
        compose.onNodeWithText("＋  新增收入").assertIsDisplayed()
    }

    @Test fun editingSalaryOpensAmountDockAndKeypadByDefault() {
        val incomeType = runBlocking { repository.incomeTypes().first { it.active && it.isCash } }
        val entry = app.caiji.android.data.SalaryEntry(
            id = "edit-opens-keypad",
            occurredOn = java.time.LocalDate.now(),
            incomeTypeId = incomeType.id,
            grossFen = 123_450,
            isCash = true,
            createdAtMillis = 1L,
            updatedAtMillis = 1L
        )
        runBlocking { repository.save(entry) }
        compose.setContent {
            CaijiTheme {
                SalaryRootScreen(repository, null, {}, {}, {}, initialDraft = SalaryDraft.from(entry), initialEntry = entry)
            }
        }
        compose.onNodeWithTag("salary-active-amount").assertTextContains("1,234.50")
        compose.onNodeWithTag("salary-key-save").assertIsDisplayed()
    }

    @Test fun salaryFieldSelectorContainsAllEightFieldsAndCanScrollToTax() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onAllNodesWithTag("salary-field-selector").assertCountEquals(0)
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        listOf("收入", "工会费", "养老保险", "医疗保险", "失业保险", "公积金", "企业年金", "税").forEach { field ->
            compose.onNodeWithTag("salary-field-$field", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun salaryEntryDockFits320DpLargeTextAcrossAllThemes() {
        compose.setContent {
            CaijiTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.size(width = 320.dp, height = 760.dp)) {
                        SalaryRootScreen(repository, null, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        enterSalaryAmount("收入", "123456789.12", replace = false)
        compose.onNodeWithTag("salary-field-税", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("salary-tax-sign").performClick()
        enterSalaryAmount("税", "12.34", replace = false)
        CaijiThemeId.entries.forEach { theme ->
            compose.runOnIdle { CaijiPalette.select(theme) }
            compose.onNodeWithTag("salary-field-selector").assertIsDisplayed()
            compose.onNodeWithTag("salary-active-amount").assertIsDisplayed()
            compose.onNodeWithTag("salary-key-save").assertIsDisplayed()
            val signBounds = compose.onNodeWithTag("salary-tax-sign").fetchSemanticsNode().boundsInRoot
            assertTrue("tax sign control should retain a 48dp target", signBounds.width >= 48f)
        }
        compose.runOnIdle { CaijiPalette.select(CaijiThemeId.AMBER) }
    }

    @Test fun editingSalaryWithChangedAmountConfirmsDiscardButUnchangedEditDoesNot() {
        val type = runBlocking { repository.incomeTypes().first { it.active && it.isCash } }
        val entry = app.caiji.android.data.SalaryEntry("edit-discard-amount", java.time.LocalDate.now(), type.id, 100L,
            isCash = true, createdAtMillis = 1L, updatedAtMillis = 1L)
        runBlocking { repository.save(entry) }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-entry-row-edit-discard-amount").performClick()
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onAllNodesWithText("放弃本次收入？").assertCountEquals(0)
        compose.onNodeWithTag("salary-entry-row-edit-discard-amount").performClick()
        enterSalaryAmount("收入", "2.00")
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithText("放弃本次收入？").assertIsDisplayed()
    }

    @Test fun salaryHomeGroupsCashAndNonCashTotalsAndAnnualSummaryReconcilesYears() {
        val type = runBlocking { repository.incomeTypes().first { it.isCash } }
        val nonCashType = runBlocking { repository.incomeTypes().first { !it.isCash } }
        val now = java.time.LocalDate.now()
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry("same-month-new", now.withDayOfMonth(5), type.id, 10_000, 2_000,
                isCash = type.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
            repository.save(app.caiji.android.data.SalaryEntry("same-month-new-2", now.withDayOfMonth(6), nonCashType.id, 5_000,
                isCash = !type.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
            repository.save(app.caiji.android.data.SalaryEntry("same-month-old", now.minusYears(1).withDayOfMonth(5), type.id, 20_000,
                isCash = type.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
        }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        val newerGroup = "salary-month-group-${now.year}-${now.monthValue.toString().padStart(2, '0')}"
        compose.onNodeWithTag(newerGroup).assertIsDisplayed()
        compose.onAllNodesWithTag("salary-month-group-${now.year - 1}-${now.monthValue.toString().padStart(2, '0')}")
            .assertCountEquals(0)
        val currentMonth = "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
        compose.onNodeWithTag("salary-group-cash-total-$currentMonth", useUnmergedTree = true).assertTextEquals("现金 ¥80.00")
        compose.onNodeWithTag("salary-group-noncash-total-$currentMonth", useUnmergedTree = true).assertTextEquals("非现金 ¥50.00")
        compose.onAllNodesWithTag("salary-month-divider-$currentMonth").assertCountEquals(1)
        compose.onAllNodesWithText("收入明细").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-detail-total").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-home-filter-ALL").assertCountEquals(0)
        compose.onNodeWithTag("salary-month-title-${now.year}-${now.monthValue}", useUnmergedTree = true)
            .assertTextEquals("${now.year}年${now.monthValue}月")

        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-year-row-${now.year}").assertTextContains("¥150.00")
            .assertTextContains("¥20.00").assertTextContains("¥130.00")
        compose.onNodeWithTag("salary-year-row-${now.year - 1}").assertTextContains("¥200.00")
            .assertTextContains("¥0.00")
    }

    @Test fun salaryAnnualSummaryTableUsesFourColumnsAndOpensSelectedYearReport() {
        val types = runBlocking { repository.incomeTypes() }
        val cash = types.first { it.isCash }
        val nonCash = types.first { !it.isCash }
        val now = java.time.LocalDate.now()
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry("table-cash", now, cash.id, 1_000_000, unionFen = 100_000,
                isCash = true, createdAtMillis = 1L, updatedAtMillis = 1L))
            repository.save(app.caiji.android.data.SalaryEntry("table-noncash", now, nonCash.id, 500_000, taxFen = -50_000,
                isCash = false, createdAtMillis = 2L, updatedAtMillis = 2L))
            repository.save(app.caiji.android.data.SalaryEntry("table-old", now.minusYears(2), cash.id, 1_000_000,
                isCash = true, createdAtMillis = 3L, updatedAtMillis = 3L))
        }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-open-summary").performClick()

        compose.onNodeWithTag("salary-net-total").assertTextContains("24,500.00", substring = true)
        compose.onNodeWithTag("salary-year-table-scroll").assertIsDisplayed()
        compose.onNodeWithText("年份").assertIsDisplayed()
        compose.onNodeWithText("收入").assertIsDisplayed()
        compose.onNodeWithText("扣款").assertIsDisplayed()
        compose.onNodeWithText("纯收入").assertIsDisplayed()
        compose.onNodeWithTag("salary-year-row-${now.year}").assertTextContains("${now.year}年")
        compose.onNodeWithTag("salary-year-row-${now.year}").assertTextContains("¥15,000.00")
            .assertTextContains("¥500.00").assertTextContains("¥14,500.00")
        compose.onNodeWithTag("salary-year-row-${now.year - 1}").assertTextContains("¥0.00")
        compose.onNodeWithTag("salary-year-row-${now.year - 2}").assertTextContains("¥10,000.00")

        compose.onNodeWithTag("salary-year-row-${now.year}").performClick()
        compose.onNodeWithTag("page-title").assertTextEquals("${now.year}年报告")
        compose.onNodeWithTag("salary-report-net-total").assertTextContains("14,500.00", substring = true)
        compose.onNodeWithTag("salary-income-ranking-${cash.id}").assertTextContains("+¥9,000.00")
        compose.onNodeWithTag("salary-income-ranking-${nonCash.id}").assertTextContains("+¥5,500.00")
        assertContainsPaletteColor("salary-income-ranking-${cash.id}-amount", CaijiPalette.Income)
    }

    @Test fun salaryAnnualTableScrollsWideAmountsAtCompactLargeFont() {
        val cash = runBlocking { repository.incomeTypes().first { it.isCash } }
        val now = java.time.LocalDate.now()
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry("table-long-a", now, cash.id, Long.MAX_VALUE,
                isCash = true, createdAtMillis = 1L, updatedAtMillis = 1L))
            repository.save(app.caiji.android.data.SalaryEntry("table-long-b", now.minusDays(1), cash.id, Long.MAX_VALUE,
                isCash = true, createdAtMillis = 2L, updatedAtMillis = 2L))
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CaijiTheme { Box(Modifier.width(320.dp).height(720.dp)) {
                    SalaryRootScreen(repository, null, {}, {}, {})
                } }
            }
        }
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-year-row-${now.year}").assertTextContains("¥184,467,440,737,095,516.14")
        val incomeHeader = compose.onNodeWithTag("salary-year-table-header-income").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("salary-year-table-scroll").performTouchInput { swipeLeft() }
        val scrolledIncomeHeader = compose.onNodeWithTag("salary-year-table-header-income").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Wide salary annual table should scroll its heading and values together", scrolledIncomeHeader < incomeHeader)
    }

    @Test fun annualReportAndIncomeDetailRestoreScrollForSameSelectionAndResetForNewSelection() {
        val types = runBlocking { repository.incomeTypes() }
        val now = java.time.LocalDate.now()
        val previousYear = now.year - 1
        val entries = mutableListOf<app.caiji.android.data.SalaryEntry>()
        types.forEach { type ->
            for (month in 1..12) {
                entries += app.caiji.android.data.SalaryEntry(
                    "scroll-$previousYear-${type.id}-$month", java.time.LocalDate.of(previousYear, month, 10), type.id,
                    (type.sortOrder + 1L) * 100_000L, isCash = type.isCash,
                    createdAtMillis = month.toLong(), updatedAtMillis = month.toLong()
                )
            }
            for (month in 1..now.monthValue) {
                entries += app.caiji.android.data.SalaryEntry(
                    "scroll-${now.year}-${type.id}-$month", java.time.LocalDate.of(now.year, month, 1), type.id,
                    (type.sortOrder + 1L) * 100_000L, isCash = type.isCash,
                    createdAtMillis = month.toLong(), updatedAtMillis = month.toLong()
                )
            }
        }
        runBlocking { entries.forEach { repository.save(it) } }
        val priorRanking = app.caiji.android.data.SalaryReports.incomeTypeTotals(
            entries.filter { it.occurredOn.year == previousYear }, types
        )
        val currentRanking = app.caiji.android.data.SalaryReports.incomeTypeTotals(
            entries.filter { it.occurredOn.year == now.year }, types
        )
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-year-row-$previousYear").performClick()
        val priorLastType = priorRanking.last().incomeType
        compose.onNodeWithTag("salary-year-report-ranking-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-income-ranking-${priorLastType.id}"))
        compose.onNodeWithTag("salary-income-ranking-${priorLastType.id}").performClick()
        val priorOldestEntry = entries.first { it.occurredOn.year == previousYear && it.incomeTypeId == priorLastType.id }
        compose.onNodeWithTag("salary-record-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-entry-row-${priorOldestEntry.id}"))
        compose.onNodeWithTag("salary-entry-row-${priorOldestEntry.id}").assertIsDisplayed()
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-income-ranking-${priorLastType.id}").assertIsDisplayed()

        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-year-row-${now.year}").performClick()
        compose.onNodeWithTag("salary-income-ranking-${currentRanking.first().incomeType.id}").assertIsDisplayed()

        compose.onNodeWithTag("salary-year-report-ranking-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-income-ranking-${priorLastType.id}"))
        compose.onNodeWithTag("salary-income-ranking-${priorLastType.id}").performClick()
        val currentLatestSameType = entries.last { it.occurredOn.year == now.year && it.incomeTypeId == priorLastType.id }
        compose.onNodeWithTag("salary-entry-row-${currentLatestSameType.id}").assertIsDisplayed()
        compose.onNodeWithTag("salary-header-back").performClick()

        val currentFirstType = currentRanking.first().incomeType
        compose.onNodeWithTag("salary-year-report-ranking-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-income-ranking-${currentFirstType.id}"))
        compose.onNodeWithTag("salary-income-ranking-${currentFirstType.id}").performClick()
        val currentLatestEntry = entries.last { it.occurredOn.year == now.year && it.incomeTypeId == currentFirstType.id }
        compose.onNodeWithTag("salary-entry-row-${currentLatestEntry.id}").assertIsDisplayed()
    }

    @Test fun salaryIncomeRankingOpensFilteredDetailsAndRefreshesAfterEditAndDelete() {
        val types = runBlocking { repository.incomeTypes() }
        val cash = types.first { it.isCash }
        val nonCash = types.first { !it.isCash }
        val now = java.time.LocalDate.now()
        val current = app.caiji.android.data.SalaryEntry("detail-current", now, cash.id, 100_000, unionFen = 10_000,
            isCash = true, createdAtMillis = 1L, updatedAtMillis = 1L)
        runBlocking {
            repository.save(current)
            repository.save(app.caiji.android.data.SalaryEntry("detail-prior-month", now.minusMonths(1), cash.id, 25_000,
                isCash = true, createdAtMillis = 4L, updatedAtMillis = 4L))
            repository.save(app.caiji.android.data.SalaryEntry("detail-other-year", now.minusYears(1), cash.id, 300_000,
                isCash = true, createdAtMillis = 2L, updatedAtMillis = 2L))
            repository.save(app.caiji.android.data.SalaryEntry("detail-other-type", now, nonCash.id, 50_000,
                isCash = false, createdAtMillis = 3L, updatedAtMillis = 3L))
        }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-year-row-${now.year}").performClick()
        compose.onNodeWithTag("salary-income-ranking-${cash.id}").assertIsDisplayed()
        compose.onNodeWithTag("salary-income-ranking-${cash.id}").performClick()

        compose.onNodeWithTag("salary-income-detail-title", useUnmergedTree = true).assertTextEquals(cash.name)
        compose.onNodeWithTag("salary-net-total").assertTextContains("1,650.00", substring = true)
        compose.onAllNodesWithText("该收入项暂无工资记录").assertCountEquals(0)
        compose.onNodeWithTag("salary-record-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-entry-row-detail-current"))
        compose.onNodeWithTag("salary-entry-row-detail-current").assertIsDisplayed()
        assertContainsPaletteColor("salary-entry-amount-detail-current", CaijiPalette.Income)
        compose.onNodeWithTag("salary-entry-date-detail-current", useUnmergedTree = true)
            .assertTextEquals("${now.monthValue}月${now.dayOfMonth}日 · 现金")
        compose.onNodeWithTag("salary-entry-row-detail-prior-month").assertIsDisplayed()
        val currentMonthKey = "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
        val previousMonth = now.minusMonths(1)
        val previousMonthKey = "${previousMonth.year}-${previousMonth.monthValue.toString().padStart(2, '0')}"
        compose.onAllNodesWithTag("salary-month-group-$currentMonthKey").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-month-group-$previousMonthKey").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-entry-row-detail-other-year").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-entry-row-detail-other-type").assertCountEquals(0)

        compose.onNodeWithTag("salary-entry-row-detail-current").performClick()
        enterSalaryAmount("收入", "1200.00")
        compose.onNodeWithTag("salary-key-save").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("salary-income-detail-title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("salary-income-detail-title", useUnmergedTree = true).assertTextEquals(cash.name)
        compose.onNodeWithTag("salary-net-total").assertTextContains("1,850.00", substring = true)
        compose.onNodeWithTag("salary-record-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-entry-row-detail-current"))
        compose.onNodeWithTag("salary-entry-row-detail-current").assertTextContains("+¥1,100.00", substring = true)

        compose.onNodeWithTag("salary-entry-row-detail-current").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("salary-delete-detail-current").performClick()
        compose.onNodeWithTag("salary-list-confirm-delete").performClick()
        compose.waitUntil(5_000) { runBlocking { repository.entries().none { it.id == current.id } } }
        compose.onNodeWithTag("salary-entry-row-detail-prior-month").assertIsDisplayed()
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-income-ranking-${cash.id}").assertIsDisplayed()
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-year-row-${now.year}").assertTextContains("¥750.00")
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-year-label").assertTextEquals("${now.year}年")
    }

    @Test fun monthlyCashSummariesUseBigIntegerAndStayRightAlignedAtLargeFont() {
        val types = runBlocking { repository.incomeTypes() }
        val cash = types.first { it.isCash }
        val nonCash = types.first { !it.isCash }
        val now = java.time.LocalDate.now()
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry("large-cash-a", now, cash.id, Long.MAX_VALUE,
                isCash = true, createdAtMillis = 1L, updatedAtMillis = 1L))
            repository.save(app.caiji.android.data.SalaryEntry("large-cash-b", now, cash.id, Long.MAX_VALUE,
                isCash = true, createdAtMillis = 2L, updatedAtMillis = 2L))
            repository.save(app.caiji.android.data.SalaryEntry("large-noncash", now, nonCash.id, Long.MAX_VALUE,
                isCash = false, createdAtMillis = 3L, updatedAtMillis = 3L))
        }
        val width = 320.dp
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CaijiTheme {
                    Box(Modifier.width(width).height(760.dp)) {
                        SalaryRootScreen(repository, null, {}, {}, {})
                    }
                }
            }
        }
        val monthKey = "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
        val title = compose.onNodeWithTag("salary-month-title-${now.year}-${now.monthValue}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithTag("salary-month-summary-$monthKey", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("salary-record-list").fetchSemanticsNode().boundsInRoot
        val expectedRight = with(compose.density) { list.right - 18.dp.toPx() }
        assertTrue("Monthly summary should not overlap the month label", title.right <= summary.left)
        assertTrue("Monthly summary should keep an 18dp right inset", kotlin.math.abs(summary.right - expectedRight) <= 1f)
        compose.onNodeWithTag("salary-group-cash-total-$monthKey", useUnmergedTree = true)
            .assertTextEquals("现金 ¥${formatYuan(java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.valueOf(2)))}")
        compose.onNodeWithTag("salary-group-noncash-total-$monthKey", useUnmergedTree = true)
            .assertTextEquals("非现金 ¥${formatYuan(java.math.BigInteger.valueOf(Long.MAX_VALUE))}")
    }

    @Test fun salaryRowsSwipeToConfirmDeleteAndRefreshTotals() {
        val type = runBlocking { repository.incomeTypes().first() }
        val row = app.caiji.android.data.SalaryEntry("swipe-delete-row", java.time.LocalDate.now(), type.id, 42_500,
            isCash = type.isCash, createdAtMillis = 1L, updatedAtMillis = 1L)
        runBlocking { repository.save(row) }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }

        compose.onNodeWithTag("salary-entry-row-${row.id}").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("salary-delete-${row.id}").assertIsDisplayed().performClick()
        compose.onNodeWithTag("salary-list-delete-dialog").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(1, runBlocking { repository.entries().size })

        compose.onNodeWithTag("salary-entry-row-${row.id}").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("salary-delete-${row.id}").performClick()
        compose.onNodeWithTag("salary-list-confirm-delete").performClick()
        compose.waitUntil(5_000) { runBlocking { repository.entries().isEmpty() } }
        compose.onNodeWithText("还没有工资记录").assertIsDisplayed()
        compose.onNodeWithTag("salary-net-total").assertTextContains("0.00", substring = true)
    }

    @Test fun incomeTypeManagementUsesCategoryControlsAndPersistsActiveOrder() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {},
            initialManagementRoute = SalaryManagementRoute.TYPES) } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("salary-income-type-move-up-builtin-1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("salary-income-type-move-up-builtin-1").assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("编辑收入项").assertCountEquals(0)
        compose.onNodeWithTag("salary-income-type-move-down-builtin-1").performClick()
        compose.waitUntil(5_000) { runBlocking { repository.incomeTypes().first().id == "builtin-2" } }
        compose.onNodeWithTag("salary-income-type-active-builtin-1").performClick()
        compose.waitUntil(5_000) { runBlocking { !repository.incomeTypes().first { it.id == "builtin-1" }.active } }
        compose.onNodeWithText("已停用").assertIsDisplayed()
        compose.onNodeWithTag("salary-income-type-move-up-builtin-1").assertIsEnabled()
    }

    @Test fun editingPreservesValuesAndRemovesNatureAndManagementRows() {
        val type = runBlocking { repository.incomeTypes().first() }
        val entry = app.caiji.android.data.SalaryEntry("edit-keeps-values", java.time.LocalDate.now(), type.id, 12_345, 123,
            isCash = type.isCash, createdAtMillis = 1L, updatedAtMillis = 1L)
        runBlocking { repository.save(entry) }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-entry-row-edit-keeps-values").performClick()
        compose.onNodeWithTag("salary-active-amount").assertTextEquals("123.45")
        compose.onNodeWithTag("salary-key-save").assertIsDisplayed()
        compose.onNodeWithTag("salary-field-工会费", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("salary-active-amount").assertTextEquals("1.23")
        compose.onAllNodesWithText("收入性质").assertCountEquals(0)
        compose.onAllNodesWithText("收入项管理").assertCountEquals(0)
    }

    @Test fun disabledIncomeTypeIsClearedFromAnUnsavedDraftAndCannotBeSelected() {
        val disabled = runBlocking { repository.incomeTypes().first { it.id == "builtin-1" } }
        runBlocking { repository.setIncomeTypeActive(disabled.id, false) }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {},
            initialDraft = SalaryDraft.blank(disabled.id)) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("所选收入项已停用，请重新选择启用的收入项。").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("salary-disabled-type-notice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("salary-cash-tab").assertIsDisplayed()
        compose.onAllNodesWithTag("salary-key-save").assertCountEquals(0)
        val active = runBlocking { repository.incomeTypes().first { it.active } }
        compose.onNodeWithTag("salary-income-type-${active.id}").performClick()
        compose.onAllNodesWithText("所选收入项已停用，请重新选择启用的收入项。").assertCountEquals(0)
        compose.onAllNodesWithText("所选收入项已停用，请重新选择启用的收入项。").assertCountEquals(0)
    }

    @Test fun settingsIncomeTypeManagementAddsAndEditsIconsWithoutChangingCashNature() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {},
            initialManagementRoute = SalaryManagementRoute.TYPES) } }
        compose.onNodeWithText("收入项管理").assertIsDisplayed()
        compose.onNodeWithTag("salary-income-cash-tab").assertIsSelected()
        compose.onNodeWithTag("salary-income-types-add").assertIsDisplayed().performClick()
        compose.onNodeWithText("添加收入项").assertIsDisplayed()
        compose.onNodeWithText("现金收入").assertIsDisplayed()
        compose.onAllNodesWithTag("salary-income-type-cash").assertCountEquals(0)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("salary-income-noncash-tab").performClick()
        compose.onNodeWithTag("salary-income-noncash-tab").assertIsSelected()
        compose.onNodeWithTag("salary-income-types-add").performClick()
        compose.onNodeWithText("非现金收入").assertIsDisplayed()
        compose.onNodeWithTag("salary-income-type-name").performTextInput("自定义福利")
        compose.onNodeWithTag("salary-income-type-icon-grid").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("category-icon-allowance"))
        compose.onNodeWithTag("category-icon-allowance").performClick()
        compose.onNodeWithTag("salary-income-type-save").performClick()

        compose.waitUntil(5_000) { runBlocking { repository.incomeTypes().any { it.name == "自定义福利" } } }
        val created = runBlocking { repository.incomeTypes().first { it.name == "自定义福利" } }
        compose.onNodeWithTag("salary-income-noncash-tab").assertIsSelected()
        compose.onNodeWithTag("salary-income-types-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-income-type-row-${created.id}"))
        compose.onNodeWithTag("salary-income-type-row-${created.id}").assertIsDisplayed()
        compose.onNodeWithTag("salary-income-type-icon-${created.id}", useUnmergedTree = true).assertTextContains("💵")
        compose.onNodeWithTag("salary-income-type-name-${created.id}").performClick()
        compose.onNodeWithText("编辑收入项").assertIsDisplayed()
        compose.onNodeWithText("非现金收入").assertIsDisplayed()
        compose.onNodeWithTag("salary-income-type-name").performTextClearance()
        compose.onNodeWithTag("salary-income-type-name").performTextInput("自定义福利新名")
        compose.onNodeWithTag("salary-income-type-icon-grid").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("category-icon-refund"))
        compose.onNodeWithTag("category-icon-refund").performClick()
        compose.onNodeWithText("保存").performClick()

        compose.waitUntil(5_000) { runBlocking { repository.incomeTypes().any { it.name == "自定义福利新名" } } }
        compose.onNodeWithTag("salary-income-type-row-${created.id}").assertIsDisplayed()
        compose.onNodeWithTag("salary-income-type-icon-${created.id}", useUnmergedTree = true).assertTextContains("💳")
        val edited = runBlocking { repository.incomeTypes().first { it.id == created.id } }
        assertEquals("自定义福利新名", edited.name)
        assertEquals(false, edited.isCash)
        assertEquals("refund", edited.iconKey)
        compose.onAllNodesWithText("新增收入项").assertCountEquals(0)
    }

    @Test fun salaryDraftSurvivesLeavingTheBottomNavigationAndReturning() {
        var salaryPage by androidx.compose.runtime.mutableStateOf(true)
        var draft by androidx.compose.runtime.mutableStateOf<SalaryDraft?>(null)
        var entry by androidx.compose.runtime.mutableStateOf<app.caiji.android.data.SalaryEntry?>(null)
        var cashNature by androidx.compose.runtime.mutableStateOf<Boolean?>(null)
        var keypadExpanded by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            CaijiTheme {
                Column(Modifier.fillMaxSize()) {
                    if (salaryPage) {
                        Box(Modifier.weight(1f)) {
                            SalaryRootScreen(repository, null, {}, {}, {}, draft, entry, onDraftSnapshotChange = { updatedDraft, updatedEntry ->
                                draft = updatedDraft
                                entry = updatedEntry
                            }, initialCashNature = cashNature, onCashNatureChange = { cashNature = it },
                                initialKeyboardExpanded = keypadExpanded, onKeyboardExpandedChange = { keypadExpanded = it })
                        }
                        TextButton(onClick = { salaryPage = false }) { Text("切换主页面") }
                    } else {
                        TextButton(onClick = { salaryPage = true }) { Text("回到工资页") }
                    }
                }
            }
        }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        enterSalaryAmount("收入", "235.50")
        compose.onNodeWithText("切换主页面").performClick()
        compose.onNodeWithText("回到工资页").performClick()
        compose.onNodeWithTag("salary-field-收入", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("salary-active-amount").assertTextContains("235.50", substring = true)
        compose.onNodeWithTag("salary-key-save").performClick()
        compose.onNodeWithTag("salary-net-total", useUnmergedTree = true).assertTextContains("235.50", substring = true)
    }

    @Test fun editorHidesBottomNavigationAndBackToHomeRestoresIt() {
        var showBottomNavigation by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            CaijiTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        SalaryRootScreen(repository, null, {}, {}, {}, onBottomNavigationVisibility = { showBottomNavigation = it })
                    }
                    if (showBottomNavigation) Text("工资主导航")
                }
            }
        }
        compose.onNodeWithText("工资主导航").assertIsDisplayed()
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onAllNodesWithText("工资主导航").assertCountEquals(0)
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithText("工资主导航").assertIsDisplayed()
        compose.onNodeWithText("工资主导航").assertIsDisplayed()
    }

    @Test fun saveFailureShowsFeedbackAndRetainsTheFormInput() {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_salary_entry BEFORE INSERT ON salary_entries BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        var failure by androidx.compose.runtime.mutableStateOf<String?>(null)
        compose.setContent {
            CaijiTheme {
                Column {
                    Box(Modifier.weight(1f)) {
                        SalaryRootScreen(repository, null, {}, {}, {}, onAsyncError = { failure = it })
                    }
                    failure?.let { BackupFeedbackBanner(it, backupFeedbackTone(it), {}) }
                }
            }
        }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        enterSalaryAmount("收入", "123.45")
        compose.onNodeWithTag("salary-key-save").performClick()
        compose.waitUntil(5_000) { failure != null }
        compose.onAllNodesWithText("工资保存失败", substring = true).assertCountEquals(2)
        assertTrue(failure.orEmpty().contains("输入内容已保留"))
        compose.onNodeWithTag("salary-active-amount").assertTextContains("123.45", substring = true)
        assertEquals(0, runBlocking { repository.entries().size })
    }

    @Test fun importFailureShowsFeedbackAndRetainsTheImportPreview() {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_salary_entry BEFORE INSERT ON salary_entries BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        val type = runBlocking { repository.incomeTypes().first() }
        val now = System.currentTimeMillis()
        val row = app.caiji.android.data.SalaryEntry("import-failure-row", java.time.LocalDate.now(), type.id, 12345,
            isCash = type.isCash, createdAtMillis = now, updatedAtMillis = now)
        val preview = app.caiji.android.data.SalaryImportPreview(listOf(row), emptyList(), 1, emptyList())
        var failure by androidx.compose.runtime.mutableStateOf<String?>(null)
        compose.setContent {
            CaijiTheme {
                Column {
                    Box(Modifier.weight(1f)) {
                        SalaryRootScreen(repository, preview, {}, {}, {}, onAsyncError = { failure = it })
                    }
                    failure?.let { BackupFeedbackBanner(it, backupFeedbackTone(it), {}) }
                }
            }
        }
        compose.onNodeWithText("确认导入").performClick()
        compose.waitUntil(5_000) { failure != null }
        compose.onNodeWithText("工资导入未完成", substring = true).assertIsDisplayed()
        assertTrue(failure.orEmpty().contains("已有工资记录仍保留"))
        compose.onNodeWithText("新增 1 笔", substring = true).assertIsDisplayed()
        assertEquals(0, runBlocking { repository.entries().size })
    }

    @Test fun managementFromHomeReturnsToHomeAndImportPreviewShowsLabels() {
        val now = System.currentTimeMillis()
        val item = kotlinx.coroutines.runBlocking { repository.incomeTypes().first() }
        val row = app.caiji.android.data.SalaryEntry("preview-row", java.time.LocalDate.now(), item.id, 10000,
            isCash = item.isCash, createdAtMillis = now, updatedAtMillis = now)
        var imported by androidx.compose.runtime.mutableStateOf<app.caiji.android.data.SalaryImportPreview?>(app.caiji.android.data.SalaryImportPreview(listOf(row), emptyList(), 1, emptyList()))
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, imported, {}, { imported = null }, {}) } }
        compose.onNodeWithText("导入 Excel").assertIsDisplayed()
        compose.onNodeWithText(item.name, substring = true).assertIsDisplayed()
        compose.onAllNodesWithText(item.id).assertCountEquals(0)
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithText("还没有工资记录").assertIsDisplayed()
        compose.onNodeWithTag("salary-net-total").assertIsDisplayed()
    }

    @Test fun cellImportErrorShowsLocationAndActionAndCanReselectFile() {
        val issue = app.caiji.android.data.SalaryImportError(
            app.caiji.android.data.SalaryImportErrorKind.CELL, "金额不是有效数字", "请输入数字，例如 123.45。", "IMP-CELL-001",
            row = 8, cell = "C8", field = "毛收入", currentValue = "1,23x"
        )
        var preview by androidx.compose.runtime.mutableStateOf<app.caiji.android.data.SalaryImportPreview?>(
            app.caiji.android.data.SalaryImportPreview(emptyList(), listOf(issue), 1, emptyList())
        )
        var selected = 0
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, preview, { selected++ }, { preview = null }, {}) } }
        compose.onNodeWithText("Sheet1 · C8 · 毛收入").assertIsDisplayed()
        compose.onNodeWithText("当前内容：1,23x").assertIsDisplayed()
        compose.onNodeWithText("请输入数字，例如 123.45。").assertIsDisplayed()
        compose.onAllNodesWithText("新增 0 笔").assertCountEquals(0)
        compose.onNodeWithText("重新选择文件").performClick()
        compose.onNodeWithText("选择工资 .xlsx 文件").assertIsDisplayed()
        assertEquals(1, selected)
    }

    @Test fun fileLevelImportErrorDoesNotPretendToBeRowZeroOrShowZeroTotals() {
        val issue = app.caiji.android.data.SalaryImportError(
            app.caiji.android.data.SalaryImportErrorKind.APPLICATION, "应用无法读取此 Excel 文件", "请重新选择文件。", "IMP-READ-001"
        )
        val preview = app.caiji.android.data.SalaryImportPreview(emptyList(), listOf(issue), 0, emptyList())
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, preview, {}, {}, {}) } }
        compose.onNodeWithText("文件未能读取，尚未导入").assertIsDisplayed()
        compose.onAllNodesWithText("第 0 行").assertCountEquals(0)
        compose.onAllNodesWithText("新增 0 笔").assertCountEquals(0)
        compose.onAllNodesWithText("IMP-READ-001", substring = true).assertCountEquals(1)
    }

    @Test fun cellErrorShowsCellValueReasonAndRepairAndReselectClearsPreview() {
        val issue = app.caiji.android.data.SalaryImportError(
            app.caiji.android.data.SalaryImportErrorKind.CELL, "内容不是有效金额", "请输入数字，例如 123.45。", "IMP-CELL-001",
            row = 8, cell = "C8", field = "毛收入", currentValue = "12x"
        )
        var preview by androidx.compose.runtime.mutableStateOf<app.caiji.android.data.SalaryImportPreview?>(app.caiji.android.data.SalaryImportPreview(emptyList(), listOf(issue), 1, emptyList()))
        var reselected = false
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, preview, { reselected = true }, { preview = null }, {}) } }
        compose.onNodeWithText("Sheet1 · C8 · 毛收入").assertIsDisplayed()
        compose.onNodeWithText("当前内容：12x").assertIsDisplayed()
        compose.onNodeWithText("请输入数字，例如 123.45。").assertIsDisplayed()
        compose.onAllNodesWithText("新增 0 笔").assertCountEquals(0)
        compose.onNodeWithText("重新选择文件").performClick()
        compose.onNodeWithText("选择工资 .xlsx 文件").assertIsDisplayed()
        assertTrue(reselected)
    }

    @Test fun fileErrorHasNoRowNumberOrZeroTotals() {
        val issue = app.caiji.android.data.SalaryImportError(app.caiji.android.data.SalaryImportErrorKind.APPLICATION,
            "应用无法读取此 Excel 文件", "请重新选择文件并附上错误编号。", "IMP-READ-001")
        val preview = app.caiji.android.data.SalaryImportPreview(emptyList(), listOf(issue), 0, emptyList())
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, preview, {}, {}, {}) } }
        compose.onNodeWithText("文件未能读取，尚未导入").assertIsDisplayed()
        compose.onAllNodesWithText("第 0 行").assertCountEquals(0)
        compose.onAllNodesWithText("新增 0 笔").assertCountEquals(0)
        compose.onAllNodesWithText("IMP-READ-001", substring = true).assertCountEquals(1)
    }

    @Test fun importErrorDetailsRemainReadableAcrossThemesOnNarrowLargeTextLayout() {
        val issue = app.caiji.android.data.SalaryImportError(
            app.caiji.android.data.SalaryImportErrorKind.CELL, "内容不是有效金额", "请输入数字，例如 123.45。", "IMP-CELL-001",
            row = 8, cell = "C8", field = "毛收入", currentValue = "12x"
        )
        val preview = app.caiji.android.data.SalaryImportPreview(emptyList(), listOf(issue), 1, emptyList())
        compose.setContent {
            CaijiTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.size(width = 320.dp, height = 600.dp)) {
                        SalaryRootScreen(repository, preview, {}, {}, {})
                    }
                }
            }
        }
        CaijiThemeId.entries.forEach { theme ->
            compose.runOnIdle { CaijiPalette.select(theme) }
            compose.onNodeWithText("Sheet1 · C8 · 毛收入").assertIsDisplayed()
            compose.onNodeWithText("当前内容：12x").assertIsDisplayed()
            compose.onNodeWithText("重新选择文件").assertIsDisplayed()
        }
    }

    @Test fun salaryExportSuccessBannerUsesSuccessStateAcrossAllThemes() {
        val message = "工资 Excel 已保存"
        compose.setContent {
            CaijiTheme {
                BackupFeedbackBanner(message, backupFeedbackTone(message), {})
            }
        }
        CaijiThemeId.entries.forEach { theme ->
            compose.runOnIdle { CaijiPalette.select(theme) }
            compose.onNodeWithTag("backup-feedback").assertIsDisplayed()
            compose.onNodeWithText("完成  ·  工资 Excel 已保存").assertIsDisplayed()
        }
    }

    @Test fun salaryExcelExportSuccessFeedbackUsesTheSuccessBannerAcrossThemes() {
        val message = "工资 Excel 已保存"
        compose.setContent {
            CaijiTheme {
                BackupFeedbackBanner(message, backupFeedbackTone(message), {})
            }
        }
        CaijiThemeId.entries.forEach { theme ->
            compose.runOnIdle { CaijiPalette.select(theme) }
            compose.onNodeWithTag("backup-feedback").assertIsDisplayed()
            compose.onNodeWithText("完成  ·  工资 Excel 已保存").assertIsDisplayed()
        }
    }

    @Test fun annualHomeKeepsYearControlsAndOpensAllRecordsSummary() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-open-summary").assertIsDisplayed()
        compose.onNodeWithTag("salary-year-label").assertIsDisplayed()
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithText("累计纯收入").assertIsDisplayed()
        compose.onNodeWithText("累计纯收入").assertIsDisplayed()
        compose.onNodeWithTag("salary-net-total").assertIsDisplayed()
        compose.onNodeWithTag("salary-year-table-scroll").assertIsDisplayed()
        compose.onNodeWithTag("salary-year-row-${java.time.LocalDate.now().year}").assertTextContains("¥0.00")
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-year-label").assertIsDisplayed()
        compose.onNodeWithText("本年纯收入").assertIsDisplayed()
    }

    @Test fun choosingPriorYearShowsItsRowsAndYearBoundsAreEnforced() {
        val now = java.time.LocalDate.now()
        val item = runBlocking { repository.incomeTypes().first() }
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry(
                "current-year", now, item.id, 100,
                isCash = item.isCash, createdAtMillis = 1L, updatedAtMillis = 1L
            ))
            repository.save(app.caiji.android.data.SalaryEntry(
                "prior-year", java.time.LocalDate.of(now.year - 1, 1, 2), item.id, 5_000,
                isCash = item.isCash, createdAtMillis = 1L, updatedAtMillis = 1L
            ))
        }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("salary-entry-row-current-year").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("salary-year-label").performClick()
        compose.onNodeWithTag("salary-year-option-${now.year - 1}").performClick()
        compose.onNodeWithTag("salary-net-total").assertTextContains("50.00", substring = true)
        compose.onNodeWithTag("salary-record-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-entry-row-prior-year"))
        compose.onNodeWithTag("salary-net-total").assertTextContains("50.00", substring = true)
        compose.onNodeWithTag("salary-year-previous").performClick()
        compose.onNodeWithText("本年纯收入").assertIsDisplayed()
        compose.onNodeWithTag("salary-net-total").assertTextContains("0.00", substring = true)
    }

    @Test fun yearSelectorDisablesNextAtTheCurrentYearBoundary() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-year-next").assertIsNotEnabled()
        compose.onNodeWithTag("salary-year-previous").assertIsDisplayed()
        compose.onAllNodesWithText("‹").assertCountEquals(0)
        compose.onAllNodesWithText("›").assertCountEquals(0)
    }

    @Test fun salaryHeaderFitsCompactWidthAndLargeFontAcrossThemes() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CaijiTheme { Box(Modifier.size(width = 320.dp, height = 760.dp)) {
                    SalaryRootScreen(repository, null, {}, {}, {})
                } }
            }
        }
        CaijiThemeId.entries.forEach { theme ->
            compose.runOnIdle { CaijiPalette.select(theme) }
            compose.onNodeWithTag("salary-open-summary").assertIsDisplayed()
            compose.onNodeWithTag("salary-year-label").assertIsDisplayed()
            val left = compose.onNodeWithTag("salary-open-summary").fetchSemanticsNode().boundsInRoot
            val previous = compose.onNodeWithTag("salary-year-previous").fetchSemanticsNode().boundsInRoot
            val right = compose.onNodeWithTag("salary-year-label").fetchSemanticsNode().boundsInRoot
            val next = compose.onNodeWithTag("salary-year-next").fetchSemanticsNode().boundsInRoot
            assertTrue("Period controls overlap under ${theme.label}", left.right <= right.left)
            assertTrue("Previous arrow overlaps the year under ${theme.label}", previous.right <= right.left)
            assertTrue("Year overlaps next arrow under ${theme.label}", right.right <= next.left)
        }
    }

    @Test fun salaryHomeAndSummaryUseIncomeTypeIconsAndCenterTheSummaryTitle() {
        val type = runBlocking { repository.incomeTypes().first { it.id == "builtin-2" } }
        val date = java.time.LocalDate.now()
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry(
                "income-icon-row", date, type.id, 12_000, isCash = type.isCash,
                createdAtMillis = 1L, updatedAtMillis = 1L
            ))
        }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("salary-entry-row-income-icon-row").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("salary-entry-row-income-icon-row").assertTextContains("🍜")
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-year-row-${date.year}").performClick()
        compose.onNodeWithTag("salary-income-ranking-${type.id}").assertTextContains("🍜")
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("page-title").fetchSemanticsNode().boundsInRoot
        assertTrue("Salary summary title should be centered", kotlin.math.abs(root.center.x - title.center.x) <= 1f)
    }

    @Test fun editingFromIncomeDetailsReturnsThroughYearReportToAnnualSummary() {
        val item = runBlocking { repository.incomeTypes().first() }
        val now = java.time.LocalDate.now()
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry("summary-current", now, item.id, 10_000,
                isCash = item.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
            repository.save(app.caiji.android.data.SalaryEntry("summary-prior", now.minusYears(1), item.id, 20_000,
                isCash = item.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
        }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-year-row-${now.year - 1}").assertTextContains("¥200.00")
        compose.onNodeWithTag("salary-year-row-${now.year}").assertTextContains("¥100.00")
        compose.onNodeWithTag("salary-year-row-${now.year - 1}").performClick()
        compose.onNodeWithTag("page-title").assertTextEquals("${now.year - 1}年报告")
        compose.onNodeWithTag("salary-report-net-total").assertTextContains("200.00", substring = true)
        compose.onNodeWithTag("salary-income-ranking-${item.id}").assertTextContains("+¥200.00").performClick()
        compose.onNodeWithTag("salary-entry-row-summary-prior").assertIsDisplayed()
        compose.onNodeWithTag("salary-entry-row-summary-prior").performClick()
        compose.onNodeWithTag("salary-field-工会费", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        enterSalaryAmount("收入", "250.00")
        compose.onNodeWithTag("salary-key-save").performClick()

        compose.onNodeWithTag("salary-income-detail-title").assertTextEquals(item.name)
        compose.onNodeWithTag("salary-net-total").assertTextContains("250.00", substring = true)
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("page-title").assertTextEquals("${now.year - 1}年报告")
        compose.onNodeWithTag("salary-report-net-total").assertTextContains("250.00", substring = true)
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-year-row-${now.year - 1}").assertTextContains("¥250.00")
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-year-label").assertTextEquals("${now.year}年")
    }

    @Test fun cancellingAndDeletingFromIncomeDetailsRefreshReportAndAnnualTable() {
        val types = runBlocking { repository.incomeTypes() }
        val cash = types.first { it.isCash }
        val today = java.time.LocalDate.now()
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry("summary-cancel", today, cash.id, 10_000,
                isCash = cash.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
            repository.save(app.caiji.android.data.SalaryEntry("summary-delete", today.minusYears(1), cash.id, 20_000,
                isCash = cash.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
        }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-year-row-${today.year}").performClick()
        compose.onNodeWithTag("salary-income-ranking-${cash.id}").performClick()
        compose.onNodeWithTag("salary-record-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-entry-row-summary-cancel"))
        compose.onNodeWithTag("salary-entry-row-summary-cancel").assertIsDisplayed()
        compose.onNodeWithTag("salary-entry-row-summary-cancel").performClick()
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-income-detail-title", useUnmergedTree = true).assertTextEquals(cash.name)
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-report-net-total").assertTextContains("100.00", substring = true)

        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-year-row-${today.year - 1}").performClick()
        compose.onNodeWithTag("salary-income-ranking-${cash.id}").performClick()
        compose.onNodeWithTag("salary-entry-row-summary-delete").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("salary-delete-summary-delete").performClick()
        compose.onNodeWithTag("salary-list-delete-dialog").assertIsDisplayed()
        compose.onNodeWithTag("salary-list-confirm-delete").performClick()
        compose.waitUntil(5_000) { runBlocking { repository.entries().none { it.id == "summary-delete" } } }
        compose.onNodeWithText("该收入项暂无工资记录").assertIsDisplayed()

        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-report-net-total").assertTextContains("0.00", substring = true)
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onAllNodesWithTag("salary-year-row-${today.year - 1}").assertCountEquals(0)
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-year-label").assertTextEquals("${today.year}年")
    }

    @Test fun newSalaryEntryHidesAllInputFieldsUntilAnIncomeTypeIsSelected() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onAllNodesWithTag("salary-field-selector").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-field-工会费").assertCountEquals(0)
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        compose.onNodeWithTag("salary-field-selector").assertIsDisplayed()
        compose.onNodeWithTag("salary-field-工会费", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun salaryAmountsUseTheInAppKeypadAndTaxAllowsRefunds() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        enterSalaryAmount("收入", "100.00")
        compose.onNodeWithTag("salary-field-税", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("salary-tax-sign").performClick()
        enterSalaryAmount("税", "10.00", replace = false)
        compose.onNodeWithTag("salary-key-save").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { runBlocking { repository.entries().isNotEmpty() } }
        val entry = runBlocking { repository.entries().single() }
        assertEquals(10_000L, entry.grossFen)
        assertEquals(-1_000L, entry.taxFen)
        assertEquals(11_000L, entry.netIncomeFen)
    }

    @Test fun switchingIncomeNatureRetainsDateAndAllEightAmountDrafts() {
        val priorDate = java.time.LocalDate.now().minusDays(1)
        compose.setContent {
            CaijiTheme {
                SalaryRootScreen(repository, null, {}, {}, {}, initialDraft = SalaryDraft.blank("").copy(date = priorDate),
                    initialCashNature = true)
            }
        }
        compose.onNodeWithTag("salary-income-type-builtin-1").performClick()
        enterSalaryAmount("收入", "100.00", replace = false)
        val values = listOf(
            "工会费" to "1.11", "养老保险" to "2.22", "医疗保险" to "3.33",
            "失业保险" to "4.44", "公积金" to "5.55", "企业年金" to "6.66"
        )
        values.forEach { (field, value) -> enterSalaryAmount(field, value, replace = false) }
        compose.onNodeWithTag("salary-field-税", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("salary-tax-sign").performClick()
        enterSalaryAmount("税", "7.77", replace = false)
        val priorDateLabel = priorDate.format(java.time.format.DateTimeFormatter.ofPattern("M月d日", java.util.Locale.CHINA))
        compose.onNodeWithTag("salary-key-date").assertTextContains(priorDateLabel)

        compose.onNodeWithTag("salary-income-noncash-tab").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("salary-income-noncash-tab").assertIsSelected()
        compose.onAllNodesWithTag("salary-key-save").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-active-amount").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-key-date").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-field-selector").assertCountEquals(0)

        compose.onNodeWithTag("salary-income-type-builtin-4").performClick()
        compose.onNodeWithTag("salary-key-date").assertTextContains(priorDateLabel)
        compose.onNodeWithTag("salary-active-amount").assertTextEquals("100.00")
        (values + ("税" to "-7.77")).forEach { (field, value) ->
            compose.onNodeWithTag("salary-field-$field", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithTag("salary-active-amount").assertTextEquals(value)
        }
        compose.onNodeWithTag("salary-field-收入", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("salary-cash-tab").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("salary-cash-tab").assertIsSelected()
        compose.onNodeWithTag("salary-income-type-builtin-1").performScrollTo().performClick()
        compose.onNodeWithTag("salary-income-type-builtin-1").assertContentDescriptionEquals("工资（交行 到账时间），已选择")
        compose.onNodeWithTag("salary-key-save").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { runBlocking { repository.entries().size == 1 } }
        val entry = runBlocking { repository.entries().single() }
        assertEquals(10_000L, entry.grossFen)
        assertEquals(111L, entry.unionFen)
        assertEquals(222L, entry.pensionFen)
        assertEquals(333L, entry.medicalFen)
        assertEquals(444L, entry.unemploymentFen)
        assertEquals(555L, entry.housingFen)
        assertEquals(666L, entry.annuityFen)
        assertEquals(-777L, entry.taxFen)
    }

    @Test fun switchedNatureOfAnEditedSalaryDraftSurvivesLeavingAndReturning() {
        val cashType = runBlocking { repository.incomeTypes().first { it.isCash } }
        val nonCashType = runBlocking { repository.incomeTypes().first { !it.isCash && it.active } }
        val entry = app.caiji.android.data.SalaryEntry("switch-edit-cash", java.time.LocalDate.now(), cashType.id, 50_000,
            isCash = true, createdAtMillis = 1L, updatedAtMillis = 1L)
        runBlocking { repository.save(entry) }
        var salaryPage by androidx.compose.runtime.mutableStateOf(true)
        var draft by androidx.compose.runtime.mutableStateOf<SalaryDraft?>(SalaryDraft.from(entry))
        var selected by androidx.compose.runtime.mutableStateOf(entry as app.caiji.android.data.SalaryEntry?)
        var cashNature by androidx.compose.runtime.mutableStateOf<Boolean?>(true)
        compose.setContent {
            CaijiTheme {
                Column(Modifier.fillMaxSize()) {
                    if (salaryPage) {
                        Box(Modifier.weight(1f)) {
                            SalaryRootScreen(repository, null, {}, {}, {}, initialDraft = draft!!, initialEntry = selected,
                                onDraftSnapshotChange = { nextDraft, nextEntry -> draft = nextDraft; selected = nextEntry },
                                initialCashNature = cashNature, onCashNatureChange = { cashNature = it })
                        }
                        TextButton(onClick = { salaryPage = false }) { Text("切换主页面") }
                    } else TextButton(onClick = { salaryPage = true }) { Text("回到工资页") }
                }
            }
        }
        compose.onNodeWithTag("salary-cash-tab").assertIsSelected()
        compose.onNodeWithTag("salary-income-noncash-tab").performClick()
        compose.onNodeWithTag("salary-income-noncash-tab").assertIsSelected()
        compose.onNodeWithTag("salary-income-type-${nonCashType.id}").performClick()
        compose.onNodeWithText("切换主页面").performClick()
        compose.onNodeWithText("回到工资页").performClick()
        compose.onNodeWithTag("salary-income-noncash-tab").assertIsSelected()
        compose.onNodeWithTag("salary-income-type-${nonCashType.id}")
            .assertContentDescriptionEquals("${nonCashType.name}，已选择")
        compose.onNodeWithTag("salary-key-1").assertIsDisplayed()
        compose.onNodeWithTag("salary-active-amount").assertTextContains("500.00")
    }

    @Test fun annualHomeAndAllYearsSummaryShowYearRowsWithoutCashFilters() {
        val types = runBlocking { repository.incomeTypes() }
        val cashItem = types.first { it.isCash }
        val nonCashItem = types.first { !it.isCash }
        val today = java.time.LocalDate.now()
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry("home-cash", today, cashItem.id, 10_000,
                isCash = cashItem.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
            repository.save(app.caiji.android.data.SalaryEntry("summary-noncash", today.minusYears(1), nonCashItem.id, 5_000,
                isCash = nonCashItem.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
        }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("salary-entry-row-home-cash").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("salary-entry-row-home-cash").assertIsDisplayed()
        val monthKey = "${today.year}-${today.monthValue.toString().padStart(2, '0')}"
        compose.onNodeWithTag("salary-group-cash-total-$monthKey", useUnmergedTree = true).assertTextEquals("现金 ¥100.00")
        compose.onNodeWithTag("salary-group-noncash-total-$monthKey", useUnmergedTree = true).assertTextEquals("非现金 ¥0.00")
        compose.onAllNodesWithText("收入明细").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-home-filter-ALL").assertCountEquals(0)

        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-net-total").assertTextContains("150.00", substring = true)
        compose.onNodeWithTag("salary-year-row-${today.year}").assertTextContains("¥100.00")
            .assertTextContains("¥0.00").assertTextContains("¥100.00")
        compose.onNodeWithTag("salary-year-row-${today.year - 1}").assertTextContains("¥50.00")
            .assertTextContains("¥0.00").assertTextContains("¥50.00")
        compose.onAllNodesWithTag("salary-record-list").assertCountEquals(0)
        compose.onAllNodesWithTag("salary-summary-filter-CASH").assertCountEquals(0)
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-entry-row-home-cash").assertIsDisplayed()

        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-net-total").assertTextContains("150.00", substring = true)
        compose.onNodeWithTag("salary-year-row-${today.year - 1}").assertTextContains("¥50.00")
    }

    @Test fun salarySummaryButtonMatchesLedgerHomePosition() {
        val viewportWidth = 393.dp
        val viewportHeight = 820.dp
        var showLedger by mutableStateOf(true)
        compose.setContent {
            CaijiTheme {
                Box(Modifier.width(viewportWidth).height(viewportHeight)) {
                    if (showLedger) {
                        LedgerHome(
                            month = java.time.YearMonth.now(), expenseFen = java.math.BigInteger.ZERO,
                            incomeFen = java.math.BigInteger.ZERO, entries = emptyList(), categories = emptyList(),
                            onPrevious = {}, onNext = {}, onEdit = {}, onCreateEntry = {}
                        )
                    } else {
                        SalaryRootScreen(repository, null, {}, {}, {})
                    }
                    TextButton(
                        onClick = { showLedger = false },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).testTag("salary-compare-screen")
                    ) { Text("切换") }
                }
            }
        }
        compose.onNodeWithTag("ledger-open-bills").assertIsDisplayed()
        val ledgerBounds = compose.onNodeWithTag("ledger-open-bills").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("salary-compare-screen").performClick()
        compose.onNodeWithTag("salary-open-summary").assertIsDisplayed()
        val salaryBounds = compose.onNodeWithTag("salary-open-summary").fetchSemanticsNode().boundsInRoot
        assertTrue("Salary summary button should align horizontally with ledger", kotlin.math.abs(ledgerBounds.left - salaryBounds.left) <= 1f)
        assertTrue("Salary summary button should align vertically with ledger", kotlin.math.abs(ledgerBounds.top - salaryBounds.top) <= 1f)
        assertTrue("Salary summary button should match ledger button size",
            kotlin.math.abs(ledgerBounds.width - salaryBounds.width) <= 1f && kotlin.math.abs(ledgerBounds.height - salaryBounds.height) <= 1f)
    }

    @Test fun settingsExportOffersAllOrIndependentYearWithoutUsingHomePeriod() {
        var selected: app.caiji.android.data.SalaryPeriod? = null
        compose.setContent {
            CaijiTheme {
                SalaryRootScreen(repository, null, {}, {}, { selected = it },
                    initialManagementRoute = SalaryManagementRoute.EXPORT)
            }
        }
        compose.onNodeWithTag("salary-export-all").assertIsSelected()
        compose.onNodeWithTag("salary-export-year").performClick()
        compose.onNodeWithTag("salary-export-year-label").assertIsDisplayed()
        compose.onNodeWithTag("salary-export-year-label").performClick()
        compose.onNodeWithTag("salary-export-year-option-${java.time.LocalDate.now().year - 1}").performClick()
        compose.onNodeWithTag("salary-export-save").performClick()
        assertEquals(app.caiji.android.data.SalaryPeriod.Year(java.time.LocalDate.now().year - 1), selected)
    }

    @Test fun successfulSettingsImportPublishesFeedbackBeforeReturning() {
        val item = runBlocking { repository.incomeTypes().first() }
        val entry = app.caiji.android.data.SalaryEntry("settings-import", java.time.LocalDate.now(), item.id, 5_000,
            isCash = item.isCash, createdAtMillis = 1L, updatedAtMillis = 1L)
        val preview = app.caiji.android.data.SalaryImportPreview(listOf(entry), emptyList(), 0, emptyList())
        var feedback: String? = null
        var returned = false
        compose.setContent {
            CaijiTheme {
                SalaryRootScreen(repository, preview, {}, {}, {},
                    initialManagementRoute = SalaryManagementRoute.IMPORT,
                    onManagementBack = { returned = true },
                    onManagementSuccess = { feedback = it })
            }
        }
        compose.onNodeWithText("确认导入").performClick()
        compose.waitUntil(5_000) { feedback != null && returned }
        assertTrue(feedback.orEmpty().startsWith("导入完成"))
        assertEquals(1, runBlocking { repository.entries().size })
    }

    @Test fun settingsIncomeTypesReturnToSettings() {
        var returned = 0
        compose.setContent {
            CaijiTheme {
                SalaryRootScreen(repository, null, {}, {}, {},
                    initialManagementRoute = SalaryManagementRoute.TYPES, onManagementBack = { returned++ })
            }
        }
        compose.onNodeWithTag("salary-header-back").performClick()
        assertEquals(1, returned)
    }
}
