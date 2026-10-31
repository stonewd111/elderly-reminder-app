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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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

/** 计算某条提醒的下一次实际触发时间（重复提醒用调度器算未来；单次用 triggerAt，已过期返回 null）。 */
private fun nextFireAt(r: Reminder, now: Long): Long? {
    return when (r.repeat) {
        Reminder.Repeat.None -> if (r.triggerAt > now) r.triggerAt else null
        else -> com.elder.reminder.alarm.ReminderScheduler.nextTrigger(r, now)
    }
}

/** 返回 t 所在日的 0 点。 */
private fun dayStart(t: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/**
 * 判断提醒"今天是否已经历过"（今天该时点已过）。
 * 用户诉求（问题2/3）：今日已过去/已关闭的提醒不应出现在"未来"页，
 * 应留在"今天"（未关闭）或"已完成"（关闭后）。故按此把今天已过的提醒归入"今天"。
 */
private fun hasFiredToday(r: Reminder, now: Long): Boolean {
    val base = Calendar.getInstance().apply { timeInMillis = r.triggerAt }
    val today = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, base.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return today.timeInMillis <= now
}

/**
 * 提醒的展示归属日（0点）。
 *  - 今天已触发过（单次已过 / 重复今天时点已过）→ 归今天，避免"今日已过去"跑进"未来"。
 *  - 否则用下一次触发时间所在日；无未来触发则用原定时间。
 */
private fun effectiveDay(r: Reminder, now: Long, todayStart: Long): Long {
    if (hasFiredToday(r, now)) return todayStart
    val next = nextFireAt(r, now)
    return if (next != null) dayStart(next) else dayStart(r.triggerAt)
}

/** 今日提醒：未完成、已启用，且归属日落在今天（含今天已触发过的提醒）。 */
private fun upcomingToday(items: List<Reminder>): List<Reminder> {
    val todayStart = dayStart(System.currentTimeMillis())
    val tomorrowStart = todayStart + 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    return items.filter { r ->
        if (!r.enabled || r.done) return@filter false
        val day = effectiveDay(r, now, todayStart)
        day in todayStart until tomorrowStart
    }.sortedBy { nextFireAt(it, now) ?: it.triggerAt }
}

/** 未来提醒：未完成、已启用，且归属日在明天 0点 及以后（绝不含今日已过去）。 */
private fun upcomingFuture(items: List<Reminder>): List<Reminder> {
    val todayStart = dayStart(System.currentTimeMillis())
    val tomorrowStart = todayStart + 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    return items.filter { r ->
        if (!r.enabled || r.done) return@filter false
        effectiveDay(r, now, todayStart) >= tomorrowStart
    }.sortedBy { nextFireAt(it, now) ?: it.triggerAt }
}

/** 全部提醒列表 */
@Composable
fun ReminderListScreen(
    items: List<Reminder>,
    onAdd: () -> Unit,
    onEdit: (Reminder) -> Unit,
    onToggle: (Reminder, Boolean) -> Unit,
    onDelete: (Reminder) -> Unit,
    onDeleteAllDone: () -> Unit
) {
    var filter by remember { mutableStateOf(0) } // 0全部 1今天 2未来 3已完成
    val filters = listOf("全部", "今天", "未来", "已完成")
    var confirmDelete by remember { mutableStateOf<Reminder?>(null) }

    val shown = remember(items, filter) {
        when (filter) {
            1 -> upcomingToday(items)
            2 -> upcomingFuture(items)
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
            if (filter == 3 && items.any { it.done }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFE4E2),
                    modifier = Modifier.clickable { confirmDelete = Reminder(id = 0, title = "", triggerAt = 0) }
                ) {
                    Text(
                        "一键删除",
                        fontSize = 14.sp,
                        color = Color(0xFFE5362F),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
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
                    // 每项操作行：编辑 / 删除（老人也能直接删，无需进编辑器找删除按钮）
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "✏️ 编辑",
                            fontSize = 14.sp,
                            color = BluePrimary,
                            modifier = Modifier.clickable { onEdit(r) }.padding(vertical = 6.dp, horizontal = 8.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "🗑 删除",
                            fontSize = 14.sp,
                            color = Color(0xFFE5362F),
                            modifier = Modifier.clickable { confirmDelete = r }.padding(vertical = 6.dp, horizontal = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (confirmDelete!!.id == 0L) "一键删除" else "删除提醒") },
            text = {
                Text(
                    if (confirmDelete!!.id == 0L) "确定删除所有已完成的提醒吗？"
                    else "确定删除“${confirmDelete!!.title}”吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (confirmDelete!!.id == 0L) onDeleteAllDone() else onDelete(confirmDelete!!)
                    confirmDelete = null
                }) { Text("删除", color = Color(0xFFE5362F)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}

/** 常用事项：按大类分组，大类→次小类（无时间），点击小类进入编辑页预填（时间由用户填写）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CommonScreen(
    onAdd: (title: String, category: String) -> Unit,
    onEdit: (Reminder) -> Unit,
    onDeleteCustomCategory: (String) -> Unit
) {
    val customItems by com.elder.reminder.data.CommonCategoryPrefs.items.collectAsState()
    var showAddCategory by remember { mutableStateOf(false) }
    var addCategoryTo by remember { mutableStateOf<String?>(null) }
    // 内建大类（服药 / 测血压血糖）固定；喝水/运动按用户要求取消，改由用户自定义。
    val groups = listOf(
        CategoryGroup("服药", Reminder.Category.MEDICATION, "💊", listOf("降压药", "降糖药", "睡前药", "其他")),
        CategoryGroup("测血压血糖", Reminder.Category.HEALTH, "🩺", listOf("量血压", "测血糖"))
    )
    // 用户自定义大类及其次小类
    val customGroups = customItems.map { c ->
        CategoryGroup(c.label, Reminder.Category.OTHER, "📌", c.subItems.ifEmpty { listOf("其他") })
    }
    val allGroups = groups + customGroups

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("常用事项", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BlueDeep, modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFEAF1FF),
                modifier = Modifier.clickable { showAddCategory = true }
            ) {
                Text(
                    "＋ 自定义分类",
                    fontSize = 14.sp,
                    color = BluePrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("点一个小类 → 进入编辑页填写时间和内容后保存", fontSize = 15.sp, color = TextSecondary)
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            allGroups.forEach { g ->
                val isCustom = g.category == Reminder.Category.OTHER && groups.none { it.label == g.label }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFEAF1FF)),
                                contentAlignment = Alignment.Center
                            ) { Text(g.emoji, fontSize = 19.sp) }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                g.label,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCustom) {
                                // 自定义大类可加次小类 / 删除
                                Text(
                                    "＋ 次小类",
                                    fontSize = 13.sp,
                                    color = BluePrimary,
                                    modifier = Modifier
                                        .clickable { addCategoryTo = g.label }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                                Text(
                                    "🗑 删除",
                                    fontSize = 13.sp,
                                    color = Color(0xFFE5362F),
                                    modifier = Modifier
                                        .clickable { onDeleteCustomCategory(g.label) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        // 次小类紧凑排列（FlowRow 自动换行，不一行一个）
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            g.templates.forEach { sub ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFF0F3F8),
                                    modifier = Modifier.clickable { onAdd(sub, g.category) }
                                ) {
                                    Text(
                                        sub,
                                        fontSize = 17.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showAddCategory) {
        InputTextDialog(
            title = "添加自定义分类",
            label = "分类名称",
            placeholder = "例如：复查",
            onConfirm = { name ->
                com.elder.reminder.data.CommonCategoryPrefs.addCategory(name)
                showAddCategory = false
            },
            onDismiss = { showAddCategory = false }
        )
    }
    if (addCategoryTo != null) {
        InputTextDialog(
            title = "添加到「$addCategoryTo」",
            label = "次小类名称",
            placeholder = "例如：复查抽血",
            onConfirm = { name ->
                com.elder.reminder.data.CommonCategoryPrefs.addSubItem(addCategoryTo!!, name)
                addCategoryTo = null
            },
            onDismiss = { addCategoryTo = null }
        )
    }
}

@Composable
private fun InputTextDialog(
    title: String,
    label: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val t = text.trim()
                if (t.isNotEmpty()) onConfirm(t)
            }) { Text("确定", color = BluePrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private data class CategoryGroup(
    val label: String,
    val category: String,
    val emoji: String,
    val templates: List<String>
)
