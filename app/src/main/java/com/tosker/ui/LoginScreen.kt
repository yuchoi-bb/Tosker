package com.tosker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tosker.R

@Composable
fun LoginDialog(
    errorMessage: String? = null,
    onSignInClick: () -> Unit,
    onDismiss: () -> Unit
) {
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
                    Text(
                        text = "로그인 실패: $errorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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
