package com.tosker.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** 목록별 버튼 설정 (표시 이름 + 색상 ARGB) */
data class ListConfig(val label: String, val colorHex: Long)

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

    fun defaultLabel(index: Int, title: String): String =
        defaultLabels.getOrNull(index)
            ?: title.trim().take(1).uppercase().ifBlank { "?" }

    fun defaultColor(index: Int): Long =
        palette[index % palette.size]
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
                            colorHex = o.getString("color").toLong(16)
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveConfig(id: String, config: ListConfig) {
        val current = loadConfigs().toMutableMap()
        current[id] = config
        val obj = JSONObject()
        current.forEach { (k, v) ->
            obj.put(
                k,
                JSONObject()
                    .put("label", v.label)
                    .put("color", java.lang.Long.toHexString(v.colorHex))
            )
        }
        prefs.edit().putString(KEY_CONFIGS, obj.toString()).apply()
    }

    private companion object {
        const val KEY_CONFIGS = "list_configs"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
