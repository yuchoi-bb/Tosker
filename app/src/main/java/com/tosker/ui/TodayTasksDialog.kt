package com.tosker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tosker.viewmodel.TodayTaskItem
import com.tosker.viewmodel.TodayTasksState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 우→좌 스와이프로 열리는 "오늘 할일" 화면.
 * 오늘 마감이거나 마감일이 지난 미완료 할일을 목록별로 묶어 보여주고,
 * 선택한 항목을 2day 목록으로 옮긴다(원본 삭제, 마감일은 오늘로).
 */
@Composable
fun TodayTasksDialog(
    state: TodayTasksState,
    todayListTitle: String?,
    onToggle: (taskId: String, listId: String) -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is TodayTasksState.Hidden) return

    // null = 전체 보기. 특정 목록만 보고 싶을 때 목록 id를 담는다.
    var filterListId by remember { mutableStateOf<String?>(null) }

    val allItems = (state as? TodayTasksState.Loaded)?.items.orEmpty()
    val selectedCount = allItems.count { it.selected }
    val visibleItems = filterListId
        ?.let { id -> allItems.filter { it.listId == id } }
        ?: allItems

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("오늘 할일", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "선택한 항목을 ${todayListTitle ?: "2day"}(으)로 옮깁니다",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        text = {
            when (state) {
                is TodayTasksState.Loading -> LoadingRow("할일을 불러오는 중...")
                is TodayTasksState.Moving -> LoadingRow("옮기는 중...")

                is TodayTasksState.Error -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )

                is TodayTasksState.Loaded -> {
                    if (allItems.isEmpty()) {
                        Text(
                            "오늘 마감이거나 기한이 지난 할일이 없습니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ListFilterRow(
                                items = allItems,
                                selectedListId = filterListId,
                                onSelect = { filterListId = it }
                            )

                            val today = LocalDate.now()
                            val countByList = visibleItems.groupingBy { it.listId }.eachCount()
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.heightIn(max = 340.dp)
                            ) {
                                itemsIndexed(
                                    visibleItems,
                                    key = { _, it -> "${it.listId}:${it.taskId}" }
                                ) { idx, item ->
                                    // 목록이 바뀌는 지점에 목록 이름 머리글을 넣는다.
                                    val isFirstOfList = idx == 0 ||
                                            visibleItems[idx - 1].listId != item.listId
                                    if (isFirstOfList) {
                                        ListGroupHeader(
                                            title = item.listTitle,
                                            count = countByList[item.listId] ?: 0,
                                            isFirst = idx == 0
                                        )
                                    }
                                    TodayTaskRow(
                                        item = item,
                                        today = today,
                                        onToggle = { onToggle(item.taskId, item.listId) }
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {}
            }
        },
        confirmButton = {
            Button(
                onClick = onMove,
                enabled = selectedCount > 0 && state !is TodayTasksState.Moving
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    if (selectedCount > 0) "${selectedCount}개 옮기기" else "옮기기"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

/** 상단 목록 필터 칩. "전체" + 항목이 있는 목록들 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListFilterRow(
    items: List<TodayTaskItem>,
    selectedListId: String?,
    onSelect: (String?) -> Unit
) {
    // 항목 순서(= 버튼 순서)를 유지한 채 목록만 추려낸다.
    val lists = items
        .map { it.listId to it.listTitle }
        .distinct()

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        FilterChip(
            selected = selectedListId == null,
            onClick = { onSelect(null) },
            label = { Text("전체 ${items.size}") }
        )
        lists.forEach { (id, title) ->
            val count = items.count { it.listId == id }
            FilterChip(
                selected = selectedListId == id,
                onClick = { onSelect(if (selectedListId == id) null else id) },
                label = { Text("$title $count") }
            )
        }
    }
}

@Composable
private fun ListGroupHeader(title: String, count: Int, isFirst: Boolean) {
    Column {
        if (!isFirst) Spacer(Modifier.size(10.dp))
        Text(
            text = "$title · ${count}개",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Divider()
    }
}

@Composable
private fun LoadingRow(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TodayTaskRow(
    item: TodayTaskItem,
    today: LocalDate,
    onToggle: () -> Unit
) {
    val overdue = item.isOverdue(today)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp)
    ) {
        Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.due.format(DateTimeFormatter.ofPattern("M/d")),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline
                )
                if (overdue) {
                    Text(
                        text = "기한 지남",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
