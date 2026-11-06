package app.caiji.android.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.caiji.android.data.RoomSalaryRepository
import app.caiji.android.data.SalaryDatabase
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

    @Test fun savesSalaryAndShowsItsIndependentSummary() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("还没有工资记录").assertIsDisplayed()
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-field-毛收入").performTextClearance()
        compose.onNodeWithTag("salary-field-毛收入").performTextInput("100.00")
        compose.onNodeWithText("保存收入").performClick()
        compose.onNodeWithTag("salary-net-total").assertTextContains("100.00", substring = true)
        compose.onNodeWithText(java.time.LocalDate.now().year.toString() + "年纯收入").assertIsDisplayed()
    }

    @Test fun draftSurvivesOpeningIncomeTypeManagementFromTheEditor() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-field-毛收入").performTextClearance()
        compose.onNodeWithTag("salary-field-毛收入").performTextInput("235.50")
        compose.onAllNodesWithTag("salary-field-工会费").assertCountEquals(0)
        compose.onNodeWithText("扣款明细").performClick()
        compose.onNodeWithTag("salary-field-工会费").assertIsDisplayed()
        compose.onNodeWithText("管理收入项", substring = true).performClick()
        compose.onNodeWithText("新增收入项").performClick()
        compose.onNodeWithText("收入项名称").performTextInput("临时补贴")
        compose.onNodeWithText("添加").performClick()
        compose.onNodeWithText("‹").performClick()
        compose.onNodeWithTag("salary-field-毛收入").assertTextContains("235.50", substring = true)
        compose.onNodeWithTag("salary-field-工会费").assertIsDisplayed()
    }

    @Test fun salaryDraftSurvivesLeavingTheBottomNavigationAndReturning() {
        var salaryPage by androidx.compose.runtime.mutableStateOf(true)
        var draft by androidx.compose.runtime.mutableStateOf<SalaryDraft?>(null)
        var entry by androidx.compose.runtime.mutableStateOf<app.caiji.android.data.SalaryEntry?>(null)
        compose.setContent {
            CaijiTheme {
                Column(Modifier.fillMaxSize()) {
                    if (salaryPage) {
                        Box(Modifier.weight(1f)) {
                            SalaryRootScreen(repository, null, {}, {}, {}, draft, entry, onDraftSnapshotChange = { updatedDraft, updatedEntry ->
                                draft = updatedDraft
                                entry = updatedEntry
                            })
                        }
                        TextButton(onClick = { salaryPage = false }) { Text("切换主页面") }
                    } else {
                        TextButton(onClick = { salaryPage = true }) { Text("回到工资页") }
                    }
                }
            }
        }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-field-毛收入").performTextClearance()
        compose.onNodeWithTag("salary-field-毛收入").performTextInput("235.50")
        compose.onNodeWithText("切换主页面").performClick()
        compose.onNodeWithText("回到工资页").performClick()
        compose.onNodeWithTag("salary-field-毛收入").assertTextContains("235.50", substring = true)
        compose.onNodeWithText("保存收入").performClick()
        compose.onNodeWithTag("salary-net-total").assertTextContains("235.50", substring = true)
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
        compose.onNodeWithText("‹").performClick()
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
        compose.onNodeWithTag("salary-field-毛收入").performTextClearance()
        compose.onNodeWithTag("salary-field-毛收入").performTextInput("123.45")
        compose.onNodeWithText("保存收入").performClick()
        compose.waitUntil(5_000) { failure != null }
        compose.onNodeWithText("工资保存失败", substring = true).assertIsDisplayed()
        assertTrue(failure.orEmpty().contains("输入内容已保留"))
        compose.onNodeWithTag("salary-field-毛收入").assertTextContains("123.45", substring = true)
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
        compose.onNodeWithText(item.name, substring = true).assertIsDisplayed()
        compose.onAllNodesWithText(item.id).assertCountEquals(0)
        compose.onNodeWithText("‹").performClick()
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
        compose.onNodeWithTag("salary-net-total").assertIsDisplayed()
        compose.onNodeWithText("‹").performClick()
        compose.onNodeWithTag("salary-year-label").assertIsDisplayed()
        compose.onNodeWithText(java.time.LocalDate.now().year.toString() + "年纯收入").assertIsDisplayed()
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
        compose.onNodeWithText((now.year - 2).toString() + "年纯收入").assertIsDisplayed()
        compose.onNodeWithTag("salary-net-total").assertTextContains("0.00", substring = true)
    }

    @Test fun yearSelectorDisablesNextAtTheCurrentYearBoundary() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithTag("salary-year-next").assertIsNotEnabled()
        compose.onNodeWithTag("salary-year-previous").assertIsDisplayed()
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
            val right = compose.onNodeWithTag("salary-year-label").fetchSemanticsNode().boundsInRoot
            assertTrue("Period controls overlap under ${theme.label}", left.right <= right.left)
        }
    }

    @Test fun summaryShowsAllYearsAndEditingAnEntryReturnsToRefreshedSummary() {
        val item = runBlocking { repository.incomeTypes().first() }
        val now = java.time.LocalDate.now()
        runBlocking {
            repository.save(app.caiji.android.data.SalaryEntry("summary-current", now, item.id, 10_000,
                isCash = item.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
            repository.save(app.caiji.android.data.SalaryEntry("summary-prior", now.minusYears(1), item.id, 20_000,
                isCash = item.isCash, createdAtMillis = 1L, updatedAtMillis = 1L))
        }
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("salary-entry-row-summary-current").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithText("汇总").assertIsDisplayed()
        compose.onNodeWithTag("salary-net-total").assertTextContains("300.00", substring = true)
        val currentRowTop = compose.onNodeWithTag("salary-entry-row-summary-current").fetchSemanticsNode().boundsInRoot.top
        val priorRowTop = compose.onNodeWithTag("salary-entry-row-summary-prior").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Salary summary should show newer records before older records", currentRowTop < priorRowTop)
        compose.onNodeWithTag("salary-record-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-entry-row-summary-prior"))
        compose.onNodeWithTag("salary-entry-row-summary-prior").performClick()
        compose.onNodeWithTag("salary-field-工会费").assertIsDisplayed()
        compose.onNodeWithTag("salary-field-毛收入").performTextClearance()
        compose.onNodeWithTag("salary-field-毛收入").performTextInput("250.00")
        compose.onNodeWithText("保存收入").performClick()

        compose.onNodeWithText("汇总").assertIsDisplayed()
        compose.onNodeWithTag("salary-net-total").assertTextContains("350.00", substring = true)
        compose.onNodeWithTag("salary-entry-row-summary-prior").assertIsDisplayed()
    }

    @Test fun cancellingAndDeletingFromSalarySummaryReturnAndRefreshTheSummary() {
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
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("salary-entry-row-summary-cancel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-net-total").assertTextContains("300.00", substring = true)

        compose.onNodeWithTag("salary-entry-row-summary-cancel").performClick()
        compose.onNodeWithText("‹").performClick()
        compose.onNodeWithTag("salary-net-total").assertTextContains("300.00", substring = true)

        compose.onNodeWithTag("salary-record-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-entry-row-summary-delete"))
        compose.onNodeWithTag("salary-entry-row-summary-delete").performClick()
        compose.onNodeWithTag("salary-field-毛收入").assertIsDisplayed()
        compose.onNodeWithTag("salary-editor-content").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-delete-entry"))
        compose.onNodeWithTag("salary-delete-entry").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("salary-delete-dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("删除这笔收入？").assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("汇总").assertIsDisplayed()
        compose.onNodeWithTag("salary-net-total").assertTextContains("100.00", substring = true)
        compose.onAllNodesWithTag("salary-entry-row-summary-delete").assertCountEquals(0)
    }

    @Test fun newSalaryEntryStartsWithDeductionsCollapsedAndCanExpandThem() {
        compose.setContent { CaijiTheme { SalaryRootScreen(repository, null, {}, {}, {}) } }
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onAllNodesWithTag("salary-field-工会费").assertCountEquals(0)
        compose.onNodeWithText("扣款明细").performClick()
        compose.onNodeWithTag("salary-field-工会费").assertIsDisplayed()
    }

    @Test fun homeAndSummaryKeepIndependentCashFilters() {
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
        compose.onNodeWithTag("salary-home-filter-NONCASH").performClick()
        compose.onNodeWithText("0 笔").assertIsDisplayed()
        compose.onNodeWithText("还没有工资记录").assertIsDisplayed()

        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-net-total").assertTextContains("150.00", substring = true)
        compose.onNodeWithTag("salary-record-list").assertIsDisplayed()
        compose.onNodeWithTag("salary-summary-filter-NONCASH").performClick()
        compose.onNodeWithText("1 笔").assertIsDisplayed()
        compose.onNodeWithTag("salary-record-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-entry-row-summary-noncash"))
        compose.onNodeWithTag("salary-entry-row-summary-noncash").assertIsDisplayed()
        compose.onNodeWithText("‹").performClick()
        compose.onNodeWithText("还没有工资记录").assertIsDisplayed()

        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithText("1 笔").assertIsDisplayed()
        compose.onNodeWithTag("salary-record-list").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("salary-entry-row-summary-noncash"))
        compose.onNodeWithTag("salary-entry-row-summary-noncash").assertIsDisplayed()
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

    @Test fun settingsIncomeTypesReturnToSettingsWhileEditorManagementReturnsToDraft() {
        var returned = 0
        compose.setContent {
            CaijiTheme {
                SalaryRootScreen(repository, null, {}, {}, {},
                    initialManagementRoute = SalaryManagementRoute.TYPES, onManagementBack = { returned++ })
            }
        }
        compose.onNodeWithText("‹").performClick()
        assertEquals(1, returned)
    }
}
