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
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import com.tosker.ai.ExtractedEvent
import com.tosker.ai.GeminiClient
import com.tosker.settings.CategoryDefaults
import com.tosker.settings.ListConfig
import com.tosker.settings.SettingsStore
import com.tosker.settings.ShortcutConfig
import com.tosker.sync.DriveSettingsSync
import com.tosker.update.UpdateChecker
import com.tosker.update.UpdateInfo
import org.json.JSONArray
import org.json.JSONObject
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
import java.time.YearMonth
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

/** 화면에 표시할 목록 하나. [apiIndex]는 Google Tasks가 준 원래 순번(기본 라벨/색상 기준). */
data class OrderedTaskList(val item: TaskListItem, val apiIndex: Int)

/**
 * 사용자가 지정한 순서(ListConfig.order)대로 목록을 정렬한다.
 * 순서가 없는 목록은 기본 순서를 쓰며, "2day" 같은 오늘 할 일 목록이 맨 앞에 온다.
 */
fun orderedTaskLists(
    taskLists: List<TaskListItem>,
    configs: Map<String, ListConfig>
): List<OrderedTaskList> =
    taskLists
        .mapIndexed { index, item -> OrderedTaskList(item, index) }
        .sortedBy { entry ->
            configs[entry.item.id]?.order
                ?: CategoryDefaults.defaultOrder(entry.apiIndex, entry.item.title)
        }

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

/** 오늘 처리할 할일 한 건 (오늘 마감이거나 마감일이 지난 미완료 항목) */
data class TodayTaskItem(
    val taskId: String,
    val listId: String,
    val listTitle: String,
    val title: String,
    val due: LocalDate,
    val notes: String?,
    val selected: Boolean = false
) {
    /** 마감일이 오늘보다 이전이면 지난 항목 */
    fun isOverdue(today: LocalDate): Boolean = due.isBefore(today)
}

/** 우→좌 스와이프로 열리는 "오늘 할일" 화면의 상태 */
sealed class TodayTasksState {
    object Hidden : TodayTasksState()
    object Loading : TodayTasksState()
    data class Loaded(val items: List<TodayTaskItem>) : TodayTasksState()
    object Moving : TodayTasksState()
    data class Error(val message: String) : TodayTasksState()
}

/**
 * 격자 한 칸의 상태.
 * [DONE]  = 한 것 (V)   — 완료 상태로 저장된 기록
 * [SKIPPED] = 안 한 것 (X) — 완료되지 않은 날짜 기록
 * 기록이 아예 없으면 빈 칸([])이라 이 값으로 표현하지 않는다.
 */
enum class HabitMarkState { DONE, SKIPPED }

/** 격자 한 칸에 저장된 기록 (어떤 할일이고 어떤 상태인지) */
data class HabitMark(val taskId: String, val state: HabitMarkState)

/** 그날의 한 줄 일기 (일기 목록의 할일 하나) */
data class JournalEntry(val taskId: String, val date: LocalDate, val text: String)

/** 습관 격자의 세로축 한 줄 (일기 목록을 뺀 습관 목록의 미완료 할일) */
data class HabitDef(val taskId: String, val title: String)

/** 습관 격자 화면 상태 */
sealed class HabitGridState {
    object Hidden : HabitGridState()
    object Loading : HabitGridState()
    data class Loaded(
        val month: YearMonth,
        val habits: List<HabitDef>,
        /** (습관 제목, 날짜) -> 그 기록 할일의 id */
        val marks: Map<Pair<String, LocalDate>, HabitMark>,
        /** 날짜 -> 그날의 한 줄 일기 */
        val journal: Map<LocalDate, JournalEntry> = emptyMap()
    ) : HabitGridState()
    data class Error(val message: String) : HabitGridState()
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
    val documentScanState: DocumentScanState = DocumentScanState.Idle,
    val todayTasksState: TodayTasksState = TodayTasksState.Hidden,
    /** Upload 아래 단축버튼 (슬롯 0~9) */
    val shortcuts: Map<Int, ShortcutConfig> = emptyMap(),
    /** 설정의 기기 간 동기화 상태 */
    val syncState: SyncState = SyncState.Idle,
    val habitGridState: HabitGridState = HabitGridState.Hidden,
    /** 습관 격자에 쓸 Tasks 목록 id */
    val habitListId: String? = null,
    /** 한 줄 일기를 담을 Tasks 목록 id */
    val journalListId: String? = null
)

/** 설정 동기화(Google Drive 앱 전용 폴더) 상태 */
sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Synced(val at: Long) : SyncState()
    data class Error(val message: String) : SyncState()
}

private const val PENDING_MARK = "__pending__"

