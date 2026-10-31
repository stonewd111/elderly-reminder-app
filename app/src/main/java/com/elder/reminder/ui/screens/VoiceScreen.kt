package com.elder.reminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    LaunchedEffect(Unit) {
        VoiceSession.reset()
        VoiceSession.start(context)
    }

    LaunchedEffect(state.finished, state.finalText) {
        if (state.finished && state.finalText.isNotBlank()) {
            onResult(state.finalText)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF5F7FA).let { it })
    ) {
        ScreenHeader("语音录入", subtitle = "请说出您要设置的提醒内容", onBack = onBack)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                "例如：明天上午九点去医院",
                fontSize = 16.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))
            Box(
                Modifier.size(140.dp).clip(CircleShape).background(Color(0xFFEAF1FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            Waveform(active = state.active || state.starting)
            Spacer(Modifier.height(24.dp))
            val hint = when {
                state.error != null -> state.error!!
                state.starting -> "正在准备语音识别…"
                state.partial.isNotBlank() -> state.partial
                else -> "正在识别中…"
            }
            Text(
                hint,
                fontSize = if (state.partial.isNotBlank()) 22.sp else 18.sp,
                color = if (state.error != null) Color(0xFFE5362F) else TextPrimary,
                fontWeight = if (state.partial.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.weight(1f))
            if (state.error != null) {
                PrimaryButton("重新说一遍", onClick = { VoiceSession.reset(); VoiceSession.start(context) })
                Spacer(Modifier.height(12.dp))
            } else {
                SecondaryButton("说完了，停止录入", onClick = { VoiceSession.stop() })
                Spacer(Modifier.height(12.dp))
            }
            SecondaryButton("取消", onClick = { VoiceSession.stop(); VoiceSession.reset(); onBack() })
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
            PrimaryButton("确认保存", onClick = {
                val id = existing?.id ?: ReminderRepository.nextId()
                val reminder = Reminder(
                    id = id,
                    title = parsed.title,
                    triggerAt = parsed.triggerAt,
                    mode = existing?.mode ?: Reminder.Mode.VOICE,
                    repeat = existing?.repeat ?: Reminder.Repeat.None,
                    enabled = true,
                    done = false
                )
                ReminderRepository.upsert(reminder)
                ReminderScheduler.schedule(context, reminder)
                android.widget.Toast.makeText(context, "提醒已保存", android.widget.Toast.LENGTH_SHORT).show()
                onSaved()
            })
            Spacer(Modifier.height(12.dp))
            SecondaryButton("重新说一遍", onClick = { onRetry() })
        }
    }
}
