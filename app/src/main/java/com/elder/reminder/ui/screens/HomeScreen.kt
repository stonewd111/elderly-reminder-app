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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.reminder.domain.LunarDate
import com.elder.reminder.domain.TimeFormats
import com.elder.reminder.domain.weekdayCn
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.isDoneNow
import com.elder.reminder.ui.components.BigMicButton
import com.elder.reminder.ui.components.ReminderCard
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
    onDelete: (Reminder) -> Unit,
    onOpenList: () -> Unit,
    onOpenDelayed: () -> Unit,
    onSettings: () -> Unit
) {
    val todayReminders = remember(items) { upcomingToday(items) }
    var confirmDelete by remember { mutableStateOf<Reminder?>(null) }
    val missedCount = remember(items) { items.count { it.delayed && !it.isDoneNow() } }
    val dateTitle = remember { SimpleDateFormat("M月d日", Locale.CHINA).format(Date()) }
    val lunar = remember { LunarDate.text() }
    val weekday = remember { weekdayCn(System.currentTimeMillis()) }
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

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
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("嘟嘟点卯", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BlueDeep)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "v${versionName}",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
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
                    .height(IntrinsicSize.Min) // 录音按钮与左侧日期区等高（用户要求）
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFEAF1FF), Color(0xFFF7FAFF)))
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 日期区（左侧，约 60%）：两行，上"日期 星期"占满一行，下"图标 | 农历"
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.weight(0.62f)
                ) {
                    Column {
                        // 第一行：日期 + 星期，占满一行，字体固定
                        Text(
                            "$dateTitle $weekday",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueDeep,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1
                        )
                        // 分隔线
                        Box(
                            Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5EAF3))
                        )
                        // 第二行：图标 | 农历
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("📅", fontSize = 18.sp) }
                            Box(
                                Modifier.width(1.dp).height(24.dp).background(Color(0xFFE5EAF3))
                            )
                            Text(
                                if (lunar.isNotBlank()) "农历$lunar" else "",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.weight(1.6f).padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                // 录音按钮（右侧，约 38%），按压说话；与左侧日期区同高
                Box(
                    Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
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
                        Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (recording) "正在听…" else "按住说话",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip
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
            // 漏报提示：有延误未处理的提醒时置顶提醒，点击直达"延误"页
            if (missedCount > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFF1E8),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenDelayed() }
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "有 $missedCount 条提醒可能错过，请查看延误",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD9480F),
                            modifier = Modifier.weight(1f)
                        )
                        Text("查看 >", fontSize = 15.sp, color = Color(0xFFD9480F), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 第一行：今日提醒 + 数量；右侧"今日"进全部提醒的"今天"界面，字体固定不随缩放
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "今日提醒",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    // 今日提醒数量
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BluePrimary
                    ) {
                        Text(
                            "${todayReminders.size}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    "今日 >",
                    fontSize = 15.sp,
                    color = BluePrimary,
                    fontWeight = FontWeight.Bold,
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    todayReminders.take(6).forEachIndexed { idx, r ->
                        // 每个提醒左上角时间前编号：粗体[x]
                        ReminderCard(
                            reminder = r,
                            onClick = { onEdit(r) },
                            onDelete = { confirmDelete = r },
                            index = idx + 1
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

            Spacer(Modifier.height(24.dp))
        }
    }

    // 首页删除确认弹窗：与"全部提醒"页一致的删除交互，点删除先确认再删。
    if (confirmDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除提醒") },
            text = { Text("确定删除“${confirmDelete!!.title}”吗？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDelete(confirmDelete!!)
                    confirmDelete = null
                }) { Text("删除", color = Color(0xFFE5362F)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            }
        )
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
    val todayStart = dayStart(now)
    val base = Calendar.getInstance().apply { timeInMillis = r.triggerAt }
    val todayAtBase = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, base.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return when (r.repeat) {
        // 单次：只有原定时间就在今天、且已过，才算"今天已到点"（明天的计划不会算进今天）
        Reminder.Repeat.None -> dayStart(r.triggerAt) == todayStart && r.triggerAt <= now
        // 重复：今天该时点已过，且提醒在此前就已存在（今天新建、时点已过的提醒归明天）
        else -> todayAtBase <= now && r.createdAt <= todayAtBase
    }
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

/** 按时间先后排序：单次与重复统一用"下一次实际触发时间"，已完成的按完成时间。 */
private fun sortedByTime(list: List<Reminder>): List<Reminder> {
    val now = System.currentTimeMillis()
    return list.sortedBy { nextFireAt(it, now) ?: it.triggerAt }
}

/** 今日提醒：未完成、已启用，且归属日落在今天（含今天已触发过的提醒）。 */
private fun upcomingToday(items: List<Reminder>): List<Reminder> {
    val todayStart = dayStart(System.currentTimeMillis())
    val tomorrowStart = todayStart + 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    return items.filter { r ->
        if (!r.enabled || r.isDoneNow() || r.delayed) return@filter false
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
        if (!r.enabled || r.isDoneNow() || r.delayed) return@filter false
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
    initialTab: Int = 0 // 0全部 1今天 2延误 3未来 4已完成
) {
    var filter by remember { mutableStateOf(initialTab) } // 0全部 1今天 2延误 3未来 4已完成
    var confirmDelete by remember { mutableStateOf<Reminder?>(null) }

    val todayList = remember(items) { upcomingToday(items) }
    val delayedList = remember(items) { sortedByTime(items.filter { it.delayed && !it.isDoneNow() }) }
    val futureList = remember(items) { upcomingFuture(items) }
    val doneList = remember(items) {
        items.filter {
            it.isDoneNow() ||
                (it.repeat != Reminder.Repeat.None && !it.enabled) // 暂停中的重复提醒也留在此页
        }.sortedBy { if (it.doneAt > 0L) it.doneAt else it.triggerAt }
    }

    val shown = remember(items, filter) {
        when (filter) {
            1 -> todayList
            2 -> delayedList
            3 -> futureList
            4 -> doneList
            else -> sortedByTime(items)
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
            if (filter == 4 && doneList.any { it.repeat == Reminder.Repeat.None }) {
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
        // 五个标签等宽固定排列：位置与字号不随字体大小变化，全部完整显示
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                    modifier = Modifier.weight(1f).height(36.dp).clickable { filter = index }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (count != null) "$name-$count" else name,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
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
                shown.forEachIndexed { idx, r ->
                    ReminderCard(
                        r,
                        index = idx + 1,
                        // 日期仅在"全部 / 未来"tab 显示（用户要求：未来没有日期会混淆；今天/延误/已完成不需要）
                        showDate = filter == 0 || filter == 3,
                        onClick = { onEdit(r) },
                        onDelete = { confirmDelete = r },
                        trailing = {
                            androidx.compose.material3.Switch(
                                // 绿=已启用/循环中（今天已完成也保持绿色）；灰=已关闭，不再响
                                checked = r.enabled,
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
    val extraSubs by com.elder.reminder.data.CommonCategoryPrefs.extraSubs.collectAsState()
    var showAddCategory by remember { mutableStateOf(false) }
    // 统一的"+次小类"弹窗：选择主分类 + 添加次分类
    var showAddSubItem by remember { mutableStateOf(false) }
    var addSubItemCategory by remember { mutableStateOf<String?>(null) }
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
    // 大类列表（内建固定 + 用户自定义），无次小类时不再自动补"其他"
    val groups = remember(customItems, extraSubs) {
        com.elder.reminder.data.CommonCategoryPrefs.allGroups()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        // 顶部"常用事项"一行：字体固定（不随缩放），与删除按钮同排，不跨行/竖行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "常用事项",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BlueDeep,
                modifier = Modifier.weight(1f)
            )
            if (!deleteMode) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFE4E2),
                    modifier = Modifier.clickable { deleteMode = true }
                ) {
                    Text(
                        "🗑 删除",
                        fontSize = 15.sp,
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // 第二行："自定义分类"、"＋次小类" 两个浅绿色背景方框，并排一行，字体固定不随缩放
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFDCF3E3),
                modifier = Modifier.weight(1f).clickable { showAddCategory = true }
            ) {
                Text(
                    "自定义分类",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B7F4B),
                    modifier = Modifier.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFDCF3E3),
                modifier = Modifier.weight(1f).clickable {
                    addSubItemCategory = groups.firstOrNull()?.label
                    showAddSubItem = true
                }
            ) {
                Text(
                    "＋次小类",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B7F4B),
                    modifier = Modifier.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        if (deleteMode) {
            Spacer(Modifier.height(6.dp))
            Text(
                "点红色“-”删除分类或次小类，可连续删除，点“确定”完成",
                fontSize = elderSp(15f), color = TextSecondary
            )
        }
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
                            // 大类名称：保持原有自适应字号（用户确认目前合适，不再改动）
                            Text(
                                g.label,
                                fontSize = elderSp(20f),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (deleteMode) {
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
                            g.subItems.forEach { sub ->
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
                                            // 灰色框左右 padding 从 16dp 收窄到 8dp，为文字腾出更多横向空间，
                                            // 配合字号，字体（大）下也能完整显示 9 个汉字而不溢出。
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                                        )
                                    }
                                    if (deleteMode && sub in g.removableSubs) {
                                        // 次小类右上角红色"-"（仅可删用户追加的次小类）
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
    // "+次小类"弹窗：第一行"选择主分类"，第二行"添加次分类"
    if (showAddSubItem) {
        var subName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSubItem = false },
            title = { Text("添加次小类") },
            text = {
                Column {
                    Text("选择主分类", fontSize = elderSp(15f), color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.forEach { g ->
                            val selected = addSubItemCategory == g.label
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) Color(0xFFDCF3E3) else Color(0xFFF7F8FA),
                                modifier = Modifier.clickable { addSubItemCategory = g.label }
                            ) {
                                Text(
                                    g.label,
                                    fontSize = elderSp(15f),
                                    color = if (selected) Color(0xFF1B7F4B) else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("添加次分类", fontSize = elderSp(15f), color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = subName,
                        onValueChange = { subName = it },
                        label = { Text("次小类名称") },
                        placeholder = { Text("例如：复查抽血") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cat = addSubItemCategory
                    val name = subName.trim()
                    if (cat != null && name.isNotEmpty()) {
                        com.elder.reminder.data.CommonCategoryPrefs.addSubItem(cat, name)
                    }
                    showAddSubItem = false
                }) { Text("确定", color = BluePrimary) }
            },
            dismissButton = { TextButton(onClick = { showAddSubItem = false }) { Text("取消") } }
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
