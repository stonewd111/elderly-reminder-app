package com.elder.reminder.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BlueDeep)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 16.sp, color = TextSecondary)
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
            fontSize = 13.sp,
            color = if (onLight) color else Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    val accent = when (reminder.id % 3L) {
        0L -> OrangeTag
        1L -> GreenTag
        else -> PurpleTag
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("⏰", fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        TimeFormats.time(reminder.triggerAt),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Tag(
                        Reminder.Category.label(reminder.category),
                        when (reminder.category) {
                            Reminder.Category.MEDICATION -> BluePrimary
                            Reminder.Category.HEALTH -> GreenTag
                            Reminder.Category.EXERCISE -> OrangeTag
                            Reminder.Category.WATER -> Color(0xFF0EA5E9)
                            else -> PurpleTag
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(reminder.title, fontSize = 19.sp, color = TextPrimary, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                Text(
                    TimeFormats.dayLabel(reminder.triggerAt) + "  " + reminder.repeatText(),
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
        }
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
                Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
        Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(29.dp)
    ) {
        Text(text, fontSize = 19.sp, color = TextPrimary)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = modifier)
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
        Text(label, fontSize = 18.sp, color = TextSecondary, modifier = Modifier.width(90.dp))
        Text(value, fontSize = 19.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

val ScreenPadding = 20.dp
