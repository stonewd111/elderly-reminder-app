package com.elder.reminder.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条提醒。打开时间 triggerAt 为毫秒时间戳。
 */
data class Reminder(
    val id: Long,
    val title: String,
    val triggerAt: Long,
    val mode: Mode = Mode.VOICE,
    val repeat: Repeat = Repeat.None,
    val enabled: Boolean = true,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** 提醒分类：medication 服药 / health 测血压血糖 / exercise 运动 / water 饮水 / other 其他 */
    val category: String = "other"
) {
    enum class Mode { VOICE, BEEP }

    object Category {
        const val MEDICATION = "medication"
        const val HEALTH = "health"
        const val EXERCISE = "exercise"
        const val WATER = "water"
        const val OTHER = "other"

        fun label(c: String): String = when (c) {
            MEDICATION -> "服药"
            HEALTH -> "测血压血糖"
            EXERCISE -> "运动"
            WATER -> "喝水"
            else -> "自定义"
        }

        fun emoji(c: String): String = when (c) {
            MEDICATION -> "💊"
            HEALTH -> "🩺"
            EXERCISE -> "🚶"
            WATER -> "🥤"
            else -> "📌"
        }
    }

    sealed interface Repeat {
        data object None : Repeat
        data object Daily : Repeat
        /** days: 1=周一 ... 7=周日 */
        data class Weekly(val days: Set<Int>) : Repeat
        /** day: 每月几号，1..31 */
        data class Monthly(val day: Int) : Repeat
        /** days: 每隔多少天 */
        data class Interval(val days: Int) : Repeat
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("triggerAt", triggerAt)
        put("mode", mode.name)
        put("enabled", enabled)
        put("done", done)
        put("createdAt", createdAt)
        put("category", category)
        put("repeat", repeatToJson(repeat))
    }

    companion object {
        fun fromJson(o: JSONObject): Reminder = Reminder(
            id = o.optLong("id"),
            title = o.optString("title", "提醒事项"),
            triggerAt = o.optLong("triggerAt"),
            mode = runCatching { Mode.valueOf(o.optString("mode", "VOICE")) }.getOrDefault(Mode.VOICE),
            repeat = repeatFromJson(o.optJSONObject("repeat")),
            enabled = o.optBoolean("enabled", true),
            done = o.optBoolean("done", false),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            category = o.optString("category", "other")
        )

        private fun repeatToJson(r: Repeat): JSONObject = JSONObject().apply {
            when (r) {
                Repeat.None -> put("type", "none")
                Repeat.Daily -> put("type", "daily")
                is Repeat.Weekly -> {
                    put("type", "weekly")
                    put("days", JSONArray(r.days.sorted()))
                }
                is Repeat.Monthly -> {
                    put("type", "monthly")
                    put("day", r.day)
                }
                is Repeat.Interval -> {
                    put("type", "interval")
                    put("days", r.days)
                }
            }
        }

        private fun repeatFromJson(o: JSONObject?): Repeat {
            if (o == null) return Repeat.None
            return when (o.optString("type", "none")) {
                "daily" -> Repeat.Daily
                "weekly" -> {
                    val arr = o.optJSONArray("days") ?: JSONArray()
                    val set = mutableSetOf<Int>()
                    for (i in 0 until arr.length()) set.add(arr.optInt(i))
                    Repeat.Weekly(if (set.isEmpty()) setOf(1) else set)
                }
                "monthly" -> Repeat.Monthly(o.optInt("day", 1).coerceIn(1, 31))
                "interval" -> Repeat.Interval(o.optInt("days", 1).coerceIn(1, 365))
                else -> Repeat.None
            }
        }
    }
}

/** 给界面展示的中文描述。 */
fun Reminder.repeatText(): String = repeat.cnLabel()

fun Reminder.Repeat.cnLabel(): String = when (this) {
    Reminder.Repeat.None -> "不重复"
    Reminder.Repeat.Daily -> "每天"
    is Reminder.Repeat.Weekly -> "每周 " + days.sorted().joinToString("、") { weekdayName(it) }
    is Reminder.Repeat.Monthly -> "每月 $day 日"
    is Reminder.Repeat.Interval -> "每隔 $days 天"
}

fun weekdayName(day: Int): String = when (day) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
