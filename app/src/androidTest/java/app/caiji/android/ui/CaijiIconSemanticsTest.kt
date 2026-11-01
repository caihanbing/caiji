package app.caiji.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.foundation.layout.Column
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.ui.theme.CaijiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaijiIconSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun outlineIconsExposeAccessibleLabels() {
        compose.setContent {
            CaijiTheme {
                Column {
                    CaijiIcon(CaijiIconName.SETTINGS, contentDescription = "设置")
                    CaijiIcon(CaijiIconName.CATEGORY, contentDescription = "分类")
                }
            }
        }
        compose.onNodeWithContentDescription("设置").assertIsDisplayed()
        compose.onNodeWithContentDescription("分类").assertIsDisplayed()
    }
}
