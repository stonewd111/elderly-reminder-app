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
