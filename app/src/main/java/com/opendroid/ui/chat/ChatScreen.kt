package com.opendroid.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendroid.data.model.Message
import com.opendroid.data.model.Role
import com.opendroid.data.model.TodoStatus
import com.opendroid.data.registry.BuiltInAgents
import com.opendroid.ui.OpenDroidViewModel
import com.opendroid.ui.components.PermissionDialog
import com.opendroid.ui.components.QuestionDialog
import com.opendroid.ui.theme.spaceLG
import com.opendroid.ui.theme.spaceMD
import com.opendroid.ui.theme.spaceSM
import com.opendroid.ui.theme.spaceXL
import com.opendroid.ui.theme.spaceXS
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: OpenDroidViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var showAgentMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.initialize() }
    LaunchedEffect(state.messages.size, state.activities.size, state.isRunning) {
        val target = state.messages.size - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(spaceXL))
                    Text(
                        "OpenDroid",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = spaceLG),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Sessions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spaceLG)
                    )
                    Spacer(Modifier.height(spaceSM))
                    HorizontalDivider()
                    LazyColumn(Modifier.weight(1f)) {
                        items(sessions, key = { it.id }) { session ->
                            val active = session.id == state.sessionId
                            NavigationDrawerItem(
                                label = {
                                    Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                selected = active,
                                onClick = {
                                    viewModel.loadSession(session.id)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = spaceSM),
                                badge = {
                                    IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                        Icon(Icons.Default.Delete, "Supprimer", Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            viewModel.newSession()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth().padding(spaceSM)
                    ) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(spaceSM)); Text("Nouvelle session")
                    }
                    Spacer(Modifier.height(spaceSM))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("OpenDroid", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${state.providerId}/${state.model.ifBlank { "modèle par défaut" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    actions = {
                        Box {
                            AssistChip(
                                onClick = { showAgentMenu = true },
                                label = { Text(viewModel.currentAgent().name) }
                            )
                            DropdownMenu(expanded = showAgentMenu, onDismissRequest = { showAgentMenu = false }) {
                                BuiltInAgents.primary.forEach { agent ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(agent.name, fontWeight = FontWeight.Medium)
                                                Text(agent.description, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                            }
                                        },
                                        onClick = { viewModel.setAgent(agent.id); showAgentMenu = false },
                                        trailingIcon = { if (state.agentId == agent.id) Icon(Icons.Default.Check, null) }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showModelSheet = true }) { Icon(Icons.Default.Tune, "Modèle") }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Paramètres") }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.error != null) {
                    ErrorBanner(state.error!!)
                }
                if (state.todos.isNotEmpty()) {
                    TodoStrip(state.todos)
                }
                if (state.messages.isEmpty() && !state.isRunning) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = spaceSM),
                        verticalArrangement = Arrangement.spacedBy(spaceSM),
                        contentPadding = PaddingValues(vertical = spaceSM)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            when (message.role) {
                                Role.USER -> UserBubble(message)
                                Role.ASSISTANT -> AssistantMessage(message)
                                Role.TOOL -> ToolResultChip(message)
                                Role.SYSTEM -> {}
                            }
                        }
                        if (state.isRunning) {
                            item(key = "streaming") {
                                StreamingBubble(state.streamingThinking, state.streamingText, state.activities.map { it.name })
                            }
                        }
                    }
                }
                InputBar(
                    text = input,
                    onTextChange = { input = it },
                    onSend = { if (input.isNotBlank()) { viewModel.sendMessage(input); input = "" } },
                    onStop = { viewModel.stop() },
                    isRunning = state.isRunning
                )
            }
        }
    }

    state.pendingPermission?.let { req ->
        PermissionDialog(req) { granted -> viewModel.resolvePermission(granted) }
    }
    state.pendingQuestion?.let { q ->
        QuestionDialog(q, onSubmit = { viewModel.resolveQuestion(it) }, onDismiss = { viewModel.resolveQuestion("(annulé)") })
    }
    if (showModelSheet) {
        ProviderModelSheet(viewModel, onDismiss = { showModelSheet = false })
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(spaceXL)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("OD", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(spaceLG))
            Text("OpenDroid", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(spaceXS))
            Text(
                "Agent autonome pour Android. Ouvre des apps, agit sur l'écran, gère tes fichiers, le web et plus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }
    }
}

