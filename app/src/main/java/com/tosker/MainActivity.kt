package com.tosker

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.api.services.tasks.TasksScopes
import com.tosker.ui.ToskerApp
import com.tosker.viewmodel.ToskerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ToskerViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.onSignInSuccess(account, this)
        } catch (e: ApiException) {
            viewModel.onSignInFailure(buildSignInErrorMessage(e.statusCode, result.resultCode))
        } catch (e: Exception) {
            viewModel.onSignInFailure("알 수 없는 오류: ${e.message} (resultCode=${result.resultCode})")
        }
    }

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { viewModel.appendVoiceText(it) }
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_MODEL, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀하세요...")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            viewModel.onVoiceError("음성 인식을 지원하지 않는 기기입니다.")
        }
    }

    /** Google Sign-In 상태 코드를 사람이 읽을 수 있는 진단 메시지로 변환 */
    private fun buildSignInErrorMessage(statusCode: Int, resultCode: Int): String {
        val codeName = GoogleSignInStatusCodes.getStatusCodeString(statusCode)
        val hint = when (statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "앱의 SHA-1/패키지명이 Google Cloud OAuth 클라이언트에 등록되지 않았거나 불일치합니다.\n" +
                "SHA-1: DF:76:1B:99:2A:BB:C9:CE:79:61:72:71:99:47:8F:83:62:BA:59:39\n" +
                "패키지명: com.tosker"
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
                "사용자가 로그인을 취소했습니다."
            GoogleSignInStatusCodes.SIGN_IN_FAILED ->
                "로그인 실패. OAuth 동의 화면/테스트 사용자 설정 또는 Tasks API 활성화를 확인하세요."
            CommonStatusCodes.NETWORK_ERROR ->
                "네트워크 오류. 연결 상태를 확인하세요."
            CommonStatusCodes.INTERNAL_ERROR ->
                "Google Play 서비스 내부 오류. 잠시 후 다시 시도하세요."
            CommonStatusCodes.API_NOT_CONNECTED ->
                "Google Play 서비스 연결 실패. 기기의 Play 서비스 상태를 확인하세요."
            else ->
                "상세 진단이 필요합니다."
        }
        return "code $statusCode ($codeName) / resultCode=$resultCode\n$hint"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tasksScope = Scope(TasksScopes.TASKS)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(tasksScope)
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val lastAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (lastAccount != null && GoogleSignIn.hasPermissions(lastAccount, tasksScope)) {
            viewModel.onSignInSuccess(lastAccount, this)
        } else {
            viewModel.setLoggedOut()
        }

        setContent {
            ToskerApp(
                viewModel = viewModel,
                onSignInClick = {
                    signInLauncher.launch(googleSignInClient.signInIntent)
                },
                onSignOutClick = {
                    googleSignInClient.signOut().addOnCompleteListener {
                        viewModel.setLoggedOut()
                    }
                },
                onStartVoice = ::startVoiceInput,
                onDismiss = { finish() }
            )
        }
    }
}
