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
 * 内建大类（服药/测血压血糖）固定，但允许用户追加次小类（存于 extraSubs）。
 * 用户可新增自定义大类及其次小类。自定义大类不默认生成"其他"次小类。
 */
object CommonCategoryPrefs {
    data class CustomCategory(val label: String, val subItems: List<String>)

    /** 内建大类（固定基础次小类）。 */
    val builtinGroups: List<Pair<String, List<String>>> = listOf(
        "服药" to listOf("降压药", "降糖药", "睡前药", "其他"),
        "测血压血糖" to listOf("量血压", "测血糖")
    )

    /** 常用页展示的大类列表：内建固定 + 用户自定义。每项含是否可删除、是否可加次小类。 */
    data class CategoryGroup(
        val label: String,
        val subItems: List<String>,
        val isBuiltin: Boolean,
        val category: String,
        /** 该大类下哪些次小类可删除（内建基础不可删，仅用户追加项可删；自定义全部可删）。 */
        val removableSubs: Set<String> = emptySet()
    )

    private const val PREFS = "elder_common_categories"
    private const val KEY = "items"
    private const val KEY_EXTRA = "extra_builtin_subs"

    private lateinit var prefs: SharedPreferences
    private val _items = MutableStateFlow<List<CustomCategory>>(emptyList())
    val items: StateFlow<List<CustomCategory>> get() = _items

    /** 内建大类（服药/测血压血糖）用户追加的次小类。 */
    private val _extraSubs = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val extraSubs: StateFlow<Map<String, List<String>>> get() = _extraSubs

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _items.value = load()
        _extraSubs.value = loadExtra()
    }

    /** 常用页完整大类列表（内建 + 自定义）。自定义大类若无次小类则列表为空（不自动补"其他"）。 */
    fun allGroups(): List<CategoryGroup> {
        val out = mutableListOf<CategoryGroup>()
        out += CategoryGroup("服药", builtinSubItems("服药"), true, Reminder.Category.MEDICATION,
            removableSubs = (_extraSubs.value["服药"] ?: emptyList()).toSet())
        out += CategoryGroup("测血压血糖", builtinSubItems("测血压血糖"), true, Reminder.Category.HEALTH,
            removableSubs = (_extraSubs.value["测血压血糖"] ?: emptyList()).toSet())
        _items.value.forEach { c ->
            out += CategoryGroup(c.label, c.subItems, false, Reminder.Category.OTHER,
                removableSubs = c.subItems.toSet())
        }
        return out
    }

    /** 按大类 label 反查其下一次小类列表（内建固定 + 用户追加 / 自定义）。 */
    fun subItemsOf(categoryLabel: String): List<String> =
        if (isBuiltin(categoryLabel)) builtinSubItems(categoryLabel)
        else _items.value.firstOrNull { it.label == categoryLabel }?.subItems ?: emptyList()

    /** 大类是否内建固定（不可删除）。 */
    fun isBuiltin(categoryLabel: String): Boolean =
        categoryLabel == "服药" || categoryLabel == "测血压血糖"

    /** 内建大类完整次小类 = 固定基础 + 用户追加（去重）。 */
    private fun builtinSubItems(label: String): List<String> {
        val base = builtinGroups.firstOrNull { it.first == label }?.second ?: emptyList()
        val extra = _extraSubs.value[label] ?: emptyList()
        return base + extra.filterNot { base.contains(it) }
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

    private fun loadExtra(): Map<String, List<String>> {
        val raw = prefs.getString(KEY_EXTRA, "{}") ?: "{}"
        val o = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val out = mutableMapOf<String, List<String>>()
        o.keys().forEach { k ->
            val arr = o.optJSONArray(k) ?: return@forEach
            val subs = mutableListOf<String>()
            for (j in 0 until arr.length()) {
                val v = arr.optString(j)
                if (v.isNotBlank()) subs.add(v)
            }
            out[k] = subs
        }
        return out
    }

    private fun persistExtra(map: Map<String, List<String>>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        prefs.edit().putString(KEY_EXTRA, o.toString()).apply()
        _extraSubs.value = map
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

    /** 追加次小类：内建大类写入 extraSubs；自定义大类写入其 subItems。 */
    fun addSubItem(categoryLabel: String, sub: String) {
        if (sub.isBlank()) return
        if (isBuiltin(categoryLabel)) {
            val cur = _extraSubs.value
            val list = cur[categoryLabel] ?: emptyList()
            if (list.contains(sub)) return
            persistExtra(cur + (categoryLabel to (list + sub)))
        } else {
            val list = _items.value.toMutableList()
            val idx = list.indexOfFirst { it.label == categoryLabel }
            if (idx < 0) return
            val cur = list[idx]
            if (cur.subItems.contains(sub)) return
            list[idx] = cur.copy(subItems = cur.subItems + sub)
            persist(list)
        }
    }

    /** 删除次小类：内建大类仅删用户追加项（固定基础不可删）；自定义大类可删任意项。 */
    fun removeSubItem(categoryLabel: String, sub: String) {
        if (isBuiltin(categoryLabel)) {
            val cur = _extraSubs.value
            val list = cur[categoryLabel] ?: emptyList()
            if (!list.contains(sub)) return
            persistExtra(cur + (categoryLabel to list.filterNot { it == sub }))
        } else {
            val list = _items.value.toMutableList()
            val idx = list.indexOfFirst { it.label == categoryLabel }
            if (idx < 0) return
            val cur = list[idx]
            list[idx] = cur.copy(subItems = cur.subItems.filterNot { it == sub })
            persist(list)
        }
    }
}
