package app.caiji.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.core.view.WindowInsetsControllerCompat

internal enum class CaijiThemeId(val key: String, val label: String) {
    AMBER("amber", "琥珀暖白"), BAMBOO("bamboo", "青竹"), SEA("sea", "海盐蓝"), MIST("mist", "雾紫"), GRAPHITE("graphite", "石墨深色")
}

private data class CaijiColors(
    val background: Color, val surface: Color, val surfaceAlt: Color, val ink: Color, val secondaryText: Color,
    val divider: Color, val primary: Color, val onPrimary: Color, val primarySoft: Color,
    val income: Color, val incomeSoft: Color, val danger: Color, val dangerSoft: Color, val isDark: Boolean = false
)

private val themeOptions = mapOf(
    CaijiThemeId.AMBER to CaijiColors(Color(0xFFF7F6F2), Color(0xFFFFFEFA), Color(0xFFF1F0EA), Color(0xFF282A26), Color(0xFF777970), Color(0xFFE9E6DD), Color(0xFFC4821C), Color(0xFF282A26), Color(0xFFF7EDD8), Color(0xFF39704F), Color(0xFFE8F0E8), Color(0xFFA74636), Color(0xFFFAECE7)),
    CaijiThemeId.BAMBOO to CaijiColors(Color(0xFFF2F7F4), Color(0xFFFCFFFD), Color(0xFFE7F0EB), Color(0xFF233A31), Color(0xFF66786F), Color(0xFFDCE7E0), Color(0xFF357961), Color(0xFFFFFFFF), Color(0xFFDDEFE6), Color(0xFF39704F), Color(0xFFE3F0E7), Color(0xFFA74636), Color(0xFFFAECE7)),
    CaijiThemeId.SEA to CaijiColors(Color(0xFFF3F7FB), Color(0xFFFFFFFF), Color(0xFFE9F0F7), Color(0xFF243449), Color(0xFF68788A), Color(0xFFDDE5EE), Color(0xFF3977B7), Color(0xFFFFFFFF), Color(0xFFE0EDF9), Color(0xFF39704F), Color(0xFFE3F0E7), Color(0xFFA74636), Color(0xFFFAECE7)),
    CaijiThemeId.MIST to CaijiColors(Color(0xFFF7F4FA), Color(0xFFFFFCFF), Color(0xFFF0EBF5), Color(0xFF352A42), Color(0xFF796F84), Color(0xFFE6DFED), Color(0xFF8063A8), Color(0xFFFFFFFF), Color(0xFFEEE5F7), Color(0xFF39704F), Color(0xFFE3F0E7), Color(0xFFA74636), Color(0xFFFAECE7)),
    CaijiThemeId.GRAPHITE to CaijiColors(Color(0xFF171B21), Color(0xFF242A33), Color(0xFF2D3540), Color(0xFFEEF2F7), Color(0xFFAAB4C2), Color(0xFF38414E), Color(0xFF90AFFF), Color(0xFF171B21), Color(0xFF2C3850), Color(0xFF83C7A3), Color(0xFF263B34), Color(0xFFF39B90), Color(0xFF442E32), true)
)

internal object CaijiPalette {
    private val active = mutableStateOf(themeOptions.getValue(CaijiThemeId.AMBER))
    private val activeId = mutableStateOf(CaijiThemeId.AMBER)
    val Background get() = active.value.background
    val Surface get() = active.value.surface
    val SurfaceAlt get() = active.value.surfaceAlt
    val Ink get() = active.value.ink
    val SecondaryText get() = active.value.secondaryText
    val Divider get() = active.value.divider
    val Primary get() = active.value.primary
    val OnPrimary get() = active.value.onPrimary
    val PrimarySoft get() = active.value.primarySoft
    val Income get() = active.value.income
    val IncomeSoft get() = active.value.incomeSoft
    val Danger get() = active.value.danger
    val DangerSoft get() = active.value.dangerSoft
    val isDark get() = active.value.isDark
    val selected get() = activeId.value
    val options get() = CaijiThemeId.entries
    fun preview(id: CaijiThemeId): List<Color> {
        val colors = themeOptions.getValue(id)
        return listOf(colors.primary, colors.primarySoft, colors.surfaceAlt, colors.income)
    }
    fun select(id: CaijiThemeId) { activeId.value = id; active.value = themeOptions.getValue(id) }
}

private val CaijiTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Medium, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
)

private val CaijiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
internal fun CaijiTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = CaijiPalette.Background.toArgb()
        window.navigationBarColor = CaijiPalette.Background.toArgb()
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = !CaijiPalette.isDark
            isAppearanceLightNavigationBars = !CaijiPalette.isDark
        }
    }
    val colors = if (CaijiPalette.isDark) darkColorScheme(
        primary = CaijiPalette.Primary, onPrimary = CaijiPalette.OnPrimary,
        primaryContainer = CaijiPalette.PrimarySoft, onPrimaryContainer = CaijiPalette.Ink,
        secondary = CaijiPalette.Ink, onSecondary = CaijiPalette.Surface,
        tertiary = CaijiPalette.Income, onTertiary = CaijiPalette.Background,
        secondaryContainer = CaijiPalette.PrimarySoft, onSecondaryContainer = CaijiPalette.Ink,
        background = CaijiPalette.Background, onBackground = CaijiPalette.Ink,
        surface = CaijiPalette.Surface, onSurface = CaijiPalette.Ink,
        surfaceVariant = CaijiPalette.SurfaceAlt, onSurfaceVariant = CaijiPalette.SecondaryText,
        outline = CaijiPalette.Divider, error = CaijiPalette.Danger, onError = CaijiPalette.Background
    ) else lightColorScheme(
        primary = CaijiPalette.Primary, onPrimary = CaijiPalette.OnPrimary,
        primaryContainer = CaijiPalette.PrimarySoft, onPrimaryContainer = CaijiPalette.Ink,
        secondary = CaijiPalette.Ink, onSecondary = CaijiPalette.Surface,
        tertiary = CaijiPalette.Income, onTertiary = Color.White,
        secondaryContainer = CaijiPalette.PrimarySoft, onSecondaryContainer = CaijiPalette.Ink,
        background = CaijiPalette.Background, onBackground = CaijiPalette.Ink,
        surface = CaijiPalette.Surface, onSurface = CaijiPalette.Ink,
        surfaceVariant = CaijiPalette.SurfaceAlt, onSurfaceVariant = CaijiPalette.SecondaryText,
        outline = CaijiPalette.Divider, error = CaijiPalette.Danger, onError = Color.White
    )
    MaterialTheme(
        colorScheme = colors,
        typography = CaijiTypography,
        shapes = CaijiShapes,
        content = content
    )
}
