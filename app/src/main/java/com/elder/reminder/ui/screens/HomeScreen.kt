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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.reminder.domain.LunarDate
import com.elder.reminder.domain.TimeFormats
import com.elder.reminder.domain.weekdayCn
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.repeatText
import com.elder.reminder.model.cnLabel
import com.elder.reminder.ui.components.BigMicButton
import com.elder.reminder.ui.components.ReminderCard
import com.elder.reminder.ui.components.SectionTitle
import com.elder.reminder.ui.theme.BlueDeep
import com.elder.reminder.ui.theme.BluePrimary
import com.elder.reminder.ui.theme.TextPrimary
import com.elder.reminder.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    items: List<Reminder>,
    recording: Boolean,
    onVoice: () -> Unit,
    onEdit: (Reminder) -> Unit,
    onOpenList: () -> Unit,
    onSettings: () -> Unit
) {
    val todayReminders = remember(items) { upcomingToday(items) }
    val dateTitle = remember { SimpleDateFormat("M月d日", Locale.CHINA).format(Date()) }
    val lunar = remember { LunarDate.text() }
    val weekday = remember { weekdayCn(System.currentTimeMillis()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("语音提醒助手", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BlueDeep)
                Text("让生活更有规律", fontSize = 16.sp, color = TextSecondary)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = TextSecondary)
            }
        }

        Spacer(Modifier.height(14.dp))

        // 日期卡片
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFEAF1FF), Color(0xFFF7FAFF)))
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Text("📅", fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "$dateTitle $weekday",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueDeep
                    )
                    if (lunar.isNotBlank()) {
                        Text(lunar, fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("今天的提醒", Modifier.weight(1f))
            Text(
                "全部提醒 >",
                fontSize = 15.sp,
                color = BluePrimary,
                modifier = Modifier.clickable { onOpenList() }
            )
        }

        Spacer(Modifier.height(10.dp))

        if (todayReminders.isEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("今天还没有提醒", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("点下方蓝色大按钮，说出您要做的事", fontSize = 15.sp, color = TextSecondary)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                todayReminders.take(4).forEach { r ->
                    ReminderCard(r, onClick = { onEdit(r) })
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BigMicButton(
                label = if (recording) "正在听…" else "按住 说出提醒",
                recording = recording,
                onClick = onVoice
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "说一句就好，例如：明天上午九点去医院拿报告",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun upcomingToday(items: List<Reminder>): List<Reminder> {
    val cal = Calendar.getInstance()
    val start = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = start + 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    return items.filter { r ->
        if (!r.enabled || r.done) return@filter false
        val next = when (r.repeat) {
            Reminder.Repeat.None -> if (r.triggerAt > now) r.triggerAt else null
            else -> com.elder.reminder.alarm.ReminderScheduler.nextTrigger(r, now)
        }
        next != null && next in start until end
    }.sortedBy { it.triggerAt }
}

/** 全部提醒列表 */
@Composable
fun ReminderListScreen(
    items: List<Reminder>,
    onAdd: () -> Unit,
    onEdit: (Reminder) -> Unit,
    onToggle: (Reminder, Boolean) -> Unit
) {
    var filter by remember { mutableStateOf(0) } // 0全部 1今天 2未来 3已完成
    val filters = listOf("全部", "今天", "未来", "已完成")

    val now = System.currentTimeMillis()
    val shown = remember(items, filter) {
        when (filter) {
            1 -> upcomingToday(items)
            2 -> items.filter { it.enabled && !it.done && it.triggerAt > now }
            3 -> items.filter { it.done }
            else -> items
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "提醒列表",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = BlueDeep,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAdd) {
                Text("＋", fontSize = 30.sp, color = BluePrimary)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            filters.forEachIndexed { index, name ->
                val selected = filter == index
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) BluePrimary else Color.White,
                    modifier = Modifier.clickable { filter = index }
                ) {
                    Text(
                        name,
                        fontSize = 16.sp,
                        color = if (selected) Color.White else TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (shown.isEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("没有符合条件的提醒", fontSize = 18.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("点右上角 ＋ 新建一条提醒", fontSize = 15.sp, color = TextSecondary)
                }
            }
        } else {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                shown.forEach { r ->
                    ReminderCard(
                        r,
                        onClick = { onEdit(r) },
                        trailing = {
                            androidx.compose.material3.Switch(
                                checked = r.enabled && !r.done,
                                onCheckedChange = { onToggle(r, it) }
                            )
                        }
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** 常用事项模板 */
@Composable
fun CommonScreen(itemCreator: (String, Int, Int, Reminder.Repeat, Reminder.Mode) -> Unit) {
    val templates = listOf(
        Template("吃降压药", 8, 0, Reminder.Repeat.Daily, Reminder.Mode.VOICE, "💊"),
        Template("吃降压药", 15, 0, Reminder.Repeat.Daily, Reminder.Mode.VOICE, "💊"),
        Template("下楼散步", 18, 0, Reminder.Repeat.Daily, Reminder.Mode.VOICE, "🚶"),
        Template("睡前吃药", 20, 30, Reminder.Repeat.Daily, Reminder.Mode.VOICE, "🌙"),
        Template("喝水", 10, 0, Reminder.Repeat.Daily, Reminder.Mode.BEEP, "🥤"),
        Template("量血压", 9, 0, Reminder.Repeat.Daily, Reminder.Mode.VOICE, "🩺")
    )

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("常用事项", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BlueDeep)
        Spacer(Modifier.height(4.dp))
        Text("点一下就能添加，按天重复提醒", fontSize = 15.sp, color = TextSecondary)
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            templates.forEach { t ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().clickable {
                        itemCreator(t.title, t.hour, t.minute, t.repeat, t.mode)
                    }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFEAF1FF)),
                            contentAlignment = Alignment.Center
                        ) { Text(t.emoji, fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                String.format(Locale.CHINA, "%02d:%02d", t.hour, t.minute) + "  " + t.repeat.cnLabel(),
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                        Text("添加", fontSize = 16.sp, color = BluePrimary)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private data class Template(
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeat: Reminder.Repeat,
    val mode: Reminder.Mode,
    val emoji: String
)
