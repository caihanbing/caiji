package app.caiji.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategorySettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun categoryManagerSwitchesTypeAndRetainsSortAndActivationControls() {
        val moved = mutableListOf<Pair<String, Int>>()
        val active = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = CategoryCatalog.defaults(),
                    onBack = {},
                    onSave = { _, _, _, _, done -> done(false) },
                    onActive = { id, enabled -> active += id to enabled },
                    onMove = { id, position, done -> moved += id to position; done(true) },
                    initialType = EntryType.EXPENSE
                )
            }
        }

        compose.onNodeWithTag("category-type-EXPENSE").assertIsSelected()
        compose.onAllNodesWithTag("category-move-down-seed-expense-0").assertCountEquals(0)
        compose.onNodeWithTag("category-row-seed-expense-0").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(0f, 140f))
            up()
        }
        compose.onNodeWithTag("category-active-name-餐饮").performClick()
        compose.onNodeWithTag("category-type-INCOME").performClick()
        compose.onNodeWithTag("category-type-INCOME").assertIsSelected()
        compose.onNodeWithText("工资").assertIsDisplayed()
        compose.onAllNodesWithText("餐饮").assertCountEquals(0)
        assertEquals(listOf("seed-expense-0" to 1), moved)
        assertEquals(listOf("seed-expense-0" to false), active)
    }

    @Test fun categoryEditorKeepsTypeFixedOnEditAndShowsAllBuiltInIconsOnCreate() {
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = CategoryCatalog.defaults(),
                    onBack = {},
                    onSave = { _, _, _, _, done -> done(false) },
                    onActive = { _, _ -> },
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
        compose.onNodeWithTag("category-editor-type-EXPENSE").assertIsSelected()
        assertEquals(48, CategoryCatalog.iconKeys().size)
        CategoryCatalog.iconKeys().forEach { icon -> compose.onAllNodesWithTag("category-icon-${icon.key}").assertCountEquals(1) }
        compose.onNodeWithText("分类名称").performTextInput("自定义分类")
        compose.onNodeWithTag("category-editor-type-INCOME").performClick()
        compose.onNodeWithTag("category-editor-type-INCOME").assertIsSelected()
    }

    @Test fun failedDropRestoresTheOriginalCategoryOrder() {
        compose.setContent {
            CaijiTheme {
                CategoryManagerScreen(
                    categories = CategoryCatalog.defaults(), onBack = {},
                    onSave = { _, _, _, _, done -> done(false) }, onActive = { _, _ -> },
                    onMove = { _, _, done -> done(false) }
                )
            }
        }
        val first = compose.onNodeWithTag("category-name-seed-expense-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val second = compose.onNodeWithTag("category-name-seed-expense-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("category-row-seed-expense-0").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(0f, 140f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("category-list").performScrollToNode(hasText("餐饮"))
        val firstAfter = compose.onNodeWithTag("category-name-seed-expense-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val secondAfter = compose.onNodeWithTag("category-name-seed-expense-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertEquals(true, first < second)
        assertEquals(true, firstAfter < secondAfter)
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

        compose.onNodeWithTag("settings-categories").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-export").assertIsDisplayed().performClick()
        compose.onNodeWithText("至少 8 个字符的密码").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("settings-import").assertIsDisplayed().performClick()
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
                            onActive = { _, _ -> },
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
