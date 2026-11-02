package app.caiji.android.ui

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.ui.theme.CaijiPalette
import app.caiji.android.ui.theme.CaijiTheme
import app.caiji.android.ui.theme.CaijiThemeId
import app.caiji.android.data.FeedbackItemRow
import app.caiji.android.data.FeedbackItemWithScreenshots
import app.caiji.android.data.FeedbackScreenshotRow
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun pendingScreenshotPreviewResolvesItsDraftPathOnce() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, app.caiji.android.data.FeedbackDatabase::class.java).build()
        val draftId = "preview-draft"
        val filenames = (0..4).map { "preview-$it.png" }
        val files = filenames.map { File(context.cacheDir, "feedback-drafts/$draftId/$it").apply { parentFile?.mkdirs() } }
        files.forEachIndexed { index, file ->
            val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.rgb(index * 30, 80, 120))
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        }
        val now = System.currentTimeMillis()
        val existing = FeedbackItemWithScreenshots(
            FeedbackItemRow("preview-item", "测试标题", "测试描述", now, now),
            filenames.mapIndexed { index, filename ->
                FeedbackScreenshotRow("preview-item", index, "pending:$draftId/$filename")
            }
        )
        compose.setContent {
            CaijiTheme {
                FeedbackEditorScreen(
                    repository = app.caiji.android.data.FeedbackRepository(context, database), existing = existing,
                    draftId = draftId, onCancel = {}, onSave = { _, _, _, done -> done(true, null) }
                )
            }
        }
        compose.onAllNodesWithText("图片不可用").assertCountEquals(0)
        compose.onNodeWithTag("feedback-preview-0").assertIsDisplayed()
        compose.onNodeWithTag("feedback-image-strip").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("feedback-preview-4").assertIsDisplayed()
        database.close()
        files.first().parentFile?.deleteRecursively()
    }

    @Test fun feedbackListEditorAndThemeTitlesShareTheCenteredHeader() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, app.caiji.android.data.FeedbackDatabase::class.java).build()
        val repository = app.caiji.android.data.FeedbackRepository(context, database)

        compose.setContent {
            var screen by remember { mutableIntStateOf(0) }
            CaijiTheme {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    when (screen) {
                        0 -> ThemePickerScreen(CaijiThemeId.AMBER, onSelect = {}, onBack = {})
                        1 -> FeedbackListScreen(emptyList(), {}, {}, {}, {}, repository, {})
                        else -> FeedbackEditorScreen(repository, null, "center-title-draft", {}, { _, _, _, done -> done(true, null) })
                    }
                    androidx.compose.foundation.layout.Row(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                        TextButton(onClick = { screen = 1 }, modifier = Modifier.testTag("show-feedback-list")) { Text("list") }
                        TextButton(onClick = { screen = 2 }, modifier = Modifier.testTag("show-feedback-editor")) { Text("editor") }
                    }
                }
            }
        }
        assertPageTitleCentered()
        compose.onNodeWithTag("show-feedback-list").performClick()
        assertPageTitleCentered()
        compose.onNodeWithTag("show-feedback-editor").performClick()
        assertPageTitleCentered()
        database.close()
    }

    @Test fun feedbackEditorBackActionIsDisabledWhileSaving() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, app.caiji.android.data.FeedbackDatabase::class.java).build()
        compose.setContent {
            CaijiTheme {
                FeedbackEditorScreen(
                    app.caiji.android.data.FeedbackRepository(context, database), null, "pending-save", {},
                    { _, _, _, _ -> }
                )
            }
        }
        compose.onNodeWithTag("feedback-title").performTextInput("Title")
        compose.onNodeWithTag("feedback-description").performTextInput("Description")
        compose.onNodeWithTag("feedback-save").performClick()
        compose.onNodeWithTag("feedback-editor-cancel").assertIsNotEnabled()
        database.close()
    }

    private fun assertPageTitleCentered() {
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("page-title").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(root.center.x - title.center.x) <= 1f)
    }
}
