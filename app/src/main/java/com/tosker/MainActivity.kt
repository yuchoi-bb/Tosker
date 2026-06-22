package com.tosker

import android.os.Bundle
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
        // resultCode가 RESULT_OK가 아니어도(예: 설정 오류 시 RESULT_CANCELED) 인텐트에는
        // 실제 ApiException 상태 코드가 담겨 있습니다. 항상 파싱해서 정확한 원인을 노출합니다.
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

        // Tasks API는 idToken/Firebase가 필요 없습니다. OAuth 액세스 토큰만 필요하며
        // GoogleAccountCredential이 직접 발급받습니다. 플레이스홀더 web client id로
        // requestIdToken을 호출하면 DEVELOPER_ERROR(code 10)로 로그인이 실패하므로 제거합니다.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(tasksScope)
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 이미 로그인된 계정이 있고 Tasks 권한도 있으면 바로 진입
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
                onDismiss = { finish() }
            )
        }
    }
}
