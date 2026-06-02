package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.data.ConnectionResult
import com.example.data.MailConfig
import com.example.data.P2PChat
import com.example.data.P2PMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("p2p_mail_chat_prefs", Context.MODE_PRIVATE)
    private val repository = ChatRepository(application)

    private val _connectionConfig = MutableStateFlow<MailConfig?>(null)
    val connectionConfig: StateFlow<MailConfig?> = _connectionConfig.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _activePartnerEmail = MutableStateFlow<String?>(null)
    val activePartnerEmail: StateFlow<String?> = _activePartnerEmail.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _pollingError = MutableStateFlow<String?>(null)
    val pollingError: StateFlow<String?> = _pollingError.asStateFlow()

    private val _sendingMessage = MutableStateFlow(false)
    val sendingMessage: StateFlow<Boolean> = _sendingMessage.asStateFlow()

    private val _sendMessageError = MutableStateFlow<String?>(null)
    val sendMessageError: StateFlow<String?> = _sendMessageError.asStateFlow()

    val chats: StateFlow<List<P2PChat>> = repository.chatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<P2PMessage>> = _activePartnerEmail
        .flatMapLatest { email ->
            if (email == null) flowOf(emptyList())
            else repository.getMessagesForChat(email)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var lastAttemptedEmail: String? = null
        private set
    var lastAttemptedDisplayName: String? = null
        private set

    private val _pendingAuthIntent = MutableStateFlow<android.content.Intent?>(null)
    val pendingAuthIntent: StateFlow<android.content.Intent?> = _pendingAuthIntent.asStateFlow()

    fun clearPendingAuthIntent() {
        _pendingAuthIntent.value = null
    }

    fun setLoginError(msg: String) {
        _loginError.value = msg
    }

    private var pollingJob: Job? = null

    private val sharedPreferenceChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "password" || key == "email") {
            viewModelScope.launch(Dispatchers.Main) {
                loadSavedConfig()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        loadSavedConfig()
    }

    private fun loadSavedConfig() {
        val email = prefs.getString("email", "") ?: ""
        val displayName = prefs.getString("display_name", "") ?: ""
        val imapHost = prefs.getString("imap_host", "") ?: ""
        val imapPort = prefs.getInt("imap_port", 993)
        val smtpHost = prefs.getString("smtp_host", "") ?: ""
        val smtpPort = prefs.getInt("smtp_port", 465)
        val useSsl = prefs.getBoolean("use_ssl", true)
        val password = prefs.getString("password", "") ?: ""
        val isOauth = prefs.getBoolean("is_oauth", false)

        if (email.isNotEmpty() && password.isNotEmpty()) {
            val config = MailConfig(
                email = email,
                displayName = displayName.ifEmpty { email.substringBefore("@") },
                imapHost = imapHost,
                imapPort = imapPort,
                smtpHost = smtpHost,
                smtpPort = smtpPort,
                useSsl = useSsl,
                password = password,
                isOauth = isOauth
            )
            _connectionConfig.value = config
            startAutoPolling()
        }
    }

    fun login(config: MailConfig, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _isConnecting.value = true
            _loginError.value = null
            
            // Test SMTP and IMAP connections concurrently/sequentially
            val result = repository.verifyNodeConnection(config)
            if (result is ConnectionResult.Failure) {
                _isConnecting.value = false
                _loginError.value = result.message
                return@launch
            }

            try {
                // Connection was successful, save credentials
                prefs.edit().apply {
                    putString("email", config.email)
                    putString("display_name", config.displayName)
                    putString("imap_host", config.imapHost)
                    putInt("imap_port", config.imapPort)
                    putString("smtp_host", config.smtpHost)
                    putInt("smtp_port", config.smtpPort)
                    putBoolean("use_ssl", config.useSsl)
                    putString("password", config.password)
                    putBoolean("is_oauth", config.isOauth)
                }.apply()

                _connectionConfig.value = config
                _isConnecting.value = false
                
                launch(Dispatchers.Main) {
                    onSuccess()
                }
                
                startAutoPolling()
            } catch (e: Exception) {
                _isConnecting.value = false
                val errMsg = e.localizedMessage ?: "Неизвестная ошибка подключения"
                _loginError.value = "Ошибка подключения к почтовому серверу: $errMsg"
                Log.e("ChatViewModel", "Login validation failed", e)
            }
        }
    }

    fun loginWithGoogle(email: String, displayName: String, context: Context) {
        lastAttemptedEmail = email
        lastAttemptedDisplayName = displayName
        viewModelScope.launch(Dispatchers.IO) {
            _isConnecting.value = true
            _loginError.value = null
            try {
                val scope = "oauth2:https://mail.google.com/ https://www.googleapis.com/auth/userinfo.email"
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(context, email, scope)
                
                val config = MailConfig(
                    email = email,
                    displayName = displayName.ifEmpty { email.substringBefore("@") },
                    imapHost = "imap.gmail.com",
                    imapPort = 993,
                    smtpHost = "smtp.gmail.com",
                    smtpPort = 465,
                    useSsl = true,
                    password = token,
                    isOauth = true
                )
                
                // Validate node via testing connection
                repository.pollImapMessages(config)
                
                // Connection was successful, save credentials
                prefs.edit().apply {
                    putString("email", config.email)
                    putString("display_name", config.displayName)
                    putString("imap_host", config.imapHost)
                    putInt("imap_port", config.imapPort)
                    putString("smtp_host", config.smtpHost)
                    putInt("smtp_port", config.smtpPort)
                    putBoolean("use_ssl", config.useSsl)
                    putString("password", config.password)
                    putBoolean("is_oauth", config.isOauth)
                }.apply()

                _connectionConfig.value = config
                _isConnecting.value = false
                startAutoPolling()
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                _isConnecting.value = false
                _pendingAuthIntent.value = e.intent
            } catch (e: Exception) {
                _isConnecting.value = false
                val errMsg = e.localizedMessage ?: "Сбой авторизации"
                _loginError.value = "Ошибка входа Google: $errMsg"
                Log.e("ChatViewModel", "OAuth failed", e)
            }
        }
    }

    fun logout() {
        pollingJob?.cancel()
        _isPolling.value = false
        prefs.edit().clear().apply()
        _connectionConfig.value = null
        _activePartnerEmail.value = null
        viewModelScope.launch {
            repository.clearAllLocal()
        }
    }

    fun selectChat(partnerEmail: String?) {
        _activePartnerEmail.value = partnerEmail
    }

    fun startNewChat(partnerEmail: String) {
        if (partnerEmail.trim().isEmpty() || !partnerEmail.contains("@")) {
            _sendMessageError.value = "Пожалуйста, введите валидный Email."
            return
        }
        viewModelScope.launch {
            repository.createNewChat(partnerEmail)
            _activePartnerEmail.value = partnerEmail.lowercase().trim()
        }
    }

    fun sendMessage(body: String) {
        val currentConfig = _connectionConfig.value
        val currentPartner = _activePartnerEmail.value
        if (currentConfig == null || currentPartner == null) {
            _sendMessageError.value = "Ошибка: сессия неактивна или партнер не выбран."
            return
        }
        if (body.trim().isEmpty()) return

        viewModelScope.launch {
            _sendingMessage.value = true
            _sendMessageError.value = null
            try {
                repository.sendMessageSecurely(currentPartner, body, currentConfig)
                _sendingMessage.value = false
            } catch (e: Exception) {
                _sendingMessage.value = false
                val errMsg = e.localizedMessage ?: "Сбой отправки SMTP"
                _sendMessageError.value = "Не удалось отправить сообщение: $errMsg"
                Log.e("ChatViewModel", "SMTP send error", e)
            }
        }
    }

    fun deleteChat(partnerEmail: String) {
        viewModelScope.launch {
            repository.deleteChatLocal(partnerEmail)
            if (_activePartnerEmail.value == partnerEmail) {
                _activePartnerEmail.value = null
            }
        }
    }

    fun manualRefresh() {
        val currentConfig = _connectionConfig.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isPolling.value = true
            _pollingError.value = null
            try {
                repository.pollImapMessages(currentConfig)
            } catch (e: Exception) {
                _pollingError.value = " Ошибка синхронизации: ${e.localizedMessage}"
                Log.e("ChatViewModel", "Manual IMAP poll error", e)
            } finally {
                _isPolling.value = false
            }
        }
    }

    private fun startAutoPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val currentConfig = _connectionConfig.value
                if (currentConfig == null) {
                    _isPolling.value = false
                    break
                }

                _isPolling.value = true
                try {
                    repository.pollImapMessages(currentConfig)
                    _pollingError.value = null
                } catch (e: Exception) {
                    _pollingError.value = "Ошибка авто-синхронизации: ${e.localizedMessage}"
                    Log.e("ChatViewModel", "Auto IMAP poll error", e)
                } finally {
                    _isPolling.value = false
                }
                
                delay(20000) // Poll every 20 seconds
            }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        pollingJob?.cancel()
        super.onCleared()
    }
}
