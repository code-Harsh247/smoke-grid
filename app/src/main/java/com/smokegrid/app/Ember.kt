package com.smokegrid.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Ember palette (dark), shared by the app screens. Grid colours live in GridPalette.
internal val Ground = Color(0xFF1A1714)
internal val CardFill = Color(0xFF221E1A)
internal val TextPrimary = Color(0xFFF3E6D3)
internal val TextSecondary = Color(0xFFA89A88)
internal val ButtonFill = Color(0xFF2A231E)
internal val ButtonBorder = Color(0xFF3A3029)
internal val Accent = Color(0xFFF59A52)

/** Manrope variable font; each weight sets its wght axis. */
internal val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
)

internal val Caption = TextStyle(fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
internal val Small = TextStyle(fontFamily = Manrope, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, color = TextSecondary)
internal val Label = TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

@Composable
internal fun Swatch(color: Color, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(if (sizeDp > 11) 4.dp else 3.dp))
            .background(color)
    )
}

/** Line icons on a 24 grid, tinted by Icon. */
private fun lineIcon(name: String, strokeWidth: Float, vararg paths: String, sizeDp: Int = 18): ImageVector =
    ImageVector.Builder(name, sizeDp.dp, sizeDp.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

internal val UndoIcon = lineIcon("undo", 2f, "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
internal val MinusIcon = lineIcon("minus", 2.2f, "M5 12h14")
internal val PlusIcon = lineIcon("plus", 2.2f, "M12 5v14", "M5 12h14")
internal val ChevronRightIcon = lineIcon("chevron_right", 2f, "m9 6 6 6-6 6")
internal val BackIcon = lineIcon("back", 2f, "m15 18-6-6 6-6", sizeDp = 22)
