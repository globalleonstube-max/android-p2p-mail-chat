package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.math.max

data class MailConfig(
    val email: String,
    val displayName: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val useSsl: Boolean,
    val password: String,
    val isOauth: Boolean = false
)

class ChatRepository(private val context: Context) {

    private val connectionService = ConnectionService(context)

    suspend fun verifyNodeConnection(config: MailConfig): ConnectionResult {
        return connectionService.verifyConnection(config)
    }

    suspend fun refreshOAuthToken(email: String, oldToken: String): String = withContext(Dispatchers.IO) {
        try {
            com.google.android.gms.auth.GoogleAuthUtil.clearToken(context, oldToken)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed clearing token", e)
        }
        val scope = "oauth2:https://mail.google.com/ https://www.googleapis.com/auth/userinfo.email"
        com.google.android.gms.auth.GoogleAuthUtil.getToken(context, email, scope)
    }

    private val db = Room.databaseBuilder(
        context.applicationContext,
        P2PDatabase::class.java,
        "p2p_chat_db"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.chatDao()

    val chatsFlow: Flow<List<P2PChat>> = dao.getChatsFlow()

    fun getMessagesForChat(partnerEmail: String): Flow<List<P2PMessage>> {
        return dao.getMessagesForChatFlow(partnerEmail.lowercase().trim())
    }

    suspend fun insertMessageLocal(message: P2PMessage) = withContext(Dispatchers.IO) {
        // First insert message
        dao.insertMessage(message)
        // Then upsert Chat partner metadata
        val chatPartner = message.partnerEmail.lowercase().trim()
        val existingChat = dao.getChatsFlow() // we can create a temporary upsert
        dao.insertChat(
            P2PChat(
                partnerEmail = chatPartner,
                lastMessage = message.body,
                lastUpdated = message.timestamp,
                unreadCount = 0
            )
        )
    }

    suspend fun createNewChat(partnerEmail: String) = withContext(Dispatchers.IO) {
        val partner = partnerEmail.lowercase().trim()
        dao.insertChat(
            P2PChat(
                partnerEmail = partner,
                lastMessage = "Чат создан",
                lastUpdated = System.currentTimeMillis(),
                unreadCount = 0
            )
        )
    }

    suspend fun deleteChatLocal(partnerEmail: String) = withContext(Dispatchers.IO) {
        val partner = partnerEmail.lowercase().trim()
        dao.deleteChat(partner)
        dao.deleteMessagesForChat(partner)
    }

    suspend fun clearAllLocal() = withContext(Dispatchers.IO) {
        dao.clearChats()
        dao.clearMessages()
    }

    suspend fun sendMessageSecurely(
        recipient: String,
        body: String,
        config: MailConfig
    ): P2PMessage = withContext(Dispatchers.IO) {
        var currentConfig = config
        var attempt = 1
        var lastException: Exception? = null

        while (attempt <= 2) {
            val props = connectionService.createSmtpProperties(currentConfig)
            val protocol = if (currentConfig.useSsl) "smtps" else "smtp"

            val session = Session.getInstance(props)
            session.debug = true
            val msg = MimeMessage(session)
            msg.setFrom(InternetAddress(currentConfig.email, currentConfig.displayName))
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
            msg.subject = "[P2P-Mail-Chat]"
            msg.setText(body, "UTF-8")

            val transport = session.getTransport(protocol)
            try {
                transport.connect(currentConfig.smtpHost, currentConfig.smtpPort, currentConfig.email, currentConfig.password)
                transport.sendMessage(msg, msg.allRecipients)
                transport.close()
                lastException = null
                break
            } catch (e: Exception) {
                lastException = e
                try { transport.close() } catch (ignored: Exception) {}
                if (currentConfig.isOauth && (e is AuthenticationFailedException || e.message?.contains("auth", ignoreCase = true) == true) && attempt == 1) {
                    Log.d("ChatRepository", "SMTP OAuth failed, attempting token refresh")
                    try {
                        val newToken = refreshOAuthToken(currentConfig.email, currentConfig.password)
                        val prefs = context.getSharedPreferences("p2p_mail_chat_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("password", newToken).apply()
                        currentConfig = currentConfig.copy(password = newToken)
                        attempt++
                        continue
                    } catch (tx: Exception) {
                        Log.e("ChatRepository", "Token refresh failed during SMTP send", tx)
                        throw tx
                    }
                } else {
                    throw e
                }
            }
        }

        if (attempt > 2 && lastException != null) {
            throw lastException
        }

        val timestamp = System.currentTimeMillis()
        val sentMessage = P2PMessage(
            partnerEmail = recipient.lowercase().trim(),
            senderEmail = currentConfig.email,
            recipientEmail = recipient,
            body = body,
            timestamp = timestamp
        )

        // Save locally immediately
        insertMessageLocal(sentMessage)
        sentMessage
    }

    suspend fun pollImapMessages(config: MailConfig): Int = withContext(Dispatchers.IO) {
        var currentConfig = config
        var attempt = 1
        var lastException: Exception? = null

        while (attempt <= 2) {
            val props = connectionService.createImapProperties(currentConfig)
            val protocol = if (currentConfig.useSsl) "imaps" else "imap"

            val session = Session.getInstance(props)
            val store = session.getStore(protocol)
            
            try {
                store.connect(currentConfig.imapHost, currentConfig.imapPort, currentConfig.email, currentConfig.password)
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)

                val messageCount = inbox.messageCount
                var newMessagesCount = 0

                if (messageCount > 0) {
                    val start = max(1, messageCount - 49) // Fetch last 50 emails
                    val end = messageCount
                    val messages = inbox.getMessages(start, end)

                    // Get already saved messages to avoid duplicates
                    val recentLocal = dao.getAllRecentMessages()
                    val existingFingerprints = recentLocal.map { "${it.senderEmail}_${it.timestamp}" }.toSet()

                    for (m in messages) {
                        try {
                            val subject = m.subject ?: ""
                            if (subject.contains("[P2P-Mail-Chat]", ignoreCase = true)) {
                                val sender = m.from?.firstOrNull()?.toString() ?: ""
                                val senderEmail = parseEmailAddress(sender)
                                val timestamp = m.sentDate?.time ?: m.receivedDate?.time ?: System.currentTimeMillis()

                                val fingerprint = "${senderEmail}_${timestamp}"
                                if (senderEmail.isNotEmpty() && 
                                    senderEmail.lowercase() != currentConfig.email.lowercase() && 
                                    !existingFingerprints.contains(fingerprint)) {

                                    // Parse email content
                                    val bodyText = extractTextFromMessage(m)
                                    val cleanMsg = P2PMessage(
                                        partnerEmail = senderEmail.lowercase().trim(),
                                        senderEmail = senderEmail,
                                        recipientEmail = currentConfig.email,
                                        body = bodyText.trim(),
                                        timestamp = timestamp
                                    )
                                    
                                    // Save to local Room
                                    dao.insertMessage(cleanMsg)
                                    
                                    // Upsert chat record
                                    dao.insertChat(
                                        P2PChat(
                                            partnerEmail = senderEmail.lowercase().trim(),
                                            lastMessage = cleanMsg.body,
                                            lastUpdated = cleanMsg.timestamp,
                                            unreadCount = 0
                                        )
                                    )
                                    newMessagesCount++
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ChatRepository", "Error parsing single email", e)
                        }
                    }
                }

                inbox.close(false)
                store.close()
                lastException = null
                return@withContext newMessagesCount
            } catch (e: Exception) {
                lastException = e
                try { store.close() } catch (ignored: Exception) {}
                if (currentConfig.isOauth && (e is AuthenticationFailedException || e.message?.contains("auth", ignoreCase = true) == true) && attempt == 1) {
                    Log.d("ChatRepository", "IMAP OAuth failed, attempting token refresh")
                    try {
                        val newToken = refreshOAuthToken(currentConfig.email, currentConfig.password)
                        val prefs = context.getSharedPreferences("p2p_mail_chat_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("password", newToken).apply()
                        currentConfig = currentConfig.copy(password = newToken)
                        attempt++
                        continue
                    } catch (tx: Exception) {
                        Log.e("ChatRepository", "Failed token refresh during IMAP poll", tx)
                        throw tx
                    }
                } else {
                    Log.e("ChatRepository", "Connection failed", e)
                    throw e
                }
            }
        }
        throw lastException ?: Exception("IMAP poll failed after token retries")
    }

    private fun parseEmailAddress(raw: String): String {
        val regex = "<([^>]+)>".toRegex()
        val match = regex.find(raw)
        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            raw.trim()
        }
    }

    private fun extractTextFromMessage(message: Message): String {
        return try {
            val content = message.content
            if (content is String) {
                content
            } else if (content is Multipart) {
                var text = ""
                for (i in 0 until content.count) {
                    val part = content.getBodyPart(i)
                    if (part.isMimeType("text/plain")) {
                        text += part.content.toString()
                    } else if (part.isMimeType("text/html")) {
                        // Minimal html strip
                        val html = part.content.toString()
                        text += html.replace("<[^>]*>".toRegex(), "")
                    }
                }
                text.ifEmpty { "[Многокомпонентное сообщение без текста]" }
            } else {
                "[Неподдерживаемый тип содержимого]"
            }
        } catch (e: Exception) {
            "Ошибка чтения содержимого письма: ${e.localizedMessage}"
        }
    }
}
