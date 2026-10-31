package com.elder.reminder.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局字体大小设置（老人视角适配）。
 * level: 0 标准 / 1 大 / 2 特大，映射到缩放系数 1.0 / 1.15 / 1.3。
 */
object FontPrefs {
    const val LEVEL_STANDARD = 0
    const val LEVEL_LARGE = 1
    const val LEVEL_XLARGE = 2

    fun scaleOf(level: Int): Float = when (level) {
        LEVEL_LARGE -> 1.15f
        LEVEL_XLARGE -> 1.3f
        else -> 1f
    }

    private const val PREFS = "elder_font"
    private const val KEY_LEVEL = "font_level"
    private lateinit var prefs: SharedPreferences
    private val _level = MutableStateFlow(LEVEL_STANDARD)
    val level: StateFlow<Int> get() = _level

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _level.value = prefs.getInt(KEY_LEVEL, LEVEL_STANDARD)
    }

    fun setLevel(level: Int) {
        _level.value = level
        prefs.edit().putInt(KEY_LEVEL, level).apply()
    }
}