/** "안 한 것(X)"으로 직접 찍은 기록임을 나타내는 표시. 평범한 할일과 구분한다. */
const val SKIP_MARKER = "[tosker:skip]"

class ToskerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(
        UiState(
            listConfigs = settingsStore.loadConfigs(),
            shortcuts = settingsStore.loadShortcuts(),
            habitListId = settingsStore.loadHabitListId(),
            journalListId = settingsStore.loadJournalListId()
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var tasksService: Tasks? = null
    private var calendarService: Calendar? = null
    private var settingsSync: DriveSettingsSync? = null

    // ── 설정 기기 간 동기화 (Google Drive 앱 전용 폴더) ──────────────

    /**
     * 기기의 설정과 Drive에 저장된 설정을 맞춘다.
     * 더 최근에 바뀐 쪽이 이긴다(마지막 저장 우선).
     * 동기화에 실패해도 기기 내 설정은 그대로 쓰므로 앱 동작에는 지장이 없다.
     */
    fun syncSettings() {
        val sync = settingsSync ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(syncState = SyncState.Syncing) }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val remoteJson = sync.download()
                    val localUpdatedAt = settingsStore.loadUpdatedAt()
                    val remoteUpdatedAt = remoteJson
                        ?.let { runCatching { JSONObject(it).optLong("updatedAt", 0L) }.getOrDefault(0L) }
                        ?: 0L

                    when {
                        // 원격이 더 최신이면 내려받아 적용
                        remoteJson != null && remoteUpdatedAt > localUpdatedAt -> {
                            settingsStore.importJson(remoteJson)
                            true
                        }
                        // 기기 쪽이 더 최신이거나 원격이 없으면 올린다
                        localUpdatedAt > remoteUpdatedAt -> {
                            sync.upload(settingsStore.exportJson())
                            true
                        }
                        else -> true // 이미 같음
                    }
                }
            }

            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        listConfigs = settingsStore.loadConfigs(),
                        shortcuts = settingsStore.loadShortcuts(),
                        habitListId = settingsStore.loadHabitListId(),
                        journalListId = settingsStore.loadJournalListId(),
                        syncState = SyncState.Synced(System.currentTimeMillis())
                    )
                } else {
                    it.copy(syncState = SyncState.Error(syncErrorMessage(result.exceptionOrNull())))
                }
            }
        }
    }

    /** 설정이 바뀔 때마다 조용히 Drive에 올린다(실패해도 기기 설정은 유지). */
    private fun pushSettings() {
        val sync = settingsSync ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { sync.upload(settingsStore.exportJson()) }
            }
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(syncState = SyncState.Synced(System.currentTimeMillis()))
                } else {
                    it.copy(syncState = SyncState.Error(syncErrorMessage(result.exceptionOrNull())))
                }
            }
        }
    }

    private fun syncErrorMessage(e: Throwable?): String {
        val raw = e?.message ?: "알 수 없는 오류"
        return when {
            raw.contains("Drive API has not been used", ignoreCase = true) ||
                    raw.contains("accessNotConfigured", ignoreCase = true) ->
                "Google Cloud 프로젝트에서 Drive API를 사용 설정해야 합니다."
            raw.contains("insufficient", ignoreCase = true) ||
                    raw.contains("403") ->
                "Drive 권한이 없습니다. 로그아웃 후 다시 로그인해 권한을 허용하세요."
            else -> raw.take(120)
        }
    }

    /** 설정 화면에서 목록 버튼의 이름/색상을 변경하고 영구 저장 (순서는 유지) */
    fun updateListConfig(id: String, label: String, colorHex: Long) {
        val existingOrder = _uiState.value.listConfigs[id]?.order
        settingsStore.saveConfig(id, ListConfig(label, colorHex, existingOrder))
        _uiState.update { it.copy(listConfigs = settingsStore.loadConfigs()) }
        pushSettings()
    }

    /**
     * 목록 버튼의 표시 순서를 한 칸 앞/뒤로 이동한다.
     * 이동 후 모든 목록에 명시적 순서를 부여해 저장한다.
     */
    fun moveList(id: String, up: Boolean) {
        val state = _uiState.value
        val ordered = orderedTaskLists(state.taskLists, state.listConfigs).toMutableList()
        val pos = ordered.indexOfFirst { it.item.id == id }
        if (pos < 0) return
        val target = if (up) pos - 1 else pos + 1
        if (target !in ordered.indices) return

        val moved = ordered.removeAt(pos)
        ordered.add(target, moved)

        val updated = ordered.mapIndexed { newOrder, entry ->
            val existing = state.listConfigs[entry.item.id]
            entry.item.id to ListConfig(
                label = existing?.label
                    ?: CategoryDefaults.defaultLabel(entry.apiIndex, entry.item.title),
                colorHex = existing?.colorHex
                    ?: CategoryDefaults.defaultColor(entry.apiIndex),
                order = newOrder
            )
        }.toMap()

        settingsStore.saveConfigs(updated)
        _uiState.update { it.copy(listConfigs = settingsStore.loadConfigs()) }
        pushSettings()
    }

    fun onSignInSuccess(account: GoogleSignInAccount, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(authState = AuthState.Loading, authError = null) }

            // Tasks + Calendar API 서비스 초기화 (OAuth 액세스 토큰만 사용, Firebase 불필요)
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                setOf(
                    TasksScopes.TASKS,
                    CalendarScopes.CALENDAR_EVENTS,
                    DriveScopes.DRIVE_APPDATA
                )
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
            settingsSync = DriveSettingsSync(
                Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("Tosker").build()
            )

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
            syncSettings()
        }
    }

    fun onSignInFailure(message: String) {
        _uiState.update { it.copy(authState = AuthState.LoggedOut, authError = message) }
    }

    fun setLoggedOut() {
        tasksService = null
        calendarService = null
        settingsSync = null
        _uiState.update {
            UiState(
                authState = AuthState.LoggedOut,
                listConfigs = it.listConfigs,
                shortcuts = it.shortcuts,
                habitListId = it.habitListId,
                journalListId = it.journalListId
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
            _uiState.update { ui ->
                // 습관 목록은 버튼에서 숨기므로 기본 선택에서도 제외한다.
                val selectable = lists.filter {
                    it.id != ui.habitListId && it.id != ui.journalListId
                }
                ui.copy(
                    taskLists = lists,
                    selectedTaskList = selectable.firstOrNull() ?: lists.firstOrNull()
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

    // ── 습관 격자 캐시 (기기 저장) ──────────────────────────────────

    /** 지금 화면에 있는 격자를 기기에 저장해 둔다. */
    private fun cacheCurrentGrid() {
        val state = _uiState.value
        val loaded = state.habitGridState as? HabitGridState.Loaded ?: return
        val listId = state.habitListId ?: return
        runCatching {
            val obj = JSONObject()
            obj.put("habits", JSONArray().apply {
                loaded.habits.forEach { put(JSONObject().put("id", it.taskId).put("t", it.title)) }
            })
            obj.put("marks", JSONArray().apply {
                loaded.marks.forEach { (key, mark) ->
                    put(
                        JSONObject()
                            .put("t", key.first)
                            .put("d", key.second.toString())
                            .put("id", mark.taskId)
                            .put("s", mark.state.name)
                    )
                }
            })
            obj.put("journal", JSONArray().apply {
                loaded.journal.values.forEach {
                    put(JSONObject().put("id", it.taskId).put("d", it.date.toString()).put("x", it.text))
                }
            })
            settingsStore.saveHabitCache(listId, loaded.month.toString(), obj.toString())
        }
    }

    /** 기기에 저장해 둔 격자를 읽어온다. 없거나 깨졌으면 null. */
    private fun readCachedGrid(listId: String, month: YearMonth): HabitGridState.Loaded? =
        runCatching {
            val raw = settingsStore.loadHabitCache(listId, month.toString()) ?: return null
            val obj = JSONObject(raw)

            val habitsArr = obj.getJSONArray("habits")
            val habits = (0 until habitsArr.length()).map { i ->
                val o = habitsArr.getJSONObject(i)
                HabitDef(o.getString("id"), o.getString("t"))
            }

            val marksArr = obj.getJSONArray("marks")
            val marks = buildMap {
                for (i in 0 until marksArr.length()) {
                    val o = marksArr.getJSONObject(i)
                    put(
                        o.getString("t") to LocalDate.parse(o.getString("d")),
                        HabitMark(o.getString("id"), HabitMarkState.valueOf(o.getString("s")))
                    )
                }
            }

            val journalArr = obj.optJSONArray("journal") ?: JSONArray()
            val journal = buildMap {
                for (i in 0 until journalArr.length()) {
                    val o = journalArr.getJSONObject(i)
                    val date = LocalDate.parse(o.getString("d"))
                    put(date, JournalEntry(o.getString("id"), date, o.getString("x")))
                }
            }

            HabitGridState.Loaded(month, habits, marks, journal)
        }.getOrNull()

    // ── 월간 습관 격자 ────────────────────────────────────────────────

    /**
     * 습관 목록(Tasks 목록 하나)에서 습관 정의와 그달의 기록을 읽어 격자를 만든다.
     * - 정의: 마감일 없는 미완료 할일
     * - 기록: 완료 상태이고 마감일이 그달인 할일 (제목이 정의와 같음)
     */
    fun openHabitGrid(month: YearMonth = YearMonth.now()) {
        val listId = _uiState.value.habitListId
        val service = tasksService
        if (listId.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    habitGridState = HabitGridState.Error(
                        "설정에서 습관 목록을 먼저 지정하세요."
                    )
                )
            }
            return
        }
        if (service == null) {
            _uiState.update {
                it.copy(habitGridState = HabitGridState.Error("로그인이 필요합니다."))
            }
            return
        }

        viewModelScope.launch {
            // 기기에 저장해 둔 게 있으면 먼저 보여주고, 뒤에서 조용히 새로 읽어 맞춘다.
            val cached = readCachedGrid(listId, month)
            _uiState.update {
                it.copy(habitGridState = cached ?: HabitGridState.Loading)
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val all = mutableListOf<Task>()
                    var pageToken: String? = null
                    do {
                        val response = service.tasks().list(listId)
                            .setShowCompleted(true)
                            .setShowHidden(true)
                            .setPageToken(pageToken)
                            .execute()
                        all += response.items.orEmpty()
                        pageToken = response.nextPageToken
                    } while (!pageToken.isNullOrBlank() && all.size < 1000)

                    // 습관 = 이 목록에 있는 모든 할일의 제목(중복 제거).
                    // 완료/미완료, 날짜 유무를 가리지 않는다. 단축버튼으로 기록한
                    // 완료 항목도 그 제목이 곧 습관이 된다.
                    val habits = all
                        .mapNotNull { task ->
                            val title = task.title?.trim().orEmpty()
                            if (title.isBlank()) null else title to task
                        }
                        .groupBy({ it.first }, { it.second })
                        .map { (title, tasks) ->
                            // 지울 때 쓸 "정의" 할일(날짜 없는 미완료)이 있으면 그 id를 든다.
                            val definition = tasks.firstOrNull {
                                it.status != "completed" && it.due.isNullOrBlank()
                            }
                            HabitDef(definition?.id ?: tasks.first().id.orEmpty(), title)
                        }

                    // 날짜가 붙은 할일만 그달의 기록으로 본다.
                    //  - 완료            → 한 것(V)
                    //  - 미완료 + 건너뜀 표시 → 안 한 것(X)
                    //  - 미완료 + 표시 없음  → 그냥 아직 안 끝낸 할일이므로 기록이 아님
                    // 같은 이름·같은 날짜가 여러 개면 완료(V)를 우선한다.
                    val marks = mutableMapOf<Pair<String, LocalDate>, HabitMark>()
                    all.forEach { task ->
                        val id = task.id ?: return@forEach
                        val title = task.title?.trim().orEmpty()
                        if (title.isBlank()) return@forEach
                        val date = parseDueDate(task.due) ?: return@forEach
                        if (YearMonth.from(date) != month) return@forEach

                        val state = when {
                            task.status == "completed" -> HabitMarkState.DONE
                            task.notes?.contains(SKIP_MARKER) == true -> HabitMarkState.SKIPPED
                            else -> return@forEach
                        }

                        val key = title to date
                        val existing = marks[key]
                        // 이미 "한 것"으로 잡혔으면 덮어쓰지 않는다.
                        if (existing?.state == HabitMarkState.DONE) return@forEach
                        marks[key] = HabitMark(id, state)
                    }

                    // 일기 목록이 지정돼 있으면 그달 한 줄 일기도 함께 읽는다.
                    val journalListId = _uiState.value.journalListId
                    val journal = if (journalListId.isNullOrBlank()) {
                        emptyMap()
                    } else {
                        val entries = mutableListOf<Task>()
                        var jToken: String? = null
                        do {
                            val response = service.tasks().list(journalListId)
                                .setShowCompleted(true)
                                .setShowHidden(true)
                                .setPageToken(jToken)
                                .execute()
                            entries += response.items.orEmpty()
                            jToken = response.nextPageToken
                        } while (!jToken.isNullOrBlank() && entries.size < 500)

                        buildMap {
                            entries.forEach { task ->
                                val id = task.id ?: return@forEach
                                val text = task.title?.trim().orEmpty()
                                if (text.isBlank()) return@forEach
                                val date = parseDueDate(task.due) ?: return@forEach
                                if (YearMonth.from(date) != month) return@forEach
                                put(date, JournalEntry(id, date, text))
                            }
                        }
                    }

                    HabitGridState.Loaded(month, habits, marks, journal)
                }
            }

            _uiState.update {
                when {
                    result.isSuccess -> it.copy(habitGridState = result.getOrThrow())
                    // 새로 읽기에 실패해도 저장해 둔 게 있으면 그걸 계속 보여준다.
                    cached != null -> it.copy(
                        uploadState = UploadState.Error("최신 기록을 불러오지 못해 저장된 내용을 보여줍니다.")
                    )
                    else -> {
                        val msg = result.exceptionOrNull()?.message ?: "습관 기록을 불러오지 못했습니다."
                        it.copy(habitGridState = HabitGridState.Error(msg))
                    }
                }
            }
            if (result.isSuccess) cacheCurrentGrid()
        }
    }

    fun closeHabitGrid() {
        _uiState.update { it.copy(habitGridState = HabitGridState.Hidden) }
    }

    /** 격자에서 월을 앞뒤로 이동 */
    fun changeHabitMonth(deltaMonths: Long) {
        val current = _uiState.value.habitGridState as? HabitGridState.Loaded ?: return
        openHabitGrid(current.month.plusMonths(deltaMonths))
    }

    /** 격자 칸을 눌렀을 때: 기록이 있으면 지우고, 없으면 그 날짜로 추가한다. */
    /**
     * 격자 칸을 누르면 빈칸 → 한 것(V) → 안 한 것(X) → 빈칸 순으로 돈다.
     * 화면을 먼저 바꿔 바로 반응하게 하고, 통신이 실패하면 원래대로 되돌린다.
     */
    fun toggleHabitMark(habitTitle: String, date: LocalDate) {
        val state = _uiState.value
        val loaded = state.habitGridState as? HabitGridState.Loaded ?: return
        val listId = state.habitListId ?: return
        val service = tasksService ?: return

        val key = habitTitle to date
        val before = loaded.marks[key]
        val next = when (before?.state) {
            null -> HabitMarkState.DONE
            HabitMarkState.DONE -> HabitMarkState.SKIPPED
            HabitMarkState.SKIPPED -> null // 다시 빈칸으로
        }

        val optimistic = loaded.marks.toMutableMap().apply {
            if (next == null) remove(key) else put(key, HabitMark(before?.taskId ?: PENDING_MARK, next))
        }
        _uiState.update { it.copy(habitGridState = loaded.copy(marks = optimistic)) }
        cacheCurrentGrid()

        viewModelScope.launch {
            val stamp = date
                .atStartOfDay(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        // 기록 지우기
                        next == null -> {
                            before?.taskId?.let { service.tasks().delete(listId, it).execute() }
                            null
                        }
                        // 기록 없음 → 새로 만들기
                        before == null -> {
                            service.tasks().insert(
                                listId,
                                Task().apply {
                                    title = habitTitle
                                    due = stamp
                                    if (next == HabitMarkState.DONE) {
                                        status = "completed"
                                        completed = stamp
                                    } else {
                                        // 평범한 할일과 구분되게 표시를 남긴다.
                                        status = "needsAction"
                                        notes = SKIP_MARKER
                                    }
                                }
                            ).execute().id
                        }
                        // 있던 기록의 상태만 바꾸기
                        else -> {
                            val patch = Task().apply {
                                if (next == HabitMarkState.DONE) {
                                    status = "completed"
                                    completed = stamp
                                    notes = ""
                                } else {
                                    status = "needsAction"
                                    completed = null
                                    notes = SKIP_MARKER
                                }
                            }
                            service.tasks().patch(listId, before.taskId, patch).execute()
                            before.taskId
                        }
                    }
                }
            }

            _uiState.update { ui ->
                val current = ui.habitGridState as? HabitGridState.Loaded ?: return@update ui
                val fixed = current.marks.toMutableMap()
                if (result.isSuccess) {
                    val id = result.getOrNull()
                    if (next != null && id != null) fixed[key] = HabitMark(id, next)
                } else {
                    // 실패하면 원래 상태로 되돌린다.
                    if (before != null) fixed[key] = before else fixed.remove(key)
                }
                ui.copy(
                    habitGridState = current.copy(marks = fixed),
                    uploadState = if (result.isFailure) {
                        UploadState.Error(result.exceptionOrNull()?.message ?: "기록 변경 실패")
                    } else {
                        ui.uploadState
                    }
                )
            }
            cacheCurrentGrid()
        }
    }

    /** Tosker에서 습관을 새로 추가한다(마감일 없는 미완료 할일로 생성). */
    fun addHabit(title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        val state = _uiState.value
        val listId = state.habitListId ?: return
        val service = tasksService ?: return
        val month = (state.habitGridState as? HabitGridState.Loaded)?.month ?: YearMonth.now()

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    service.tasks().insert(listId, Task().apply { this.title = clean }).execute()
                }
            }
            if (result.isSuccess) {
                openHabitGrid(month)
            } else {
                _uiState.update {
                    it.copy(
                        uploadState = UploadState.Error(
                            result.exceptionOrNull()?.message ?: "습관 추가 실패"
                        )
                    )
                }
            }
        }
    }

    /**
     * 그날의 한 줄 일기를 저장한다.
     * 이미 그 날짜 글이 있으면 내용을 바꾸고, 없으면 새로 만든다.
     * 빈 글로 저장하면 그날 기록을 지운다.
     */
    fun saveJournal(date: LocalDate, text: String) {
        val state = _uiState.value
        val listId = state.journalListId ?: return
        val service = tasksService ?: return
        val loaded = state.habitGridState as? HabitGridState.Loaded ?: return
        val existing = loaded.journal[date]
        val clean = text.trim()

        viewModelScope.launch {
            val stamp = date
                .atStartOfDay(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        clean.isBlank() && existing != null -> {
                            service.tasks().delete(listId, existing.taskId).execute()
                            null
                        }
                        clean.isBlank() -> null
                        existing != null -> {
                            service.tasks()
                                .patch(listId, existing.taskId, Task().apply { title = clean })
                                .execute()
                            existing.taskId
                        }
                        else -> {
                            service.tasks().insert(
                                listId,
                                Task().apply {
                                    title = clean
                                    due = stamp
                                }
                            ).execute().id
                        }
                    }
                }
            }

            if (result.isSuccess) {
                _uiState.update { ui ->
                    val current = ui.habitGridState as? HabitGridState.Loaded ?: return@update ui
                    val next = current.journal.toMutableMap()
                    val newId = result.getOrNull()
                    if (clean.isBlank() || newId == null) {
                        next.remove(date)
                    } else {
                        next[date] = JournalEntry(newId, date, clean)
                    }
                    ui.copy(habitGridState = current.copy(journal = next))
                }
            } else {
                _uiState.update {
                    it.copy(
                        uploadState = UploadState.Error(
                            result.exceptionOrNull()?.message ?: "일기 저장 실패"
                        )
                    )
                }
            }
        }
    }

    /**
     * 습관 한 줄을 지운다.
     * 습관 목록에서 그 제목의 미완료 할일(정의)을 지우고, 지난 기록은 남긴다.
     * [alsoRecords]가 true면 그 제목의 완료 기록까지 전부 지운다.
     */
    /**
     * 습관 이름을 바꾼다.
     * 습관은 "제목"으로 기록과 이어져 있어서, 정의뿐 아니라
     * 그 제목으로 남은 지난 기록까지 전부 같이 바꿔야 매칭이 유지된다.
     *
     * 이미 [newTitle] 줄이 따로 있으면 두 줄이 하나로 합쳐진다.
     */
    fun renameHabit(oldTitle: String, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isBlank() || clean == oldTitle) return
        val state = _uiState.value
        val listId = state.habitListId ?: return
        val service = tasksService ?: return
        val month = (state.habitGridState as? HabitGridState.Loaded)?.month ?: YearMonth.now()

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val all = mutableListOf<Task>()
                    var pageToken: String? = null
                    do {
                        val response = service.tasks().list(listId)
                            .setShowCompleted(true)
                            .setShowHidden(true)
                            .setPageToken(pageToken)
                            .execute()
                        all += response.items.orEmpty()
                        pageToken = response.nextPageToken
                    } while (!pageToken.isNullOrBlank() && all.size < 1000)

                    all.filter { it.title?.trim() == oldTitle }
                        .forEach { task ->
                            task.id?.let { id ->
                                service.tasks()
                                    .patch(listId, id, Task().apply { title = clean })
                                    .execute()
                            }
                        }
                }
            }

            if (result.isSuccess) {
                openHabitGrid(month)
            } else {
                _uiState.update {
                    it.copy(
                        uploadState = UploadState.Error(
                            result.exceptionOrNull()?.message ?: "이름 변경 실패"
                        )
                    )
                }
            }
        }
    }

    fun deleteHabit(title: String, alsoRecords: Boolean = false) {
        val state = _uiState.value
        val listId = state.habitListId ?: return
        val service = tasksService ?: return
        val month = (state.habitGridState as? HabitGridState.Loaded)?.month ?: YearMonth.now()

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val all = mutableListOf<Task>()
                    var pageToken: String? = null
                    do {
                        val response = service.tasks().list(listId)
                            .setShowCompleted(true)
                            .setShowHidden(true)
                            .setPageToken(pageToken)
                            .execute()
                        all += response.items.orEmpty()
                        pageToken = response.nextPageToken
                    } while (!pageToken.isNullOrBlank() && all.size < 1000)

                    all.filter { task ->
                        task.title?.trim() == title &&
                                (alsoRecords || task.status != "completed")
                    }.forEach { task ->
                        task.id?.let { service.tasks().delete(listId, it).execute() }
                    }
                }
            }

            if (result.isSuccess) {
                openHabitGrid(month)
            } else {
                _uiState.update {
                    it.copy(
                        uploadState = UploadState.Error(
                            result.exceptionOrNull()?.message ?: "습관 삭제 실패"
                        )
                    )
                }
            }
        }
    }

    /** 설정에서 한 줄 일기 목록을 지정한다. */
    fun setJournalList(listId: String?) {
        settingsStore.saveJournalListId(listId)
        _uiState.update { ui ->
            val nextSelected = if (ui.selectedTaskList?.id == listId) {
                ui.taskLists.firstOrNull { it.id != listId }
            } else {
                ui.selectedTaskList
            }
            ui.copy(journalListId = listId, selectedTaskList = nextSelected)
        }
        pushSettings()
    }

    /** 설정에서 습관 목록을 지정한다. */
    fun setHabitList(listId: String?) {
        settingsStore.saveHabitListId(listId)
        _uiState.update { ui ->
            // 방금 숨긴 목록이 선택돼 있었다면 보이는 목록으로 옮겨둔다.
            val nextSelected = if (ui.selectedTaskList?.id == listId) {
                ui.taskLists.firstOrNull { it.id != listId }
            } else {
                ui.selectedTaskList
            }
            ui.copy(habitListId = listId, selectedTaskList = nextSelected)
        }
        pushSettings()
    }

    // ── Upload 아래 단축버튼 ──────────────────────────────────────────

    fun saveShortcut(
        slot: Int,
        title: String,
        iconKey: String,
        colorHex: Long,
        listId: String?,
        markCompleted: Boolean
    ) {
        settingsStore.saveShortcut(
            ShortcutConfig(
                slot = slot,
                title = title.trim(),
                iconKey = iconKey,
                colorHex = colorHex,
                listId = listId,
                markCompleted = markCompleted
            )
        )
        _uiState.update { it.copy(shortcuts = settingsStore.loadShortcuts()) }
        pushSettings()
    }

    fun deleteShortcut(slot: Int) {
        settingsStore.deleteShortcut(slot)
        _uiState.update { it.copy(shortcuts = settingsStore.loadShortcuts()) }
        pushSettings()
    }

    /**
     * 단축버튼을 눌렀을 때: 저장된 제목의 할일을
     * 화면에서 고른 날짜에 "완료" 상태로 지정된 목록에 등록한다.
     */
    fun runShortcut(slot: Int) {
        val state = _uiState.value
        val shortcut = state.shortcuts[slot] ?: return
        val targetListId = shortcut.listId ?: state.selectedTaskList?.id ?: return
        val service = tasksService ?: return
        if (state.uploadState is UploadState.Loading) return

        viewModelScope.launch {
            _uiState.update { it.copy(uploadState = UploadState.Loading) }

            val stamp = state.selectedDate
                .atStartOfDay(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val task = Task().apply {
                        title = shortcut.title
                        due = stamp
                        // 한 일 기록이면 완료 상태로, 해야 할 일이면 미완료로 등록한다.
                        if (shortcut.markCompleted) {
                            status = "completed"
                            completed = stamp
                        }
                    }
                    service.tasks().insert(targetListId, task).execute()
                }
            }

            if (result.isSuccess) {
                _uiState.update { it.copy(uploadState = UploadState.Success) }
                delay(1500)
                _uiState.update { it.copy(uploadState = UploadState.Idle) }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "등록 실패"
                _uiState.update { it.copy(uploadState = UploadState.Error(msg)) }
            }
        }
    }

    // ── 오늘 할일 화면 (우→좌 스와이프) ────────────────────────────────

    /** "2day" 등 오늘 할 일 목록. 없으면 표시 순서상 맨 앞 목록을 쓴다. */
    private fun resolveTodayList(state: UiState): TaskListItem? {
        val ordered = orderedTaskLists(state.taskLists, state.listConfigs)
        return ordered.firstOrNull { CategoryDefaults.isTodayList(it.item.title) }?.item
            ?: ordered.firstOrNull()?.item
    }

    /** 2day 목록 이름 (화면 안내 문구용) */
    fun todayListTitle(): String? = resolveTodayList(_uiState.value)?.title

    /**
     * 2day를 제외한 모든 목록에서 오늘 마감이거나 마감일이 지난 미완료 할일을 모아 온다.
     */
    fun openTodayTasks() {
        val state = _uiState.value
        val todayList = resolveTodayList(state)
        val service = tasksService
        if (service == null) {
            _uiState.update {
                it.copy(todayTasksState = TodayTasksState.Error("로그인이 필요합니다."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(todayTasksState = TodayTasksState.Loading) }

            val today = LocalDate.now()
            // 버튼과 같은 순서(개인 → W → 운동 → 반복)로 훑어야 목록별로 묶여서 나온다.
            val sourceLists = orderedTaskLists(state.taskLists, state.listConfigs)
                .map { it.item }
                .filter {
                    it.id != todayList?.id &&
                            it.id != state.habitListId &&
                            it.id != state.journalListId
                }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 오늘 끝까지를 상한으로 두면 "오늘 마감 + 지난 것"만 서버에서 걸러진다.
                    // (마감일 없는 할일은 dueMax 필터에서 제외된다)
                    val dueMax = today
                        .atTime(23, 59, 59)
                        .atZone(ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

                    sourceLists.flatMap { list ->
                        val tasks = mutableListOf<Task>()
                        var pageToken: String? = null
                        do {
                            val response = service.tasks().list(list.id)
                                .setShowCompleted(false)
                                .setShowHidden(false)
                                .setDueMax(dueMax)
                                .setPageToken(pageToken)
                                .execute()
                            tasks += response.items.orEmpty()
                            pageToken = response.nextPageToken
                        } while (!pageToken.isNullOrBlank() && tasks.size < 200)

                        tasks.mapNotNull { task ->
                            val id = task.id ?: return@mapNotNull null
                            val title = task.title?.trim().orEmpty()
                            if (title.isBlank()) return@mapNotNull null
                            val dueDate = parseDueDate(task.due) ?: return@mapNotNull null
                            if (dueDate.isAfter(today)) return@mapNotNull null

                            TodayTaskItem(
                                taskId = id,
                                listId = list.id,
                                listTitle = list.title,
                                title = title,
                                due = dueDate,
                                notes = task.notes
                            )
                        }.sortedWith(compareBy({ it.due }, { it.title }))
                        // 목록 안에서만 마감일 순으로 정렬해 목록별 묶음을 유지한다.
                    }
                }
            }

            _uiState.update {
                if (result.isSuccess) {
                    it.copy(todayTasksState = TodayTasksState.Loaded(result.getOrThrow()))
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "할일을 불러오지 못했습니다."
                    it.copy(todayTasksState = TodayTasksState.Error(msg))
                }
            }
        }
    }

    fun closeTodayTasks() {
        _uiState.update { it.copy(todayTasksState = TodayTasksState.Hidden) }
    }

    fun toggleTodayTask(taskId: String, listId: String) {
        val current = _uiState.value.todayTasksState
        if (current !is TodayTasksState.Loaded) return
        val updated = current.items.map { item ->
            if (item.taskId == taskId && item.listId == listId) {
                item.copy(selected = !item.selected)
            } else {
                item
            }
        }
        _uiState.update { it.copy(todayTasksState = TodayTasksState.Loaded(updated)) }
    }

    /**
     * 선택한 할일을 2day 목록으로 옮긴다.
     * 2day에 마감일을 오늘로 해서 새로 만들고, 원본은 삭제한다.
     */
    fun moveSelectedToTodayList() {
        val state = _uiState.value
        val current = state.todayTasksState
        if (current !is TodayTasksState.Loaded) return

        val selected = current.items.filter { it.selected }
        if (selected.isEmpty()) return

        val todayList = resolveTodayList(state) ?: return
        val service = tasksService ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(todayTasksState = TodayTasksState.Moving) }

            val today = LocalDate.now()
            val dueRfc3339 = today
                .atStartOfDay(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

            val outcome = withContext(Dispatchers.IO) {
                var moved = 0
                var failed = 0
                selected.forEach { item ->
                    val ok = runCatching {
                        val newTask = Task().apply {
                            title = item.title
                            notes = item.notes
                            due = dueRfc3339
                        }
                        // 먼저 2day에 생성하고, 성공했을 때만 원본을 삭제한다.
                        service.tasks().insert(todayList.id, newTask).execute()
                        service.tasks().delete(item.listId, item.taskId).execute()
                    }.isSuccess
                    if (ok) moved++ else failed++
                }
                moved to failed
            }

            val (moved, failed) = outcome
            _uiState.update {
                it.copy(
                    todayTasksState = TodayTasksState.Hidden,
                    uploadState = if (failed > 0) {
                        UploadState.Error("${moved}개 이동, ${failed}개 실패")
                    } else {
                        UploadState.Success
                    }
                )
            }
            if (failed == 0) {
                delay(1500)
                _uiState.update { it.copy(uploadState = UploadState.Idle) }
            }
        }
    }

    /** Google Tasks의 due(RFC3339 UTC 자정)를 날짜로 변환 */
    private fun parseDueDate(due: String?): LocalDate? {
        if (due.isNullOrBlank()) return null
        return runCatching {
            // "2026-09-26T00:00:00.000Z" 형태 — 앞 10자리가 날짜
            LocalDate.parse(due.take(10))
        }.getOrNull()
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
