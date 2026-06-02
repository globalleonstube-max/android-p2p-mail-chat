package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.P2PChat
import com.example.data.P2PMessage
import com.example.ui.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val chats by viewModel.chats.collectAsState()
    val activePartner by viewModel.activePartnerEmail.collectAsState()
    val connectionConfig by viewModel.connectionConfig.collectAsState()
    val isPolling by viewModel.isPolling.collectAsState()
    val pollingError by viewModel.pollingError.collectAsState()

    var showAddChatDialog by remember { mutableStateOf(false) }
    var newChatEmail by remember { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 640

    // Master layout split for adaptive design
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "P2P Mail Chat",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        connectionConfig?.let {
                            Text(
                                text = "Узел: ${it.email}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.manualRefresh() }) {
                        if (isPolling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить почту"
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Выйти из аккаунта",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (activePartner == null || isWideScreen) {
                FloatingActionButton(
                    onClick = { showAddChatDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_chat_fab")
                ) {
                    Icon(imageVector = Icons.Default.AddComment, contentDescription = "Новый диалог")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Polling Status bar / error notice if any
                AnimatedVisibility(visible = pollingError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(vertical = 6.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = pollingError ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (isWideScreen) {
                    // SIDE-BY-SIDE TAB LAYOUT
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Column: Chat lists
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            ChatListPane(
                                chats = chats,
                                activePartner = activePartner,
                                onSelectChat = { viewModel.selectChat(it) },
                                onDeleteChat = { viewModel.deleteChat(it) }
                            )
                        }

                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Right Column: Conversation detail
                        Box(modifier = Modifier.weight(2.5f).fillMaxHeight()) {
                            if (activePartner != null) {
                                ActiveConversationPane(
                                    viewModel = viewModel,
                                    partnerEmail = activePartner!!,
                                    onBackClicked = { viewModel.selectChat(null) }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Forum,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Выберите собеседника",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = "Или создайте новую сессию чата, чтобы отправлять письма-сообщения.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp),
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // PHONE SINGLE DETAILE VIEW SWITCHER
                    AnimatedContent(
                        targetState = activePartner,
                        label = "ChatScreenTransition"
                    ) { partner ->
                        if (partner == null) {
                            ChatListPane(
                                chats = chats,
                                activePartner = null,
                                onSelectChat = { viewModel.selectChat(it) },
                                onDeleteChat = { viewModel.deleteChat(it) }
                            )
                        } else {
                            ActiveConversationPane(
                                viewModel = viewModel,
                                partnerEmail = partner,
                                onBackClicked = { viewModel.selectChat(null) }
                            )
                        }
                    }
                }
            }
        }

        // Add Chat Dialog
        if (showAddChatDialog) {
            AlertDialog(
                onDismissRequest = { showAddChatDialog = false },
                shape = RoundedCornerShape(24.dp),
                title = { Text(text = "Начать новый P2P диалог") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Введите адрес электронной почты вашего собеседника. Первое сообщение создаст защищенный канал обмена.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = newChatEmail,
                            onValueChange = { newChatEmail = it },
                            label = { Text("Email собеседника") },
                            placeholder = { Text("friend@domain.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_chat_email_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newChatEmail.trim().isNotEmpty()) {
                                viewModel.startNewChat(newChatEmail.trim())
                                newChatEmail = ""
                                showAddChatDialog = false
                            }
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = newChatEmail.contains("@") && newChatEmail.length > 5
                    ) {
                        Text("Создать диалог")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddChatDialog = false }) {
                        Text("Отмена", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListPane(
    chats: List<P2PChat>,
    activePartner: String?,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    if (chats.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Нет активных чатов",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Используйте кнопку добавления внизу, чтобы начать децентрализованный диалог по Email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            items(chats, key = { it.partnerEmail }) { chat ->
                var showDeleteConfirm by remember { mutableStateOf(false) }

                val isSelected = chat.partnerEmail == activePartner
                val timeString = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(chat.lastUpdated))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onSelectChat(chat.partnerEmail) },
                                onLongClick = { showDeleteConfirm = true }
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chat.partnerEmail.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = chat.partnerEmail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = chat.lastMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            if (chat.unreadCount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = chat.unreadCount.toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Удалить диалог?") },
                        text = { Text("Вы действительно хотите удалить этот чат с ${chat.partnerEmail} и удалить всю историю сообщений локально?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onDeleteChat(chat.partnerEmail)
                                    showDeleteConfirm = false
                                }
                            ) {
                                Text("Да, удалить", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("Отмена")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveConversationPane(
    viewModel: ChatViewModel,
    partnerEmail: String,
    onBackClicked: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isSending by viewModel.sendingMessage.collectAsState()
    val sendError by viewModel.sendMessageError.collectAsState()

    var inputBody by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Conversation Top Header Bar (specifically for Compact layouts, on Wide screens behaves nicely)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Назад")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partnerEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Защищенный P2P канал обмена",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Messages list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Нет сообщений",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Отправьте первое сообщение, чтобы инициировать SMTP/IMAP P2P Mail Chat.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isMe = message.senderEmail.lowercase().trim() != partnerEmail.lowercase().trim()
                        
                        val msgHeaderFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val timeStr = msgHeaderFormat.format(Date(message.timestamp))

                        val bubbleShape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 2.dp,
                            bottomEnd = if (isMe) 2.dp else 16.dp
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(bubbleShape)
                                    .background(
                                        if (isMe) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.secondaryContainer
                                    )
                                    .let { modifier ->
                                        if (!isMe) modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), bubbleShape)
                                        else modifier
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = message.body,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = (if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSecondaryContainer).copy(alpha = 0.6f),
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sending state error
        AnimatedVisibility(visible = sendError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(vertical = 6.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = sendError ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Input Field Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputBody,
                onValueChange = { inputBody = it },
                placeholder = { Text("Напишите сообщение...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("message_input")
                    .windowInsetsPadding(WindowInsets.navigationBars),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default
                ),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputBody.trim().isNotEmpty()) {
                        viewModel.sendMessage(inputBody.trim())
                        inputBody = ""
                    }
                },
                enabled = !isSending && inputBody.trim().isNotEmpty(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputBody.trim().isEmpty() || isSending) MaterialTheme.colorScheme.outlineVariant
                        else MaterialTheme.colorScheme.primary
                    )
                    .testTag("send_message_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (inputBody.trim().isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
