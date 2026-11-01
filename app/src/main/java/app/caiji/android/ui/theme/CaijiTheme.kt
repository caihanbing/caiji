package app.caiji.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object CaijiPalette {
    val Background = Color(0xFFF7F6F2)
    val Surface = Color(0xFFFFFEFA)
    val SurfaceAlt = Color(0xFFF1F0EA)
    val Ink = Color(0xFF282A26)
    val SecondaryText = Color(0xFF777970)
    val Divider = Color(0xFFE9E6DD)
    val Primary = Color(0xFFC4821C)
    val OnPrimary = Ink
    val PrimarySoft = Color(0xFFF7EDD8)
    val Income = Color(0xFF39704F)
    val IncomeSoft = Color(0xFFE8F0E8)
    val Danger = Color(0xFFA74636)
    val DangerSoft = Color(0xFFFAECE7)
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
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CaijiPalette.Primary,
            onPrimary = CaijiPalette.OnPrimary,
            primaryContainer = CaijiPalette.PrimarySoft,
            onPrimaryContainer = CaijiPalette.Ink,
            secondary = CaijiPalette.Ink,
            onSecondary = CaijiPalette.Surface,
            tertiary = CaijiPalette.Income,
            onTertiary = Color.White,
            secondaryContainer = CaijiPalette.PrimarySoft,
            onSecondaryContainer = CaijiPalette.Ink,
            background = CaijiPalette.Background,
            onBackground = CaijiPalette.Ink,
            surface = CaijiPalette.Surface,
            onSurface = CaijiPalette.Ink,
            surfaceVariant = CaijiPalette.SurfaceAlt,
            onSurfaceVariant = CaijiPalette.SecondaryText,
            outline = CaijiPalette.Divider,
            error = CaijiPalette.Danger,
            onError = Color.White
        ),
        typography = CaijiTypography,
        shapes = CaijiShapes,
        content = content
    )
}
