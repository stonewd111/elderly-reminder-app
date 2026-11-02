package com.elder.reminder.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.elder.reminder.data.SoundPrefs
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
import com.elder.reminder.ui.theme.elderSp
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

    // 新增判定：无初始数据，或来自"常用"页模板（id<=0 的临时对象）都算新增
    val isNew = initial == null || initial.id <= 0
    val existingId = initial?.id?.takeIf { it > 0 }

    // 用 id 作为 key，切换不同提醒时状态正确重置（避免上一提醒内容残留）
    val initKey = existingId ?: -1L
    // 语音修改/录入修复：spokenText 非空时，语音解析结果优先覆盖旧提醒内容与时间，
    // 否则用户看到的是旧内容、语音修改不生效。无语音时用旧提醒内容。
    var title by remember(initKey, spokenText) { mutableStateOf(parsed?.title ?: initial?.title ?: "") }
    var triggerAt by remember(initKey, spokenText) { mutableStateOf(parsed?.triggerAt ?: initial?.triggerAt ?: defaultAt) }
    var mode by remember(initKey) { mutableStateOf(initial?.mode ?: Reminder.Mode.VOICE) }
    var category by remember(initKey) { mutableStateOf(initial?.category ?: Reminder.Category.OTHER) }
    var categoryLabel by remember(initKey) { mutableStateOf(initial?.categoryLabel ?: "") }
    var drug by remember(initKey) { mutableStateOf(initial?.drug ?: "") }
    var subCategory by remember(initKey) { mutableStateOf(initial?.subCategory ?: "") }
    var repeat by remember(initKey) { mutableStateOf<Reminder.Repeat>(initial?.repeat ?: Reminder.Repeat.None) }
    // 本条提醒独立铃声（空=跟随全局默认）
    var soundType by remember(initKey) { mutableStateOf(initial?.soundType ?: "") }
    var soundUri by remember(initKey) { mutableStateOf(initial?.soundUri ?: "") }
    var soundName by remember(initKey) { mutableStateOf(initial?.soundName ?: "") }
    var showSoundPicker by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(1) }
    var showDelete by remember { mutableStateOf(false) }
    // 分类选择弹窗：1 选大类 → 2 选次小类（pickingCategory 存大类 label，区分自定义大类）
    var showCategoryPicker by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf<String?>(null) }

    // 自定义铃声选择：OpenDocument 保证可持久化读权限，后台播报时也能读取
    val soundFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            soundUri = uri.toString()
            soundName = soundDisplayName(context, uri)
            soundType = SoundPrefs.TYPE_CUSTOM
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    fun save() {
        // 提醒内容 与 药品名称 2 选 1 填写即可通过（用户要求）
        val t = title.trim()
        val d = drug.trim()
        if (t.isEmpty() && d.isEmpty()) {
            android.widget.Toast.makeText(context, "请填写提醒内容或药品名称", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val reminder = Reminder(
            id = existingId ?: ReminderRepository.nextId(),
            title = t,
            triggerAt = triggerAt,
            mode = mode,
            repeat = repeat,
            enabled = true,
            done = false,
            createdAt = initial?.createdAt?.takeIf { !isNew } ?: System.currentTimeMillis(),
            category = category,
            categoryLabel = categoryLabel,
            drug = d,
            subCategory = subCategory.trim(),
            soundType = soundType,
            soundUri = soundUri,
            soundName = soundName
        )
        ReminderRepository.upsert(reminder)
        ReminderScheduler.schedule(context, reminder)
        android.widget.Toast.makeText(context, "提醒已保存", android.widget.Toast.LENGTH_SHORT).show()
        onSaved()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
        ScreenHeader(
            if (step == 1) (if (isNew) "新增提醒" else "编辑提醒") else "设置周期",
            onBack = { if (step == 2) step = 1 else onBack() }
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).weight(1f)
        ) {
            if (step == 1) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // 语音录入卡片（手动输入已按用户要求删除——直接输入提醒内容即可）
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard("🎙", "语音录入", "说出提醒内容", true, Modifier.weight(1f)) { onVoiceEdit(existingId) }
                    // 手动输入已删除，右侧用"直接填写"说明卡片占位，保持版面均衡
                    ModeCard("✏️", "直接填写", "在下方输入内容", false, Modifier.weight(1f)) { }
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("提醒内容") },
                    placeholder = { Text("例如：吃降压药") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = elderSp(20f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 1
                )

                // 药名（仅服药类提醒显示；用户选填，未填留空）
                if (category == Reminder.Category.MEDICATION) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = drug,
                        onValueChange = { drug = it },
                        label = { Text("💊 药品名称（选填）") },
                        placeholder = { Text("例如：氨氯地平") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = elderSp(20f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 分类选择：点选弹窗（大类 → 次小类），结合常用页定义的分类
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().clickable { showCategoryPicker = true }
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("分类", fontSize = elderSp(18f), color = TextSecondary, modifier = Modifier.width(90.dp))
                        Text(
                            Reminder.Category.emoji(category) + " " +
                                categoryDisplayName(category, categoryLabel) +
                                (if (subCategory.isNotBlank()) " · $subCategory" else ""),
                            fontSize = elderSp(19f),
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text("＞", fontSize = elderSp(20f), color = TextSecondary)
                    }
                }

                Spacer(Modifier.height(12.dp))
                // 铃声 + 重复合并为一张卡片：第1步少占一张卡片的间距，
                // 尽量保证特大字体下"删除这条提醒"仍无需大幅滚动即可见。
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        // 提醒铃声：逐条提醒独立选择（跟随全局 / 系统闹铃 / 男声 / 女声 / 仅振动 / 自定义）
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { showSoundPicker = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🔔 铃声",
                                fontSize = elderSp(18f),
                                color = TextSecondary,
                                maxLines = 1,
                                modifier = Modifier.width(90.dp)
                            )
                            Text(
                                soundLabel(soundType, soundName),
                                fontSize = elderSp(19f),
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                        // 重复（点击进入设置周期）
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { step = 2 }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "重复",
                                fontSize = elderSp(18f),
                                color = TextSecondary,
                                maxLines = 1,
                                modifier = Modifier.width(90.dp)
                            )
                            Text(
                                repeatTextOf(repeat),
                                fontSize = elderSp(19f),
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    }
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
                } // 结束 step1 滚动 Column
            } else {
                // 设置周期页：内容区可滚动，按钮固定在底部（特大字体下"保存/上一步"也不落屏外）
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                    RepeatOptions(repeat) { repeat = it }

                    Spacer(Modifier.height(18.dp))
                    Text("提醒时间", fontSize = elderSp(18f), fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    // 日期/时间：绿色线框圈住（用户要求）
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().border(1.5.dp, Color(0xFF7BC67E), RoundedCornerShape(16.dp))
                    ) {
                        Column {
                            // 日期/时间：value 用固定 19sp 且单行（fixedValue），
                            // 特大字体设置下日期也一行完整显示、不换行遮挡时间。
                            InfoRow("日期", TimeFormats.dayLabel(triggerAt) + "（" + com.elder.reminder.domain.dateCn(triggerAt) + "）", fixedValue = true, onClick = {
                                val c = Calendar.getInstance().apply { timeInMillis = triggerAt }
                                DatePickerDialog(context, { _, y, m, d ->
                                    val nc = Calendar.getInstance().apply { timeInMillis = triggerAt }
                                    nc.set(y, m, d); triggerAt = nc.timeInMillis
                                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                            })
                            InfoRow("时间", TimeFormats.time(triggerAt), fixedValue = true, onClick = {
                                val c = Calendar.getInstance().apply { timeInMillis = triggerAt }
                                TimePickerDialog(context, { _, h, min ->
                                    val nc = Calendar.getInstance().apply { timeInMillis = triggerAt }
                                    nc.set(Calendar.HOUR_OF_DAY, h); nc.set(Calendar.MINUTE, min)
                                    nc.set(Calendar.SECOND, 0); triggerAt = nc.timeInMillis
                                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                            })
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                // 底部固定按钮（不随内容滚动，特大字体也始终可见）
                PrimaryButton("保存", onClick = { save() })
                Spacer(Modifier.height(10.dp))
                SecondaryButton("上一步", onClick = { step = 1 })
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDelete && initial != null && existingId != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除提醒") },
            text = { Text("确定删除“${initial.title}”吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    ReminderScheduler.cancel(context, existingId)
                    ReminderRepository.remove(existingId)
                    onDeleted()
                }) { Text("删除", color = Color(0xFFE5362F)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }

    // 铃声选择弹窗：逐条提醒独立设置（含"仅振动"与自定义 MP3）
    if (showSoundPicker) {
        val options = listOf(
            "" to soundLabel("", ""),
            SoundPrefs.TYPE_SYSTEM to "🔔 系统闹铃",
            SoundPrefs.TYPE_MALE to "👨 男声（到点了，请注意！）",
            SoundPrefs.TYPE_FEMALE to "👩 女声（到点了，请注意！）",
            "vibrate" to "📳 仅振动（不发声）",
            SoundPrefs.TYPE_CUSTOM to "🎵 自定义 MP3" +
                if (soundName.isNotBlank()) "（$soundName）" else "（选择文件）"
        )
        AlertDialog(
            onDismissRequest = { showSoundPicker = false },
            title = { Text("选择提醒铃声") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (type, label) ->
                        val selected = soundType == type
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Color(0xFFEAF1FF) else Color(0xFFF7F8FA),
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (type == SoundPrefs.TYPE_CUSTOM) {
                                    // 自定义：打开系统文件选择器（可重复选择以更换文件）
                                    showSoundPicker = false
                                    soundFileLauncher.launch(arrayOf("audio/mpeg", "audio/mp3", "audio/*"))
                                } else {
                                    soundType = type
                                    showSoundPicker = false
                                }
                            }
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, fontSize = elderSp(17f), color = TextPrimary, modifier = Modifier.weight(1f))
                                if (selected) Text("✓", fontSize = elderSp(18f), color = BluePrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSoundPicker = false }) { Text("取消") } }
        )
    }

    // 分类选择弹窗：先选大类，再选次小类（pickingCategory 存大类 label）
    if (showCategoryPicker) {
        val customItems by com.elder.reminder.data.CommonCategoryPrefs.items.collectAsState()
        val categoryGroups = listOf(
            CategoryChoice("服药", Reminder.Category.MEDICATION, "💊"),
            CategoryChoice("测血压血糖", Reminder.Category.HEALTH, "🩺"),
            CategoryChoice("其他", Reminder.Category.OTHER, "📌")
        ) + customItems.map { CategoryChoice(it.label, Reminder.Category.OTHER, "📌") }

        // 当前已选大类 label（用于高亮 & 反查次小类）
        // 优先用保存的 categoryLabel（自定义大类显示名），避免"始终显示自定义"bug
        val currentCategoryLabel = when {
            category == Reminder.Category.MEDICATION -> "服药"
            category == Reminder.Category.HEALTH -> "测血压血糖"
            category == Reminder.Category.OTHER && categoryLabel.isNotBlank() -> categoryLabel
            category == Reminder.Category.OTHER -> pickingCategory
            else -> Reminder.Category.label(category)
        }

        if (pickingCategory == null) {
            // 第一步：选大类
            AlertDialog(
                onDismissRequest = {
                    showCategoryPicker = false
                    pickingCategory = null
                },
                title = { Text("选择分类") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categoryGroups.forEach { c ->
                            val selected = currentCategoryLabel == c.label
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) Color(0xFFEAF1FF) else Color(0xFFF7F8FA),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    category = c.category
                                    pickingCategory = c.label
                                    // 自定义大类（other）记录显示名，内建大类清空
                                    categoryLabel = if (c.category == Reminder.Category.OTHER) c.label else ""
                                    // 切换大类时清空上次的次小类，避免残留
                                    subCategory = ""
                                }
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.emoji, fontSize = elderSp(20f))
                                    Spacer(Modifier.width(10.dp))
                                    Text(c.label, fontSize = elderSp(17f), color = TextPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showCategoryPicker = false; pickingCategory = null }) { Text("取消") } }
            )
        } else {
            // 第二步：按大类 label 选次小类（统一从常用页配置反查，保证与常用页一致）
            val currentPicking = pickingCategory!!
            val subChoices = com.elder.reminder.data.CommonCategoryPrefs.subItemsOf(currentPicking)
            AlertDialog(
                onDismissRequest = {
                    pickingCategory = null
                    showCategoryPicker = false
                },
                title = { Text("选择「$pickingCategory」的次小类") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subChoices.forEach { sub ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF7F8FA),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    // 选中次小类：同步到 subCategory；服药→药名占位（药品名仍由用户填），其他→标题
                                    subCategory = sub
                                    if (category == Reminder.Category.MEDICATION) {
                                        // 次小类如降压药/降糖药放入 subCategory，drug 留给用户填具体药名
                                        if (title.isBlank()) title = "服药"
                                    } else {
                                        if (title.isBlank()) title = sub
                                    }
                                    pickingCategory = null
                                    showCategoryPicker = false
                                }
                            ) {
                                Text(sub, fontSize = elderSp(17f), color = TextPrimary, modifier = Modifier.padding(14.dp))
                            }
                        }
                        // 允许自行输入内容
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEAF1FF),
                            modifier = Modifier.fillMaxWidth().clickable {
                                pickingCategory = null
                                showCategoryPicker = false
                            }
                        ) {
                            Text("自行输入内容", fontSize = elderSp(16f), color = BluePrimary, modifier = Modifier.padding(14.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { pickingCategory = null; showCategoryPicker = false }) { Text("取消") } }
            )
        }
    }
}

