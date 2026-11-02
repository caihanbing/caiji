package app.caiji.android

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.ui.theme.CaijiPalette
import app.caiji.android.ui.theme.CaijiThemeId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectedThemeIsRestoredWhenActivityIsRecreated() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putString("theme", CaijiThemeId.SEA.key).commit()
            CaijiPalette.select(CaijiThemeId.SEA)
        }

        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { assertEquals(CaijiThemeId.SEA, CaijiPalette.selected) }

        compose.activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putString("theme", CaijiThemeId.AMBER.key).commit()
            CaijiPalette.select(CaijiThemeId.AMBER)
        }
    }
}
