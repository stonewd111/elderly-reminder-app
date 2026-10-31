package com.elder.reminder.data

import android.content.Context
import com.elder.reminder.model.Reminder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicLong

/**
 * 本地提醒仓库：SharedPreferences 持久化 + StateFlow 供界面订阅。
 */
object ReminderRepository {
    private const val PREFS = "elder_reminders"
    private const val KEY = "items"

    private var appContext: Context? = null
    private val ids = AtomicLong(System.currentTimeMillis())

    private val _items = MutableStateFlow<List<Reminder>>(emptyList())
    val items: StateFlow<List<Reminder>> = _items.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        reload()
    }

    fun reload() {
        val ctx = appContext ?: return
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val out = mutableListOf<Reminder>()
        for (i in 0 until arr.length()) {
            runCatching { out.add(Reminder.fromJson(arr.getJSONObject(i))) }
        }
        _items.value = out.sortedBy { it.triggerAt }
    }

    private fun persist(list: List<Reminder>) {
        val ctx = appContext ?: return
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        _items.value = list.sortedBy { it.triggerAt }
    }

    fun all(): List<Reminder> = _items.value
    fun find(id: Long): Reminder? = _items.value.firstOrNull { it.id == id }
    fun nextId(): Long = ids.incrementAndGet()

    fun upsert(reminder: Reminder) {
        val list = _items.value.toMutableList()
        val idx = list.indexOfFirst { it.id == reminder.id }
        if (idx >= 0) list[idx] = reminder else list.add(reminder)
        persist(list)
    }

    fun remove(id: Long) {
        persist(_items.value.filterNot { it.id == id })
    }
}
