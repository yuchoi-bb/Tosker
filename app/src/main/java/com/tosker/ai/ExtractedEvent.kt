package com.tosker.ai

import java.time.LocalDate

/**
 * Claude가 사진 속 문서(학사일정 등)에서 추출한 날짜 기반 일정 하나.
 * [endDate]가 null이 아니면 [date] ~ [endDate] 범위의 여러 날짜에 걸친 일정(예: 방학).
 */
data class ExtractedEvent(
    val title: String,
    val date: LocalDate,
    val endDate: LocalDate? = null,
    val time: String? = null, // "HH:mm" 형식, 없으면 종일 일정
    val note: String? = null
) {
    val isAllDay: Boolean get() = time == null
}
