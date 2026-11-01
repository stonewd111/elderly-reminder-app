package com.elder.reminder.ui.alarm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.reminder.data.AlarmThemePrefs
import com.elder.reminder.domain.TimeFormats
import com.elder.reminder.ui.theme.AlarmBlue
import com.elder.reminder.ui.theme.AlarmBlueDark
import com.elder.reminder.ui.theme.AlarmRed
import com.elder.reminder.ui.theme.AlarmRedDark
import com.elder.reminder.ui.theme.TextPrimary
import com.elder.reminder.ui.theme.elderSp

/**
 * 到点提醒全屏页（v1.4.0）：
 *  - 标题"到点了，请注意！"单行、按最大字体一次到位，不随字体大小设置调整
 *  - 大屏内提醒内容框文字随字体大小设置缩放
 *  - 停止提醒按钮紧贴内容框；新增"已服用"按钮（点选→已完成，未点/自动停止→延误）
 *  - 背景主题可配：红色（默认）/ 蓝色，仅背景色不同
 */
@Composable
fun AlarmScreen(
    title: String,
    modeText: String,
    repeatText: String,
    timeMillis: Long,
    alarmTheme: String = AlarmThemePrefs.THEME_RED,
    onStop: () -> Unit,
    onTaken: (() -> Unit)? = null,
    takenText: String = "已完成"
) {
    val isBlue = alarmTheme == AlarmThemePrefs.THEME_BLUE
    val bgTop = if (isBlue) AlarmBlue else AlarmRed
    val bgBottom = if (isBlue) AlarmBlueDark else AlarmRedDark
    val accent = if (isBlue) AlarmBlueDark else AlarmRedDark

    val transition = rememberInfiniteTransition(label = "alarm")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "bell"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            // 顶部：铃铛 + 单行大标题（固定最大字号，一次到位，不随字体大小设置调整）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.size(112.dp).scale(pulse).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                // "到点了，请注意！"8 个字：按屏宽自适应字号，保证单行完整显示、不被裁切
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val titleSp = (maxWidth.value / 8.6f).coerceIn(20f, 48f)
                    Text(
                        "到点了，请注意！",
                        fontSize = titleSp.sp,
                        lineHeight = (titleSp * 1.2f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 中部：提醒内容框 + 已服用按钮 + 停止提醒按钮，紧贴排列（内容少时整体上移，不留大空隙）
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            TimeFormats.time(timeMillis),
                            fontSize = elderSp(34f),
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        title,
                        fontSize = elderSp(30f),
                        lineHeight = elderSp(40f),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 完成按钮（所有分类）：点选→本次已完成；未点/自动停止→延误
                    if (onTaken != null) {
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = Color(0xFF12B76A),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onTaken
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(58.dp)) {
                                Text(
                                    takenText,
                                    fontSize = elderSp(22f),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 停止提醒按钮：紧贴内容框下方（内容少时也上移，避免与内容间隔过大）
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(34.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                onClick = onStop
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("停止提醒", fontSize = elderSp(26f), fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "如不点击，约 2 分钟后自动停止",
                fontSize = elderSp(14f),
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
