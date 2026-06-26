package com.tosker.ui

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
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

private data class CategoryDef(val label: String, val icon: ImageVector)

private val categories = listOf(
    CategoryDef("개인", Icons.Default.Person),
    CategoryDef("업무", Icons.Default.Work),
    CategoryDef("운동", Icons.Default.FitnessCenter),
    CategoryDef("반복", Icons.Default.Refresh)
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
                // 카테고리 버튼 2x2
                categories.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { cat ->
                            val taskList = uiState.taskLists.find { it.title == cat.label }
                            val isSelected = uiState.selectedTaskList?.title == cat.label
                            FilterChip(
                                selected = isSelected,
                                onClick = { taskList?.let(onTaskListSelect) },
                                label = { Text(cat.label, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = {
                                    Icon(
                                        cat.icon,
                                        contentDescription = cat.label,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = taskList != null || uiState.taskLists.isEmpty()
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