/**
 * 分类显示名：自定义大类（other）用保存的 categoryLabel（用户定义的分类名），
 * 内建大类用默认名。修复"在常用选了自定义分类后回到编辑页仍显示'自定义'"的 bug。
 */
private fun categoryDisplayName(category: String, categoryLabel: String): String =
    if (category == Reminder.Category.OTHER && categoryLabel.isNotBlank()) categoryLabel
    else Reminder.Category.label(category)

/** 分类弹窗用的大类项。 */
private data class CategoryChoice(val label: String, val category: String, val emoji: String)

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
            Text(emoji, fontSize = elderSp(30f))
            Spacer(Modifier.height(6.dp))
            // title/subtitle 固定字号 + 单行：特大字体下两张卡片（语音录入/直接填写）保持等高，
            // 不会因右侧 subtitle 换行而突然比左侧高出一块，从而挤占滚动区、把"删除这条提醒"挤出屏幕。
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (highlighted) Color.White else TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 13.sp,
                color = if (highlighted) Color(0xFFDCE7FF) else TextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip
            )
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
            fontSize = elderSp(16f),
            color = if (selected) Color.White else TextPrimary,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun RepeatOptions(current: Reminder.Repeat, onChange: (Reminder.Repeat) -> Unit) {
    // 浅绿色线框圈住整组周期选项（用户要求）
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, Color(0xFF7BC67E), RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 不重复、每天同一排（用户要求：不重复左、每天右）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RepeatRow("不重复", current is Reminder.Repeat.None, Modifier.weight(1f)) {
                    onChange(Reminder.Repeat.None)
                }
                RepeatRow("每天", current is Reminder.Repeat.Daily, Modifier.weight(1f)) {
                    onChange(Reminder.Repeat.Daily)
                }
            }
            // 每周、每月同一排（用户要求）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RepeatRow("每周", current is Reminder.Repeat.Weekly, Modifier.weight(1f)) {
                    onChange(Reminder.Repeat.Weekly(setOf(1, 2, 3, 4, 5))) // 默认周一~周五
                }
                RepeatRow("每月", current is Reminder.Repeat.Monthly, Modifier.weight(1f)) {
                    onChange(Reminder.Repeat.Monthly(15)) // 默认 15 号
                }
            }
            RepeatRow("自定义间隔", current is Reminder.Repeat.Interval) { onChange(Reminder.Repeat.Interval(7)) } // 默认 7 天

            // 选择行：点击"每周/每月/自定义间隔"时，下方选择行用红色粗线框住（做提醒）
            when (val r = current) {
                is Reminder.Repeat.Weekly -> HighlightedBox { WeekdayPicker(r.days) { onChange(Reminder.Repeat.Weekly(it)) } }
                is Reminder.Repeat.Monthly -> HighlightedBox { NumberStepper("每月几号", r.day, 1..31) { onChange(Reminder.Repeat.Monthly(it)) } }
                is Reminder.Repeat.Interval -> HighlightedBox { NumberStepper("每隔几天", r.days, 1..365) { onChange(Reminder.Repeat.Interval(it)) } }
                else -> Unit
            }
        }
    }
}

