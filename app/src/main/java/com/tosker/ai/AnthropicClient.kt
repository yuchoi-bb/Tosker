package com.tosker.ai

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Anthropic Messages API(Claude Vision)로 사진 속 문서에서 날짜 기반 일정을 추출한다. */
object AnthropicClient {

    private const val API_URL = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-sonnet-5"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    class AnthropicException(message: String) : Exception(message)

    fun extractEvents(apiKey: String, imageBytes: ByteArray, mimeType: String): List<ExtractedEvent> {
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val prompt = """
            오늘 날짜는 $today 입니다. 첨부된 사진은 학사일정표, 공지문, 안내문 등
            날짜가 포함된 문서입니다. 이 문서에서 날짜(또는 날짜 범위)가 있는 모든
            일정을 찾아서 JSON 배열로만 응답하세요. 다른 설명, 마크다운 코드블록,
            인사말 없이 순수 JSON 배열만 출력하세요.

            각 항목의 형식:
            {
              "title": "일정 이름 (한국어, 간결하게)",
              "date": "YYYY-MM-DD",
              "end_date": "YYYY-MM-DD 또는 null (여름방학처럼 기간이 있는 경우에만 채움)",
              "time": "HH:mm(24시간) 또는 null (특정 시각이 명시되지 않으면 null, 종일 일정)",
              "note": "급식 미실시 같은 비고사항, 없으면 null"
            }

            연도가 명시되지 않은 날짜는 문서의 다른 날짜들과 문맥, 그리고 오늘 날짜를
            참고해 합리적으로 추정하세요. 표의 각 행이 하나의 일정입니다. "예시"라고
            명확히 표시된 항목은 실제 일정이 아니므로 제외하세요.
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 4096)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            JSONArray()
                                .put(
                                    JSONObject().apply {
                                        put("type", "image")
                                        put(
                                            "source",
                                            JSONObject().apply {
                                                put("type", "base64")
                                                put("media_type", mimeType)
                                                put("data", base64Image)
                                            }
                                        )
                                    }
                                )
                                .put(
                                    JSONObject().apply {
                                        put("type", "text")
                                        put("text", prompt)
                                    }
                                )
                        )
                    }
                )
            )
        }

        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            setRequestProperty("content-type", "application/json")
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
                throw AnthropicException("API 오류 ($responseCode): $errorMessage")
            }

            val responseJson = JSONObject(responseText)
            val contentArray = responseJson.getJSONArray("content")
            val text = buildString {
                for (i in 0 until contentArray.length()) {
                    val block = contentArray.getJSONObject(i)
                    if (block.optString("type") == "text") append(block.optString("text"))
                }
            }

            return parseEvents(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEvents(rawText: String): List<ExtractedEvent> {
        val jsonText = extractJsonArray(rawText)
            ?: throw AnthropicException("응답에서 일정 정보를 찾지 못했어요.")

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
