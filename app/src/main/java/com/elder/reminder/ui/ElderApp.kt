package com.elder.reminder.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.elder.reminder.alarm.ReminderScheduler
import com.elder.reminder.asr.VoiceSession
import com.elder.reminder.data.FontPrefs
import com.elder.reminder.data.ReminderRepository
import com.elder.reminder.data.SoundPrefs
import com.elder.reminder.model.Reminder
import com.elder.reminder.domain.LunarDate
import com.elder.reminder.ui.screens.CommonScreen
import com.elder.reminder.ui.screens.ConfirmScreen
import com.elder.reminder.ui.screens.EditorScreen
import com.elder.reminder.ui.screens.HomeScreen
import com.elder.reminder.ui.screens.ReminderListScreen
import com.elder.reminder.ui.screens.VoiceScreen
import com.elder.reminder.ui.theme.BluePrimary

private const val ROUTE_HOME = "home"
private const val ROUTE_LIST = "list"
private const val ROUTE_COMMON = "common"

@Composable
fun ElderApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val items by ReminderRepository.items.collectAsState()
    val voice by VoiceSession.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var pendingVoiceId by remember { mutableStateOf<Long?>(null) }
    // 常用模板点击后预填编辑页（用户要求：点类型→编辑页确认后才创建提醒）
    var prefillTemplate by remember { mutableStateOf<Reminder?>(null) }
    // 全部提醒页初始 tab（首页"今日"→今天=1；默认全部=0）
    var listInitialTab by remember { mutableStateOf(0) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            navController.navigate("voice?id=${pendingVoiceId ?: -1L}")
        } else {
            Toast.makeText(context, "需要麦克风权限才能语音输入", Toast.LENGTH_LONG).show()
        }
        pendingVoiceId = null
    }

    val startVoice: (Long?) -> Unit = { id ->
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            navController.navigate("voice?id=${id ?: -1L}")
        } else {
            pendingVoiceId = id
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 无论授予与否，在 OPPO/ColorOS 上尝试触发“锁屏显示”授权判定
        if (granted) com.elder.reminder.alarm.LockscreenGuide.requestLockscreenCheck(context)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // 通知权限已授予，直接尝试触发 OPPO“锁屏显示”授权判定
            com.elder.reminder.alarm.LockscreenGuide.requestLockscreenCheck(context)
        }
    }

    val scaleUp: (Reminder, Boolean) -> Unit = { r, on ->
        // 重复提醒：自动循环，关闭开关=暂停（可再打开恢复）；单次提醒：关闭=完成。
        val updated = if (on) {
            r.copy(enabled = true, done = false, doneAt = 0L)
        } else {
            r.copy(enabled = false, done = true, doneAt = System.currentTimeMillis())
        }
        ReminderRepository.upsert(updated)
        if (on) ReminderScheduler.schedule(context, updated)
        else ReminderScheduler.cancel(context, r.id)
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = Color(0xFFF5F7FA),
        bottomBar = {
            if (currentRoute == ROUTE_HOME || currentRoute == ROUTE_LIST || currentRoute == ROUTE_COMMON) {
                ElderBottomBar(currentRoute) { route ->
                    if (route == currentRoute) return@ElderBottomBar
                    // 底部栏切换：弹回已存在的目的地；不存在则新开，避免回退栈堆积导致
                    // “进全部提醒后无法进常用页面”的状态错乱。
                    val popped = navController.popBackStack(route, false)
                    if (!popped) {
                        navController.navigate(route) {
                            popUpTo(ROUTE_HOME)
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    items = items,
                    recording = voice.active,
                    onVoice = { startVoice(null) },
                    onEdit = { navController.navigate("editor?id=${it.id}") },
                    onOpenList = {
                        listInitialTab = 1 // 首页"今日"→全部提醒"今天"tab
                        navController.navigate(ROUTE_LIST)
                    },
                    onOpenDelayed = {
                        listInitialTab = 2 // 首页漏报提示→全部提醒"延误"tab
                        navController.navigate(ROUTE_LIST)
                    },
                    onSettings = { showSettings = true }
                )
            }
            composable(ROUTE_LIST) {
                ReminderListScreen(
                    items = items,
                    initialTab = listInitialTab,
                    onAdd = { navController.navigate("editor?id=-1") },
                    onEdit = { navController.navigate("editor?id=${it.id}") },
                    onToggle = scaleUp,
                    onDelete = { r ->
                        ReminderScheduler.cancel(context, r.id)
                        ReminderRepository.remove(r.id)
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteAllDone = {
                        // 只清理单次提醒的完成记录；重复提醒只是暂停/本次完成，不能被一键删除
                        val targets = ReminderRepository.all()
                            .filter { it.done && it.repeat == Reminder.Repeat.None }
                        targets.forEach {
                            ReminderScheduler.cancel(context, it.id)
                            ReminderRepository.remove(it.id)
                        }
                        Toast.makeText(context, "已删除全部完成提醒", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            composable(ROUTE_COMMON) {
                CommonScreen(
                    // 点小类 → 预填编辑页（仅预填分类 + 次小类 + 药名），提醒内容一律留空由用户填写。
                    onAdd = { sub, category, subCategory, categoryLabel ->
                        prefillTemplate = Reminder(
                            id = -1L,
                            title = "",
                            triggerAt = System.currentTimeMillis() + 60 * 60 * 1000L, // 默认 1 小时后，用户可改
                            mode = Reminder.Mode.VOICE,
                            repeat = Reminder.Repeat.None,
                            enabled = true,
                            done = false,
                            category = category,
                            categoryLabel = categoryLabel,
                            subCategory = subCategory,
                            drug = "" // 药名由用户选填（服药类留空，用户填具体药名）
                        )
                        navController.navigate("editor?id=-1&text=")
                    },
                    onEdit = { reminder ->
                        navController.navigate("editor?id=${reminder.id}")
                    },
                    onDeleteCustomCategory = { label ->
                        com.elder.reminder.data.CommonCategoryPrefs.removeCategory(label)
                        Toast.makeText(context, "已删除自定义分类：$label", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            composable(
                "voice?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                VoiceScreen(
                    existingId = if (id > 0) id else null,
                    onBack = { VoiceSession.reset(); navController.popBackStack() },
                    onResult = { text ->
                        navController.navigate("confirm?text=${Uri.encode(text)}&id=$id")
                    }
                )
            }
            composable(
                "confirm?text={text}&id={id}",
                arguments = listOf(
                    navArgument("text") { type = NavType.StringType; defaultValue = "" },
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { entry ->
                val text = entry.arguments?.getString("text").orEmpty()
                val id = entry.arguments?.getLong("id") ?: -1L
                ConfirmScreen(
                    text = text,
                    existing = if (id > 0) ReminderRepository.find(id) else null,
                    onBack = { navController.popBackStack() },
                    onRetry = { navController.popBackStack() },
                    // 修复语音录入跳步 BUG：确认后进入编辑页设置周期，而非直接保存。
                    onSaved = {
                        // 携带语音文本跳转到编辑页，由用户确认周期后保存
                        navController.navigate("editor?id=$id&text=${Uri.encode(text)}") {
                            popUpTo("voice?id={id}") { inclusive = true }
                        }
                    }
                )
            }
            composable(
                "editor?id={id}&text={text}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("text") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                val text = entry.arguments?.getString("text")?.takeIf { it.isNotBlank() }
                // 优先加载已存在的提醒（按 id），避免被残留的 prefillTemplate 覆盖导致内容清零/时间错乱。
                // 仅当 id<=0 且存在模板预填时才用模板（常用页点模板）。
                val loadedInitial = if (id > 0) ReminderRepository.find(id) else null
                EditorScreen(
                    initial = loadedInitial ?: (if (id <= 0) prefillTemplate else null),
                    spokenText = text,
                    onBack = { prefillTemplate = null; navController.popBackStack() },
                    onVoiceEdit = { vid -> startVoice(vid) },
                    onSaved = { prefillTemplate = null; navController.popBackStack() },
                    onDeleted = { prefillTemplate = null; navController.popBackStack() }
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(context) { showSettings = false }
    }
}

@Composable
private fun ElderBottomBar(current: String?, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
            selected = current == ROUTE_HOME,
            onClick = { onSelect(ROUTE_HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("首页", fontSize = 15.sp) }
        )
        NavigationBarItem(
            selected = current == ROUTE_LIST,
            onClick = { onSelect(ROUTE_LIST) },
            icon = { Icon(Icons.Filled.FormatListBulleted, contentDescription = null) },
            label = { Text("全部提醒", fontSize = 15.sp) }
        )
        NavigationBarItem(
            selected = current == ROUTE_COMMON,
            onClick = { onSelect(ROUTE_COMMON) },
            icon = { Icon(Icons.Filled.Star, contentDescription = null) },
            label = { Text("常用", fontSize = 15.sp) }
        )
    }
}

@Composable
private fun SettingsDialog(context: Context, onDismiss: () -> Unit) {
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // Android 14+ 检测是否允许全屏通知（OPPO/ColorOS 默认拒绝，导致锁屏/后台不弹窗）
    val fullScreenAllowed = remember {
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.canUseFullScreenIntent()
        } else true
    }
    // 通知权限是否已授予
    val notifGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    // OPPO/ColorOS 厂商专属“锁屏显示”权限（无法在安装时预授权，需系统运行时判定）
    val isColorOS = remember { com.elder.reminder.alarm.LockscreenGuide.isColorOS(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒权限设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("为了锁屏、后台也能准时大声提醒，请开启以下权限：", fontSize = 15.sp, color = Color(0xFF667085))
                Spacer(Modifier.height(10.dp))

                if (!notifGranted) {
                    Text("⚠️ 通知权限未开启——锁屏/后台将无法弹窗提醒",
                        fontSize = 14.sp, color = Color(0xFFE5362F))
                    Spacer(Modifier.height(6.dp))
                }
                SettingRow("🔔 通知权限${if (notifGranted) "（已开启）" else "（未开启）"}") {
                    open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
                if (Build.VERSION.SDK_INT >= 34 && !fullScreenAllowed) {
                    Text("⚠️ 全屏提醒权限未开启——锁屏/后台无法弹出红色全屏页",
                        fontSize = 14.sp, color = Color(0xFFE5362F))
                    Spacer(Modifier.height(6.dp))
                }
                SettingRow("📱 全屏提醒${if (Build.VERSION.SDK_INT >= 34) if (fullScreenAllowed) "（已开启）" else "（未开启）" else ""}") {
                    if (Build.VERSION.SDK_INT >= 34) {
                        open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                    } else {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                }
                if (isColorOS) {
                    Text(
                        "⚠️ OPPO/ColorOS 需在“隐私→权限管理→权限→锁屏显示”允许本应用在锁屏上显示，" +
                            "否则锁屏时无法弹出红色大屏。该权限无法安装时预授权，由系统首次触发时引导。",
                        fontSize = 13.sp, color = Color(0xFFE5362F)
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingRow("🔒 锁屏显示（OPPO 专属）") {
                        com.elder.reminder.alarm.LockscreenGuide.openLockscreenSetting(context)
                    }
                }
                SettingRow("🔋 忽略电池优化") {
                    open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }
                SettingRow("⏰ 精确闹钟") {
                    open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
                SettingRow("🎤 麦克风权限") {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
                SettingRow("🚀 自启动 + 后台无限制（OPPO 必开）") {
                    com.elder.reminder.alarm.LockscreenGuide.openAutoStartSetting(context)
                }
                SettingRow("📂 应用详情（权限 / 电池 / 最近任务锁定）") {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }

                Spacer(Modifier.height(6.dp))
                SoundSettingSection(context)

                Spacer(Modifier.height(14.dp))
                AlarmThemeSection()

                Spacer(Modifier.height(14.dp))
                FontSettingSection()

                Spacer(Modifier.height(10.dp))
                Text(
                    "小提示：设置好后，点下面按钮建一条 2 分钟后的测试提醒，锁屏等待看看是否会全屏弹出并响铃。",
                    fontSize = 14.sp, color = Color(0xFF667085)
                )
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = {
                    val test = Reminder(
                        id = ReminderRepository.nextId(),
                        title = "测试提醒：到点了，请注意！",
                        triggerAt = System.currentTimeMillis() + 2 * 60 * 1000L,
                        mode = Reminder.Mode.VOICE,
                        repeat = Reminder.Repeat.None,
                        enabled = true,
                        done = false
                    )
                    ReminderRepository.upsert(test)
                    ReminderScheduler.schedule(context, test)
                    Toast.makeText(context, "已创建 2 分钟后的测试提醒", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text("＋ 创建 2 分钟后的测试提醒", color = BluePrimary) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

/**
 * 提醒音效设置区：系统闹铃 / 男声 / 女声 / 自定义MP3。
 * 音效全局统一（用户要求），自定义 MP3 通过系统文件选择器选取，持久化读权限。
 */
@Composable
private fun SoundSettingSection(context: Context) {
    val soundType by SoundPrefs.soundType.collectAsState()
    val customName by SoundPrefs.customName.collectAsState()

    val mp3Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryDisplayName(context, uri) ?: "自定义铃声"
        runCatching {
            // OpenDocument 保证可持久化读权限，服务播报时也能读取
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        SoundPrefs.setCustom(uri.toString(), name)
        SoundPrefs.setSoundType(SoundPrefs.TYPE_CUSTOM)
        Toast.makeText(context, "已设置提醒音效：$name", Toast.LENGTH_SHORT).show()
    }

    Text("🔊 提醒音效", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color(0xFF101828))
    Spacer(Modifier.height(4.dp))
    Text(
        "到点提醒时统一使用这里的音效（系统闹铃 / 男声 / 女声 / 自定义）",
        fontSize = 13.sp, color = Color(0xFF667085)
    )
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SoundOption("🔔 系统闹铃", soundType == SoundPrefs.TYPE_SYSTEM) {
            SoundPrefs.setSoundType(SoundPrefs.TYPE_SYSTEM)
        }
        SoundOption("👨 男声（到点了，请注意！）", soundType == SoundPrefs.TYPE_MALE) {
            SoundPrefs.setSoundType(SoundPrefs.TYPE_MALE)
        }
        SoundOption("👩 女声（到点了，请注意！）", soundType == SoundPrefs.TYPE_FEMALE) {
            SoundPrefs.setSoundType(SoundPrefs.TYPE_FEMALE)
        }
        SoundOption(
            "🎵 自定义${if (customName.isNotBlank()) "（$customName）" else "（未选择）"}",
            soundType == SoundPrefs.TYPE_CUSTOM
        ) {
            if (soundType != SoundPrefs.TYPE_CUSTOM || SoundPrefs.customPath.value.isBlank()) {
                mp3Launcher.launch(arrayOf("audio/mpeg", "audio/mp3", "audio/*"))
            } else {
                SoundPrefs.setSoundType(SoundPrefs.TYPE_CUSTOM)
            }
        }
    }
    if (soundType == SoundPrefs.TYPE_CUSTOM) {
        Spacer(Modifier.height(4.dp))
        Text(
            "规则：MP3 格式，时长 30 秒以内（建议 3~15 秒），内容建议提前录好“到点了，请注意！”。" +
                "到点会循环播放，直到点击停止。选好后点下面的“知道了”关闭，下次提醒自动生效。",
            fontSize = 12.sp, color = Color(0xFF667085)
        )
    }
}

@Composable
private fun SoundOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFEAF1FF) else Color(0xFFF7F8FA),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                if (selected) "◉" else "○",
                fontSize = 18.sp,
                color = if (selected) BluePrimary else Color(0xFF98A2B3)
            )
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 16.sp, color = Color(0xFF101828))
        }
    }
}

/** 大屏提醒背景颜色设置区（红色 / 蓝色，仅背景色不同）。 */
@Composable
private fun AlarmThemeSection() {
    val theme by com.elder.reminder.data.AlarmThemePrefs.theme.collectAsState()
    Text("🎨 大屏提醒颜色", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color(0xFF101828))
    Spacer(Modifier.height(4.dp))
    Text("到点全屏提醒的背景颜色（红色醒目 / 蓝色柔和）", fontSize = 13.sp, color = Color(0xFF667085))
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeOption(
            "🔴 红色",
            theme == com.elder.reminder.data.AlarmThemePrefs.THEME_RED,
            Color(0xFFE5362F),
            Modifier.weight(1f)
        ) { com.elder.reminder.data.AlarmThemePrefs.setTheme(com.elder.reminder.data.AlarmThemePrefs.THEME_RED) }
        ThemeOption(
            "🔵 蓝色",
            theme == com.elder.reminder.data.AlarmThemePrefs.THEME_BLUE,
            Color(0xFF1E6FEB),
            Modifier.weight(1f)
        ) { com.elder.reminder.data.AlarmThemePrefs.setTheme(com.elder.reminder.data.AlarmThemePrefs.THEME_BLUE) }
    }
}

@Composable
private fun ThemeOption(text: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFEAF1FF) else Color(0xFFF7F8FA),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, color) else null,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(text, fontSize = 16.sp, color = Color(0xFF101828))
        }
    }
}

/** 从 content Uri 查询显示名（文件名）。 */
private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }.getOrNull()
}

/** 字体大小设置区（老人视角适配）。 */
@Composable
private fun FontSettingSection() {
    val level by FontPrefs.level.collectAsState()
    Text("🔤 字体大小", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color(0xFF101828))
    Spacer(Modifier.height(4.dp))
    Text("为方便老人看清，可放大全局文字（含提醒大屏）", fontSize = 13.sp, color = Color(0xFF667085))
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FontOption("标准", level == FontPrefs.LEVEL_STANDARD) { FontPrefs.setLevel(FontPrefs.LEVEL_STANDARD) }
        FontOption("大（+15%）", level == FontPrefs.LEVEL_LARGE) { FontPrefs.setLevel(FontPrefs.LEVEL_LARGE) }
        FontOption("特大（+30%）", level == FontPrefs.LEVEL_XLARGE) { FontPrefs.setLevel(FontPrefs.LEVEL_XLARGE) }
    }
}

@Composable
private fun FontOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFEAF1FF) else Color(0xFFF7F8FA),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                if (selected) "◉" else "○",
                fontSize = 18.sp,
                color = if (selected) BluePrimary else Color(0xFF98A2B3)
            )
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 16.sp, color = Color(0xFF101828))
        }
    }
}

@Composable
private fun SettingRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 17.sp,
        color = Color(0xFF101828),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}
