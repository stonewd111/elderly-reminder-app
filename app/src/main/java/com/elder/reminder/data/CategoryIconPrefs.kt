package com.elder.reminder.data

import android.content.Context
import android.content.SharedPreferences
import com.elder.reminder.model.Reminder
import org.json.JSONObject

/**
 * 大类提醒图标配置（需求6：提醒图标用 3 种——服药/测量/自定义，常用页可更换）。
 *
 * 每个大类（按 label 存储）可配置一个图标：
 *  - Emoji：内置可选 emoji 图标列表之一；
 *  - Image：用户自定义上传的图片（持久化 content URI，如 content://…/icon.png）。
 *
 * 默认：服药→💊、测血压血糖→🩺、其他→📌。
 */
object CategoryIconPrefs {
    /** 预置可选 emoji 图标列表（常用页点分类图标弹出的可选列表）。 */
    val presetEmojis: List<String> = listOf(
        "💊", "🩺", "📌", "💉", "🧪", "🏥", "🍚", "💧", "🚶", "😴",
        "❤️", "🩸", "🥗", "☕", "🔔", "⏰", "📅", "🩹", "🌡️", "🫀"
    )

    /** 图标类型：emoji 或自定义上传图片（content URI）。 */
    sealed interface CategoryIcon {
        data class Emoji(val emoji: String) : CategoryIcon
        data class Image(val uri: String) : CategoryIcon
    }

    private const val PREFS = "elder_category_icons"
    private const val KEY = "map"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 取某大类图标；未配置则按分类类型给默认。label 为显示名（如"服药""复查"）。 */
    fun iconOf(label: String, category: String = Reminder.Category.OTHER): CategoryIcon {
        val raw = prefs.getString(KEY, "{}") ?: "{}"
        val o = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val v = o.optString(label)
        if (v.isNotBlank()) {
            return when {
                v.startsWith("img:") -> CategoryIcon.Image(v.removePrefix("img:"))
                v.startsWith("emoji:") -> CategoryIcon.Emoji(v.removePrefix("emoji:"))
                else -> CategoryIcon.Emoji(v)
            }
        }
        // 默认
        return CategoryIcon.Emoji(defaultEmoji(category))
    }

    /** 按提醒的分类给出默认 emoji。 */
    fun defaultEmoji(category: String): String = Reminder.Category.emoji(category)

    fun setIcon(label: String, icon: CategoryIcon) {
        val raw = prefs.getString(KEY, "{}") ?: "{}"
        val o = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val v = when (icon) {
            is CategoryIcon.Emoji -> "emoji:" + icon.emoji
            is CategoryIcon.Image -> "img:" + icon.uri
        }
        o.put(label, v)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    /**
     * 自定义上传图标的格式与大小要求（展示给用户）：
     * 建议 PNG/JPEG，正方形，边长 256~1024px，< 2MB，透明背景更佳。
     */
    const val CUSTOM_ICON_HINT = "格式：PNG/JPG，正方形；建议边长 256~1024 像素，大小不超过 2MB，透明背景效果更佳。"
}
