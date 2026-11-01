package app.caiji.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.data.LedgerEntry
import app.caiji.android.domain.CategoryCatalog
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiTheme
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val categories = CategoryCatalog.defaults()

    @Test
    fun entryEditorShowsRequiredFieldsAndLeavesCategoryUnselected() {
        compose.setContent {
            CaijiTheme {
                EntryEditorScreen(
                    categories = categories,
                    existing = null,
                    initialType = EntryType.EXPENSE,
                    onCancel = {},
                    onSave = { _, _, _, _, _, done -> done(false) },
                    onDelete = {},
                    onSaveCategory = { _, _, _, _, done -> done(false) },
                    onSetCategoryActive = { _, _ -> },
                    onReorderCategory = { _, _, done -> done(true) }
                )
            }
        }

        compose.onNodeWithTag("entry-type-expense").assertIsSelected()
        compose.onNodeWithTag("entry-type-income").assertIsNotSelected()
        compose.onNodeWithTag("amount-display").assertIsDisplayed()
        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
        compose.onAllNodesWithTag("entry-save").assertCountEquals(0)
        compose.onAllNodesWithTag("entry-date").assertCountEquals(0)
        compose.onAllNodesWithTag("entry-note").assertCountEquals(0)
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-grid"))
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").assertIsDisplayed()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasText("请选择分类"))
        compose.onNodeWithText("请选择分类").assertIsDisplayed()
    }

    @Test fun choosingCategoryShowsKeypadAndChangingTypeHidesItAgain() {
        compose.setContent {
            CaijiTheme {
                EntryEditorScreen(
                    categories = categories, existing = null, initialType = EntryType.EXPENSE, onCancel = {},
                    onSave = { _, _, _, _, _, done -> done(false) }, onDelete = {},
                    onSaveCategory = { _, _, _, _, done -> done(false) }, onSetCategoryActive = { _, _ -> },
                    onReorderCategory = { _, _, done -> done(true) }
                )
            }
        }

        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        compose.onNodeWithTag("entry-key-1").assertIsDisplayed()
        compose.onAllNodesWithTag("entry-key-dismiss").assertCountEquals(0)
        compose.onNodeWithTag("amount-display").performClick()
        compose.onNodeWithTag("entry-key-1").assertIsDisplayed()
        compose.onNodeWithTag("entry-type-income").performClick()
        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
    }

    @Test
    fun switchingEntryTypeShowsMatchingCategoriesAndClearsSelection() {
        compose.setContent {
            CaijiTheme {
                EntryEditorScreen(
                    categories = categories,
                    existing = null,
                    initialType = EntryType.EXPENSE,
                    onCancel = {},
                    onSave = { _, _, _, _, _, done -> done(false) },
                    onDelete = {},
                    onSaveCategory = { _, _, _, _, done -> done(false) },
                    onSetCategoryActive = { _, _ -> },
                    onReorderCategory = { _, _, done -> done(true) }
                )
            }
        }

        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-grid"))
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        compose.onNodeWithTag("entry-type-income").performClick()
        compose.onNodeWithTag("entry-type-income").assertIsSelected()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-grid"))
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-income-0"))
        compose.onNodeWithTag("entry-category-seed-income-0").assertIsDisplayed()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasText("请选择分类"))
        compose.onNodeWithText("请选择分类").assertIsDisplayed()
    }

    @Test
    fun editingLargeExistingAmountKeepsExactFenWhenSaved() {
        val expenseCategory = categories.first { it.type == EntryType.EXPENSE }
        val entry = LedgerEntry(
            id = "large-existing",
            type = EntryType.EXPENSE,
            amountFen = 850000,
            categoryId = expenseCategory.id,
            occurredOn = LocalDate.of(2026, 11, 1)
        )
        val savedFen = AtomicLong(-1)
        compose.setContent {
            CaijiTheme {
                EntryEditorScreen(
                    categories = categories,
                    existing = entry,
                    initialType = EntryType.EXPENSE,
                    onCancel = {},
                    onSave = { _, amountFen, _, _, _, done ->
                        savedFen.set(amountFen)
                        done(true)
                    },
                    onDelete = {},
                    onSaveCategory = { _, _, _, _, done -> done(false) },
                    onSetCategoryActive = { _, _ -> },
                    onReorderCategory = { _, _, done -> done(true) }
                )
            }
        }

        compose.onNodeWithTag("amount-display").assertTextEquals("8,500.00")
        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
        compose.onNodeWithTag("amount-display").performClick()
        compose.onNodeWithTag("entry-key-save").performClick()
        assertEquals(850000, savedFen.get())
    }

    @Test
    fun imeStateHidesCustomKeypadAndKeepsDateAndSaveVisible() {
        compose.setContent {
            CaijiTheme {
                EntryEditorScreen(
                    categories = categories,
                    existing = null,
                    initialType = EntryType.EXPENSE,
                    onCancel = {},
                    onSave = { _, _, _, _, _, done -> done(false) },
                    onDelete = {},
                    onSaveCategory = { _, _, _, _, done -> done(false) },
                    onSetCategoryActive = { _, _ -> },
                    onReorderCategory = { _, _, done -> done(true) },
                    imeVisible = true
                )
            }
        }

        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-note"))
        compose.onNodeWithTag("entry-note").assertIsDisplayed()
        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
        compose.onAllNodesWithTag("entry-date").assertCountEquals(0)
        compose.onAllNodesWithTag("entry-save").assertCountEquals(0)
    }

    @Test
    fun dateKeyOpensPickerAndOnlySelectionChangesDateLabel() {
        val callback = AtomicReference<((LocalDate?) -> Unit)?>(null)
        val shownDate = AtomicReference<LocalDate>()
        val selectedDate = LocalDate.now().minusDays(1)
        compose.setContent {
            CaijiTheme {
                EntryEditorScreen(
                    categories = categories, existing = null, initialType = EntryType.EXPENSE,
                    onCancel = {}, onSave = { _, _, _, _, _, done -> done(false) }, onDelete = {},
                    onSaveCategory = { _, _, _, _, done -> done(false) }, onSetCategoryActive = { _, _ -> },
                    onReorderCategory = { _, _, done -> done(true) }, datePickerLauncher = { date, onSelected ->
                        shownDate.set(date)
                        callback.set(onSelected)
                    }
                )
            }
        }
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        compose.onNodeWithTag("entry-key-date").assertIsDisplayed().performClick()
        assertEquals(LocalDate.now(), shownDate.get())
        val todayDescription = compose.onNodeWithTag("entry-key-date").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()
        assertEquals("日期，今天，${LocalDate.now().year}年${LocalDate.now().monthValue}月${LocalDate.now().dayOfMonth}日", todayDescription)
        callback.get()?.invoke(null)
        compose.onNodeWithTag("entry-key-date").assertTextContains("今天", substring = true)
        compose.onNodeWithTag("entry-key-date").performClick()
        callback.get()?.invoke(selectedDate)
        compose.onNodeWithTag("entry-key-date").assertTextContains("${selectedDate.monthValue}月${selectedDate.dayOfMonth}日", substring = true)
    }

    @Test
    fun keypadSaveIsTheOnlySaveActionAndImeDoneRestoresIt() {
        val saveCount = java.util.concurrent.atomic.AtomicInteger()
        val savedFen = AtomicLong()
        compose.setContent {
            CaijiTheme {
                EntryEditorScreen(
                    categories = categories, existing = null, initialType = EntryType.EXPENSE,
                    onCancel = {}, onSave = { _, amount, _, _, _, done -> savedFen.set(amount); saveCount.incrementAndGet(); done(false) },
                    onDelete = {}, onSaveCategory = { _, _, _, _, done -> done(false) },
                    onSetCategoryActive = { _, _ -> }, onReorderCategory = { _, _, done -> done(true) }
                )
            }
        }
        compose.onAllNodesWithTag("entry-save").assertCountEquals(0)
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        compose.onNodeWithTag("entry-note").performTextInput("早餐")
        compose.onNodeWithTag("entry-note").performImeAction()
        compose.onNodeWithTag("entry-key-save").assertIsDisplayed()
        compose.onNodeWithTag("entry-key-1").performClick()
        compose.onNodeWithTag("entry-key-save").performClick()
        assertEquals(1, saveCount.get())
        assertEquals(100, savedFen.get())
        compose.onNodeWithTag("entry-note").assertTextContains("早餐")
    }

    @Test
    fun compactLargeTextKeepsNoteReachableWhileSystemKeyboardOwnsInput() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                CaijiTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        EntryEditorScreen(
                            categories = categories, existing = null, initialType = EntryType.EXPENSE,
                            onCancel = {}, onSave = { _, _, _, _, _, done -> done(false) }, onDelete = {},
                            onSaveCategory = { _, _, _, _, done -> done(false) }, onSetCategoryActive = { _, _ -> },
                            onReorderCategory = { _, _, done -> done(true) }, imeVisible = true
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-note"))
        compose.onNodeWithTag("entry-note").assertIsDisplayed()
        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
        compose.onAllNodesWithTag("entry-key-save").assertCountEquals(0)
    }

    @Test
    fun completingNoteInputRestoresTheCustomKeypad() {
        compose.setContent {
            CaijiTheme {
                EntryEditorScreen(
                    categories = categories,
                    existing = null,
                    initialType = EntryType.EXPENSE,
                    onCancel = {},
                    onSave = { _, _, _, _, _, done -> done(false) },
                    onDelete = {},
                    onSaveCategory = { _, _, _, _, done -> done(false) },
                    onSetCategoryActive = { _, _ -> },
                    onReorderCategory = { _, _, done -> done(true) }
                )
            }
        }

        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        compose.onNodeWithTag("entry-note").performTextInput("早餐")
        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
        compose.onNodeWithTag("entry-note").performImeAction()
        compose.onNodeWithTag("entry-key-1").assertIsDisplayed()
        compose.onNodeWithTag("entry-note").assertTextContains("早餐")
    }
}
