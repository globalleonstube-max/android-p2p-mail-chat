package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.ui.ChatViewModel
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.ConfigScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    private val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val email = account?.email
                val displayName = account?.displayName ?: ""
                if (email != null) {
                    viewModel.loginWithGoogle(email, displayName, this)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Google Sign-In failed", e)
                viewModel.setLoginError("Ошибка авторизации Google: ${e.localizedMessage}")
            }
        }
    }

    private val userRecoveryLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val email = viewModel.lastAttemptedEmail
            if (email != null) {
                viewModel.loginWithGoogle(email, viewModel.lastAttemptedDisplayName ?: "", this)
            }
        }
    }

    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://mail.google.com/"))
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            viewModel.pendingAuthIntent.collect { intent ->
                if (intent != null) {
                    userRecoveryLauncher.launch(intent)
                    viewModel.clearPendingAuthIntent()
                }
            }
        }

        setContent {
            MyApplicationTheme {
                val connectionConfig by viewModel.connectionConfig.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (connectionConfig == null) {
                        ConfigScreen(
                            viewModel = viewModel,
                            onGoogleSignIn = { startGoogleSignIn() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        ChatListScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

