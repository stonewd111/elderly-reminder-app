package com.elder.reminder.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 到点提醒全屏大屏的主题颜色（用户要求：红色 / 蓝色可选，仅背景色不同）。
 * theme: red（默认，红色大屏）/ blue（蓝色大屏）。
 */
object AlarmThemePrefs {
    const val THEME_RED = "red"
    const val THEME_BLUE = "blue"

    private const val PREFS = "elder_alarm_theme"
    private const val KEY_THEME = "theme"

    private lateinit var prefs: SharedPreferences
    private val _theme = MutableStateFlow(THEME_RED)
    val theme: StateFlow<String> get() = _theme

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _theme.value = prefs.getString(KEY_THEME, THEME_RED) ?: THEME_RED
    }

    fun setTheme(theme: String) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme).apply()
    }
}
