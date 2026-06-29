package com.tosker.settings

import android.content.Context
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
    }
}
