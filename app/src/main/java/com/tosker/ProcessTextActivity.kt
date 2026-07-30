package com.tosker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections

/**
 * 텍스트 선택 툴바(복사/검색 옆)에서 "오늘 할일 추가"를 누르면 실행됩니다.
 * 선택한 텍스트를 오늘 날짜로 Google Tasks 기본 목록(@default)에 바로 등록합니다.
 */
class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selected = intent
            .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?.toString()
            ?.trim()

        if (selected.isNullOrBlank()) {
            toastAndFinish("추가할 텍스트가 없습니다.")
            return
        }

        val tasksScope = Scope(TasksScopes.TASKS)
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null || !GoogleSignIn.hasPermissions(account, tasksScope)) {
            toastAndFinish("먼저 Tosker 앱에서 Google 로그인을 해주세요.")
            return
        }

        Toast.makeText(this, "할일 추가 중...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val credential = GoogleAccountCredential.usingOAuth2(
                        this@ProcessTextActivity,
                        Collections.singleton(TasksScopes.TASKS)
                    )
                    credential.selectedAccount = account.account

                    val service = Tasks.Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential
                    ).setApplicationName("Tosker").build()

                    val due = LocalDate.now()
                        .atStartOfDay(ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

                    val task = Task().apply {
                        title = selected
                        this.due = due
                    }
                    // "@default" = 사용자의 기본 Tasks 목록
                    service.tasks().insert("@default", task).execute()
                }
            }

            if (result.isSuccess) {
                toastAndFinish("오늘 할일에 추가됨 ✓")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "알 수 없는 오류"
                toastAndFinish("추가 실패: $msg")
            }
        }
    }

    private fun toastAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
