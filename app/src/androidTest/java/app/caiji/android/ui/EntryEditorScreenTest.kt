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
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.data.LedgerEntry
import app.caiji.android.domain.CategoryCatalog
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiTheme
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
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
                    onReorderCategory = { _, _ -> }
                )
            }
        }

        compose.onNodeWithTag("entry-type-expense").assertIsSelected()
        compose.onNodeWithTag("entry-type-income").assertIsNotSelected()
        compose.onNodeWithTag("amount-display").assertIsDisplayed()
        compose.onNodeWithTag("entry-save").assertIsDisplayed()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-grid"))
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").assertIsDisplayed()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-date"))
        compose.onNodeWithTag("entry-date").assertIsDisplayed()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-note"))
        compose.onNodeWithTag("entry-note").assertIsDisplayed()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasText("请选择分类"))
        compose.onNodeWithText("请选择分类").assertIsDisplayed()
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
                    onReorderCategory = { _, _ -> }
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
                    onReorderCategory = { _, _ -> }
                )
            }
        }

        compose.onNodeWithTag("amount-display").assertTextEquals("8,500.00")
        compose.onNodeWithTag("entry-key-complete").performClick()
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
                    onReorderCategory = { _, _ -> },
                    imeVisible = true
                )
            }
        }

        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-note"))
        compose.onNodeWithTag("entry-note").assertIsDisplayed()
        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
        compose.onNodeWithTag("entry-date").assertIsDisplayed()
        compose.onNodeWithTag("entry-save").assertIsDisplayed()
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
                    onReorderCategory = { _, _ -> }
                )
            }
        }

        compose.onNodeWithTag("entry-note").performTextInput("早餐")
        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
        compose.onNodeWithTag("entry-note").performImeAction()
        compose.onNodeWithTag("entry-key-1").assertIsDisplayed()
        compose.onNodeWithTag("entry-note").assertTextContains("早餐")
    }
}