/** 红色粗线框容器：包住"每周/每月/自定义间隔"下方的选择行，做醒目标识。 */
@Composable
private fun HighlightedBox(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .border(2.dp, Color(0xFFE5362F), RoundedCornerShape(14.dp))
            .padding(6.dp)
    ) { content() }
}

@Composable
private fun RepeatRow(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(vertical = 12.dp, horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected) "◉" else "○", fontSize = elderSp(20f), color = if (selected) BluePrimary else TextSecondary)
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = elderSp(18f), color = TextPrimary, modifier = Modifier.weight(1f))
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
                    fontSize = elderSp(15f),
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
            Text(label, fontSize = elderSp(16f), color = TextSecondary, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (value > range.first) onChange(value - 1) }) { Text("－", fontSize = elderSp(22f)) }
            Text("$value", fontSize = elderSp(20f), color = TextPrimary)
            TextButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("＋", fontSize = elderSp(22f)) }
        }
    }
}

/** 全局默认铃声的中文名（用于"跟随全局"的说明）。 */
private fun globalSoundLabel(): String = when (SoundPrefs.effectiveType()) {
    SoundPrefs.TYPE_MALE -> "男声"
    SoundPrefs.TYPE_FEMALE -> "女声"
    SoundPrefs.TYPE_CUSTOM -> "自定义"
    else -> "系统闹铃"
}

/** 本条提醒铃声的显示文案。 */
private fun soundLabel(type: String, name: String): String = when (type) {
    "" -> "跟随全局（${globalSoundLabel()}）"
    SoundPrefs.TYPE_SYSTEM -> "🔔 系统闹铃"
    SoundPrefs.TYPE_MALE -> "👨 男声"
    SoundPrefs.TYPE_FEMALE -> "👩 女声"
    "vibrate" -> "📳 仅振动（不发声）"
    SoundPrefs.TYPE_CUSTOM -> "🎵 自定义" + if (name.isNotBlank()) "（$name）" else "（未选择）"
    else -> "跟随全局（${globalSoundLabel()}）"
}

/** 从 content URI 取文件名，取不到则回退末段。 */
private fun soundDisplayName(context: Context, uri: Uri): String {
    val queried = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
    return queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "自定义铃声"
}
