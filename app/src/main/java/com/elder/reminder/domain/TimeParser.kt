package com.elder.reminder.domain

import java.util.Calendar
import java.util.Locale

data class ParsedReminder(
    val title: String,
    val triggerAt: Long,
    val hasTime: Boolean,
    val hasDate: Boolean
)

/**
 * 把老人说的自然语言解析为提醒时间。
 * 支持：今天/明天/后天/大后天、周X/星期X/礼拜X/下周X、上午/下午/晚上/中午/凌晨，
 * X点/X点半/X点X分/"8:30"、中文数字与阿拉伯数字。
 */
object TimeParser {

    private val chineseDigits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )

    fun parse(rawInput: String, now: Long = System.currentTimeMillis()): ParsedReminder {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return ParsedReminder("提醒事项", now + 60 * 60 * 1000, false, false)
        val text = normalize(raw)

        var dayOffset = 0
        var hasDate = false
        var targetWeekday: Int? = null
        var nextWeek = false

        when {
            text.contains("大后天") -> { dayOffset = 3; hasDate = true }
            text.contains("后天") -> { dayOffset = 2; hasDate = true }
            text.contains("明天") || text.contains("明日") -> { dayOffset = 1; hasDate = true }
            text.contains("今天") || text.contains("今日") -> { dayOffset = 0; hasDate = true }
        }

        val weekdayRegex = Regex("(下)?(个)?(周|星期|礼拜)(一|二|三|四|五|六|日|天|1|2|3|4|5|6|7)")
        val wm = weekdayRegex.find(text)
        if (wm != null) {
            targetWeekday = when (wm.groupValues[4]) {
                "一", "1" -> 1
                "二", "2" -> 2
                "三", "3" -> 3
                "四", "4" -> 4
                "五", "5" -> 5
                "六", "6" -> 6
                else -> 7
            }
            nextWeek = wm.groupValues[1] == "下"
            hasDate = true
        }

        // 过几天
        val overDays = Regex("过\\s*([0-9一二两三四五六七八九十]{1,3})\\s*天").find(text)
        if (overDays != null) {
            val d = parseNumber(overDays.groupValues[1])
            if (d > 0) { dayOffset = d; hasDate = true }
        }

        val timeResult = parseTime(text)
        val cal = Calendar.getInstance().apply { timeInMillis = now }

        if (targetWeekday != null) {
            val currentIso = cal.get(Calendar.DAY_OF_WEEK).toIso()
            var diff = targetWeekday - currentIso
            if (diff < 0) diff += 7
            if (nextWeek) { if (diff == 0) diff = 7; else diff += 7 }
            cal.add(Calendar.DAY_OF_YEAR, diff)
        } else {
            cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        }

        val hasTime = timeResult != null
        if (timeResult != null) {
            cal.set(Calendar.HOUR_OF_DAY, timeResult.first)
            cal.set(Calendar.MINUTE, timeResult.second)
        } else {
            cal.add(Calendar.HOUR_OF_DAY, 1)
        }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // 未指定日期时，如果时间已过则顺延到明天
        if (!hasDate && cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (hasDate && cal.timeInMillis <= now && dayOffset == 0 && targetWeekday == null) {
            // 说“今天八点”但已过：顺延一天
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (targetWeekday != null && cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
        }

        return ParsedReminder(
            title = extractTitle(raw),
            triggerAt = cal.timeInMillis,
            hasTime = hasTime,
            hasDate = hasDate
        )
    }

    private fun normalize(s: String): String = s
        .replace('０', '0').replace('１', '1').replace('２', '2').replace('３', '3')
        .replace('４', '4').replace('５', '5').replace('６', '6').replace('７', '7')
        .replace('８', '8').replace('９', '9').replace('：', ':')

    /** 返回 hour to minute */
    private fun parseTime(text: String): Pair<Int, Int>? {
        val colon = Regex("(\\d{1,2})\\s*:\\s*(\\d{1,2})").find(text)
        if (colon != null) {
            val h = colon.groupValues[1].toIntOrNull() ?: return null
            val m = colon.groupValues[2].toIntOrNull() ?: 0
            if (h in 0..23 && m in 0..59) return h to m
        }

        val period = when {
            text.contains("凌晨") -> "凌晨"
            text.contains("早上") || text.contains("早晨") -> "早上"
            text.contains("上午") -> "上午"
            text.contains("中午") -> "中午"
            text.contains("下午") -> "下午"
            text.contains("傍晚") -> "傍晚"
            text.contains("晚上") || text.contains("夜里") -> "晚上"
            else -> ""
        }

        val re = Regex("([零〇一二两三四五六七八九十\\d]{1,3})\\s*[点时]\\s*(半|一刻|([零〇一二两三四五六七八九十\\d]{1,3})\\s*分?)?")
        val m = re.find(text) ?: return null
        var hour = parseNumber(m.groupValues[1])
        val minuteText = m.groupValues[2]
        val minute = when {
            minuteText == "半" -> 30
            minuteText == "一刻" -> 15
            m.groupValues[3].isNotBlank() -> parseNumber(m.groupValues[3])
            else -> 0
        }

        if ((period == "下午" || period == "晚上" || period == "傍晚") && hour in 1..11) hour += 12
        if (period == "中午" && hour in 1..10) hour += 12
        if ((period == "早上" || period == "上午" || period == "凌晨") && hour == 12) hour = 0

        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    fun parseNumber(s: String): Int {
        val t = s.trim()
        if (t.isEmpty()) return 0
        t.toIntOrNull()?.let { return it }
        if (t == "十") return 10
        if (t.startsWith("十")) return 10 + (chineseDigits[t.getOrNull(1)] ?: 0)
        if (t.contains("十")) {
            val tens = chineseDigits[t.substringBefore("十").firstOrNull()] ?: 1
            val ones = t.substringAfter("十").firstOrNull()?.let { chineseDigits[it] } ?: 0
            return tens * 10 + ones
        }
        return chineseDigits[t.first()] ?: 0
    }

    private fun extractTitle(raw: String): String {
        var s = raw
        val removals = listOf(
            "请提醒我", "提醒我", "请提醒", "记得提醒", "记得到时候", "到时候", "麻烦",
            "大后天", "后天", "明天", "明日", "今天", "今日", "过两天",
            "凌晨", "早上", "早晨", "上午", "中午", "下午", "傍晚", "晚上", "夜里"
        )
        removals.forEach { s = s.replace(it, "") }
        s = s.replace(Regex("(下)?(个)?(周|星期|礼拜)(一|二|三|四|五|六|日|天|1|2|3|4|5|6|7)"), "")
        s = s.replace(Regex("过\\s*[0-9一二两三四五六七八九十]{1,3}\\s*天"), "")
        s = s.replace(Regex("([零〇一二两三四五六七八九十\\d]{1,3})\\s*[点时]\\s*(半|一刻|([零〇一二两三四五六七八九十\\d]{1,3})\\s*分?)?"), "")
        s = s.replace(Regex("\\d{1,2}\\s*:\\s*\\d{1,2}"), "")
        s = s.replace(Regex("^(我|要|去|得|需要|帮我)+"), "")
        s = s.replace(Regex("[，,。！!？?、\\s]+"), " ").trim()
        return s.ifBlank { raw.trim() }
    }
}

/** 星期中文。 */
fun weekdayCn(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return when (c.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "星期一"
        Calendar.TUESDAY -> "星期二"
        Calendar.WEDNESDAY -> "星期三"
        Calendar.THURSDAY -> "星期四"
        Calendar.FRIDAY -> "星期五"
        Calendar.SATURDAY -> "星期六"
        else -> "星期日"
    }
}

fun dateCn(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(Locale.CHINA, "%d月%d日", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

fun timeCn(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(Locale.CHINA, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

private fun Int.toIso(): Int = when (this) {
    Calendar.MONDAY -> 1
    Calendar.TUESDAY -> 2
    Calendar.WEDNESDAY -> 3
    Calendar.THURSDAY -> 4
    Calendar.FRIDAY -> 5
    Calendar.SATURDAY -> 6
    else -> 7
}

object TimeFormats {
    fun time(millis: Long): String = timeCn(millis)

    fun dayLabel(millis: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        val todayStart = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayStart = (target.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val diffDays = ((dayStart - todayStart) / (24 * 60 * 60 * 1000L)).toInt()
        return when (diffDays) {
            0 -> "今天"
            1 -> "明天"
            2 -> "后天"
            -1 -> "昨天"
            else -> dateCn(millis)
        }
    }

    fun full(millis: Long): String = "${dateCn(millis)} ${weekdayCn(millis)} ${timeCn(millis)}"
}
