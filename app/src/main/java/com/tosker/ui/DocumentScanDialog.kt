package com.tosker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tosker.viewmodel.DocumentScanState
import com.tosker.viewmodel.ReviewableEvent
import com.tosker.viewmodel.UploadDestination
import java.time.format.DateTimeFormatter

/**
 * 사진 또는 붙여넣은 텍스트에서 추출한 일정을 검토하고, 캘린더/태스크 중 어디에
 * 올릴지 고른 뒤 업로드하는 화면. [DocumentScanState]에 따라 단계를 표시한다.
 */
@Composable
fun DocumentScanDialog(
    state: DocumentScanState,
    taskListLabel: String?,
    onPickImage: () -> Unit,
    onShowTextInput: () -> Unit,
    onAnalyzeText: (String) -> Unit,
    onItemChange: (index: Int, included: Boolean?, destination: UploadDestination?, title: String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is DocumentScanState.Idle) return

    var documentText by remember(state is DocumentScanState.TextInput) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("문서에서 일정 가져오기", style = MaterialTheme.typography.titleLarge) },
        text = {
            when (state) {
                is DocumentScanState.Choosing -> SourceChooser(
                    onPickImage = onPickImage,
                    onShowTextInput = onShowTextInput
                )
                is DocumentScanState.TextInput -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "문서에서 옮긴 텍스트를 붙여넣으면 날짜와 일정을 찾아드려요.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = documentText,
                        onValueChange = { documentText = it },
                        label = { Text("예: 10월 9일(3시) 보강, 14일(3시) 보강") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 280.dp)
                    )
                }
                is DocumentScanState.Analyzing -> ScanProgressRow("내용을 분석하고 있어요…")
                is DocumentScanState.Uploading -> ScanProgressRow("업로드하고 있어요…")
                is DocumentScanState.Done -> Text(
                    if (state.failCount == 0) "${state.successCount}개 일정을 등록했어요 ✓"
                    else "${state.successCount}개 성공, ${state.failCount}개 실패했어요."
                )
                is DocumentScanState.Error -> Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error
                )
                is DocumentScanState.Review -> ReviewList(
                    items = state.items,
                    taskListLabel = taskListLabel,
                    onItemChange = onItemChange
                )
                is DocumentScanState.Idle -> {}
            }
        },
        confirmButton = {
            when (state) {
                is DocumentScanState.Review -> Button(
                    onClick = onConfirm,
                    enabled = state.items.any { it.included }
                ) {
                    Text("업로드 (${state.items.count { it.included }}개)")
                }
                is DocumentScanState.TextInput -> Button(
                    onClick = { onAnalyzeText(documentText) },
                    enabled = documentText.isNotBlank()
                ) {
                    Text("분석")
                }
                else -> {}
            }
        },
        dismissButton = {
            if (state !is DocumentScanState.Uploading) {
                TextButton(onClick = onDismiss) {
                    Text(
                        when (state) {
                            is DocumentScanState.Review, is DocumentScanState.TextInput -> "취소"
                            else -> "닫기"
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun SourceChooser(onPickImage: () -> Unit, onShowTextInput: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "학사일정표나 안내문에서 일정을 한 번에 가져옵니다.",
            style = MaterialTheme.typography.bodyMedium
        )
        FilledTonalButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Text("📷  사진에서 가져오기")
        }
        FilledTonalButton(onClick = onShowTextInput, modifier = Modifier.fillMaxWidth()) {
            Text("📝  텍스트 붙여넣기")
        }
    }
}

@Composable
private fun ScanProgressRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label)
    }
}

@Composable
private fun ReviewList(
    items: List<ReviewableEvent>,
    taskListLabel: String?,
    onItemChange: (index: Int, included: Boolean?, destination: UploadDestination?, title: String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "태스크로 올리면 \"${taskListLabel ?: "선택된 목록 없음"}\" 목록에 추가돼요.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            items(items.size) { index ->
                ReviewRow(
                    item = items[index],
                    onIncludedChange = { onItemChange(index, it, null, null) },
                    onDestinationChange = { onItemChange(index, null, it, null) },
                    onTitleChange = { onItemChange(index, null, null, it) }
                )
            }
        }
    }
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@Composable
private fun ReviewRow(
    item: ReviewableEvent,
    onIncludedChange: (Boolean) -> Unit,
    onDestinationChange: (UploadDestination) -> Unit,
    onTitleChange: (String) -> Unit
) {
    var title by remember(item.event) { mutableStateOf(item.event.title) }

    val dateLabel = buildString {
        append(item.event.date.format(dateFormatter))
        item.event.endDate?.let { append(" ~ ${it.format(dateFormatter)}") }
        item.event.time?.let { append(" $it") }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = item.included, onCheckedChange = onIncludedChange)
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onTitleChange(it)
                    },
                    singleLine = true,
                    enabled = item.included,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        if (item.included) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DestinationOption("캘린더", item.destination == UploadDestination.CALENDAR) {
                    onDestinationChange(UploadDestination.CALENDAR)
                }
                DestinationOption("태스크", item.destination == UploadDestination.TASK) {
                    onDestinationChange(UploadDestination.TASK)
                }
                DestinationOption("둘 다", item.destination == UploadDestination.BOTH) {
                    onDestinationChange(UploadDestination.BOTH)
                }
            }
        }
        Divider()
    }
}

@Composable
private fun DestinationOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    )
}
