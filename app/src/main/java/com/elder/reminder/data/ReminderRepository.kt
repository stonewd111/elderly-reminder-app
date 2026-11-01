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
        val parsed = mutableListOf<Reminder>()
        for (i in 0 until arr.length()) {
            runCatching { parsed.add(Reminder.fromJson(arr.getJSONObject(i))) }
        }
        // 保证新 id 大于已存 id，时钟回拨时也不会与旧提醒冲突
        parsed.maxOfOrNull { it.id }?.let { max -> if (max > ids.get()) ids.set(max) }
        var migrated = false
        val out = parsed.map { r ->
            // 旧版本"常用"页模板以 id=-1 保存，会导致后建提醒覆盖前一条；这里补发唯一 id
            if (r.id <= 0L) {
                migrated = true
                r.copy(id = nextId())
            } else r
        }
        _items.value = out.sortedBy { it.triggerAt }
        if (migrated) persist(_items.value)
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

    fun removeAllDone() {
        persist(_items.value.filterNot { it.done })
    }
}
