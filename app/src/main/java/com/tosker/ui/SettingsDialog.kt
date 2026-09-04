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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tosker.settings.CategoryDefaults
import com.tosker.settings.ListConfig
import com.tosker.viewmodel.TaskListItem
import com.tosker.viewmodel.UiState

/**
 * 설정 창. Google Tasks 목록별 버튼 이름/색상 설정과,
 * 문서 스캔 기능(Claude API)에 사용할 Anthropic API 키 설정을 제공한다.
 */
@Composable
fun SettingsDialog(
    uiState: UiState,
    initialApiKey: String,
    onConfigChange: (id: String, label: String, colorHex: Long) -> Unit,
    onApiKeySave: (String) -> Unit,
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
                        "문서 스캔용 Anthropic API 키",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            onApiKeySave(it)
                        },
                        label = { Text("sk-ant-...") },
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
                        "console.anthropic.com 에서 발급받은 키를 입력하면, 사진 문서를 분석해 " +
                            "일정을 자동으로 추출할 수 있어요. 키는 기기에 암호화되어 저장됩니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
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
                            "버튼 이름과 색상 설정",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.heightIn(max = 280.dp)
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
