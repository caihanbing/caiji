package app.caiji.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class CaijiIconName {
    LEDGER,
    ADD,
    SETTINGS,
    BACK,
    CATEGORY,
    DATE,
    EDIT,
    DELETE,
    MONTH_PREVIOUS,
    MONTH_NEXT,
    IMPORT,
    EXPORT
}

@Composable
fun CaijiIcon(
    name: CaijiIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val image = when (name) {
        CaijiIconName.LEDGER -> Icons.AutoMirrored.Outlined.List
        CaijiIconName.ADD -> Icons.Outlined.Add
        CaijiIconName.SETTINGS -> Icons.Outlined.Settings
        CaijiIconName.BACK -> Icons.AutoMirrored.Outlined.ArrowBack
        CaijiIconName.CATEGORY -> null
        CaijiIconName.DATE -> Icons.Outlined.DateRange
        CaijiIconName.EDIT -> Icons.Outlined.Edit
        CaijiIconName.DELETE -> Icons.Outlined.Delete
        CaijiIconName.IMPORT, CaijiIconName.EXPORT -> null
        CaijiIconName.MONTH_PREVIOUS, CaijiIconName.MONTH_NEXT -> null
    }
    if (image == null) {
        if (name == CaijiIconName.CATEGORY) CategoryTagGlyph(contentDescription, modifier)
        else if (name == CaijiIconName.MONTH_PREVIOUS || name == CaijiIconName.MONTH_NEXT) {
            MonthChevronGlyph(name == CaijiIconName.MONTH_NEXT, contentDescription, modifier)
        } else FileActionGlyph(name == CaijiIconName.IMPORT, contentDescription, modifier)
        return
    }
    Icon(
        imageVector = image,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = LocalContentColor.current
    )
}

