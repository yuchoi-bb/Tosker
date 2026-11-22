package com.tosker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tosker.settings.CategoryDefaults
import com.tosker.settings.ListConfig
import com.tosker.viewmodel.TaskListItem
import com.tosker.viewmodel.UiState
import com.tosker.viewmodel.SyncState
import com.tosker.viewmodel.orderedTaskLists

/**
 * 설정 창. Google Tasks 목록별 버튼 이름/색상 설정과,
 * 문서 스캔 기능(Gemini API)에 사용할 Gemini API 키 설정을 제공한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    uiState: UiState,
    initialApiKey: String,
    onConfigChange: (id: String, label: String, colorHex: Long) -> Unit,
    onMoveList: (id: String, up: Boolean) -> Unit,
    onApiKeySave: (String) -> Unit,
    onSyncNow: () -> Unit,
    onHabitListChange: (String?) -> Unit,
    onJournalListChange: (String?) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "문서 스캔용 Gemini API 키",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            onApiKeySave(it)
                        },
                        label = { Text("AIza...") },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    if (apiKeyVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = "표시 전환"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "aistudio.google.com/apikey 에서 발급받은 키를 입력하면, 사진 문서를 분석해 " +
                            "일정을 자동으로 추출할 수 있어요. 키는 기기에 암호화되어 저장됩니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Divider()

                // 기기 간 설정 동기화 상태
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "기기 간 설정 동기화",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        TextButton(
                            onClick = onSyncNow,
                            enabled = uiState.syncState !is SyncState.Syncing
                        ) { Text("지금 동기화") }
                    }
                    when (val sync = uiState.syncState) {
                        is SyncState.Syncing -> Text(
                            "동기화 중...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        is SyncState.Synced -> Text(
                            "동기화됨 · ${
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                                    .format(java.util.Date(sync.at))
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is SyncState.Error -> Text(
                            sync.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text(
                            "단축버튼·버튼 설정이 Google 계정에 저장되어 다른 기기에서도 쓰입니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Divider()

                // 습관 격자에 쓸 목록 지정
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "습관 목록",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "이 목록의 마감일 없는 할일이 습관이 되고, 완료 기록이 격자에 X로 표시됩니다. " +
                                "메인 화면에서 왼쪽→오른쪽으로 쓸어 넘기면 격자가 열립니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.taskLists.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                pair.forEach { list ->
                                    FilterChip(
                                        selected = uiState.habitListId == list.id,
                                        onClick = {
                                            onHabitListChange(
                                                if (uiState.habitListId == list.id) null else list.id
                                            )
                                        },
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

                Divider()

                // 한 줄 일기를 담을 목록
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "일기 목록",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "습관 격자 아래에서 그날의 한 줄을 남길 수 있습니다. " +
                                "기록은 이 목록에 날짜별 할일로 저장됩니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.taskLists.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                pair.forEach { list ->
                                    FilterChip(
                                        selected = uiState.journalListId == list.id,
                                        onClick = {
                                            onJournalListChange(
                                                if (uiState.journalListId == list.id) null else list.id
                                            )
                                        },
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

                Divider()

                if (uiState.taskLists.isEmpty()) {
                    Text(
                        "목록을 불러오는 중이거나 목록이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "버튼 이름·색상·순서 설정",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "▲▼ 로 버튼 순서를 바꿀 수 있습니다. 맨 위가 최좌측 버튼입니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        val ordered = orderedTaskLists(uiState.taskLists, uiState.listConfigs)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            itemsIndexed(ordered, key = { _, entry -> entry.item.id }) { pos, entry ->
                                ListConfigRow(
                                    list = entry.item,
                                    position = pos,
                                    config = uiState.listConfigs[entry.item.id]
                                        ?: ListConfig(
                                            label = CategoryDefaults.defaultLabel(
                                                entry.apiIndex, entry.item.title
                                            ),
                                            colorHex = CategoryDefaults.defaultColor(entry.apiIndex)
                                        ),
                                    canMoveUp = pos > 0,
                                    canMoveDown = pos < ordered.lastIndex,
                                    onConfigChange = onConfigChange,
                                    onMoveList = onMoveList
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
        dismissButton = {
            TextButton(onClick = onSignOut) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text("로그아웃")
            }
        }
    )
}

@Composable
private fun ListConfigRow(
    list: TaskListItem,
    position: Int,
    config: ListConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onConfigChange: (id: String, label: String, colorHex: Long) -> Unit,
    onMoveList: (id: String, up: Boolean) -> Unit
) {
    var label by remember(list.id) { mutableStateOf(config.label) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 순서 번호 + 원본 Tasks 목록 이름 + 순서 이동 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${position + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = list.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onMoveList(list.id, true) },
                enabled = canMoveUp,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "위로 이동",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { onMoveList(list.id, false) },
                enabled = canMoveDown,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "아래로 이동",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 표시 이름 입력
        OutlinedTextField(
            value = label,
            onValueChange = {
                label = it
                onConfigChange(list.id, it, config.colorHex)
            },
            label = { Text("표시 이름") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 색상 선택 팔레트
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryDefaults.palette.forEach { colorHex ->
                val selected = colorHex == config.colorHex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(colorHex))
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .clickable {
                            onConfigChange(list.id, label, colorHex)
                        }
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "선택됨",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Divider()
    }
}
