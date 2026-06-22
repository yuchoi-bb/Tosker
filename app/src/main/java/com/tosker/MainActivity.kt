package com.tosker

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
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
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            runCatching {
                val account = task.getResult(ApiException::class.java)
                viewModel.onSignInSuccess(account, this)
            }.onFailure { e ->
                viewModel.onSignInFailure(e.message ?: "로그인 실패")
            }
        } else {
            viewModel.onSignInFailure("로그인 취소")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tasksScope = Scope(TasksScopes.TASKS)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(getString(R.string.default_web_client_id))
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
