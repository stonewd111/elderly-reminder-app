package com.elder.reminder.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局提醒音效设置（用户要求：音效放设置里，不在单次提醒里选）。
 * soundType: system（系统闹铃）/ male（内置男声）/ female（内置女声）/ custom（自定义MP3）
 * customPath: 自定义 MP3 的 URI 字符串（可选）
 */
object SoundPrefs {
    const val TYPE_SYSTEM = "system"
    const val TYPE_MALE = "male"
    const val TYPE_FEMALE = "female"
    const val TYPE_CUSTOM = "custom"

    private const val PREFS = "elder_sound"
    private const val KEY_TYPE = "sound_type"
    private const val KEY_CUSTOM = "custom_path"
    private const val KEY_CUSTOM_NAME = "custom_name"

    private lateinit var prefs: SharedPreferences
    private val _soundType = MutableStateFlow(TYPE_SYSTEM)
    private val _customPath = MutableStateFlow("")
    private val _customName = MutableStateFlow("")

    val soundType: StateFlow<String> get() = _soundType
    val customPath: StateFlow<String> get() = _customPath
    val customName: StateFlow<String> get() = _customName

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _soundType.value = prefs.getString(KEY_TYPE, TYPE_SYSTEM) ?: TYPE_SYSTEM
        _customPath.value = prefs.getString(KEY_CUSTOM, "") ?: ""
        _customName.value = prefs.getString(KEY_CUSTOM_NAME, "") ?: ""
    }

    fun setSoundType(type: String) {
        _soundType.value = type
        prefs.edit().putString(KEY_TYPE, type).apply()
    }

    fun setCustom(path: String, name: String) {
        _customPath.value = path
        _customName.value = name
        prefs.edit().putString(KEY_CUSTOM, path).putString(KEY_CUSTOM_NAME, name).apply()
    }

    /** 当前有效的音效类型（若为 custom 但未设置路径，回退 system）。 */
    fun effectiveType(): String =
        if (_soundType.value == TYPE_CUSTOM && _customPath.value.isBlank()) TYPE_SYSTEM else _soundType.value
}