@Composable
private fun FileActionGlyph(isImport: Boolean, contentDescription: String?, modifier: Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp).semantics {
        if (contentDescription != null) this.contentDescription = contentDescription
    }) {
        val scale = size.minDimension / 24f
        val outline = Path().apply {
            moveTo(6f * scale, 3f * scale)
            lineTo(14f * scale, 3f * scale)
            lineTo(19f * scale, 8f * scale)
            lineTo(19f * scale, 21f * scale)
            lineTo(6f * scale, 21f * scale)
            close()
            moveTo(14f * scale, 3f * scale)
            lineTo(14f * scale, 8f * scale)
            lineTo(19f * scale, 8f * scale)
        }
        drawPath(outline, color, style = Stroke(width = 1.7f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val direction = if (isImport) 1f else -1f
        val centerX = 12f * scale
        val startY = (if (isImport) 10f else 18f) * scale
        val endY = (if (isImport) 17f else 11f) * scale
        drawLine(color, Offset(centerX, startY), Offset(centerX, endY), strokeWidth = 1.8f * scale, cap = StrokeCap.Round)
        drawLine(color, Offset(centerX, endY), Offset((centerX - 3f * scale), (endY - direction * 3f * scale)), strokeWidth = 1.8f * scale, cap = StrokeCap.Round)
        drawLine(color, Offset(centerX, endY), Offset((centerX + 3f * scale), (endY - direction * 3f * scale)), strokeWidth = 1.8f * scale, cap = StrokeCap.Round)
    }
}

@Composable
private fun MonthChevronGlyph(isRight: Boolean, contentDescription: String?, modifier: Modifier) {
    val color = LocalContentColor.current
    Canvas(
        modifier.size(24.dp).semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }
    ) {
        val scale = size.minDimension / 24f
        val left = if (isRight) 9f else 15f
        val right = if (isRight) 15f else 9f
        val path = Path().apply {
            moveTo(left * scale, 5f * scale)
            lineTo(right * scale, 12f * scale)
            lineTo(left * scale, 19f * scale)
        }
        drawPath(path, color, style = Stroke(width = 2.2f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun CategoryTagGlyph(contentDescription: String?, modifier: Modifier) {
    val color = LocalContentColor.current
    Canvas(
        modifier
            .size(24.dp)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            }
    ) {
        val scale = size.minDimension / 24f
        val path = Path().apply {
            moveTo(4f * scale, 11f * scale)
            lineTo(12f * scale, 3f * scale)
            lineTo(21f * scale, 3f * scale)
            lineTo(21f * scale, 12f * scale)
            lineTo(13f * scale, 21f * scale)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color, radius = 1.2f * scale, center = Offset(16.5f * scale, 7f * scale))
    }
}

internal fun categoryEmoji(key: String): String = when (key) {
    "other" -> "✨"
    "restaurant" -> "🍜"; "shopping_bag" -> "🛍️"; "train" -> "🚆"; "home" -> "🏠"; "daily" -> "🧹"
    "movie" -> "🎬"; "medical" -> "✚"; "school" -> "📚"; "phone" -> "📱"; "favorite" -> "💝"
    "salary" -> "💼"; "gift" -> "🎁"; "work" -> "🧰"; "savings" -> "💰"
    "coffee" -> "☕"; "tea" -> "🧋"; "fruit" -> "🍎"; "dessert" -> "🍰"; "grocery" -> "🥬"
    "clothes" -> "👕"; "beauty" -> "💄"; "haircut" -> "💇"; "digital" -> "📷"; "insurance" -> "🛡️"; "tax" -> "🧾"; "refund" -> "💳"
    "bus" -> "🚌"; "taxi" -> "🚕"; "car" -> "🚗"; "fuel" -> "⛽"; "parking" -> "🅿️"; "bicycle" -> "🚲"
    "flight" -> "✈️"; "travel" -> "🧳"; "hotel" -> "🏨"; "utilities" -> "💡"; "repair" -> "🛠️"; "furniture" -> "🪑"
    "game" -> "🎮"; "music" -> "🎵"; "fitness" -> "🏋️"; "pet" -> "🐾"; "baby" -> "🍼"; "family" -> "👨‍👩‍👧"
    "rent_income" -> "🏠"; "interest" -> "📈"; "allowance" -> "💵"
    "breakfast" -> "🍳"; "lunch" -> "🍱"; "dinner" -> "🥘"; "late_night" -> "🍢"
    "snack" -> "🍿"; "beverage" -> "🍺"; "takeaway" -> "🥡"; "dining_out" -> "🥂"
    "housekeeping" -> "🧑‍🍳"; "cleaning" -> "🧽"; "laundry" -> "🧺"; "kitchenware" -> "🍴"
    "bedding" -> "🛏️"; "flower" -> "💐"; "moving" -> "📦"; "delivery" -> "🚚"
    "motorcycle" -> "🏍️"; "high_speed_rail" -> "🚄"; "subway" -> "🚇"; "ferry" -> "⛴️"
    "toll" -> "🛣️"; "car_service" -> "🔧"; "driving" -> "🪪"; "shipping" -> "📮"
    "medicine" -> "💊"; "dental" -> "🦷"; "checkup" -> "🩺"; "glasses" -> "👓"
    "rehab" -> "🩹"; "books" -> "📖"; "stationery" -> "✏️"; "course" -> "🎓"
    "office" -> "🖥️"; "meeting" -> "🗓️"; "software" -> "💿"; "subscription" -> "🔑"
    "cloud" -> "☁️"; "investment" -> "📊"; "securities" -> "📉"; "loan" -> "🏦"
    "sports" -> "⚽"; "swimming" -> "🏊"; "ball_sports" -> "🏀"; "camping" -> "🏕️"
    "photography" -> "📸"; "concert" -> "🎤"; "retirement" -> "🧓"; "wedding" -> "💍"
    "festival" -> "🎆"; "present" -> "🎀"; "donation" -> "🤝"; "volunteer" -> "🫶"
    else -> "✨"
}
