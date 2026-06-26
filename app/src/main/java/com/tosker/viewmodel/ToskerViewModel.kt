package com.tosker.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val displayName: String, val email: String) : AuthState()
}

sealed class UploadState {
    object Idle : UploadState()
    object Loading : UploadState()
    object Success : UploadState()
    data class Error(val message: String) : UploadState()
}

data class TaskListItem(val id: String, val title: String)

data class UiState(
    val authState: AuthState = AuthState.Loading,
    val authError: String? = null,
    val taskLists: List<TaskListItem> = emptyList(),
    val selectedTaskList: TaskListItem? = null,
    val taskText: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val uploadState: UploadState = UploadState.Idle
)

class ToskerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var tasksService: Tasks? = null

    fun onSignInSuccess(account: GoogleSignInAccount, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(authState = AuthState.Loading, authError = null) }

            // Tasks API 서비스 초기화 (OAuth 액세스 토큰만 사용, Firebase 불필요)
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singleton(TasksScopes.TASKS)
            )
            credential.selectedAccount = account.account
            tasksService = Tasks.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Tosker").build()

            _uiState.update {
                it.copy(
                    authState = AuthState.LoggedIn(
                        displayName = account.displayName ?: "",
                        email = account.email ?: ""
                    ),
                    authError = null
                )
            }
            loadTaskLists()
        }
    }

    fun onSignInFailure(message: String) {
        _uiState.update { it.copy(authState = AuthState.LoggedOut, authError = message) }
    }

    fun setLoggedOut() {
        tasksService = null
        _uiState.update { UiState(authState = AuthState.LoggedOut) }
    }

    private fun loadTaskLists() {
        viewModelScope.launch {
            val lists = withContext(Dispatchers.IO) {
                runCatching {
                    tasksService?.tasklists()?.list()?.execute()?.items
                        ?.mapNotNull { tl ->
                            val id = tl.id ?: return@mapNotNull null
                            val title = tl.title ?: return@mapNotNull null
                            TaskListItem(id, title)
                        } ?: emptyList()
                }.getOrElse { emptyList() }
            }
            _uiState.update {
                it.copy(
                    taskLists = lists,
                    selectedTaskList = lists.firstOrNull()
                )
            }
        }
    }

    fun selectTaskList(item: TaskListItem) {
        _uiState.update { it.copy(selectedTaskList = item) }
    }

    fun updateTaskText(text: String) {
        _uiState.update { it.copy(taskText = text) }
    }

    fun updateDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun uploadTask() {
        val state = _uiState.value
        val taskListId = state.selectedTaskList?.id ?: return
        val taskText = state.taskText.trim().ifEmpty { return }

        viewModelScope.launch {
            _uiState.update { it.copy(uploadState = UploadState.Loading) }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dueRfc3339 = state.selectedDate
                        .atStartOfDay(ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

                    val task = Task().apply {
                        title = taskText
                        due = dueRfc3339
                    }
                    tasksService?.tasks()?.insert(taskListId, task)?.execute()
                }
            }

            if (result.isSuccess) {
                _uiState.update { it.copy(taskText = "", uploadState = UploadState.Success) }
                delay(1500)
                _uiState.update { it.copy(uploadState = UploadState.Idle) }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "업로드 실패"
                _uiState.update { it.copy(uploadState = UploadState.Error(msg)) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(uploadState = UploadState.Idle) }
    }

    fun appendVoiceText(text: String) {
        _uiState.update { state ->
            val newText = if (state.taskText.isBlank()) text else "${state.taskText} $text"
            state.copy(taskText = newText)
        }
    }

    fun onVoiceError(message: String) {
        _uiState.update { it.copy(uploadState = UploadState.Error(message)) }
    }
}
