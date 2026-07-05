package com.raissummit.orchestrator.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raissummit.orchestrator.ui.components.ConfirmationDialog
import com.raissummit.orchestrator.ui.components.FollowUpDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val conversations by viewModel.conversationManager.conversations.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val activeConvId by viewModel.conversationManager.activeConversationId.collectAsStateWithLifecycle(
        initialValue = ""
    )
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !uiState.isProcessing,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Text(
                    text = "Conversations",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(conversations) { conv ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                            label = {
                                Text(
                                    text = conv.title,
                                    maxLines = 1,
                                    style = if (conv.id == activeConvId) MaterialTheme.typography.titleSmall
                                    else MaterialTheme.typography.bodyMedium
                                )
                            },
                            selected = conv.id == activeConvId,
                            onClick = {
                                scope.launch {
                                    viewModel.conversationManager.switchConversation(conv.id)
                                    viewModel.loadConversation(conv.id)
                                    drawerState.close()
                                }
                            },
                            badge = {
                                if (conversations.size > 1) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                viewModel.conversationManager.deleteConversation(conv.id)
                                                viewModel.loadConversation(
                                                    activeConvId
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Supprimer",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
                HorizontalDivider()
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.resetConversation()
                            val id = viewModel.conversationManager.createConversation()
                            drawerState.close()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nouvelle conversation")
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("RAIS Orchestrator") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Chat, contentDescription = "Conversations")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.messages) { message ->
                        ChatBubble(
                            message = message,
                            isLatest = message == uiState.messages.lastOrNull()
                        )
                    }

                    if (uiState.streamingText != null) {
                        item {
                            StreamingBubble(
                                text = uiState.streamingText ?: "",
                                thinking = uiState.streamingThinking
                            )
                        }
                    }
                }

                InputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = !uiState.isProcessing
                )
            }
        }
    }

    if (uiState.confirmationMessage != null) {
        ConfirmationDialog(
            message = uiState.confirmationMessage!!,
            onConfirm = { viewModel.confirmAction() },
            onDismiss = { viewModel.cancelAction() }
        )
    }

    if (uiState.followUpQuestion != null) {
        FollowUpDialog(
            question = uiState.followUpQuestion!!,
            onSubmit = { answer -> viewModel.provideFollowUpAnswer(answer) },
            onDismiss = { viewModel.cancelAction() }
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, isLatest: Boolean) {
    val isUser = message is ChatMessage.User
    val isError = message is ChatMessage.Error
    val isTool = message is ChatMessage.ToolResult

    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )

    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isTool -> MaterialTheme.colorScheme.tertiaryContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isTool -> MaterialTheme.colorScheme.onTertiaryContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Show thinking/CoT for assistant messages
        if (message is ChatMessage.Assistant && message.thinking != null) {
            CotSection(thinking = message.thinking)
        }

        Surface(
            shape = shape,
            color = containerColor,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = when (message) {
                    is ChatMessage.User -> message.text
                    is ChatMessage.Assistant -> message.text
                    is ChatMessage.Error -> "⚠ ${message.text}"
                    is ChatMessage.ToolResult -> "🔧 [${message.toolName}] ${message.result}"
                    is ChatMessage.Streaming -> ""
                },
                modifier = Modifier.padding(12.dp),
                color = contentColor,
                fontFamily = if (isTool) FontFamily.Monospace else FontFamily.Default,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun StreamingBubble(text: String, thinking: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (thinking != null) {
            CotSection(thinking = thinking)
        }
        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 14.sp,
                    maxLines = 10
                )
            }
        }
    }
}

@Composable
private fun CotSection(thinking: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .widthIn(max = 320.dp)
            .padding(bottom = 4.dp)
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) "▼ Raisonnement" else "▶ Raisonnement",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = thinking,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tapez votre requête…") },
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Envoyer")
            }
        }
    }
}