@Composable
private fun ErrorBanner(text: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(spaceSM), shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(spaceMD), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TodoStrip(todos: List<com.opendroid.data.model.Todo>) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(horizontal = spaceSM, vertical = spaceXS), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(spaceMD)) {
            Text("Tâches", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            todos.take(6).forEach { todo ->
                val mark = when (todo.status) {
                    TodoStatus.DONE -> "✓"
                    TodoStatus.IN_PROGRESS -> "▸"
                    TodoStatus.PENDING -> "•"
                }
                Text("$mark ${todo.content}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun UserBubble(message: Message) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.widthIn(max = 340.dp)) {
            Text(message.text, modifier = Modifier.padding(horizontal = spaceLG, vertical = spaceMD), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun AssistantMessage(message: Message) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(24.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("OD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(spaceSM))
            Text("OpenDroid", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(spaceXS))
        if (!message.thinking.isNullOrBlank()) {
            ThinkingBlock(message.thinking)
        }
        message.toolCalls.forEach { call ->
            ToolCallHeader(call.name, call.arguments)
        }
        if (message.text.isNotBlank()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 420.dp)) {
                MarkdownText(message.text, modifier = Modifier.padding(spaceMD), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ThinkingBlock(thinking: String) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }) {
        Text("💭 Raisonnement ${if (open) "▾" else "▸"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedVisibility(visible = open) {
            Text(
                thinking,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = spaceSM, top = spaceXS)
            )
        }
    }
}

@Composable
private fun ToolCallHeader(name: String, args: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.padding(horizontal = spaceSM, vertical = spaceXS), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(spaceXS))
            Text(name, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ToolResultChip(message: Message) {
    val bg = if (message.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = MaterialTheme.shapes.small, color = bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spaceSM)) {
            Text(message.toolName ?: "outil", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = fg, fontWeight = FontWeight.SemiBold)
            message.attachments.firstOrNull()?.let { attachment ->
                val bitmap = remember(attachment.path) { android.graphics.BitmapFactory.decodeFile(attachment.path) }
                if (bitmap != null) {
                    Spacer(Modifier.height(spaceXS))
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "capture d'écran",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(spaceXS))
                }
            }
            Text(message.text.take(1500), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = fg.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun StreamingBubble(thinking: String, text: String, activities: List<String>) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(spaceSM))
            Text("OpenDroid réfléchit…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (thinking.isNotBlank()) {
            Spacer(Modifier.height(spaceXS))
            ThinkingBlock(thinking)
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(spaceXS))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                MarkdownText(text, Modifier.padding(spaceMD).animateContentSize(), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun InputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit, isRunning: Boolean) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = spaceMD, vertical = spaceSM), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isRunning) "En cours…" else "Demandez quelque chose…") },
                enabled = !isRunning,
                maxLines = 4,
                shape = MaterialTheme.shapes.large
            )
            Spacer(Modifier.width(spaceSM))
            if (isRunning) {
                FilledIconButton(onClick = onStop) { Icon(Icons.Default.Stop, "Arrêter") }
            } else {
                FilledIconButton(onClick = onSend, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Envoyer") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderModelSheet(viewModel: OpenDroidViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val providers = viewModel.providerList()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selected = providers.firstOrNull { it.id == state.providerId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = spaceLG).padding(bottom = spaceXL)) {
            Text("Provider", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(spaceSM))
            providers.forEach { provider ->
                NavigationDrawerItem(
                    label = { Text(provider.name) },
                    selected = provider.id == state.providerId,
                    onClick = { viewModel.setProvider(provider.id) },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(spaceMD))
            Text("Modèle", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(spaceSM))
            (selected?.models ?: emptyList()).forEach { model ->
                NavigationDrawerItem(
                    label = { Text(model) },
                    selected = model == state.model,
                    onClick = { viewModel.setModel(model) },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(spaceSM))
            Text(
                "Ajoutez vos clés API dans Paramètres → Providers.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
