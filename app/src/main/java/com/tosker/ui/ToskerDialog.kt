package com.tosker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tosker.R
import com.tosker.viewmodel.TaskListItem
import com.tosker.viewmodel.UiState
import com.tosker.viewmodel.UploadState
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
    CategoryDef("P", Icons.Default.Person,    Color(0xFFF4788A)),
    CategoryDef("W", Icons.Default.Work,      Color(0xFF9B97D3)),
    CategoryDef("S", Icons.Default.FitnessCenter, Color(0xFF7BB8D4)),
    CategoryDef("R", Icons.Default.Refresh,   Color(0xFFB5B0CC))
)

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
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tosker", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onSignOut) {
                    Text(
                        text = stringResource(R.string.sign_out),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
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

                // 할 일 텍스트 입력
                OutlinedTextField(
                    value = uiState.taskText,
                    onValueChange = onTaskTextChange,
                    label = { Text(stringResource(R.string.task_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onUpload()
                    }),
                    enabled = uiState.uploadState !is UploadState.Loading
                )

                // 날짜 선택 행
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
