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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.elder.reminder.data.ReminderRepository
import com.elder.reminder.model.Reminder
import com.elder.reminder.domain.LunarDate
import com.elder.reminder.ui.screens.CommonScreen
import com.elder.reminder.ui.screens.ConfirmScreen
import com.elder.reminder.ui.screens.EditorScreen
import com.elder.reminder.ui.screens.HomeScreen
import com.elder.reminder.ui.screens.ReminderListScreen
import com.elder.reminder.ui.screens.VoiceScreen
import com.elder.reminder.ui.theme.BluePrimary
import java.util.Calendar

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
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val scaleUp: (Reminder, Boolean) -> Unit = { r, on ->
        val updated = r.copy(enabled = on, done = false)
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
                    navController.navigate(route) {
                        popUpTo(ROUTE_HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
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
                    onOpenList = { navController.navigate(ROUTE_LIST) },
                    onSettings = { showSettings = true }
                )
            }
            composable(ROUTE_LIST) {
                ReminderListScreen(
                    items = items,
                    onAdd = { navController.navigate("editor?id=-1") },
                    onEdit = { navController.navigate("editor?id=${it.id}") },
                    onToggle = scaleUp
                )
            }
            composable(ROUTE_COMMON) {
                CommonScreen { title, hour, minute, repeat, mode ->
                    val c = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (c.timeInMillis <= System.currentTimeMillis()) {
                        c.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    val reminder = Reminder(
                        id = ReminderRepository.nextId(),
                        title = title,
                        triggerAt = c.timeInMillis,
                        mode = mode,
                        repeat = repeat,
                        enabled = true,
                        done = false
                    )
                    ReminderRepository.upsert(reminder)
                    ReminderScheduler.schedule(context, reminder)
                    Toast.makeText(context, "已添加：$title", Toast.LENGTH_SHORT).show()
                    navController.navigate(ROUTE_LIST)
                }
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
                    onSaved = { navController.popBackStack(ROUTE_HOME, false) }
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
                EditorScreen(
                    initial = if (id > 0) ReminderRepository.find(id) else null,
                    spokenText = text,
                    onBack = { navController.popBackStack() },
                    onVoiceEdit = { vid -> startVoice(vid) },
                    onSaved = { navController.popBackStack(ROUTE_HOME, false) },
                    onDeleted = { navController.popBackStack(ROUTE_HOME, false) }
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
                SettingRow("🔋 忽略电池优化") {
                    open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }
                SettingRow("⏰ 精确闹钟") {
                    open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
                SettingRow("🎤 麦克风权限") {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
                SettingRow("📂 后台运行（OPPO 需允许自启动+后台无限制）") {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
                SettingRow("🗣️ 语音引擎（语音提醒无声时，检查是否安装了中文语音）") {
                    open(Intent("android.speech.tts.engine.INSTALL_TTS_DATA").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
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
