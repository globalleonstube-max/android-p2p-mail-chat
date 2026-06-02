package com.example.data

import android.content.Context
import android.util.Log
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of the connection verification process for SMTP/IMAP servers.
 */
sealed class ConnectionResult {
    object Success : ConnectionResult()
    data class Failure(
        val type: FailureType,
        val message: String,
        val cause: Throwable? = null
    ) : ConnectionResult()
}

/**
 * Categorization of potential mail connection failure modes.
 */
enum class FailureType {
    AUTHENTICATION,
    CONNECTION_TIMEOUT,
    SSL_HANDSHAKE,
    UNKNOWN_HOST,
    UNKNOWN
}

/**
 * Pre-configured presets for popular email providers to ease node configuration.
 */
data class ProviderPresetInfo(
    val name: String,
    val domain: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val useSsl: Boolean,
    val infoMessage: String
)

object MailPresets {
    val list = listOf(
        ProviderPresetInfo(
            name = "Gmail",
            domain = "@gmail.com",
            imapHost = "imap.gmail.com",
            imapPort = 993,
            smtpHost = "smtp.gmail.com",
            smtpPort = 465,
            useSsl = true,
            infoMessage = "Requires Google App Password or secure OAuth2 login. Normal password is not accepted."
        ),
        ProviderPresetInfo(
            name = "Yandex",
            domain = "@yandex.ru",
            imapHost = "imap.yandex.ru",
            imapPort = 993,
            smtpHost = "smtp.yandex.ru",
            smtpPort = 465,
            useSsl = true,
            infoMessage = "Requires App Password from Yandex ID Security page and enabling IMAP in Mail settings."
        ),
        ProviderPresetInfo(
            name = "Mail.ru",
            domain = "@mail.ru",
            imapHost = "imap.mail.ru",
            imapPort = 993,
            smtpHost = "smtp.mail.ru",
            smtpPort = 465,
            useSsl = true,
            infoMessage = "Requires custom App Password created in Mail.ru Security Settings."
        ),
        ProviderPresetInfo(
            name = "Outlook",
            domain = "@outlook.com",
            imapHost = "outlook.office365.com",
            imapPort = 993,
            smtpHost = "smtp.office365.com",
            smtpPort = 587,
            useSsl = false,
            infoMessage = "Requires enabling STARTTLS protocol and using App Password if 2FA is active."
        ),
        ProviderPresetInfo(
            name = "Yahoo",
            domain = "@yahoo.com",
            imapHost = "imap.mail.yahoo.com",
            imapPort = 993,
            smtpHost = "smtp.mail.yahoo.com",
            smtpPort = 465,
            useSsl = true,
            infoMessage = "Requires generating an App Password in your Yahoo Account Security tab."
        )
    )

    fun findPresetForEmail(email: String): ProviderPresetInfo? {
        val trimmed = email.trim().lowercase()
        return list.firstOrNull { trimmed.endsWith(it.domain) }
    }
}

/**
 * Service dedicated to managing SMTP & IMAP connections, validating hosts, 
 * standardizing configuration, and isolating network transport layers.
 */
class ConnectionService(private val context: Context) {

