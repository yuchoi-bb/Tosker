package com.tosker.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import com.tosker.ai.ExtractedEvent
import com.tosker.ai.GeminiClient
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

/** 문서 스캔으로 추출된 일정 하나를 어디에 올릴지 선택하는 대상 */
enum class UploadDestination { CALENDAR, TASK, BOTH }

/** 리뷰 화면에서 사용자가 켜고 끄거나 수정할 수 있는, 추출된 일정 한 건 */
data class ReviewableEvent(
    val event: ExtractedEvent,
    val included: Boolean = true,
    val destination: UploadDestination = UploadDestination.CALENDAR
)

sealed class DocumentScanState {
    object Idle : DocumentScanState()

    /** 사진에서 가져올지, 텍스트를 붙여넣을지 고르는 단계 */
    object Choosing : DocumentScanState()

    /** 문서에서 옮겨온 텍스트를 직접 붙여넣는 단계 */
    object TextInput : DocumentScanState()

    object Analyzing : DocumentScanState()
    data class Review(val items: List<ReviewableEvent>) : DocumentScanState()
    object Uploading : DocumentScanState()
    data class Done(val successCount: Int, val failCount: Int) : DocumentScanState()
    data class Error(val message: String) : DocumentScanState()
}

data class UiState(
    val authState: AuthState = AuthState.Loading,
    val authError: String? = null,
    val taskLists: List<TaskListItem> = emptyList(),
    val selectedTaskList: TaskListItem? = null,
    val taskText: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val uploadState: UploadState = UploadState.Idle,
    val updateInfo: UpdateInfo? = null,
    val listConfigs: Map<String, ListConfig> = emptyMap(),
    val documentScanState: DocumentScanState = DocumentScanState.Idle
)

class ToskerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(
        UiState(listConfigs = settingsStore.loadConfigs())
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var tasksService: Tasks? = null
    private var calendarService: Calendar? = null

    /** 설정 화면에서 목록 버튼의 이름/색상을 변경하고 영구 저장 */
    fun updateListConfig(id: String, label: String, colorHex: Long) {
        settingsStore.saveConfig(id, ListConfig(label, colorHex))
        _uiState.update { it.copy(listConfigs = settingsStore.loadConfigs()) }
    }

    fun onSignInSuccess(account: GoogleSignInAccount, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(authState = AuthState.Loading, authError = null) }

            // Tasks + Calendar API 서비스 초기화 (OAuth 액세스 토큰만 사용, Firebase 불필요)
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                setOf(TasksScopes.TASKS, CalendarScopes.CALENDAR_EVENTS)
            )
            credential.selectedAccount = account.account
            tasksService = Tasks.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Tosker").build()
            calendarService = Calendar.Builder(
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
        calendarService = null
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

    // ---------------------------------------------------------------------
    // 문서 스캔 (사진 또는 붙여넣은 텍스트 속 날짜/일정을 Gemini로 추출해
    // 캘린더/태스크에 업로드)
    // ---------------------------------------------------------------------

    fun loadGeminiApiKey(): String = settingsStore.loadGeminiApiKey() ?: ""

    fun saveGeminiApiKey(apiKey: String) {
        settingsStore.saveGeminiApiKey(apiKey)
    }

    /** 문서 아이콘을 눌렀을 때: 사진 / 텍스트 중 무엇으로 가져올지 고르게 한다. */
    fun startDocumentScan() {
        _uiState.update { it.copy(documentScanState = DocumentScanState.Choosing) }
    }

    fun showDocumentTextInput() {
        _uiState.update { it.copy(documentScanState = DocumentScanState.TextInput) }
    }

    fun analyzeDocument(imageBytes: ByteArray, mimeType: String) {
        extractEvents { apiKey -> GeminiClient.extractEvents(apiKey, imageBytes, mimeType) }
    }

    /** 이미지에서 추출해 온 텍스트 등, 붙여넣은 문자열에서 일정을 뽑는다. */
    fun analyzeDocumentText(documentText: String) {
        val text = documentText.trim()
        if (text.isEmpty()) return
        extractEvents { apiKey -> GeminiClient.extractEventsFromText(apiKey, text) }
    }

    private fun extractEvents(extract: (apiKey: String) -> List<ExtractedEvent>) {
        val apiKey = settingsStore.loadGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    documentScanState = DocumentScanState.Error(
                        "설정에서 Gemini API 키를 먼저 입력해주세요."
                    )
                )
            }
            return
        }

        _uiState.update { it.copy(documentScanState = DocumentScanState.Analyzing) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { extract(apiKey) } }

            result.onSuccess { events ->
                if (events.isEmpty()) {
                    _uiState.update {
                        it.copy(documentScanState = DocumentScanState.Error("문서에서 날짜가 있는 일정을 찾지 못했어요."))
                    }
                } else {
                    _uiState.update {
                        it.copy(documentScanState = DocumentScanState.Review(events.map { e -> ReviewableEvent(e) }))
                    }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(documentScanState = DocumentScanState.Error(e.message ?: "문서 분석 실패"))
                }
            }
        }
    }

    fun updateReviewItem(
        index: Int,
        included: Boolean? = null,
        destination: UploadDestination? = null,
        title: String? = null
    ) {
        val current = _uiState.value.documentScanState
        if (current !is DocumentScanState.Review) return

        val updatedItems = current.items.mapIndexed { i, item ->
            if (i != index) return@mapIndexed item
            item.copy(
                included = included ?: item.included,
                destination = destination ?: item.destination,
                event = if (title != null) item.event.copy(title = title) else item.event
            )
        }
        _uiState.update { it.copy(documentScanState = DocumentScanState.Review(updatedItems)) }
    }

    fun dismissDocumentScan() {
        _uiState.update { it.copy(documentScanState = DocumentScanState.Idle) }
    }

    fun onDocumentPickError(message: String) {
        _uiState.update { it.copy(documentScanState = DocumentScanState.Error(message)) }
    }

    fun confirmDocumentUpload() {
        val current = _uiState.value.documentScanState
        if (current !is DocumentScanState.Review) return
        val taskListId = _uiState.value.selectedTaskList?.id

        val toUpload = current.items.filter { it.included }
        if (toUpload.isEmpty()) {
            _uiState.update { it.copy(documentScanState = DocumentScanState.Idle) }
            return
        }

        _uiState.update { it.copy(documentScanState = DocumentScanState.Uploading) }

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0

            withContext(Dispatchers.IO) {
                for (item in toUpload) {
                    val needsCalendar = item.destination != UploadDestination.TASK
                    val needsTask = item.destination != UploadDestination.CALENDAR

                    val calendarOk = if (!needsCalendar) true else runCatching {
                        calendarService?.events()?.insert("primary", buildCalendarEvent(item.event))?.execute()
                    }.isSuccess

                    val taskOk = if (!needsTask) true else runCatching {
                        taskListId?.let {
                            tasksService?.tasks()?.insert(it, buildGoogleTask(item.event))?.execute()
                        } ?: throw IllegalStateException("선택된 Tasks 목록이 없습니다.")
                    }.isSuccess

                    if (calendarOk && taskOk) successCount++ else failCount++
                }
            }

            _uiState.update {
                it.copy(documentScanState = DocumentScanState.Done(successCount, failCount))
            }
            delay(2000)
            _uiState.update { it.copy(documentScanState = DocumentScanState.Idle) }
        }
    }

    private fun buildCalendarEvent(e: ExtractedEvent): Event {
        val event = Event().setSummary(e.title)
        e.note?.let { event.description = it }

        val zone = ZoneId.systemDefault()
        val time = e.time
        if (time != null) {
            val start = e.date.atTime(LocalTime.parse(time))
            val end = start.plusHours(1)
            event.start = EventDateTime()
                .setDateTime(DateTime(start.atZone(zone).toInstant().toEpochMilli()))
            event.end = EventDateTime()
                .setDateTime(DateTime(end.atZone(zone).toInstant().toEpochMilli()))
        } else {
            val endExclusive = (e.endDate ?: e.date).plusDays(1)
            event.start = EventDateTime()
                .setDate(DateTime(true, e.date.toEpochDay() * 86_400_000L, null))
            event.end = EventDateTime()
                .setDate(DateTime(true, endExclusive.toEpochDay() * 86_400_000L, null))
        }
        return event
    }

    private fun buildGoogleTask(e: ExtractedEvent): Task {
        val dueRfc3339 = e.date
            .atStartOfDay(ZoneId.of("UTC"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

        val notesParts = buildList {
            if (e.time != null) add("시간: ${e.time}")
            if (e.endDate != null) add("~ ${e.endDate}")
            if (e.note != null) add(e.note)
        }

        return Task().apply {
            title = e.title
            due = dueRfc3339
            if (notesParts.isNotEmpty()) notes = notesParts.joinToString(" · ")
        }
    }
}
