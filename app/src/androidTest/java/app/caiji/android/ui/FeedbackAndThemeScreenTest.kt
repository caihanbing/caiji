package app.caiji.android.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.ui.theme.CaijiPalette
import app.caiji.android.ui.theme.CaijiTheme
import app.caiji.android.ui.theme.CaijiThemeId
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackAndThemeScreenTest {
    @get:Rule val compose = createComposeRule()
    @After fun restoreDefaultTheme() { CaijiPalette.select(CaijiThemeId.AMBER) }

    @Test fun selectingAnyOfFiveThemesAppliesItsPaletteImmediately() {
        compose.setContent {
            CaijiTheme {
                ThemePickerScreen(CaijiThemeId.AMBER, onSelect = CaijiPalette::select, onBack = {})
            }
        }
        assertEquals(5, CaijiPalette.options.size)
        compose.onNodeWithTag("theme-sea").performClick()
        assertEquals(CaijiThemeId.SEA, CaijiPalette.selected)
        assertEquals(CaijiPalette.preview(CaijiThemeId.SEA).first(), CaijiPalette.Primary)
    }

    @Test fun feedbackEditorRequiresTitleAndDescriptionBeforeSaving() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, app.caiji.android.data.FeedbackDatabase::class.java).build()
        compose.setContent {
            CaijiTheme {
                FeedbackEditorScreen(
                    repository = app.caiji.android.data.FeedbackRepository(context, database), existing = null,
                    draftId = "draft-test", onCancel = {}, onSave = { _, _, _, done -> done(true, null) }
                )
            }
        }
        compose.onNodeWithTag("feedback-save").assertIsNotEnabled()
        compose.onNodeWithTag("feedback-title").performClick()
        compose.onNodeWithTag("feedback-title").performTextInput("页面体验")
        compose.onNodeWithTag("feedback-save").assertIsNotEnabled()
        compose.onNodeWithTag("feedback-description").performTextInput("切换页面时希望保留位置")
        compose.onNodeWithTag("feedback-save").performClick()
        database.close()
    }
}
