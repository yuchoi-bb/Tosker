package com.tosker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tosker.viewmodel.HabitGridState
import java.time.LocalDate
import java.time.YearMonth

private val LABEL_WIDTH = 86.dp
private val CELL_SIZE = 26.dp

/**
 * 월간 습관 격자.
 * 세로축은 습관 목록의 "마감일 없는 미완료 할일",
 * 가로축은 그달의 1~말일. 칸을 누르면 그 날짜 기록이 켜지고 꺼진다.
 */
@Composable
fun HabitGridDialog(
    state: HabitGridState,
    onToggle: (habitTitle: String, date: LocalDate) -> Unit,
    onChangeMonth: (delta: Long) -> Unit,
    onAddHabit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is HabitGridState.Hidden) return

    var showAddHabit by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 제목 + 닫기
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("습관 기록", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                when (state) {
                    is HabitGridState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text("불러오는 중...", style = MaterialTheme.typography.bodyMedium)
                    }

                    is HabitGridState.Error -> Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    is HabitGridState.Loaded -> {
                        MonthHeader(
                            month = state.month,
                            onChangeMonth = onChangeMonth
                        )

                        if (state.habits.isEmpty()) {
                            Text(
                                "습관이 없습니다. 아래 + 버튼으로 추가하거나 " +
                                        "Google Tasks의 습관 목록에 마감일 없는 할일을 만드세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            HabitGrid(state = state, onToggle = onToggle)
                        }

                        TextButton(onClick = { showAddHabit = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("습관 추가")
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    if (showAddHabit) {
        AddHabitDialog(
            onAdd = {
                onAddHabit(it)
                showAddHabit = false
            },
            onDismiss = { showAddHabit = false }
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onChangeMonth: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChangeMonth(-1) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
        }
        Text(
            text = "${month.year}. ${month.monthValue}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onChangeMonth(1) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
        }
    }
}

@Composable
private fun HabitGrid(
    state: HabitGridState.Loaded,
    onToggle: (habitTitle: String, date: LocalDate) -> Unit
) {
    val days = (1..state.month.lengthOfMonth()).map { state.month.atDay(it) }
    val today = LocalDate.now()
    val dayScroll = rememberScrollState()

    Row(modifier = Modifier.heightIn(max = 380.dp)) {
        // 왼쪽 고정 열: 습관 이름 + 달성률
        Column(modifier = Modifier.width(LABEL_WIDTH)) {
            // 날짜 머리글과 높이를 맞추기 위한 빈 칸
            Spacer(Modifier.height(CELL_SIZE))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                state.habits.forEach { habit ->
                    val done = days.count { state.marks.containsKey(habit.title to it) }
                    Column(
                        modifier = Modifier.height(CELL_SIZE),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$done/${days.size}",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // 오른쪽: 날짜 머리글 + X 격자 (가로 스크롤)
        Column(modifier = Modifier.horizontalScroll(dayScroll)) {
            Row {
                days.forEach { date ->
                    val isToday = date == today
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(CELL_SIZE)
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                state.habits.forEach { habit ->
                    Row {
                        days.forEach { date ->
                            val marked = state.marks.containsKey(habit.title to date)
                            val isToday = date == today
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(CELL_SIZE)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .border(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onToggle(habit.title, date) }
                            ) {
                                if (marked) {
                                    Text(
                                        text = "X",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddHabitDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("습관 추가") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("습관 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text("추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
