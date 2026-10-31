package com.elder.reminder.data

import android.content.Context
import android.content.SharedPreferences
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

    private const val PREFS = "elder_common_categories"
    private const val KEY = "items"

    private lateinit var prefs: SharedPreferences
    private val _items = MutableStateFlow<List<CustomCategory>>(emptyList())
    val items: StateFlow<List<CustomCategory>> get() = _items

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _items.value = load()
    }

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
