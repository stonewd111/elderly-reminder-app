package com.elder.reminder.domain

import android.icu.util.ChineseCalendar
import java.util.Calendar
import java.util.Date

/** 农历展示（使用系统自带 ICU，无需联网）。 */
object LunarDate {

    private val STEM = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val BRANCH = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val ZODIAC = arrayOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")

    /** 公历年份 → 天干地支（如 2026 → 丙午）。 */
    fun ganzhi(year: Int): String {
        val gi = ((year - 4) % 10 + 10) % 10
        val zi = ((year - 4) % 12 + 12) % 12
        return STEM[gi] + BRANCH[zi]
    }

    /** 公历年份 → 生肖（如 2026 → 马）。 */
    fun zodiac(year: Int): String {
        val zi = ((year - 4) % 12 + 12) % 12
        return ZODIAC[zi]
    }

    /** 干支 + 生肖 + 农历月日，如「丙午马年九月十五」。 */
    fun text(date: Date = Date()): String = try {
        val cal = Calendar.getInstance().apply { time = date }
        val y = cal.get(Calendar.YEAR)
        val cc = ChineseCalendar()
        cc.time = date
        val month = cc.get(ChineseCalendar.MONTH) + 1
        val day = cc.get(ChineseCalendar.DAY_OF_MONTH)
        val leap = cc.get(ChineseCalendar.IS_LEAP_MONTH) == 1
        ganzhi(y) + zodiac(y) + "年" + (if (leap) "闰" else "") + monthName(month) + "月" + dayName(day)
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
        d == 20 -> "廿"
        d == 30 -> "三十"
        d < 10 -> "初" + chineseNum(d.toString())
        d < 20 -> "十" + chineseNum((d - 10).toString())
        d < 30 -> "廿" + chineseNum((d - 20).toString())
        else -> "三十" + chineseNum((d - 30).toString())
    }

    private fun chineseNum(n: String): String = n.toIntOrNull()?.let { chineseNum(it) } ?: n

    private fun chineseNum(n: Int): String = when (n) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"
        6 -> "六"; 7 -> "七"; 8 -> "八"; 9 -> "九"; 10 -> "十"
        else -> n.toString()
    }
}
