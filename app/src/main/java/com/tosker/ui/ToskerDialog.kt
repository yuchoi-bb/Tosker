package com.tosker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tosker.R
import com.tosker.viewmodel.TaskListItem
import com.tosker.viewmodel.UiState
import com.tosker.viewmodel.UploadState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private data class CategoryDef(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

// 순서대로 P=개인(0번), W=업무(1번), S=운동(2번), R=반복(3번) 목록에 매핑
private val categories = listOf(
    CategoryDef("P", Icons.Default.Person,       Color(0xFFF4788A)),
    CategoryDef("W", Icons.Default.Work,          Color(0xFF9B97D3)),
    CategoryDef("S", Icons.Default.FitnessCenter, Color(0xFF7BB8D4)),
    CategoryDef("R", Icons.Default.Refresh,       Color(0xFFB5B0CC))
)

// 일(Sun)부터 시작하는 무지개 색상 요일 정의
private data class DayDef(val label: String, val dow: DayOfWeek, val color: Color)

private val days = listOf(
    DayDef("일", DayOfWeek.SUNDAY,    Color(0xFFFF5252)),
    DayDef("월", DayOfWeek.MONDAY,    Color(0xFFFF8C00)),
    DayDef("화", DayOfWeek.TUESDAY,   Color(0xFFFFD600)),
    DayDef("수", DayOfWeek.WEDNESDAY, Color(0xFF4CAF50)),
    DayDef("목", DayOfWeek.THURSDAY,  Color(0xFF2196F3)),
    DayDef("금", DayOfWeek.FRIDAY,    Color(0xFF3F51B5)),
    DayDef("토", DayOfWeek.SATURDAY,  Color(0xFF9C27B0))
)

// 오늘 기준으로 해당 요일의 가장 가까운 날짜 반환 (오늘 포함 ~ 6일 후)
private fun nextDateForDow(dow: DayOfWeek): LocalDate {
    val today = LocalDate.now()
    val daysUntil = ((dow.value - today.dayOfWeek.value + 7) % 7).toLong()
    return today.plusDays(daysUntil)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToskerDialog(
    uiState: UiState,
    onTaskListSelect: (TaskListItem) -> Unit,
    onTaskTextChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onUpload: () -> Unit,
    onClearError: () -> Unit,
    onSignOut: () -> Unit,
    onStartVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tosker", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 설정 버튼 (로그아웃 메뉴 포함)
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "설정",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_out)) },
                                onClick = {
                                    showSettingsMenu = false
                                    onSignOut()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Logout, contentDescription = null)
                                }
                            )
                        }
                    }
                    // 닫기 버튼 (최우측)
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 아래로 스와이프(제스처) 시 음성 입력 실행
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                if (totalDrag > 80f) {
                                    focusManager.clearFocus()
                                    onStartVoice()
                                }
                            }
                        ) { _, dragAmount ->
                            if (dragAmount > 0) totalDrag += dragAmount
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 카테고리 동그라미 버튼 - 가로 1줄
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    categories.forEachIndexed { index, cat ->
                        val taskList = uiState.taskLists.getOrNull(index)
                        val isSelected = taskList != null &&
                                uiState.selectedTaskList?.id == taskList.id
                        val enabled = taskList != null

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = enabled) { taskList?.let(onTaskListSelect) }
                                .padding(4.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) cat.color
                                        else cat.color.copy(alpha = 0.18f)
                                    )
                            ) {
                                Icon(
                                    cat.icon,
                                    contentDescription = cat.label,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (isSelected) Color.White
                                           else cat.color.copy(alpha = if (enabled) 0.7f else 0.3f)
                                )
                            }
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) cat.color
                                        else MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (enabled) 0.5f else 0.25f
                                        )
                            )
                        }
                    }
                }

                // 할 일 텍스트 입력 + 음성 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.taskText,
                        onValueChange = onTaskTextChange,
                        label = { Text(stringResource(R.string.task_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            onUpload()
                        }),
                        enabled = uiState.uploadState !is UploadState.Loading
                    )
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onStartVoice()
                        },
                        enabled = uiState.uploadState !is UploadState.Loading
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "음성 입력",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 요일 선택 무지개 동그라미 (날짜 위)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    days.forEach { day ->
                        val isSelected = uiState.selectedDate.dayOfWeek == day.dow
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) day.color else day.color.copy(alpha = 0.15f)
                                )
                                .clickable {
                                    onDateChange(nextDateForDow(day.dow))
                                }
                        ) {
                            Text(
                                text = day.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White
                                        else day.color
                            )
                        }
                    }
                }

                // 날짜 표시 + 캘린더 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "날짜 선택",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 업로드 상태 메시지
                when (val state = uiState.uploadState) {
                    is UploadState.Success -> Text(
                        text = stringResource(R.string.upload_success),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    is UploadState.Error -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) { Text("닫기") }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onUpload()
                },
                enabled = uiState.taskText.isNotBlank()
                        && uiState.selectedTaskList != null
                        && uiState.uploadState !is UploadState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.uploadState is UploadState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.upload))
                }
            }
        },
        dismissButton = null
    )

    // 날짜 선택 다이얼로그
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atOffset(ZoneOffset.UTC)
                            .toLocalDate()
                        onDateChange(date)
                    }
                    showDatePicker = false
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
