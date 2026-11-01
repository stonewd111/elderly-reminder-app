package com.elder.reminder.data

import android.content.Context
import android.content.SharedPreferences
import com.elder.reminder.model.Reminder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 常用页自定义分类及次小类（用户要求：可自定义、可删除）。
 * 结构：List<CustomCategory(label, subItems: List<String>)>。
 * 内建大类（服药/测血压血糖）固定；用户可新增自定义大类及其次小类。
 */
object CommonCategoryPrefs {
    data class CustomCategory(val label: String, val subItems: List<String>)

    /** 内建大类（固定，次小类不可增删）。 */
    val builtinGroups: List<Pair<String, List<String>>> = listOf(
        "服药" to listOf("降压药", "降糖药", "睡前药", "其他"),
        "测血压血糖" to listOf("量血压", "测血糖")
    )

    /** 常用页展示的大类列表：内建固定 + 用户自定义。每项含是否可删除、是否可加次小类。 */
    data class CategoryGroup(
        val label: String,
        val subItems: List<String>,
        val isBuiltin: Boolean,
        val category: String
    )

    private const val PREFS = "elder_common_categories"
    private const val KEY = "items"

    private lateinit var prefs: SharedPreferences
    private val _items = MutableStateFlow<List<CustomCategory>>(emptyList())
    val items: StateFlow<List<CustomCategory>> get() = _items

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _items.value = load()
    }

    /** 常用页完整大类列表（内建 + 自定义）。 */
    fun allGroups(): List<CategoryGroup> {
        val out = mutableListOf<CategoryGroup>()
        out += CategoryGroup("服药", builtinGroups[0].second, true, Reminder.Category.MEDICATION)
        out += CategoryGroup("测血压血糖", builtinGroups[1].second, true, Reminder.Category.HEALTH)
        _items.value.forEach { c ->
            out += CategoryGroup(c.label, c.subItems.ifEmpty { listOf("其他") }, false, Reminder.Category.OTHER)
        }
        return out
    }

    /** 按大类 label 反查其下一次小类列表（内建固定 / 自定义）。 */
    fun subItemsOf(categoryLabel: String): List<String> = when (categoryLabel) {
        "服药" -> builtinGroups[0].second
        "测血压血糖" -> builtinGroups[1].second
        else -> _items.value.firstOrNull { it.label == categoryLabel }?.subItems?.ifEmpty { listOf("其他") } ?: listOf("其他")
    }

    /** 大类是否内建固定（不可删除）。 */
    fun isBuiltin(categoryLabel: String): Boolean =
        categoryLabel == "服药" || categoryLabel == "测血压血糖"

    private fun load(): List<CustomCategory> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val out = mutableListOf<CustomCategory>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val label = o.optString("label")
            if (label.isBlank()) continue
            val subs = mutableListOf<String>()
            val s = o.optJSONArray("subs") ?: JSONArray()
            for (j in 0 until s.length()) {
                val v = s.optString(j)
                if (v.isNotBlank()) subs.add(v)
            }
            out.add(CustomCategory(label, subs))
        }
        return out
    }

    private fun persist(list: List<CustomCategory>) {
        val arr = JSONArray()
        list.forEach { c ->
            val o = JSONObject().apply {
                put("label", c.label)
                put("subs", JSONArray(c.subItems))
            }
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        _items.value = list
    }

    fun addCategory(label: String) {
        if (label.isBlank()) return
        val list = _items.value.toMutableList()
        if (list.any { it.label == label }) return
        list.add(CustomCategory(label, emptyList()))
        persist(list)
    }

    fun removeCategory(label: String) {
        persist(_items.value.filterNot { it.label == label })
    }

    fun addSubItem(categoryLabel: String, sub: String) {
        if (sub.isBlank()) return
        val list = _items.value.toMutableList()
        val idx = list.indexOfFirst { it.label == categoryLabel }
        if (idx < 0) return
        val cur = list[idx]
        if (cur.subItems.contains(sub)) return
        list[idx] = cur.copy(subItems = cur.subItems + sub)
        persist(list)
    }

    fun removeSubItem(categoryLabel: String, sub: String) {
        val list = _items.value.toMutableList()
        val idx = list.indexOfFirst { it.label == categoryLabel }
        if (idx < 0) return
        val cur = list[idx]
        list[idx] = cur.copy(subItems = cur.subItems.filterNot { it == sub })
        persist(list)
    }
}
