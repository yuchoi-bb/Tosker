package com.tosker.ai

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Gemini API로 사진 또는 텍스트에서 날짜 기반 일정을 추출한다. */
object GeminiClient {

    private const val MODEL = "gemini-2.5-flash"
    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    class GeminiException(message: String) : Exception(message)

    /** 사진(학사일정표, 공지문 등)에서 일정을 추출한다. */
    fun extractEvents(apiKey: String, imageBytes: ByteArray, mimeType: String): List<ExtractedEvent> {
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val parts = JSONArray()
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", mimeType)
                        .put("data", base64Image)
                )
            )
            .put(JSONObject().put("text", buildPrompt("첨부된 사진은")))

        return parseEvents(extractResponseText(callGemini(apiKey, parts)))
    }

    /** 문서에서 옮겨 적었거나 복사해 온 텍스트에서 일정을 추출한다. */
    fun extractEventsFromText(apiKey: String, documentText: String): List<ExtractedEvent> {
        val prompt = buildPrompt("아래 --- 다음에 오는 텍스트는") + "\n\n---\n" + documentText
        val parts = JSONArray().put(JSONObject().put("text", prompt))

        return parseEvents(extractResponseText(callGemini(apiKey, parts)))
    }

    private fun buildPrompt(sourceDescription: String): String {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return """
            오늘 날짜는 $today 입니다. $sourceDescription 학사일정표, 공지문, 안내문 등
            날짜가 포함된 문서입니다. 여기에서 날짜(또는 날짜 범위)가 있는 모든
            일정을 찾아 JSON 배열로 반환하세요.

            규칙:
            - title: 일정 이름을 한국어로 간결하게.
            - date: 시작일을 YYYY-MM-DD 형식으로.
            - end_date: 여름방학처럼 기간이 있는 경우에만 종료일을 채우고, 하루짜리
              일정이면 비워두세요.
            - time: "14:00"처럼 특정 시각이 명시된 경우에만 24시간 형식으로 채우고,
              시각이 없으면 비워두세요(종일 일정).
            - note: "급식 미실시" 같은 비고가 있으면 채우세요.
            - 연도가 없는 날짜는 문서의 다른 날짜와 오늘 날짜를 참고해 추정하세요.
            - 표에서는 각 행이 하나의 일정입니다.
            - "예시"라고 표시된 항목은 실제 일정이 아니므로 제외하세요.
        """.trimIndent()
    }

    private fun responseSchema(): JSONObject = JSONObject().apply {
        put("type", "ARRAY")
        put(
            "items",
            JSONObject().apply {
                put("type", "OBJECT")
                put(
                    "properties",
                    JSONObject().apply {
                        put("title", JSONObject().put("type", "STRING"))
                        put("date", JSONObject().put("type", "STRING"))
                        put("end_date", JSONObject().put("type", "STRING"))
                        put("time", JSONObject().put("type", "STRING"))
                        put("note", JSONObject().put("type", "STRING"))
                    }
                )
                put("required", JSONArray().put("title").put("date"))
            }
        )
    }

    private fun callGemini(apiKey: String, parts: JSONArray): String {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", responseSchema())
            )
        }

        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 90_000
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.use { out: OutputStream ->
                out.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            if (responseCode !in 200..299) {
                val errorMessage = runCatching {
                    JSONObject(responseText).getJSONObject("error").getString("message")
                }.getOrDefault(responseText)
                throw GeminiException("API 오류 ($responseCode): $errorMessage")
            }

            return responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun extractResponseText(responseText: String): String {
        val candidates = JSONObject(responseText).optJSONArray("candidates")
            ?: throw GeminiException("응답이 비어 있어요.")
        if (candidates.length() == 0) throw GeminiException("응답이 비어 있어요.")

        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw GeminiException("응답에서 내용을 찾지 못했어요.")

        return buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text"))
            }
        }
    }

    private fun parseEvents(rawText: String): List<ExtractedEvent> {
        val jsonText = extractJsonArray(rawText)
            ?: throw GeminiException("응답에서 일정 정보를 찾지 못했어요.")

        val array = JSONArray(jsonText)
        val events = mutableListOf<ExtractedEvent>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val title = obj.optString("title").trim()
            val dateStr = obj.optString("date").trim()
            if (title.isEmpty() || dateStr.isEmpty()) continue

            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            val endDate = obj.optString("end_date").takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val time = obj.optString("time").takeIf { it.isNotBlank() && it != "null" }
            val note = obj.optString("note").takeIf { it.isNotBlank() && it != "null" }

            events.add(ExtractedEvent(title = title, date = date, endDate = endDate, time = time, note = note))
        }
        return events
    }

    /** 모델이 설명 문구나 마크다운 코드펜스를 덧붙인 경우를 대비해 JSON 배열 부분만 추출한다. */
    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }
}
