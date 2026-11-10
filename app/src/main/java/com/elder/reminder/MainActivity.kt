package com.elder.reminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.elder.reminder.data.FontPrefs
import com.elder.reminder.ui.CrashScreen
import com.elder.reminder.ui.ElderApp
import com.elder.reminder.ui.theme.ElderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 用户打开界面时做一次漏报自检。不能放到 Application.onCreate：
        // 闹钟到点会先起进程、再派发 AlarmReceiver，提前判延误会让到点提醒不响。
        (application as? ReminderApp)?.markMissedAlarms()
        val crash = CrashLog.latest(this)
        if (crash != null) {
            // 上次启动崩溃：显示错误信息页，方便用户反馈，避免再次闪退
            setContent {
                ElderTheme {
                    CrashScreen(crash) { CrashLog.clear(this) }
                }
            }
            return
        }
        setContent {
            val level by FontPrefs.level.collectAsState()
            ElderTheme(scale = FontPrefs.scaleOf(level)) {
                ElderApp()
            }
        }
    }
}
