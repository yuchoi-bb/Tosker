package com.tosker.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import com.tosker.settings.ListConfig
import com.tosker.settings.SettingsStore
import com.tosker.update.UpdateChecker
import com.tosker.update.UpdateInfo
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
    val uploadState: UploadState = UploadState.Idle,
    val updateInfo: UpdateInfo? = null,
    val listConfigs: Map<String, ListConfig> = emptyMap()
)

class ToskerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(
        UiState(listConfigs = settingsStore.loadConfigs())
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var tasksService: Tasks? = null

    /** 설정 화면에서 목록 버튼의 이름/색상을 변경하고 영구 저장 */
    fun updateListConfig(id: String, label: String, colorHex: Long) {
        settingsStore.saveConfig(id, ListConfig(label, colorHex))
        _uiState.update { it.copy(listConfigs = settingsStore.loadConfigs()) }
    }

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
        _uiState.update {
            UiState(
                authState = AuthState.LoggedOut,
                listConfigs = it.listConfigs
            )
        }
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

    /** GitHub 최신 릴리즈를 확인하여 더 높은 버전이 있으면 updateInfo 설정 */
    fun checkForUpdate(currentVersionName: String) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { UpdateChecker.fetchIfUpdateAvailable(currentVersionName) }
                    .getOrNull()
            }
            if (info != null) {
                _uiState.update { it.copy(updateInfo = info) }
            }
        }
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
