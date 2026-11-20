package com.tosker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tosker.settings.CategoryDefaults
import com.tosker.settings.ShortcutConfig
import com.tosker.viewmodel.TaskListItem

private const val SHORTCUT_SLOTS = 10
private const val SHORTCUT_COLUMNS = 5

/**
 * Upload 버튼 아래 2행 × 5열 단축버튼.
 * 탭하면 저장된 할일이 선택된 날짜에 완료 상태로 등록되고,
 * 길게 누르면 수정/삭제 창이 열린다. 빈 칸은 탭하면 등록 창이 열린다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutGrid(
    shortcuts: Map<Int, ShortcutConfig>,
    enabled: Boolean,
    onRun: (slot: Int) -> Unit,
    onEdit: (slot: Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until SHORTCUT_SLOTS).chunked(SHORTCUT_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { slot ->
                    val config = shortcuts[slot]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                enabled = enabled,
                                onClick = {
                                    if (config == null) onEdit(slot) else onRun(slot)
                                },
                                onLongClick = { onEdit(slot) }
                            )
                            .padding(vertical = 2.dp)
                    ) {
                        if (config == null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "단축버튼 추가",
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                        } else {
                            val color = Color(config.colorHex)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color.copy(alpha = 0.18f))
                            ) {
                                Icon(
                                    ShortcutIcons.icon(config.iconKey),
                                    contentDescription = config.title,
                                    tint = color,
                                    modifier = Modifier.size(22.dp)
                                )
                                // 해야 할 일 버튼은 우상단에 작은 원 표시로 구분
                                if (!config.markCompleted) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(7.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(color)
                                    )
                                }
                            }
                            Text(
                                text = config.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 단축버튼 등록/수정 창 */
@Composable
fun ShortcutEditDialog(
    slot: Int,
    existing: ShortcutConfig?,
    taskLists: List<TaskListItem>,
    defaultListId: String?,
    onSave: (title: String, iconKey: String, colorHex: Long, listId: String?, markCompleted: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(slot) { mutableStateOf(existing?.title.orEmpty()) }
    var iconKey by remember(slot) { mutableStateOf(existing?.iconKey ?: ShortcutIcons.defaultKey) }
    var colorHex by remember(slot) {
        mutableStateOf(existing?.colorHex ?: CategoryDefaults.palette.first())
    }
    var listId by remember(slot) { mutableStateOf(existing?.listId ?: defaultListId) }
    var markCompleted by remember(slot) { mutableStateOf(existing?.markCompleted ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) "단축버튼 추가" else "단축버튼 수정",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("할 일 내용") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("버튼을 누르면")
                ActionTypePicker(
                    markCompleted = markCompleted,
                    onSelect = { markCompleted = it }
                )

                SectionLabel("등록할 목록")
                ListPicker(
                    taskLists = taskLists,
                    selectedId = listId,
                    onSelect = { listId = it }
                )

                SectionLabel("색상")
                ColorPicker(selected = colorHex, onSelect = { colorHex = it })

                SectionLabel("아이콘")
                IconPicker(
                    selectedKey = iconKey,
                    tint = Color(colorHex),
                    onSelect = { iconKey = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, iconKey, colorHex, listId, markCompleted) },
                enabled = title.isNotBlank() && listId != null
            ) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.size(2.dp))
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline
    )
}

/** "한 일 기록(완료)" / "해야 할 일(미완료)" 선택 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTypePicker(
    markCompleted: Boolean,
    onSelect: (Boolean) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = markCompleted,
            onClick = { onSelect(true) },
            leadingIcon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text("한 일 기록", style = MaterialTheme.typography.labelSmall)
            },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = !markCompleted,
            onClick = { onSelect(false) },
            leadingIcon = {
                Icon(
                    Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text("해야 할 일", style = MaterialTheme.typography.labelSmall)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPicker(
    taskLists: List<TaskListItem>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        taskLists.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEach { list ->
                    FilterChip(
                        selected = selectedId == list.id,
                        onClick = { onSelect(list.id) },
                        label = {
                            Text(
                                list.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryDefaults.palette.forEach { hex ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(hex))
                    .border(
                        width = if (hex == selected) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun IconPicker(
    selectedKey: String,
    tint: Color,
    onSelect: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.heightIn(max = 200.dp)
    ) {
        ShortcutIcons.groups.forEach { (groupName, entries) ->
            item(key = "h_$groupName") {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            entries.chunked(6).forEachIndexed { rowIndex, row ->
                item(key = "$groupName-$rowIndex") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { entry ->
                            val isSelected = entry.key == selectedKey
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) tint.copy(alpha = 0.25f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = tint,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelect(entry.key) }
                            ) {
                                Icon(
                                    entry.icon,
                                    contentDescription = entry.key,
                                    tint = if (isSelected) tint
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
