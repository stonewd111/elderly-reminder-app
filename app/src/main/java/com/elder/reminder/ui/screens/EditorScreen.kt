package com.elder.reminder.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.reminder.alarm.ReminderScheduler
import com.elder.reminder.data.ReminderRepository
import com.elder.reminder.domain.TimeFormats
import com.elder.reminder.domain.TimeParser
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.repeatText
import com.elder.reminder.ui.components.InfoRow
import com.elder.reminder.ui.components.PrimaryButton
import com.elder.reminder.ui.components.ScreenHeader
import com.elder.reminder.ui.components.SecondaryButton
import com.elder.reminder.ui.theme.BluePrimary
import com.elder.reminder.ui.theme.TextPrimary
import com.elder.reminder.ui.theme.TextSecondary
import java.util.Calendar

@Composable
fun EditorScreen(
    initial: Reminder?,
    spokenText: String?,
    onBack: () -> Unit,
    onVoiceEdit: (Long?) -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val parsed = remember(spokenText) { spokenText?.let { TimeParser.parse(it) } }
    val defaultAt = remember {
        val c = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        c.timeInMillis
    }

    var title by remember { mutableStateOf(initial?.title ?: parsed?.title ?: "") }
    var triggerAt by remember { mutableStateOf(initial?.triggerAt ?: parsed?.triggerAt ?: defaultAt) }
    var mode by remember { mutableStateOf(initial?.mode ?: Reminder.Mode.VOICE) }
    var category by remember { mutableStateOf(initial?.category ?: Reminder.Category.OTHER) }
    var repeat by remember { mutableStateOf<Reminder.Repeat>(initial?.repeat ?: Reminder.Repeat.None) }
    var step by remember { mutableIntStateOf(1) }
    var showDelete by remember { mutableStateOf(false) }

    fun save() {
        val t = title.trim()
        if (t.isEmpty()) {
            android.widget.Toast.makeText(context, "请填写提醒内容", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val reminder = Reminder(
            id = initial?.id ?: ReminderRepository.nextId(),
            title = t,
            triggerAt = triggerAt,
            mode = mode,
            repeat = repeat,
            enabled = true,
            done = false,
            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
            category = category
        )
        ReminderRepository.upsert(reminder)
        ReminderScheduler.schedule(context, reminder)
        android.widget.Toast.makeText(context, "提醒已保存", android.widget.Toast.LENGTH_SHORT).show()
        onSaved()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
        ScreenHeader(
            if (step == 1) (if (initial == null) "新增提醒" else "编辑提醒") else "设置周期",
            onBack = { if (step == 2) step = 1 else onBack() }
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (step == 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard("🎙", "语音录入", "说出提醒内容", true, Modifier.weight(1f)) { onVoiceEdit(initial?.id) }
                    ModeCard("✏️", "手动输入", "自己输入内容", false, Modifier.weight(1f)) { /* 当前即手动 */ }
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("提醒内容") },
                    placeholder = { Text("例如：吃降压药") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 1
                )

                Spacer(Modifier.height(10.dp))
                Text("常用事项", fontSize = 15.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("吃药", "喝水", "散步", "量血压").forEach { name ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            modifier = Modifier.clickable { title = name }
                        ) {
                            Text(name, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("提醒分类", fontSize = 15.sp, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Reminder.Category.MEDICATION,
                                Reminder.Category.HEALTH,
                                Reminder.Category.EXERCISE,
                                Reminder.Category.WATER,
                                Reminder.Category.OTHER
                            ).forEach { c ->
                                val selected = category == c
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) BluePrimary else Color(0xFFF0F3F8),
                                    modifier = Modifier.weight(1f).clickable { category = c }
                                ) {
                                    Column(
                                        Modifier.padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(Reminder.Category.emoji(c), fontSize = 20.sp)
                                        Text(
                                            Reminder.Category.label(c),
                                            fontSize = 12.sp,
                                            color = if (selected) Color.White else TextPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("提醒方式", fontSize = 15.sp, color = TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "音效在【设置 → 提醒音效】中统一选择（系统闹铃 / 男声 / 女声 / 自定义）",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    InfoRow("重复", repeatTextOf(repeat)) { step = 2 }
                }

                Spacer(Modifier.height(22.dp))
                PrimaryButton("下一步：设置周期", onClick = { step = 2 })
                Spacer(Modifier.height(10.dp))
                val isExisting = initial?.id?.let { it > 0 } == true
                if (isExisting) {
                    SecondaryButton("🎙 语音修改这条提醒", onClick = { onVoiceEdit(initial.id) })
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("删除这条提醒", onClick = { showDelete = true })
                }
                Spacer(Modifier.height(24.dp))
            } else {
                RepeatOptions(repeat) { repeat = it }

                Spacer(Modifier.height(18.dp))
                Text("提醒时间", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        InfoRow("日期", TimeFormats.dayLabel(triggerAt) + "（" + com.elder.reminder.domain.dateCn(triggerAt) + "）") {
                            val c = Calendar.getInstance().apply { timeInMillis = triggerAt }
                            DatePickerDialog(context, { _, y, m, d ->
                                val nc = Calendar.getInstance().apply { timeInMillis = triggerAt }
                                nc.set(y, m, d); triggerAt = nc.timeInMillis
                            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                        }
                        InfoRow("时间", TimeFormats.time(triggerAt)) {
                            val c = Calendar.getInstance().apply { timeInMillis = triggerAt }
                            TimePickerDialog(context, { _, h, min ->
                                val nc = Calendar.getInstance().apply { timeInMillis = triggerAt }
                                nc.set(Calendar.HOUR_OF_DAY, h); nc.set(Calendar.MINUTE, min)
                                nc.set(Calendar.SECOND, 0); triggerAt = nc.timeInMillis
                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                PrimaryButton("保存", onClick = { save() })
                Spacer(Modifier.height(10.dp))
                SecondaryButton("上一步", onClick = { step = 1 })
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除提醒") },
            text = { Text("确定删除“${initial.title}”吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    ReminderScheduler.cancel(context, initial.id)
                    ReminderRepository.remove(initial.id)
                    onDeleted()
                }) { Text("删除", color = Color(0xFFE5362F)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}

private fun repeatTextOf(r: Reminder.Repeat): String {
    val tmp = Reminder(id = 0, title = "", triggerAt = 0, repeat = r)
    return tmp.repeatText()
}

@Composable
private fun ModeCard(
    emoji: String,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) Color(0xFF155EEF) else Color.White,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (highlighted) Color.White else TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = if (highlighted) Color(0xFFDCE7FF) else TextSecondary)
        }
    }
}

@Composable
private fun ModePill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (selected) BluePrimary else Color(0xFFF0F3F8),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            fontSize = 16.sp,
            color = if (selected) Color.White else TextPrimary,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun RepeatOptions(current: Reminder.Repeat, onChange: (Reminder.Repeat) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RepeatRow("不重复", current is Reminder.Repeat.None) { onChange(Reminder.Repeat.None) }
        RepeatRow("每天", current is Reminder.Repeat.Daily) { onChange(Reminder.Repeat.Daily) }
        RepeatRow("每周", current is Reminder.Repeat.Weekly) { onChange(Reminder.Repeat.Weekly(setOf(1))) }
        RepeatRow("每月", current is Reminder.Repeat.Monthly) {
            onChange(Reminder.Repeat.Monthly(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)))
        }
        RepeatRow("自定义间隔", current is Reminder.Repeat.Interval) { onChange(Reminder.Repeat.Interval(3)) }

        when (val r = current) {
            is Reminder.Repeat.Weekly -> WeekdayPicker(r.days) { onChange(Reminder.Repeat.Weekly(it)) }
            is Reminder.Repeat.Monthly -> NumberStepper("每月几号", r.day, 1..31) { onChange(Reminder.Repeat.Monthly(it)) }
            is Reminder.Repeat.Interval -> NumberStepper("每隔几天", r.days, 1..365) { onChange(Reminder.Repeat.Interval(it)) }
            else -> Unit
        }
    }
}

@Composable
private fun RepeatRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected) "◉" else "○", fontSize = 20.sp, color = if (selected) BluePrimary else TextSecondary)
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 18.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekdayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(1, 2, 3, 4, 5, 6, 7).forEach { d ->
            val on = d in days
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (on) BluePrimary else Color.White,
                modifier = Modifier.weight(1f).clickable {
                    val next = days.toMutableSet()
                    if (on) next.remove(d) else next.add(d)
                    if (next.isEmpty()) next.add(d)
                    onChange(next)
                }
            ) {
                Text(
                    com.elder.reminder.model.weekdayName(d).removePrefix("周"),
                    fontSize = 15.sp,
                    color = if (on) Color.White else TextPrimary,
                    modifier = Modifier.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun NumberStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 16.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (value > range.first) onChange(value - 1) }) { Text("－", fontSize = 22.sp) }
            Text("$value", fontSize = 20.sp, color = TextPrimary)
            TextButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("＋", fontSize = 22.sp) }
        }
    }
}
