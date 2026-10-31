package com.elder.reminder.ui.alarm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.elder.reminder.domain.TimeFormats
import com.elder.reminder.ui.theme.AlarmRed
import com.elder.reminder.ui.theme.AlarmRedDark

/**
 * 到点提醒全屏页（界面2 第8屏）：红色背景 + 铃铛 + 提醒内容 + 停止按钮。
 */
@Composable
fun AlarmScreen(
    title: String,
    modeText: String,
    repeatText: String,
    timeMillis: Long,
    onStop: () -> Unit
) {
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
            .background(Brush.verticalGradient(listOf(AlarmRed, AlarmRedDark)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(132.dp).scale(pulse).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(26.dp))
            Text(
                "到点了，\n请注意！",
                fontSize = 44.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(30.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${TimeFormats.time(timeMillis)}  $modeText" + if (repeatText.isNotBlank()) "  $repeatText" else "",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
            Surface(
                shape = RoundedCornerShape(34.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                onClick = onStop
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("停止提醒", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AlarmRedDark)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "如不点击，约 2 分钟后自动停止",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
