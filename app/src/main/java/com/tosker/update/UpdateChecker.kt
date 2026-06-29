package com.tosker.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 최신 릴리즈 정보 */
data class UpdateInfo(
    val versionName: String,        // 예: "1.1.0" (v 접두사 제거됨)
    val tagName: String,            // 예: "v1.1.0"
    val apkUrl: String,             // APK 다운로드 URL
    val apkName: String             // 예: "tosker-v1.1.0-debug.apk"
)

object UpdateChecker {

    private const val RELEASES_API =
        "https://api.github.com/repos/yuchoi-bb/Tosker/releases/latest"

    /**
     * GitHub 최신 릴리즈를 조회하여 현재 버전보다 높으면 UpdateInfo 반환, 아니면 null.
     * 네트워크 IO이므로 반드시 백그라운드 스레드(Dispatchers.IO)에서 호출하세요.
     */
    fun fetchIfUpdateAvailable(currentVersionName: String): UpdateInfo? {
        val latest = fetchLatestRelease() ?: return null
        return if (isNewer(latest.versionName, currentVersionName)) latest else null
    }

    private fun fetchLatestRelease(): UpdateInfo? {
        return runCatching {
            val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            conn.use { c ->
                if (c.responseCode != 200) return null
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").ifBlank { return null }

                val assets = json.optJSONArray("assets") ?: return null
                var apkUrl: String? = null
                var apkName: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = name
                        break
                    }
                }
                if (apkUrl.isNullOrBlank() || apkName.isNullOrBlank()) return null

                UpdateInfo(
                    versionName = tag.removePrefix("v"),
                    tagName = tag,
                    apkUrl = apkUrl,
                    apkName = apkName
                )
            }
        }.getOrNull()
    }

    /** "1.2.0" vs "1.1.0" 처럼 점 구분 숫자 버전을 비교. latest > current 면 true */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        try {
            return block(this)
        } finally {
            disconnect()
        }
    }
}
