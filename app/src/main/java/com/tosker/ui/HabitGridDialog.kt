package com.tosker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tosker.viewmodel.HabitGridState
import com.tosker.viewmodel.HabitMarkState
import java.time.LocalDate
import java.time.YearMonth

/** 한 것(V) 표시 색 */
private val DONE_COLOR = Color(0xFF2E7D32)

private val LABEL_WIDTH = 86.dp
private val CELL_SIZE = 26.dp

/**
 * 월간 습관 격자.
 * 세로축은 습관 목록의 "마감일 없는 미완료 할일",
 * 가로축은 그달의 1~말일. 칸을 누르면 그 날짜 기록이 켜지고 꺼진다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitGridDialog(
    state: HabitGridState,
    journalEnabled: Boolean,
    onToggle: (habitTitle: String, date: LocalDate) -> Unit,
    onChangeMonth: (delta: Long) -> Unit,
    onAddHabit: (String) -> Unit,
    onDeleteHabit: (title: String, alsoRecords: Boolean) -> Unit,
    onRenameHabit: (oldTitle: String, newTitle: String) -> Unit,
    onSaveJournal: (date: LocalDate, text: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is HabitGridState.Hidden) return

    var showAddHabit by remember { mutableStateOf(false) }
    // 수정 창을 띄운 습관 이름
    var editingHabit by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.97f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 제목 + 닫기
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("습관 기록", style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 저장된 내용 말고 지금 바로 다시 읽어오기
                        IconButton(onClick = { onChangeMonth(0) }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "새로고침",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "닫기",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                when (state) {
                    is HabitGridState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text("불러오는 중...", style = MaterialTheme.typography.bodyMedium)
                    }

                    is HabitGridState.Error -> Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    is HabitGridState.Loaded -> {
                        MonthHeader(
                            month = state.month,
                            onChangeMonth = onChangeMonth
                        )

                        if (state.habits.isEmpty()) {
                            Text(
                                "습관이 없습니다. 아래 + 버튼으로 추가하거나 " +
                                        "Google Tasks의 습관 목록에 마감일 없는 할일을 만드세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            HabitGrid(
                                state = state,
                                onToggle = onToggle,
                                onDeleteHabit = { editingHabit = it }
                            )
                        }

                        TextButton(onClick = { showAddHabit = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("습관 추가")
                        }

                        Divider()
                        JournalSection(
                            state = state,
                            enabled = journalEnabled,
                            onSave = onSaveJournal
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    editingHabit?.let { title ->
        HabitEditDialog(
            title = title,
            onRename = { newTitle ->
                onRenameHabit(title, newTitle)
                editingHabit = null
            },
            onDelete = { alsoRecords ->
                onDeleteHabit(title, alsoRecords)
                editingHabit = null
            },
            onDismiss = { editingHabit = null }
        )
    }

    if (showAddHabit) {
        AddHabitDialog(
            onAdd = {
                onAddHabit(it)
                showAddHabit = false
            },
            onDismiss = { showAddHabit = false }
        )
    }
}

/** 격자 아래 한 줄 일기. 날짜 하나를 골라 그날 기록을 쓰고, 그달 기록을 아래 보여준다. */
@Composable
private fun JournalSection(
    state: HabitGridState.Loaded,
    enabled: Boolean,
    onSave: (LocalDate, String) -> Unit
) {
    Text(
        "한 줄 일기",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )

    if (!enabled) {
        Text(
            "설정에서 일기 목록을 지정하면 그날의 한 줄을 남길 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        return
    }

    val today = LocalDate.now()
    // 이번 달을 보고 있으면 오늘, 아니면 그달 1일을 기본으로 고른다.
    var pickedDate by remember(state.month) {
        mutableStateOf(
            if (YearMonth.from(today) == state.month) today else state.month.atDay(1)
        )
    }
    var text by remember(pickedDate, state.journal) {
        mutableStateOf(state.journal[pickedDate]?.text.orEmpty())
    }

    val journalScroll = rememberScrollState()
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }

    // 일기 날짜 줄도 오늘이 보이게 맞춘다.
    LaunchedEffect(state.month, journalScroll.maxValue) {
        if (journalScroll.maxValue == 0) return@LaunchedEffect
        val cellPx = with(density) { CELL_SIZE.toPx() }
        val target = ((pickedDate.dayOfMonth - 3) * cellPx).toInt()
            .coerceIn(0, journalScroll.maxValue)
        journalScroll.animateScrollTo(target)
    }

    // 날짜 고르기
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(journalScroll),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        (1..state.month.lengthOfMonth()).forEach { day ->
            val date = state.month.atDay(day)
            val selected = date == pickedDate
            val written = state.journal.containsKey(date)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(CELL_SIZE)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            written -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable { pickedDate = date }
            ) {
                Text(
                    text = day.toString(),
                    fontSize = 10.sp,
                    fontWeight = if (selected || written) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = {
            Text("${pickedDate.monthValue}월 ${pickedDate.dayOfMonth}일 — 기억할 한 줄")
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        maxLines = 3
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = {
            onSave(pickedDate, text)
            // 저장한 뒤에도 커서를 입력칸에 둬서 이어서 쓸 수 있게 한다.
            focusRequester.requestFocus()
        }) { Text("저장") }
    }

    // 그달에 쓴 기록 목록
    val written = state.journal.values.sortedBy { it.date }
    if (written.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
        ) {
            written.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickedDate = entry.date }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${entry.date.dayOfMonth}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(22.dp),
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onChangeMonth: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChangeMonth(-1) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
        }
        Text(
            text = "${month.year}. ${month.monthValue}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onChangeMonth(1) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitGrid(
    state: HabitGridState.Loaded,
    onToggle: (habitTitle: String, date: LocalDate) -> Unit,
    onDeleteHabit: (String) -> Unit
) {
    val days = (1..state.month.lengthOfMonth()).map { state.month.atDay(it) }
    val today = LocalDate.now()
    val dayScroll = rememberScrollState()
    val density = LocalDensity.current

    // 열면 1일이 아니라 오늘이 보이도록 가로 스크롤을 맞춘다.
    LaunchedEffect(state.month, dayScroll.maxValue) {
        if (YearMonth.from(today) != state.month || dayScroll.maxValue == 0) return@LaunchedEffect
        val cellPx = with(density) { CELL_SIZE.toPx() }
        // 오늘 칸이 왼쪽에서 두 칸쯤 들어오게 둔다.
        val target = ((today.dayOfMonth - 3) * cellPx).toInt().coerceIn(0, dayScroll.maxValue)
        dayScroll.animateScrollTo(target)
    }

    Row(modifier = Modifier.heightIn(max = 560.dp)) {
        // 왼쪽 고정 열: 습관 이름 + 달성률
        Column(modifier = Modifier.width(LABEL_WIDTH)) {
            // 날짜 머리글과 높이를 맞추기 위한 빈 칸
            Spacer(Modifier.height(CELL_SIZE))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                state.habits.forEach { habit ->
                    val done = days.count {
                state.marks[habit.title to it]?.state == HabitMarkState.DONE
            }
                    Column(
                        modifier = Modifier
                            .height(CELL_SIZE)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onDeleteHabit(habit.title) }
                            ),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$done/${days.size}",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // 오른쪽: 날짜 머리글 + X 격자 (가로 스크롤)
        Column(modifier = Modifier.horizontalScroll(dayScroll)) {
            Row {
                days.forEach { date ->
                    val isToday = date == today
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(CELL_SIZE)
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                state.habits.forEach { habit ->
                    Row {
                        days.forEach { date ->
                            val mark = state.marks[habit.title to date]
                            val isToday = date == today
                            // 아직 오지 않은 날은 체크할 수 없다.
                            val isFuture = date.isAfter(today)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(CELL_SIZE)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                            isFuture -> MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.outline.copy(
                                            alpha = if (isFuture) 0.12f else 0.25f
                                        ),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable(enabled = !isFuture) {
                                        onToggle(habit.title, date)
                                    }
                            ) {
                                // 한 것 V(초록) / 안 한 것 X(빨강) / 기록 없음은 빈 칸
                                when (mark?.state) {
                                    HabitMarkState.DONE -> Text(
                                        text = "V",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = DONE_COLOR
                                    )
                                    HabitMarkState.SKIPPED -> Text(
                                        text = "X",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    null -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 습관 수정 창.
 * 습관은 제목으로 기록과 이어져 있어서, 이름을 바꾸면 지난 기록 제목도 같이 바뀐다.
 */
@Composable
private fun HabitEditDialog(
    title: String,
    onRename: (String) -> Unit,
    onDelete: (alsoRecords: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var newTitle by remember(title) { mutableStateOf(title) }
    var confirmingDelete by remember(title) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirmingDelete) "습관 삭제" else "습관 수정") },
        text = {
            if (confirmingDelete) {
                Text("「$title」을(를) 지울까요?\n지난 기록은 남겨둘 수 있습니다.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("습관 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "이름을 바꾸면 지난 기록도 함께 바뀌어 격자가 이어집니다. " +
                                "이미 같은 이름의 줄이 있으면 하나로 합쳐집니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            if (confirmingDelete) {
                TextButton(onClick = { onDelete(false) }) { Text("습관만 삭제") }
            } else {
                TextButton(
                    onClick = { onRename(newTitle) },
                    enabled = newTitle.isNotBlank() && newTitle.trim() != title
                ) { Text("이름 바꾸기") }
            }
        },
        dismissButton = {
            Row {
                if (confirmingDelete) {
                    TextButton(onClick = { onDelete(true) }) {
                        Text("기록까지 삭제", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { confirmingDelete = false }) { Text("뒤로") }
                } else {
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) { Text("취소") }
                }
            }
        }
    )
}

@Composable
private fun AddHabitDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("습관 추가") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("습관 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text("추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
