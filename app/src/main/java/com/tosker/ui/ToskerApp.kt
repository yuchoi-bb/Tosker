package com.tosker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tosker.update.UpdateInfo
import com.tosker.viewmodel.AuthState
import com.tosker.viewmodel.ToskerViewModel

@Composable
fun ToskerApp(
    viewModel: ToskerViewModel,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onStartVoice: () -> Unit,
    onScanDocument: () -> Unit,
    onUpdateClick: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState.authState) {
        is AuthState.Loading -> LoadingDialog(onDismiss = onDismiss)

        is AuthState.LoggedOut -> LoginDialog(
            errorMessage = uiState.authError,
            onSignInClick = onSignInClick,
            onDismiss = onDismiss
        )

        is AuthState.LoggedIn -> ToskerDialog(
            uiState = uiState,
            onTaskListSelect = viewModel::selectTaskList,
            onTaskTextChange = viewModel::updateTaskText,
            onDateChange = viewModel::updateDate,
            onUpload = viewModel::uploadTask,
            onClearError = viewModel::clearError,
            onSignOut = onSignOutClick,
            onStartVoice = onStartVoice,
            onStartDocumentScan = viewModel::startDocumentScan,
            onPickDocumentImage = onScanDocument,
            onShowDocumentTextInput = viewModel::showDocumentTextInput,
            onAnalyzeDocumentText = viewModel::analyzeDocumentText,
            onUpdateClick = onUpdateClick,
            onConfigChange = viewModel::updateListConfig,
            initialApiKey = viewModel.loadGeminiApiKey(),
            onApiKeySave = viewModel::saveGeminiApiKey,
            onReviewItemChange = viewModel::updateReviewItem,
            onConfirmDocumentUpload = viewModel::confirmDocumentUpload,
            onDismissDocumentScan = viewModel::dismissDocumentScan,
            onDismiss = onDismiss
        )
    }
}
