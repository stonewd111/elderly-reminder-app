package com.elder.reminder.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.elder.reminder.data.CategoryIconPrefs
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.repeatText
import com.elder.reminder.domain.TimeFormats
import com.elder.reminder.ui.theme.BluePrimary
import com.elder.reminder.ui.theme.BlueDeep
import com.elder.reminder.ui.theme.GreenTag
import com.elder.reminder.ui.theme.OrangeTag
import com.elder.reminder.ui.theme.PurpleTag
import com.elder.reminder.ui.theme.TextPrimary
import com.elder.reminder.ui.theme.TextSecondary
import com.elder.reminder.ui.theme.elderSp
import android.graphics.BitmapFactory
import android.net.Uri

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = elderSp(26f), fontWeight = FontWeight.Bold, color = BlueDeep)
                if (subtitle != null) {
                    Text(subtitle, fontSize = elderSp(16f), color = TextSecondary)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun Tag(text: String, color: Color, onLight: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (onLight) color.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.2f)
    ) {
        Text(
            text,
            fontSize = elderSp(13f),
            color = if (onLight) color else Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * 大类图标视图：Emoji 直接显示文字；自定义上传图片加载显示。
 * 供提醒卡片、常用页、编辑页、大屏共用。按 label（大类显示名）取图标。
 */
@Composable
fun CategoryIconView(
    icon: CategoryIconPrefs.CategoryIcon,
    emojiSize: TextUnit = 22.sp,
    imageSize: Dp = 46.dp,
    modifier: Modifier = Modifier,
    clipShape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    when (icon) {
        is CategoryIconPrefs.CategoryIcon.Emoji -> {
            Text(icon.emoji, fontSize = emojiSize, modifier = modifier)
        }
        is CategoryIconPrefs.CategoryIcon.Image -> {
            val context = LocalContext.current
            var bitmap by remember(icon.uri) { mutableStateOf<ImageBitmap?>(null) }
            androidx.compose.runtime.LaunchedEffect(icon.uri) {
                bitmap = runCatching {
                    val resolver = context.contentResolver
                    resolver.openInputStream(Uri.parse(icon.uri))?.use { ins ->
                        BitmapFactory.decodeStream(ins)?.asImageBitmap()
                    }
                }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .size(imageSize)
                        .clip(clipShape)
                )
            } else {
                Box(
                    modifier.size(imageSize).clip(clipShape).background(Color(0xFFEAF1FF)),
                    contentAlignment = Alignment.Center
                ) { Text("📌", fontSize = emojiSize) }
            }
        }
    }
}

/** 分类对应的强调色。 */
private fun categoryAccent(category: String): Color = when (category) {
    Reminder.Category.MEDICATION -> BluePrimary
    Reminder.Category.HEALTH -> GreenTag
    Reminder.Category.EXERCISE -> OrangeTag
    Reminder.Category.WATER -> Color(0xFF0EA5E9)
    else -> PurpleTag
}

/**
 * 提醒卡片（v1.6.0 按参考图"提醒项布局"全新排版）：
 *  第一行（字体固定，不随缩放）：[图标] [编号]时间 | 分类 | 次小类 | 删除图标（红色垃圾桶）
 *  第二行（内容居中，字体随缩放）：药品名+提醒内容
 *  "已服用"按钮已移除（与右上角滑动按钮功能重复，用户要求去掉）。
 */
@Composable
fun ReminderCard(
    reminder: Reminder,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    index: Int? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val accent = when (reminder.id % 3L) {
        0L -> OrangeTag
        1L -> GreenTag
        else -> PurpleTag
    }
    val catColor = categoryAccent(reminder.category)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            // ===== 第一行：整行高度与字号均固定，不随字体大小变化 =====
            Row(
                Modifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = CategoryIconPrefs.iconOf(
                    categoryLabelOf(reminder),
                    reminder.category
                )
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    CategoryIconView(
                        icon,
                        emojiSize = 15.sp,
                        imageSize = 22.dp,
                        modifier = Modifier
                    )
                }
                Spacer(Modifier.width(6.dp))
                // [编号] 时间（固定字号，单行）
                Text(
                    (if (index != null) "[$index] " else "") + TimeFormats.time(reminder.triggerAt),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(Modifier.width(6.dp))
                // 分类名 / 次小类：紧跟时间，占剩余宽度，固定字号，超长省略
                Row(
                    Modifier.weight(1f).clipToBounds(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FixedTag(categoryLabelOf(reminder), catColor)
                    if (reminder.subCategory.isNotBlank()) {
                        FixedTag(reminder.subCategory, catColor)
                    }
                }
                Spacer(Modifier.width(6.dp))
                // 右侧固定列：滑动按钮（缩小到与时间同高）+ 红色垃圾桶，两者右侧固定对齐
                if (trailing != null) {
                    Box(
                        Modifier.width(40.dp).height(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp
                        ) {
                            Box(Modifier.scale(0.72f)) { trailing() }
                        }
                    }
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = Color(0xFFE5362F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            // ===== 第二行：内容（药品名+提醒内容），字体随缩放，居中 =====
            val display = displayText(reminder)
            if (display.isNotBlank()) {
                Text(
                    display,
                    fontSize = elderSp(18f),
                    color = TextPrimary,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 大类显示名：自定义大类（other）用保存的 categoryLabel，否则用默认分类名。 */
fun categoryLabelOf(r: Reminder): String =
    if (r.category == Reminder.Category.OTHER && r.categoryLabel.isNotBlank()) r.categoryLabel
    else Reminder.Category.label(r.category)

/** 固定字号标签（提醒卡片第一行专用）：不随字体大小缩放，高度紧贴文字。 */
@Composable
private fun FixedTag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
}

/** 内容显示：按（"药品"：提醒内容）格式；药品为空则仅内容。 */
private fun displayText(r: Reminder): String {
    val t = r.title.trim()
    val d = r.drug.trim()
    return when {
        d.isNotBlank() && t.isNotBlank() -> "$d：$t"
        d.isNotBlank() -> d
        else -> t
    }
}

@Composable
fun BigMicButton(
    label: String,
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            Modifier
                .size(150.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (recording) Color(0xFF3B82F6) else Color(0xFF2563EB),
                            Color(0xFF1D4ED8)
                        )
                    )
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
                Text(label, color = Color.White, fontSize = elderSp(18f), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun Waveform(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "phase"
    )
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(13) { i ->
            val h = if (active) {
                val v = kotlin.math.sin((phase * 2 * Math.PI + i * 0.45).toFloat())
                14 + (v * v) * 40
            } else 10f
            Box(
                Modifier
                    .width(5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(BluePrimary.copy(alpha = if (active) 0.85f else 0.3f))
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(30.dp)
    ) {
        Text(text, fontSize = elderSp(21f), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(29.dp)
    ) {
        Text(text, fontSize = elderSp(19f), color = TextPrimary)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = elderSp(20f), fontWeight = FontWeight.Bold, color = TextPrimary, modifier = modifier)
}

@Composable
fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = elderSp(18f), color = TextSecondary, modifier = Modifier.width(90.dp))
        Text(value, fontSize = elderSp(19f), color = TextPrimary, modifier = Modifier.weight(1f))
        if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
