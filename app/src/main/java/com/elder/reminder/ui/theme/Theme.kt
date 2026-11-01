package com.elder.reminder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
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
val AlarmBlue = Color(0xFF1E6FEB)
val AlarmBlueDark = Color(0xFF0F4FBF)
val CardWhite = Color(0xFFFFFFFF)

/**
 * 全局字体缩放（老人视角适配）。
 * 标准 1.0 / 大 1.15 / 特大 1.3。默认标准。
 */
data class FontScaleState(val scale: Float = 1f)

val LocalFontScale = staticCompositionLocalOf { FontScaleState(1f) }

/** 按全局缩放系数放大字号（老人视角）。 */
fun Float.scaled(scale: Float): Float = this * scale

/**
 * 读取当前全局字体缩放系数（Composable 内使用）。
 * 标准 1.0 / 大 1.15 / 特大 1.3。
 */
@Composable
fun elderFontScale(): Float = LocalFontScale.current.scale

/**
 * 让硬编码 fontSize = X.sp 跟随全局字体缩放（老人视角适配）。
 * 替换各处硬编码字号，使"字体大小"设置真正作用于文字本身。
 * 大屏标题等固定字号请勿使用本函数（用原生 .sp）。
 */
@Composable
fun elderSp(v: Float): TextUnit = (v * LocalFontScale.current.scale).sp

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
fun ElderTheme(scale: Float = 1f, content: @Composable () -> Unit) {
    val base = ElderTypography
    val scaledType = Typography(
        headlineMedium = base.headlineMedium.copy(fontSize = (base.headlineMedium.fontSize.value * scale).sp, lineHeight = if (base.headlineMedium.lineHeight != TextUnit.Unspecified) (base.headlineMedium.lineHeight.value * scale).sp else TextUnit.Unspecified),
        titleLarge = base.titleLarge.copy(fontSize = (base.titleLarge.fontSize.value * scale).sp, lineHeight = if (base.titleLarge.lineHeight != TextUnit.Unspecified) (base.titleLarge.lineHeight.value * scale).sp else TextUnit.Unspecified),
        titleMedium = base.titleMedium.copy(fontSize = (base.titleMedium.fontSize.value * scale).sp, lineHeight = if (base.titleMedium.lineHeight != TextUnit.Unspecified) (base.titleMedium.lineHeight.value * scale).sp else TextUnit.Unspecified),
        bodyLarge = base.bodyLarge.copy(fontSize = (base.bodyLarge.fontSize.value * scale).sp, lineHeight = if (base.bodyLarge.lineHeight != TextUnit.Unspecified) (base.bodyLarge.lineHeight.value * scale).sp else TextUnit.Unspecified),
        bodyMedium = base.bodyMedium.copy(fontSize = (base.bodyMedium.fontSize.value * scale).sp, lineHeight = if (base.bodyMedium.lineHeight != TextUnit.Unspecified) (base.bodyMedium.lineHeight.value * scale).sp else TextUnit.Unspecified),
        labelLarge = base.labelLarge.copy(fontSize = (base.labelLarge.fontSize.value * scale).sp, lineHeight = if (base.labelLarge.lineHeight != TextUnit.Unspecified) (base.labelLarge.lineHeight.value * scale).sp else TextUnit.Unspecified),
        labelMedium = base.labelMedium.copy(fontSize = (base.labelMedium.fontSize.value * scale).sp, lineHeight = if (base.labelMedium.lineHeight != TextUnit.Unspecified) (base.labelMedium.lineHeight.value * scale).sp else TextUnit.Unspecified)
    )
    CompositionLocalProvider(LocalFontScale provides FontScaleState(scale)) {
        MaterialTheme(
            colorScheme = ElderColors,
            typography = scaledType,
            shapes = ElderShapes,
            content = content
        )
    }
}
