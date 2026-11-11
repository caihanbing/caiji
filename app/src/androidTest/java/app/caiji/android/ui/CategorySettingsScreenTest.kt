package app.caiji.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.domain.CategoryCatalog
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategorySettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun categoryTypeCanSwitchByHorizontalSwipe() {
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = CategoryCatalog.defaults(), onBack = {},
                    onSave = { _, _, _, _, done -> done(false) }, onActive = { _, _, done -> done(true) },
                    onMove = { _, _, done -> done(true) }
                )
            }
        }
        compose.onNodeWithTag("category-type-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("category-type-INCOME").assertIsSelected()
        compose.onNodeWithTag("category-row-seed-income-0").assertIsDisplayed()
    }

    @Test fun categoryTypeSelectionTracksSwipeBeforeThePageSettles() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = CategoryCatalog.defaults(), onBack = {},
                    onSave = { _, _, _, _, done -> done(false) }, onActive = { _, _, done -> done(true) },
                    onMove = { _, _, done -> done(true) }
                )
            }
        }
        val pager = compose.onNodeWithTag("category-type-pager")
        pager.performTouchInput {
            val start = center
            down(start)
            moveTo(start.copy(x = start.x - width * 0.7f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("category-type-INCOME").assertIsSelected()
        compose.onNodeWithTag("category-active-name-工资").assertIsEnabled()
        compose.mainClock.autoAdvance = true
    }

    @Test fun addCategoryActionIsDisabledUntilPageSwipeSettles() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = CategoryCatalog.defaults(), onBack = {},
                    onSave = { _, _, _, _, done -> done(false) },
                    onActive = { _, _, done -> done(true) },
                    onMove = { _, _, done -> done(true) }
                )
            }
        }
        val pager = compose.onNodeWithTag("category-type-pager")
        pager.performTouchInput {
            down(center)
            moveTo(center.copy(x = center.x - width * 0.7f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("category-add-button").assertIsNotEnabled()
        compose.onAllNodesWithText("添加分类").assertCountEquals(0)
    }

    @Test fun categoryManagerTitleIsCenteredAcrossUnequalHeaderActions() {
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = CategoryCatalog.defaults(), onBack = {},
                    onSave = { _, _, _, _, done -> done(false) }, onActive = { _, _, done -> done(true) },
                    onMove = { _, _, done -> done(true) }
                )
            }
        }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("分类管理").fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, title.center.x, 1f)
    }

    @Test fun categoryTypeTabCannotChangeWhileOrderWriteIsPending() {
        val pending = AtomicReference<((Boolean) -> Unit)?>(null)
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = CategoryCatalog.defaults(), onBack = {},
                    onSave = { _, _, _, _, done -> done(false) }, onActive = { _, _, done -> done(true) },
                    onMove = { _, _, done -> pending.set(done) }
                )
            }
        }
        compose.onNodeWithTag("category-move-down-seed-expense-0").performClick()
        compose.onNodeWithTag("category-type-INCOME").assertIsNotEnabled()
        pending.get()?.invoke(true)
    }

    @Test fun categoryManagerMovesOnePositionByArrowAndLongPressDoesNotReorder() {
        val moved = mutableListOf<Pair<String, Int>>()
        val active = mutableListOf<Pair<String, Boolean>>()
        val categories = CategoryCatalog.defaults().map {
            if (it.id == "seed-expense-0") it.copy(active = false) else it
        }
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = categories,
                    onBack = {},
                    onSave = { _, _, _, _, done -> done(false) },
                    onActive = { id, enabled, done -> active += id to enabled; done(true) },
                    onMove = { id, position, done -> moved += id to position; done(true) },
                    initialType = EntryType.EXPENSE
                )
            }
        }

        compose.onNodeWithTag("category-type-EXPENSE").assertIsSelected()
        compose.onNodeWithTag("category-move-up-seed-expense-0").assertIsNotEnabled()
        compose.onNodeWithTag("category-move-down-seed-expense-0").assertIsEnabled().performClick()
        assertEquals(listOf("seed-expense-0" to 1), moved)
        compose.onNodeWithTag("category-row-seed-expense-0").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 140f))
            up()
        }
        assertEquals(listOf("seed-expense-0" to 1), moved)
        compose.onNodeWithTag("category-active-name-日用").performClick()
        compose.onNodeWithTag("category-type-INCOME").performClick()
        compose.onNodeWithTag("category-type-INCOME").assertIsSelected()
        compose.onNodeWithText("工资").assertIsDisplayed()
        compose.onAllNodesWithText("餐饮").assertCountEquals(0)
        assertEquals(listOf(categories.first { it.name == "日用" }.id to false), active)
    }

    @Test fun categoryEditorKeepsTypeFixedOnEditAndShowsAllBuiltInIconsOnCreate() {
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = CategoryCatalog.defaults(),
                    onBack = {},
                    onSave = { _, _, _, _, done -> done(false) },
                    onActive = { _, _, done -> done(true) },
                    onMove = { _, _, done -> done(true) },
                    initialType = EntryType.EXPENSE
                )
            }
        }

        compose.onNodeWithTag("category-edit-seed-expense-0").performClick()
        compose.onNodeWithText("支出分类").assertIsDisplayed()
        compose.onAllNodesWithTag("category-editor-type-EXPENSE").assertCountEquals(0)
        compose.onNodeWithText("取消").performClick()

        compose.onNodeWithTag("category-add").performClick()
        compose.onNodeWithText("支出分类").assertIsDisplayed()
        compose.onAllNodesWithTag("category-editor-type-EXPENSE").assertCountEquals(0)
        assertEquals(100, CategoryCatalog.iconKeys().size)
        CategoryCatalog.iconKeys().forEach { icon -> compose.onAllNodesWithTag("category-icon-${icon.key}").assertCountEquals(1) }
        compose.onNodeWithText("分类名称").performTextInput("自定义分类")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("category-type-INCOME").performClick()
        compose.onNodeWithTag("category-add").performClick()
        compose.onNodeWithText("收入分类").assertIsDisplayed()
        compose.onAllNodesWithTag("category-editor-type-INCOME").assertCountEquals(0)
    }

    @Test fun categoryArrowMoveLocksDuringSaveAndFailurePreservesOrder() {
        val categories = CategoryCatalog.defaults()
        var complete: ((Boolean) -> Unit)? = null
        var moveCalls = 0
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = categories, onBack = {},
                    onSave = { _, _, _, _, done -> done(false) }, onActive = { _, _, done -> done(true) },
                    onMove = { _, _, done -> moveCalls += 1; complete = done }
                )
            }
        }
        val first = compose.onNodeWithTag("category-row-seed-expense-0").fetchSemanticsNode().boundsInRoot.top
        val second = compose.onNodeWithTag("category-row-seed-expense-1").fetchSemanticsNode().boundsInRoot.top
        val clickArrow = compose.onNodeWithTag("category-move-down-seed-expense-0")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action ?: error("Arrow click action is missing")
        compose.runOnIdle { clickArrow(); clickArrow() }
        assertEquals("Two taps in one frame must start one reorder request", 1, moveCalls)
        compose.onNodeWithTag("category-move-down-seed-expense-1").assertIsNotEnabled()
        compose.onNodeWithTag("category-active-name-购物").assertIsNotEnabled()
        compose.runOnIdle { complete?.invoke(false) }
        compose.waitForIdle()
        val firstAfter = compose.onNodeWithTag("category-row-seed-expense-0").fetchSemanticsNode().boundsInRoot.top
        val secondAfter = compose.onNodeWithTag("category-row-seed-expense-1").fetchSemanticsNode().boundsInRoot.top
        assertEquals(true, first < second)
        assertEquals(true, firstAfter < secondAfter)
        compose.onNodeWithTag("category-move-down-seed-expense-0").assertIsEnabled()
        compose.onNodeWithText("更新分类顺序失败，请重试。").assertIsDisplayed()
    }

    @Test fun settingsExposesCategoryAndEncryptedBackupActions() {
        val events = mutableListOf<String>()
        compose.setContent {
            CaijiTheme {
                SettingsScreen(
                    onCategories = { events += "categories" },
                    onExport = { events += "export:$it" },
                    onImport = { events += "import" }
                )
            }
        }

        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-theme"))
        compose.onNodeWithTag("settings-theme").assertIsDisplayed()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-feedback"))
        compose.onNodeWithTag("settings-feedback").assertIsDisplayed()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-categories"))
        compose.onNodeWithTag("settings-categories").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-export"))
        compose.onNodeWithTag("settings-export").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-categories"))
        val visibleBackupTop = compose.onNodeWithTag("settings-export").fetchSemanticsNode().boundsInRoot.top
        val visibleRestoreTop = compose.onNodeWithTag("settings-import").fetchSemanticsNode().boundsInRoot.top
        val visibleCategoriesTop = compose.onNodeWithTag("settings-categories").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Backup and restore should follow category management",
            visibleCategoriesTop < visibleBackupTop && visibleBackupTop < visibleRestoreTop)
        compose.onAllNodesWithText("加密备份").assertCountEquals(2)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-import"))
        compose.onNodeWithTag("settings-import").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-local-data"))
        compose.onNodeWithTag("settings-local-data").assertIsDisplayed()
        assertEquals(listOf("categories", "import"), events)
    }

    @Test fun longCategoryNameDoesNotOverlapSwitchAtCompactWidthAndLargeFont() {
        val category = CategoryCatalog.defaults().first().copy(name = "日常支出特别长的分类名称用于检查省略显示")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                CaijiTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        CategoryManagerScreen(
                            categories = listOf(category),
                            onBack = {},
                            onSave = { _, _, _, _, done -> done(false) },
                            onActive = { _, _, done -> done(true) },
                            onMove = { _, _, done -> done(true) },
                            initialType = EntryType.EXPENSE
                        )
                    }
                }
            }
        }

        val name = compose.onNodeWithTag("category-name-${category.id}", useUnmergedTree = true)
        val active = compose.onNodeWithTag("category-active-name-${category.name}", useUnmergedTree = true)
        name.assertIsDisplayed()
        active.assertIsDisplayed()
        assertEquals(false, name.fetchSemanticsNode().boundsInRoot.right > active.fetchSemanticsNode().boundsInRoot.left)
    }
}
