package com.tosker

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.api.services.tasks.TasksScopes
import com.tosker.ui.ToskerApp
import com.tosker.update.UpdateInfo
import com.tosker.viewmodel.ToskerViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: ToskerViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient

    private var downloadId: Long = -1L
    private var pendingApkFile: File? = null

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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀하세요...")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            viewModel.onVoiceError("음성 인식을 지원하지 않는 기기입니다.")
        }
    }

    // 다운로드 완료 시 패키지 설치 화면을 띄우는 리시버
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id == downloadId && id != -1L) {
                pendingApkFile?.let { installApk(it) }
            }
        }
    }

    /** GitHub 릴리즈 APK를 DownloadManager로 내려받고 완료 시 설치 화면을 띄움 */
    private fun downloadAndInstall(info: UpdateInfo) {
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, info.apkName)
            if (file.exists()) file.delete()
            pendingApkFile = file

            val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
                setTitle("Tosker ${info.tagName}")
                setDescription("업데이트 다운로드 중...")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalFilesDir(
                    this@MainActivity,
                    Environment.DIRECTORY_DOWNLOADS,
                    info.apkName
                )
                setMimeType("application/vnd.android.package-archive")
            }
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)
            Toast.makeText(this, "업데이트 다운로드를 시작합니다...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "다운로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** 다운로드한 APK 파일에 대해 패키지 설치 인텐트 실행 */
    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "설치 실행 실패: ${e.message}", Toast.LENGTH_LONG).show()
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

        // 다운로드 완료 브로드캐스트 등록
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        // 앱 시작 시 GitHub 최신 릴리즈와 현재 버전 비교 → 더 높으면 업데이트 버튼 노출
        viewModel.checkForUpdate(BuildConfig.VERSION_NAME)

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
                onUpdateClick = { info -> downloadAndInstall(info) },
                onDismiss = { finish() }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(downloadReceiver) }
    }
}
