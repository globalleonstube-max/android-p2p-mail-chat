package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MailConfig
import com.example.ui.ChatViewModel

data class ProviderPreset(
    val name: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val ssl: Boolean
)

val PRESETS = listOf(
    ProviderPreset("Gmail", "imap.gmail.com", 993, "smtp.gmail.com", 465, true),
    ProviderPreset("Yandex", "imap.yandex.ru", 993, "smtp.yandex.ru", 465, true),
    ProviderPreset("Mail.ru", "imap.mail.ru", 993, "smtp.mail.ru", 465, true),
    ProviderPreset("Outlook", "outlook.office365.com", 993, "smtp.office365.com", 587, false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val isConnecting by viewModel.isConnecting.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var imapHost by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("993") }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("465") }
    var useSsl by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val gradientBg = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlternateEmail,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "P2P Mail Node",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBg)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🌐 SMTP/IMAP Чат-Узел",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Это приложение превращает обычный почтовый ящик в децентрализованный защищенный узел P2P-коммуникации. Все ваши сообщения пересылаются и синхронизируются через зашифрованные SMTP/IMAP протоколы.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Error message
                if (loginError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Ошибка",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = loginError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Provider Presets
                Text(
                    text = "Быстрые шаблоны провайдеров",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start),
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESETS.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable {
                                    imapHost = preset.imapHost
                                    imapPort = preset.imapPort.toString()
                                    smtpHost = preset.smtpHost
                                    smtpPort = preset.smtpPort.toString()
                                    useSsl = preset.ssl
                                    if (email.contains("@") && !email.endsWith("@gmail.com") && !email.endsWith("@yandex.ru") && !email.endsWith("@mail.ru") && !email.endsWith("@outlook.com")) {
                                        // User had typed some custom email, keep it
                                    } else {
                                        // Suggest domain
                                        val extension = when (preset.name) {
                                            "Gmail" -> "@gmail.com"
                                            "Yandex" -> "@yandex.ru"
                                            "Mail.ru" -> "@mail.ru"
                                            "Outlook" -> "@outlook.com"
                                            else -> ""
                                        }
                                        if (email.substringBefore("@").isNotEmpty()) {
                                            email = email.substringBefore("@") + extension
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Connection details fields
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Адрес электронной почты") },
                    placeholder = { Text("your_email@example.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_input")
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Имя отправителя (Опционально)") },
                    placeholder = { Text("Илья Децентрализованный") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль или Пароль приложения") },
                    placeholder = { Text("16-значный пароль приложения") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Скрыть пароль" else "Показать пароль",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input")
                )

                Text(
                    text = "⚠️ Внимание: Для Gmail/Yandex/Mail.ru требуется сгенерировать специальный \"Пароль приложения\" (App Password) в настройках безопасности вашего аккаунта. Ообычный пароль от входа не подойдет.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Параметры серверов",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = imapHost,
                                onValueChange = { imapHost = it },
                                label = { Text("IMAP Сервер") },
                                placeholder = { Text("imap.example.com") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = imapPort,
                                onValueChange = { imapPort = it },
                                label = { Text("Порт IMAP") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = smtpHost,
                                onValueChange = { smtpHost = it },
                                label = { Text("SMTP Сервер") },
                                placeholder = { Text("smtp.example.com") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = smtpPort,
                                onValueChange = { smtpPort = it },
                                label = { Text("Порт SMTP") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { useSsl = !useSsl }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = useSsl,
                                onCheckedChange = { useSsl = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "Использовать безопасное подключение SSL/TLS",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val iPort = imapPort.toIntOrNull() ?: 993
                        val sPort = smtpPort.toIntOrNull() ?: 465
                        if (email.isNotEmpty() && password.isNotEmpty() && imapHost.isNotEmpty() && smtpHost.isNotEmpty()) {
                            viewModel.login(
                                MailConfig(
                                    email = email.trim(),
                                    displayName = displayName.trim(),
                                    imapHost = imapHost.trim(),
                                    imapPort = iPort,
                                    smtpHost = smtpHost.trim(),
                                    smtpPort = sPort,
                                    useSsl = useSsl,
                                    password = password.trim()
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("login_button"),
                    enabled = !isConnecting && email.isNotEmpty() && password.isNotEmpty() && imapHost.isNotEmpty() && smtpHost.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(50)  // Tailwind 'rounded-full' capsule buttons
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Авторизация...")
                    } else {
                        Icon(Icons.Default.Power, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Создать узел и войти",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

