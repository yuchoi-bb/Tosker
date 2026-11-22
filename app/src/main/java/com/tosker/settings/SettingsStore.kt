package com.tosker.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** 목록별 버튼 설정 (표시 이름 + 색상 ARGB + 버튼 표시 순서) */
data class ListConfig(
    val label: String,
    val colorHex: Long,
    /** 버튼이 표시될 순서. null이면 기본 순서(CategoryDefaults.defaultOrder)를 따른다. */
    val order: Int? = null
)

/**
 * Upload 아래 단축버튼 한 칸의 설정.
 * 누르면 [title] 할일이 선택된 날짜에 [listId] 목록으로 등록된다.
 * [markCompleted]가 true면 "한 일"로 완료 상태로, false면 "해야 할 일"로 등록한다.
 */
data class ShortcutConfig(
    val slot: Int,
    val title: String,
    val iconKey: String,
    val colorHex: Long,
    /** 등록할 목록. null이면 현재 선택된 목록을 쓴다. */
    val listId: String? = null,
    /** true = 한 일 기록(완료 상태), false = 해야 할 일 등록 */
    val markCompleted: Boolean = true
)

/** 기본 라벨/색상 팔레트. 설정되지 않은 목록에 적용됩니다. */
object CategoryDefaults {
    val palette = listOf(
        0xFFF4788AL, // 핑크
        0xFF9B97D3L, // 라벤더
        0xFF7BB8D4L, // 블루
        0xFFB5B0CCL, // 퍼플
        0xFFFF8C00L, // 주황
        0xFF4CAF50L, // 초록
        0xFF2196F3L, // 파랑
        0xFF9C27B0L  // 보라
    )

    private val defaultLabels = listOf("P", "W", "S", "R")

    /** "오늘 할 일" 성격의 목록은 기본으로 맨 앞(최좌측)에 배치한다. */
    private val todayKeywords = listOf("2day", "today", "투데이", "오늘")

    fun defaultLabel(index: Int, title: String): String {
        val clean = title.trim()
        // 기본 4개는 기존 P/W/S/R 라벨을 유지하고, 그 이후 목록은 제목을 그대로 쓴다.
        return defaultLabels.getOrNull(index)
            ?: clean.take(6).ifBlank { "?" }
    }

    fun defaultColor(index: Int): Long =
        palette[index % palette.size]

    /** "2day"처럼 오늘 할 일 전용 목록인지 판별 */
    fun isTodayList(title: String): Boolean {
        val clean = title.trim().lowercase()
        return todayKeywords.any { clean.contains(it) }
    }

    /**
     * 사용자가 순서를 지정하지 않았을 때의 기본 순서.
     * 오늘 할 일 목록(2day 등)은 -1을 반환해 항상 맨 앞에 오게 한다.
     */
    fun defaultOrder(index: Int, title: String): Int =
        if (isTodayList(title)) -1 else index
}

class SettingsStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("tosker_settings", Context.MODE_PRIVATE)

    // API 키처럼 민감한 값은 별도의 암호화된 저장소에 보관한다.
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "tosker_secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** 문서 스캔(Gemini API) 기능에 사용할 사용자 본인의 Gemini API 키 */
    fun loadGeminiApiKey(): String? =
        securePrefs.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun saveGeminiApiKey(apiKey: String) {
        securePrefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun loadConfigs(): Map<String, ListConfig> {
        val raw = prefs.getString(KEY_CONFIGS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val o = obj.getJSONObject(id)
                    put(
                        id,
                        ListConfig(
                            label = o.getString("label"),
                            colorHex = o.getString("color").toLong(16),
                            order = if (o.has("order")) o.getInt("order") else null
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveConfig(id: String, config: ListConfig) {
        saveConfigs(mapOf(id to config))
    }

    /** 여러 목록의 설정을 한 번에 저장한다(순서 변경 시 전체 재작성). */
    fun saveConfigs(configs: Map<String, ListConfig>) {
        val current = loadConfigs().toMutableMap()
        current.putAll(configs)
        val obj = JSONObject()
        current.forEach { (k, v) ->
            val entry = JSONObject()
                .put("label", v.label)
                .put("color", java.lang.Long.toHexString(v.colorHex))
            v.order?.let { entry.put("order", it) }
            obj.put(k, entry)
        }
        prefs.edit().putString(KEY_CONFIGS, obj.toString()).apply()
        touch()
    }

    /** 단축버튼 설정을 슬롯 번호(0~9) → 설정으로 반환 */
    fun loadShortcuts(): Map<Int, ShortcutConfig> {
        val raw = prefs.getString(KEY_SHORTCUTS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val slot = key.toIntOrNull() ?: continue
                    val o = obj.getJSONObject(key)
                    put(
                        slot,
                        ShortcutConfig(
                            slot = slot,
                            title = o.getString("title"),
                            iconKey = o.getString("icon"),
                            colorHex = o.getString("color").toLong(16),
                            listId = o.optString("listId").takeIf { it.isNotBlank() },
                            // 이전 버전 설정에는 없던 값이라 기본은 "한 일 기록"
                            markCompleted = o.optBoolean("done", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveShortcut(config: ShortcutConfig) {
        val current = loadShortcuts().toMutableMap()
        current[config.slot] = config
        writeShortcuts(current)
    }

    fun deleteShortcut(slot: Int) {
        val current = loadShortcuts().toMutableMap()
        current.remove(slot)
        writeShortcuts(current)
    }

    private fun writeShortcuts(shortcuts: Map<Int, ShortcutConfig>) {
        val obj = JSONObject()
        shortcuts.forEach { (slot, v) ->
            val entry = JSONObject()
                .put("title", v.title)
                .put("icon", v.iconKey)
                .put("color", java.lang.Long.toHexString(v.colorHex))
                .put("done", v.markCompleted)
            v.listId?.let { entry.put("listId", it) }
            obj.put(slot.toString(), entry)
        }
        prefs.edit().putString(KEY_SHORTCUTS, obj.toString()).apply()
        touch()
    }

    /** 습관 격자에 쓸 Google Tasks 목록 id. 지정 전에는 null. */
    fun loadHabitListId(): String? =
        prefs.getString(KEY_HABIT_LIST, null)?.takeIf { it.isNotBlank() }

    fun saveHabitListId(listId: String?) {
        prefs.edit().apply {
            if (listId.isNullOrBlank()) remove(KEY_HABIT_LIST) else putString(KEY_HABIT_LIST, listId)
        }.apply()
        touch()
    }

    /** 한 줄 일기를 담을 Google Tasks 목록 id. 지정 전에는 null. */
    fun loadJournalListId(): String? =
        prefs.getString(KEY_JOURNAL_LIST, null)?.takeIf { it.isNotBlank() }

    fun saveJournalListId(listId: String?) {
        prefs.edit().apply {
            if (listId.isNullOrBlank()) remove(KEY_JOURNAL_LIST) else putString(KEY_JOURNAL_LIST, listId)
        }.apply()
        touch()
    }

    /** 저녁 알림 사용 여부 (기본 켜짐) */
    fun loadReminderEnabled(): Boolean = prefs.getBoolean(KEY_REMINDER, true)

    fun saveReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER, enabled).apply()
        touch()
    }

    /** 설정이 마지막으로 바뀐 시각(epoch millis). 동기화 충돌 해결에 쓴다. */
    fun loadUpdatedAt(): Long = prefs.getLong(KEY_UPDATED_AT, 0L)

    private fun touch() {
        prefs.edit().putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply()
    }

    /**
     * 동기화할 설정 전체를 JSON으로 내보낸다.
     * Gemini API 키는 민감 정보라 제외한다.
     */
    fun exportJson(): String {
        val listConfigs = JSONObject()
        loadConfigs().forEach { (id, v) ->
            val entry = JSONObject()
                .put("label", v.label)
                .put("color", java.lang.Long.toHexString(v.colorHex))
            v.order?.let { entry.put("order", it) }
            listConfigs.put(id, entry)
        }

        val shortcuts = JSONObject()
        loadShortcuts().forEach { (slot, v) ->
            val entry = JSONObject()
                .put("title", v.title)
                .put("icon", v.iconKey)
                .put("color", java.lang.Long.toHexString(v.colorHex))
                .put("done", v.markCompleted)
            v.listId?.let { entry.put("listId", it) }
            shortcuts.put(slot.toString(), entry)
        }

        return JSONObject()
            .put("version", 1)
            .put("updatedAt", loadUpdatedAt())
            .put("listConfigs", listConfigs)
            .put("shortcuts", shortcuts)
            .put("habitListId", loadHabitListId() ?: "")
            .put("journalListId", loadJournalListId() ?: "")
            .put("reminderEnabled", loadReminderEnabled())
            .toString()
    }

    /**
     * 내보낸 JSON을 이 기기에 그대로 적용한다.
     * 성공하면 true, 형식이 잘못되면 아무것도 바꾸지 않고 false.
     */
    fun importJson(json: String): Boolean = runCatching {
        val obj = JSONObject(json)
        val listConfigs = obj.optJSONObject("listConfigs") ?: JSONObject()
        val shortcuts = obj.optJSONObject("shortcuts") ?: JSONObject()
        val remoteUpdatedAt = obj.optLong("updatedAt", 0L)

        prefs.edit()
            .putString(KEY_CONFIGS, listConfigs.toString())
            .putString(KEY_SHORTCUTS, shortcuts.toString())
            .putLong(KEY_UPDATED_AT, remoteUpdatedAt)
            .apply()

        // 습관 목록 / 알림 설정도 함께 받아온다.
        obj.optString("habitListId").let { id ->
            prefs.edit().apply {
                if (id.isBlank()) remove(KEY_HABIT_LIST) else putString(KEY_HABIT_LIST, id)
            }.apply()
        }
        obj.optString("journalListId").let { id ->
            prefs.edit().apply {
                if (id.isBlank()) remove(KEY_JOURNAL_LIST) else putString(KEY_JOURNAL_LIST, id)
            }.apply()
        }
        if (obj.has("reminderEnabled")) {
            prefs.edit().putBoolean(KEY_REMINDER, obj.optBoolean("reminderEnabled", true)).apply()
        }
        true
    }.getOrDefault(false)

    private companion object {
        const val KEY_CONFIGS = "list_configs"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_SHORTCUTS = "shortcuts"
        const val KEY_UPDATED_AT = "settings_updated_at"
        const val KEY_HABIT_LIST = "habit_list_id"
        const val KEY_JOURNAL_LIST = "journal_list_id"
        const val KEY_REMINDER = "reminder_enabled"
    }
}
