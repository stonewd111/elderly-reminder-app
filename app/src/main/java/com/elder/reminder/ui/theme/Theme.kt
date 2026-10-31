package com.elder.reminder.ui.theme

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

val BluePrimary = Color(0xFF155EEF)
val BlueDeep = Color(0xFF173B72)
val BgLight = Color(0xFFF5F7FA)
val TextPrimary = Color(0xFF101828)
val TextSecondary = Color(0xFF667085)
val OrangeTag = Color(0xFFF79009)
val GreenTag = Color(0xFF12B76A)
val PurpleTag = Color(0xFF7A5AF8)
val AlarmRed = Color(0xFFE5362F)
val AlarmRedDark = Color(0xFFC4120C)
val CardWhite = Color(0xFFFFFFFF)

private val ElderColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = BlueDeep,
    secondary = GreenTag,
    background = BgLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = TextSecondary,
    error = AlarmRed
)

private val ElderTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp)
)

private val ElderShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun ElderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ElderColors,
        typography = ElderTypography,
        shapes = ElderShapes,
        content = content
    )
}
