package com.elder.reminder.domain

import android.icu.util.ChineseCalendar
import java.util.Date

/** 农历展示（使用系统自带 ICU，无需联网）。 */
object LunarDate {
    fun text(date: Date = Date()): String = try {
        val cc = ChineseCalendar()
        cc.time = date
        val month = cc.get(ChineseCalendar.MONTH) + 1
        val day = cc.get(ChineseCalendar.DAY_OF_MONTH)
        val leap = cc.get(ChineseCalendar.IS_LEAP_MONTH) == 1
        "农历" + (if (leap) "闰" else "") + monthName(month) + "月" + dayName(day)
    } catch (_: Throwable) {
        ""
    }

    private fun monthName(m: Int): String = when (m) {
        1 -> "正"
        11 -> "冬"
        12 -> "腊"
        else -> chineseNum(m)
    }

    private fun dayName(d: Int): String = when {
        d == 10 -> "初十"
        d == 20 -> "二十"
        d == 30 -> "三十"
        d < 10 -> "初" + chineseNum(d.toString())
        d < 20 -> "十" + chineseNum((d - 10).toString())
        else -> "二十" + chineseNum((d - 20).toString())
    }

    private fun chineseNum(n: String): String = n.toIntOrNull()?.let { chineseNum(it) } ?: n

    private fun chineseNum(n: Int): String = when (n) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"
        6 -> "六"; 7 -> "七"; 8 -> "八"; 9 -> "九"; 10 -> "十"
        else -> n.toString()
    }
}
