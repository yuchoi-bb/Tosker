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

    /**
     * 사용자가 순서를 지정하지 않았을 때의 기본 순서.
     * 오늘 할 일 목록(2day 등)은 -1을 반환해 항상 맨 앞에 오게 한다.
     */
    fun defaultOrder(index: Int, title: String): Int {
        val clean = title.trim().lowercase()
        return if (todayKeywords.any { clean.contains(it) }) -1 else index
    }
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
    }

    private companion object {
        const val KEY_CONFIGS = "list_configs"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
