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
    val category: String = "other",
    /** 自定义分类显示名（仅 category==other 且用户选自定义大类时非空，如"复查"），修复"始终显示自定义"bug */
    val categoryLabel: String = "",
    /** 药名（仅服药类提醒需要，可空） */
    val drug: String = "",
    /** 次小类：如降压药、降糖药、量血压、测血糖等（从常用页大类选中同步，可空） */
    val subCategory: String = "",
    /** 是否已触发（闹钟已到点响过），用于区分"今天/未来"与"延误" */
    val triggered: Boolean = false,
    /** 是否延误：闹钟触发后无人点击"已服用"、系统自动停止 2 分钟后置 true */
    val delayed: Boolean = false,
    /** 标记为完成的时间；重复提醒只认"完成当天"，用于次日自动回到队列 */
    val doneAt: Long = 0L,
    /**
     * 本条提醒的铃声：空串=跟随全局默认铃声；可选 system/male/female/custom/vibrate。
     * 逐条提醒独立设置，避免所有提醒只能共用一种声音。
     */
    val soundType: String = "",
    /** 自定义铃声的 content URI（soundType==custom 时有效，需已持久化读授权） */
    val soundUri: String = "",
    /** 自定义铃声显示名（仅用于界面展示） */
    val soundName: String = "",
    /**
     * 最近一次真正响铃的时刻（由 AlarmReceiver 写入）。
     * 有了它才能判断"某次该响却没响"：与 [com.elder.reminder.alarm.ReminderScheduler.lastDueBefore]
     * 算出的应响时刻比较，前者更早就说明漏报。
     */
    val lastFiredAt: Long = 0L
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
        put("categoryLabel", categoryLabel)
        put("drug", drug)
        put("subCategory", subCategory)
        put("triggered", triggered)
        put("delayed", delayed)
        put("doneAt", doneAt)
        put("soundType", soundType)
        put("soundUri", soundUri)
        put("soundName", soundName)
        put("lastFiredAt", lastFiredAt)
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
            category = o.optString("category", "other"),
            categoryLabel = o.optString("categoryLabel", ""),
            drug = o.optString("drug", ""),
            subCategory = o.optString("subCategory", ""),
            triggered = o.optBoolean("triggered", false),
            delayed = o.optBoolean("delayed", false),
            doneAt = o.optLong("doneAt", 0L),
            soundType = o.optString("soundType", ""),
            soundUri = o.optString("soundUri", ""),
            soundName = o.optString("soundName", ""),
            lastFiredAt = o.optLong("lastFiredAt", 0L)
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

/**
 * done 对当前时刻是否仍然有效：
 *  - 单次提醒：完成即永久有效；
 *  - 重复提醒：只认完成当天，次日自动失效并回到队列（无需人工恢复）。
 */
fun Reminder.isDoneNow(now: Long = System.currentTimeMillis()): Boolean {
    if (!done) return false
    if (repeat == Reminder.Repeat.None) return true
    if (doneAt <= 0L) return true
    val a = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val b = java.util.Calendar.getInstance().apply { timeInMillis = doneAt }
    return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
}

fun Reminder.Repeat.cnLabel(): String = when (this) {
    Reminder.Repeat.None -> "不重复"
    Reminder.Repeat.Daily -> "每天"
    is Reminder.Repeat.Weekly -> {
        // 紧凑写法：首个星期用全称，后续用单字，如"每周 周一/三/五"，避免过长跨行
        val sorted = days.sorted()
        if (sorted.isEmpty()) "每周"
        else "每周 " + sorted.mapIndexed { i, d ->
            if (i == 0) weekdayName(d) else weekdayShortName(d)
        }.joinToString("/")
    }
    is Reminder.Repeat.Monthly -> "每月 $day 日"
    is Reminder.Repeat.Interval -> "每隔 $days 天"
}

/** 星期简称：1→一 … 7→日，用于"每周 周一/三/五"紧凑写法。 */
fun weekdayShortName(day: Int): String = when (day) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
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
