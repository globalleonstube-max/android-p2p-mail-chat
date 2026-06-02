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
    val password: String
)

class ChatRepository(context: Context) {

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
        val props = Properties()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.host"] = config.smtpHost
        props["mail.smtp.port"] = config.smtpPort.toString()
        
        if (config.useSsl) {
            props["mail.smtp.socketFactory.port"] = config.smtpPort.toString()
            props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
            props["mail.smtp.ssl.enable"] = "true"
        } else {
            props["mail.smtp.starttls.enable"] = "true"
        }

        // Timeout settings
        props["mail.smtp.connectiontimeout"] = "10000"
        props["mail.smtp.timeout"] = "10000"

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(config.email, config.password)
            }
        })

        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(config.email, config.displayName))
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
        msg.subject = "[P2P-Mail-Chat]"
        msg.setText(body, "UTF-8")

        Transport.send(msg)

        val timestamp = System.currentTimeMillis()
        val sentMessage = P2PMessage(
            partnerEmail = recipient.lowercase().trim(),
            senderEmail = config.email,
            recipientEmail = recipient,
            body = body,
            timestamp = timestamp
        )

        // Save locally immediately
        insertMessageLocal(sentMessage)
        sentMessage
    }

    suspend fun pollImapMessages(config: MailConfig): Int = withContext(Dispatchers.IO) {
        val props = Properties()
        props["mail.store.protocol"] = if (config.useSsl) "imaps" else "imap"
        
        if (config.useSsl) {
            props["mail.imap.socketFactory.port"] = config.imapPort.toString()
            props["mail.imap.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
            props["mail.imap.ssl.enable"] = "true"
        } else {
            props["mail.imap.starttls.enable"] = "true"
        }

        // Timeout settings
        props["mail.imap.connectiontimeout"] = "10000"
        props["mail.imap.timeout"] = "10000"

        val session = Session.getInstance(props)
        val store = session.getStore(if (config.useSsl) "imaps" else "imap")
        
        try {
            store.connect(config.imapHost, config.imapPort, config.email, config.password)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Connection failed", e)
            throw e
        }

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
                            senderEmail.lowercase() != config.email.lowercase() && 
                            !existingFingerprints.contains(fingerprint)) {

                            // Parse email content
                            val bodyText = extractTextFromMessage(m)
                            val cleanMsg = P2PMessage(
                                partnerEmail = senderEmail.lowercase().trim(),
                                senderEmail = senderEmail,
                                recipientEmail = config.email,
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
        newMessagesCount
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
