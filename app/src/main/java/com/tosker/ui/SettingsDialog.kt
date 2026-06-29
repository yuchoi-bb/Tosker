package com.tosker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tosker.settings.CategoryDefaults
import com.tosker.settings.ListConfig
import com.tosker.viewmodel.TaskListItem
import com.tosker.viewmodel.UiState

/**
 * 설정 창. 첫 번째 기능: Google Tasks에 추가된 목록들을 보여주고
 * 각 목록의 버튼 색상과 표시 이름을 설정할 수 있게 한다.
 */
@Composable
fun SettingsDialog(
    uiState: UiState,
    onConfigChange: (id: String, label: String, colorHex: Long) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정", style = MaterialTheme.typography.titleLarge) },
        text = {
            if (uiState.taskLists.isEmpty()) {
                Text(
                    "목록을 불러오는 중이거나 목록이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "버튼 이름과 색상 설정",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(uiState.taskLists, key = { it.id }) { list ->
                            val index = uiState.taskLists.indexOf(list)
                            ListConfigRow(
                                list = list,
                                config = uiState.listConfigs[list.id]
                                    ?: ListConfig(
                                        label = CategoryDefaults.defaultLabel(index, list.title),
                                        colorHex = CategoryDefaults.defaultColor(index)
                                    ),
                                onConfigChange = onConfigChange
                            )
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
    config: ListConfig,
    onConfigChange: (id: String, label: String, colorHex: Long) -> Unit
) {
    var label by remember(list.id) { mutableStateOf(config.label) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 원본 Tasks 목록 이름
        Text(
            text = list.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

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
