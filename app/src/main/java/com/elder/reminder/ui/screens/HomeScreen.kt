package com.elder.reminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.elder.reminder.ui.theme.elderSp
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
            .padding(horizontal = 20.dp)
    ) {
        // ===== 顶部固定区：标题 + 日期/录音并排，不随页面滚动，字体不随缩放调整 =====
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("嘟嘟点卯", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BlueDeep)
                Text("让生活更有规律", fontSize = 16.sp, color = TextSecondary)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = TextSecondary)
            }
        }

        Spacer(Modifier.height(14.dp))

        // 日期 + 录音并排（固定，不滚动；字体不随缩放调整）
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
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 日期（左侧，约占 60%）
                Row(
                    Modifier.weight(0.6f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) { Text("📅", fontSize = 22.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "$dateTitle $weekday",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueDeep
                        )
                        if (lunar.isNotBlank()) {
                            Text(lunar, fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }
                // 录音按钮（右侧，约 40%），按压说话
                Box(
                    Modifier
                        .weight(0.4f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (recording) Color(0xFF3B82F6) else Color(0xFF155EEF))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onVoice()
                                    tryAwaitRelease()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        Modifier.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (recording) "正在听…" else "按住说话",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ===== 滚动区：仅"今日提醒"内容滚动 =====
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("今天的提醒", Modifier.weight(1f))
                Text(
                    "全部提醒 >",
                    fontSize = elderSp(15f),
                    color = BluePrimary,
                    modifier = Modifier.clickable { onOpenList() }
                )
            }

            Spacer(Modifier.height(10.dp))

            if (todayReminders.isEmpty()) {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("今天还没有提醒", fontSize = elderSp(18f), color = TextPrimary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("点上方蓝色按钮，说出您要做的事", fontSize = elderSp(15f), color = TextSecondary)
                    }
                }
            } else {
                // 今日提醒计数
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今日共 ${todayReminders.size} 条",
                        fontSize = elderSp(15f),
                        color = TextSecondary
                    )
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    todayReminders.take(6).forEachIndexed { idx, r ->
                        // 每个提醒左上角时间前编号：粗体[x]
                        ReminderCard(
                            reminder = r,
                            onClick = { onEdit(r) },
                            onDelete = { onEdit(r) },
                            trailing = {
                                Text(
                                    "[${idx + 1}]",
                                    fontSize = elderSp(20f),
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimary
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

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
                fontSize = elderSp(14f),
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
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

/** 全部提醒列表（v1.4.0 重构）：
 *  - tabs：全部 / 今天 / 延误 / 未来 / 已完成（今天、延误带计数）
 *  - 每卡：时间左上、图标正上、分类+次小类、删除右下、"已服用"深绿按钮
 *  - 删除"编辑"操作行（编辑不再是对该提醒的调整）
 *  - "延误"：闹钟触发但无人点击已服用/系统自动停止 2 分钟的提醒
 */
@Composable
fun ReminderListScreen(
    items: List<Reminder>,
    onAdd: () -> Unit,
    onEdit: (Reminder) -> Unit,
    onToggle: (Reminder, Boolean) -> Unit,
    onDelete: (Reminder) -> Unit,
    onDeleteAllDone: () -> Unit,
    onMarkTaken: (Reminder) -> Unit
) {
    var filter by remember { mutableStateOf(0) } // 0全部 1今天 2延误 3未来 4已完成
    var confirmDelete by remember { mutableStateOf<Reminder?>(null) }

    val todayList = remember(items) { upcomingToday(items) }
    val delayedList = remember(items) { items.filter { it.delayed && !it.done } }
    val futureList = remember(items) { upcomingFuture(items) }
    val doneList = remember(items) { items.filter { it.done } }

    val shown = remember(items, filter) {
        when (filter) {
            1 -> todayList
            2 -> delayedList
            3 -> futureList
            4 -> doneList
            else -> items
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "提醒列表",
                fontSize = elderSp(28f),
                fontWeight = FontWeight.Bold,
                color = BlueDeep,
                modifier = Modifier.weight(1f)
            )
            if (filter == 4 && doneList.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFE4E2),
                    modifier = Modifier.clickable { confirmDelete = Reminder(id = 0, title = "", triggerAt = 0) }
                ) {
                    Text(
                        "一键删除",
                        fontSize = elderSp(14f),
                        color = Color(0xFFE5362F),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onAdd) {
                Text("＋", fontSize = elderSp(30f), color = BluePrimary)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val tabs = listOf(
                "全部" to null,
                "今天" to todayList.size,
                "延误" to delayedList.size,
                "未来" to null,
                "已完成" to null
            )
            tabs.forEachIndexed { index, (name, count) ->
                val selected = filter == index
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) BluePrimary else Color.White,
                    modifier = Modifier.clickable { filter = index }
                ) {
                    Text(
                        if (count != null) "$name-$count" else name,
                        fontSize = elderSp(16f),
                        color = if (selected) Color.White else TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (shown.isEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("没有符合条件的提醒", fontSize = elderSp(18f), color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("点右上角 ＋ 新建一条提醒", fontSize = elderSp(15f), color = TextSecondary)
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
                        onDelete = { confirmDelete = r },
                        onTaken = { onMarkTaken(r) },
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
    onAdd: (title: String, category: String, subCategory: String, categoryLabel: String) -> Unit,
    onEdit: (Reminder) -> Unit,
    onDeleteCustomCategory: (String) -> Unit
) {
    val customItems by com.elder.reminder.data.CommonCategoryPrefs.items.collectAsState()
    var showAddCategory by remember { mutableStateOf(false) }
    var addCategoryTo by remember { mutableStateOf<String?>(null) }
    // 删除模式：点"删除"后进入，分类/次小类右上角出现红色"-"，点"-"删除对应项，点"确定"退出。
    var deleteMode by remember { mutableStateOf(false) }
    // 图标更换：点某大类图标弹出选择器（iconPickerLabel 存大类 label；null 表示关闭）
    var iconPickerLabel by remember { mutableStateOf<String?>(null) }
    var iconPickerCategory by remember { mutableStateOf<String>(Reminder.Category.OTHER) }

    // 自定义上传图标：选择图片后持久化其 content URI
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && iconPickerLabel != null) {
            com.elder.reminder.data.CategoryIconPrefs.setIcon(
                iconPickerLabel!!,
                com.elder.reminder.data.CategoryIconPrefs.CategoryIcon.Image(uri.toString())
            )
            iconPickerLabel = null
        }
    }
    // 合并内建固定 + 用户自定义大类（固定类不可删除、不可加次小类）
    val groups = remember(customItems) {
        listOf(
            CategoryGroup("服药", Reminder.Category.MEDICATION, "💊",
                com.elder.reminder.data.CommonCategoryPrefs.builtinGroups[0].second, true),
            CategoryGroup("测血压血糖", Reminder.Category.HEALTH, "🩺",
                com.elder.reminder.data.CommonCategoryPrefs.builtinGroups[1].second, true)
        ) + customItems.map { c ->
            CategoryGroup(c.label, Reminder.Category.OTHER, "📌",
                c.subItems.ifEmpty { listOf("其他") }, false)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("常用事项", fontSize = elderSp(28f), fontWeight = FontWeight.Bold, color = BlueDeep, modifier = Modifier.weight(1f))
            if (!deleteMode) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFEAF1FF),
                    modifier = Modifier.clickable { showAddCategory = true }
                ) {
                    Text(
                        "＋ 自定义分类",
                        fontSize = elderSp(14f),
                        color = BluePrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFE4E2),
                    modifier = Modifier.clickable { deleteMode = true }
                ) {
                    Text(
                        "🗑 删除",
                        fontSize = elderSp(14f),
                        color = Color(0xFFE5362F),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            } else {
                // 删除模式：可连续删除多个，点"确定"退出
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE5362F),
                    modifier = Modifier.clickable { deleteMode = false }
                ) {
                    Text(
                        "✔ 确定",
                        fontSize = elderSp(14f),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (deleteMode) "点红色“-”删除分类或次小类，可连续删除，点“确定”完成"
            else "点一个小类 → 进入编辑页填写时间和内容后保存",
            fontSize = elderSp(15f), color = TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            groups.forEach { g ->
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
                            // 大类图标：点击可更换（预置列表 + 自定义上传）
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFEAF1FF))
                                    .clickable {
                                        iconPickerLabel = g.label
                                        iconPickerCategory = g.category
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                com.elder.reminder.ui.components.CategoryIconView(
                                    com.elder.reminder.data.CategoryIconPrefs.iconOf(g.label, g.category),
                                    emojiSize = elderSp(19f),
                                    imageSize = 34.dp,
                                    modifier = Modifier
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                g.label,
                                fontSize = elderSp(20f),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (!deleteMode) {
                                if (!g.isBuiltin) {
                                    // 自定义大类可加次小类 / 删除
                                    Text(
                                        "＋ 次小类",
                                        fontSize = elderSp(13f),
                                        color = BluePrimary,
                                        modifier = Modifier
                                            .clickable { addCategoryTo = g.label }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                    Text(
                                        "🗑 删除",
                                        fontSize = elderSp(13f),
                                        color = Color(0xFFE5362F),
                                        modifier = Modifier
                                            .clickable { com.elder.reminder.data.CommonCategoryPrefs.removeCategory(g.label) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                } else {
                                    Text("固定", fontSize = elderSp(13f), color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                                }
                            } else {
                                // 删除模式：大类右侧红色"-"（固定类不可删，仅自定义类可删）
                                if (!g.isBuiltin) {
                                    DeleteMinusButton {
                                        com.elder.reminder.data.CommonCategoryPrefs.removeCategory(g.label)
                                    }
                                }
                            }
                        }
                        // 次小类紧凑排列（FlowRow 自动换行，不一行一个）
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            g.templates.forEach { sub ->
                                // 次小类项：可选则点击进入编辑；删除模式显示红色"-"
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFFF0F3F8),
                                        modifier = if (deleteMode) Modifier else Modifier.clickable {
                                            onAdd(sub, g.category, sub, if (g.isBuiltin) "" else g.label)
                                        }
                                    ) {
                                        Text(
                                            sub,
                                            fontSize = elderSp(17f),
                                            color = TextPrimary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                    if (deleteMode && !g.isBuiltin) {
                                        // 次小类右上角红色"-"（固定类次小类不可删）
                                        DeleteMinusButton(
                                            modifier = Modifier.align(Alignment.TopEnd),
                                            small = true
                                        ) {
                                            com.elder.reminder.data.CommonCategoryPrefs.removeSubItem(g.label, sub)
                                        }
                                    }
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

    // 图标更换弹窗：预置 emoji 列表 + 自定义上传
    if (iconPickerLabel != null) {
        val targetLabel = iconPickerLabel!!
        val current = com.elder.reminder.data.CategoryIconPrefs.iconOf(targetLabel, iconPickerCategory)
        AlertDialog(
            onDismissRequest = { iconPickerLabel = null },
            title = { Text("更换「$targetLabel」图标") },
            text = {
                Column {
                    // 当前图标
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.elder.reminder.ui.components.CategoryIconView(
                            current, emojiSize = elderSp(28f), imageSize = 44.dp, modifier = Modifier
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("当前图标", fontSize = elderSp(15f), color = TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("选择内置图标", fontSize = elderSp(14f), color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    // 预置 emoji 图标（网格）
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.elder.reminder.data.CategoryIconPrefs.presetEmojis.forEach { e ->
                            val selected = (current as? com.elder.reminder.data.CategoryIconPrefs.CategoryIcon.Emoji)?.emoji == e
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) Color(0xFFEAF1FF) else Color(0xFFF7F8FA),
                                modifier = Modifier.clickable {
                                    com.elder.reminder.data.CategoryIconPrefs.setIcon(
                                        targetLabel,
                                        com.elder.reminder.data.CategoryIconPrefs.CategoryIcon.Emoji(e)
                                    )
                                    iconPickerLabel = null
                                }
                            ) {
                                Text(e, fontSize = elderSp(24f), modifier = Modifier.padding(6.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // 自定义上传
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEAF1FF),
                        modifier = Modifier.fillMaxWidth().clickable {
                            imagePicker.launch("image/*")
                        }
                    ) {
                        Text(
                            "⬆ 上传自定义图标",
                            fontSize = elderSp(16f),
                            color = BluePrimary,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        com.elder.reminder.data.CategoryIconPrefs.CUSTOM_ICON_HINT,
                        fontSize = elderSp(12f),
                        color = TextSecondary
                    )
                }
            },
            confirmButton = { TextButton(onClick = { iconPickerLabel = null }) { Text("关闭") } }
        )
    }
}

/** 删除模式：红色圆 + 白色粗体"-"。默认 24dp，次小类用 small 18dp。 */
@Composable
private fun DeleteMinusButton(
    modifier: Modifier = Modifier,
    small: Boolean = false,
    onClick: () -> Unit
) {
    val d = if (small) 20.dp else 26.dp
    Box(
        modifier
            .size(d)
            .clip(CircleShape)
            .background(Color(0xFFE5362F))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "－",
            fontSize = if (small) elderSp(13f) else elderSp(17f),
            fontWeight = FontWeight.Bold,
            color = Color.White
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
    val templates: List<String>,
    val isBuiltin: Boolean
)
