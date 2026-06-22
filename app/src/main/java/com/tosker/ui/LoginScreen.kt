package com.tosker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tosker.R

@Composable
fun LoginDialog(
    errorMessage: String? = null,
    onSignInClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tosker") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Google 계정으로 로그인하여\nGoogle Tasks와 연동하세요.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (errorMessage != null) {
                    val fullError = "로그인 실패: $errorMessage"
                    // 롱프레스로 직접 선택/복사 가능
                    SelectionContainer {
                        Text(
                            text = fullError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    // 원터치 복사 버튼 (캡처 없이 텍스트로 공유)
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(fullError))
                    }) {
                        Text("오류 복사")
                    }
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSignInClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sign_in_with_google))
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("닫기")
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun LoadingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tosker") },
        text = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                CircularProgressIndicator()
            }
        },
        confirmButton = {}
    )
}
