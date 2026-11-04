package com.elder.reminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.reminder.asr.VoiceSession
import com.elder.reminder.data.ReminderRepository
import com.elder.reminder.alarm.ReminderScheduler
import com.elder.reminder.domain.TimeFormats
import com.elder.reminder.domain.TimeParser
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.repeatText
import com.elder.reminder.ui.components.PrimaryButton
import com.elder.reminder.ui.components.SecondaryButton
import com.elder.reminder.ui.components.ScreenHeader
import com.elder.reminder.ui.components.Waveform
import com.elder.reminder.ui.theme.BluePrimary
import com.elder.reminder.ui.theme.GreenTag
import com.elder.reminder.ui.theme.TextPrimary
import com.elder.reminder.ui.theme.TextSecondary

/** 语音录入页（界面2 第2屏） */
@Composable
fun VoiceScreen(
    existingId: Long?,
    onBack: () -> Unit,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    val state by VoiceSession.state.collectAsState()

    // 按压说话模式：进入页面不自动录音，按住麦克风开始，抬起结束。
    // 抬起时若已识别到内容则提交，否则保持等待下一次按压。
    var holding by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished, state.finalText) {
        if (state.finished && state.finalText.isNotBlank()) {
            onResult(state.finalText)
        }
    }

    // 抬起结束：停止并提交识别结果（若为空则不跳转，允许重按）
    fun release() {
        holding = false
        VoiceSession.stopHold()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF5F7FA).let { it })
    ) {
        ScreenHeader("语音录入", subtitle = "按住麦克风说话，抬起结束", onBack = {
            release(); VoiceSession.reset(); onBack()
        })
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "例如：明天上午九点去医院",
                fontSize = 16.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            // 弹性空间：把麦克风按钮下移到屏幕中下部
            Spacer(Modifier.weight(1.2f))
            // 可按住说话的大按钮
            Box(
                Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(if (holding) Color(0xFF3B82F6) else Color(0xFFEAF1FF))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                holding = true
                                VoiceSession.reset()
                                VoiceSession.startHold(context)
                                tryAwaitRelease()
                                release()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (holding) Color.White else BluePrimary,
                        modifier = Modifier.size(64.dp)
                    )
                    if (holding) {
                        Text("松开结束", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Waveform(active = holding)
            Spacer(Modifier.height(24.dp))
            val hint = when {
                state.error != null -> state.error!!
                state.starting -> "正在准备语音识别…"
                holding && state.partial.isNotBlank() -> state.partial
                holding -> "正在听…请说话"
                else -> "按住上方按钮开始说话"
            }
            Text(
                hint,
                fontSize = if (state.partial.isNotBlank()) 22.sp else 18.sp,
                color = if (state.error != null) Color(0xFFE5362F) else TextPrimary,
                fontWeight = if (state.partial.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.weight(0.8f))
            if (state.error != null) {
                PrimaryButton("重试", onClick = { VoiceSession.reset() })
                Spacer(Modifier.height(12.dp))
            }
            SecondaryButton("取消", onClick = { release(); VoiceSession.reset(); onBack() })
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 识别结果确认页（界面2 第3屏） */
@Composable
fun ConfirmScreen(
    text: String,
    existing: Reminder?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val parsed = androidx.compose.runtime.remember(text) { TimeParser.parse(text) }

    Column(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFF5F7FA))
    ) {
        ScreenHeader("确认提醒内容", onBack = onBack)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).weight(1f)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GreenTag, modifier = Modifier.size(34.dp))
                Spacer(Modifier.size(10.dp))
                Text("已识别完成", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenTag)
            }
            Spacer(Modifier.height(16.dp))

            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("提醒内容", fontSize = 16.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text(parsed.title, fontSize = 19.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("日期", fontSize = 16.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text(TimeFormats.dayLabel(parsed.triggerAt) + "（" + com.elder.reminder.domain.dateCn(parsed.triggerAt) + "）", fontSize = 19.sp, color = TextPrimary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("时间", fontSize = 16.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text(TimeFormats.time(parsed.triggerAt), fontSize = 19.sp, color = TextPrimary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("提醒方式", fontSize = 16.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text(if (existing?.mode == Reminder.Mode.BEEP) "滴滴滴提醒" else "语音提醒", fontSize = 19.sp, color = TextPrimary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("重复", fontSize = 16.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text(existing?.repeatText() ?: "不重复", fontSize = 19.sp, color = TextPrimary)
                    }
                }
            }

            if (!parsed.hasTime) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "没有听清具体时间，已默认设为 1 小时后，可在编辑页调整。",
                    fontSize = 15.sp,
                    color = Color(0xFFF79009)
                )
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton("下一步：设置周期", onClick = {
                // 修复语音录入跳步 BUG：不再直接保存，交由编辑页设置周期后保存
                onSaved()
            })
            Spacer(Modifier.height(12.dp))
            SecondaryButton("重新说一遍", onClick = { onRetry() })
        }
    }
}