    /**
     * Build appropriate IMAP properties for standard sessions.
     */
    fun createImapProperties(config: MailConfig): Properties {
        val props = Properties()
        val protocol = if (config.useSsl) "imaps" else "imap"
        props["mail.store.protocol"] = protocol
        
        if (config.isOauth) {
            props["mail.imap.auth.mechanisms"] = "XOAUTH2"
            props["mail.imaps.auth.mechanisms"] = "XOAUTH2"
        } else {
            if (config.useSsl) {
                props["mail.imap.socketFactory.port"] = config.imapPort.toString()
                props["mail.imap.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
                props["mail.imap.ssl.enable"] = "true"
            } else {
                props["mail.imap.starttls.enable"] = "true"
            }
        }
        
        props["mail.imap.connectiontimeout"] = "10000"
        props["mail.imap.timeout"] = "10000"
        props["mail.imaps.connectiontimeout"] = "10000"
        props["mail.imaps.timeout"] = "10000"
        return props
    }

    /**
     * Build appropriate SMTP properties for standard sessions.
     */
    fun createSmtpProperties(config: MailConfig): Properties {
        val props = Properties()
        val protocol = if (config.useSsl) "smtps" else "smtp"
        props["mail.transport.protocol"] = protocol
        
        val protocols = listOf("smtp", "smtps")
        for (proto in protocols) {
            props["mail.$proto.auth"] = "true"
            props["mail.$proto.host"] = config.smtpHost
            props["mail.$proto.port"] = config.smtpPort.toString()
            props["mail.$proto.connectiontimeout"] = "10000"
            props["mail.$proto.timeout"] = "10000"
            props["mail.$proto.ssl.trust"] = "*"
            props["mail.$proto.ssl.protocols"] = "TLSv1.2 TLSv1.3"
            
            if (config.isOauth) {
                props["mail.$proto.auth.mechanisms"] = "XOAUTH2"
            }

            if (config.useSsl) {
                props["mail.$proto.ssl.enable"] = "true"
                props["mail.$proto.socketFactory.port"] = config.smtpPort.toString()
                props["mail.$proto.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
                props["mail.$proto.socketFactory.fallback"] = "false"
            } else {
                props["mail.$proto.starttls.enable"] = "true"
                props["mail.$proto.starttls.required"] = "true"
            }
        }
        return props
    }

    /**
     * Evaluates a JavaMail exception to identify the likely root cause.
     */
    fun classifyException(e: Exception): FailureType {
        val msg = e.message?.lowercase() ?: ""
        return when {
            e is AuthenticationFailedException || 
            msg.contains("auth") || 
            msg.contains("username or password") || 
            msg.contains("fail") || 
            msg.contains("credential") ||
            msg.contains("invalid") -> {
                FailureType.AUTHENTICATION
            }
            e is javax.net.ssl.SSLHandshakeException || 
            msg.contains("ssl") || 
            msg.contains("handshake") || 
            msg.contains("pkix") || 
            msg.contains("certificate") -> {
                FailureType.SSL_HANDSHAKE
            }
            e is java.net.UnknownHostException || 
            msg.contains("unreachable") || 
            msg.contains("unknown host") || 
            msg.contains("could not resolve") || 
            msg.contains("dns") -> {
                FailureType.UNKNOWN_HOST
            }
            msg.contains("timeout") || 
            msg.contains("timed out") || 
            msg.contains("connect") || 
            msg.contains("refused") -> {
                FailureType.CONNECTION_TIMEOUT
            }
            else -> FailureType.UNKNOWN
        }
    }

    /**
     * Performs direct sequential tests of both IMAP and SMTP connection parameters.
     */
    suspend fun verifyConnection(config: MailConfig): ConnectionResult = withContext(Dispatchers.IO) {
        // 1. Verify IMAP protocol
        val imapProtocol = if (config.useSsl) "imaps" else "imap"
        val imapProps = createImapProperties(config)
        val imapSession = Session.getInstance(imapProps)
        var store: Store? = null
        try {
            store = imapSession.getStore(imapProtocol)
            store.connect(config.imapHost, config.imapPort, config.email, config.password)
        } catch (e: Exception) {
            Log.e("ConnectionService", "IMAP connection failure during validation", e)
            val failureType = classifyException(e)
            val friendlyMsg = when (failureType) {
                FailureType.AUTHENTICATION -> "Неверные данные для входа IMAP. Убедитесь, что логин и пароль верны. Если включена двухфакторная аутентификация, используйте 'Пароль приложения'."
                FailureType.SSL_HANDSHAKE -> "Сбой шифрованного подключения SSL к серверу IMAP ${config.imapHost}. Проверьте порт (${config.imapPort}) или выключите SSL."
                FailureType.UNKNOWN_HOST -> "Не удалось распознать адрес хоста IMAP: ${config.imapHost}. Проверьте правильность написания."
                FailureType.CONNECTION_TIMEOUT -> "Время ожидания ответа от сервера IMAP ${config.imapHost} истекло. Проверьте подключение к сети и порт."
                FailureType.UNKNOWN -> "Сбой подключения к IMAP: ${e.localizedMessage}"
            }
            return@withContext ConnectionResult.Failure(failureType, friendlyMsg, e)
        } finally {
            try { store?.close() } catch (ignored: Exception) {}
        }

        // 2. Verify SMTP protocol
        val smtpProtocol = if (config.useSsl) "smtps" else "smtp"
        val smtpProps = createSmtpProperties(config)
        val smtpSession = Session.getInstance(smtpProps)
        var transport: Transport? = null
        try {
            transport = smtpSession.getTransport(smtpProtocol)
            transport.connect(config.smtpHost, config.smtpPort, config.email, config.password)
        } catch (e: Exception) {
            Log.e("ConnectionService", "SMTP connection failure during validation", e)
            val failureType = classifyException(e)
            val friendlyMsg = when (failureType) {
                FailureType.AUTHENTICATION -> "Неверные данные аутентификации SMTP. Многие провайдеры требуют генерации отдельного кода для отправки писем."
                FailureType.SSL_HANDSHAKE -> "Сбой секретного обмена TLS/SSL с сервером SMTP ${config.smtpHost}. Проверьте порт (${config.smtpPort})."
                FailureType.UNKNOWN_HOST -> "Неверный сервер исходящей почты SMTP: ${config.smtpHost}. Проверьте доменное имя."
                FailureType.CONNECTION_TIMEOUT -> "Не удалось подключиться к серверу SMTP ${config.smtpHost} на порту ${config.smtpPort}. Порт закрыт или заблокирован."
                FailureType.UNKNOWN -> "Сбой подключения к SMTP: ${e.localizedMessage}"
            }
            return@withContext ConnectionResult.Failure(failureType, friendlyMsg, e)
        } finally {
            try { transport?.close() } catch (ignored: Exception) {}
        }

        return@withContext ConnectionResult.Success
    }
}
