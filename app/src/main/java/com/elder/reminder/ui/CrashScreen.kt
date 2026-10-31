package com.elder.reminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 崩溃诊断页：显示最近一次崩溃堆栈，用于“进不去界面”排查。
 * 点“清除并重试”后回到正常应用。
 */
@Composable
fun CrashScreen(stack: String, onClear: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF4F4))
            .padding(20.dp)
    ) {
        Text("上次启动出错了", fontSize = 24.sp, color = Color(0xFFB42318), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "请把下面的错误信息截图发给我，即可定位问题。",
            fontSize = 15.sp, color = Color(0xFF667085)
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                stack,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF101828)
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onClear, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("清除并重试", fontSize = 18.sp)
        }
    }
}
