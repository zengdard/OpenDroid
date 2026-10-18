package com.opendroid.ui.terminal

import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.termux.TermuxBridge
import com.opendroid.tool.impl.Shell
import com.opendroid.tool.impl.ShellResult
import kotlinx.coroutines.launch

private enum class TerminalBackend(val label: String) {
    APP("app"),
    TERMUX("termux"),
    ROOT("root")
}

private data class TerminalEntry(
    val id: Long,
    val command: String,
    val backend: TerminalBackend,
    val running: Boolean,
    val result: ShellResult? = null
)

private val TerminalBg = Color(0xFF0B0B0D)
private val TerminalBar = Color(0xFF16161A)
private val TerminalText = Color(0xFFEDEDED)
private val TerminalDim = Color(0xFF8E8E93)
private val TerminalPrompt = Color(0xFF0A84FF)
private val TerminalError = Color(0xFFFF453A)
private val TerminalOk = Color(0xFF34C759)

/** Terminal intégré : shell de l'app, environnant Termux, ou root (`su`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val hasRoot = remember { Shell.hasRoot() }
    val hasTermux = remember { TermuxBridge.isInstalled(context) }
    val entries = remember { mutableStateListOf<TerminalEntry>() }
    var input by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf(TerminalBackend.APP) }
    var running by remember { mutableStateOf(false) }

    fun execute(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty() || running) return
        val entry = TerminalEntry(System.nanoTime(), cmd, backend, true)
        entries.add(entry)
        input = ""
        running = true
        val selected = backend
        scope.launch {
            val result = when (selected) {
                TerminalBackend.APP -> Shell.run(cmd, timeoutSec = 60, root = false)
                TerminalBackend.ROOT -> Shell.run(cmd, timeoutSec = 60, root = true)
                TerminalBackend.TERMUX -> TermuxBridge.run(context, cmd, timeoutSec = 120)
            }
            val index = entries.indexOfFirst { it.id == entry.id }
            if (index >= 0) entries[index] = entries[index].copy(running = false, result = result)
            running = false
        }
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Scaffold(
        containerColor = TerminalBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminal", color = TerminalText, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "UID ${Process.myUid()} · ${backend.label}",
                            color = TerminalDim,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = TerminalText)
                    }
                },
                actions = {
                    IconButton(onClick = { entries.clear() }) {
                        Icon(Icons.Default.DeleteSweep, "Effacer", tint = TerminalDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TerminalBar)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // Sélecteur de backend
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackendChip("App", backend == TerminalBackend.APP) { backend = TerminalBackend.APP }
                if (hasTermux) {
                    BackendChip("Termux", backend == TerminalBackend.TERMUX) { backend = TerminalBackend.TERMUX }
                }
                if (hasRoot) {
                    BackendChip("Root", backend == TerminalBackend.ROOT) { backend = TerminalBackend.ROOT }
                }
            }

            if (backend == TerminalBackend.TERMUX) {
                Text(
                    "Termux : allow-external-apps=true + permission RUN_COMMAND requise.",
                    color = TerminalDim,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }

            if (!hasTermux) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Termux non installé.",
                        color = TerminalDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                        )
                    }) {
                        Text("Installer (F-Droid)", color = TerminalPrompt, fontSize = 12.sp)
                    }
                }
            }

            if (entries.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "opendroid:~$",
                            color = TerminalDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when (backend) {
                                TerminalBackend.APP -> "Commandes exécutées avec l'UID de l'app."
                                TerminalBackend.TERMUX -> "Commandes exécutées dans Termux (bash, pkg, python…)."
                                TerminalBackend.ROOT -> "Commandes exécutées via su -c."
                            },
                            color = TerminalDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (entry.backend == TerminalBackend.ROOT) "# " else "$ ",
                                    color = TerminalPrompt,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    entry.command,
                                    color = TerminalText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                                if (entry.backend != TerminalBackend.APP) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        entry.backend.label,
                                        color = TerminalDim,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            if (entry.running) {
                                Text("…", color = TerminalDim, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            } else {
                                val result = entry.result
                                if (result != null) {
                                    if (result.stdout.isNotBlank()) {
                                        Text(
                                            result.stdout.trimEnd(),
                                            color = TerminalText,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    if (result.stderr.isNotBlank()) {
                                        Text(
                                            result.stderr.trimEnd(),
                                            color = TerminalError,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    val label = when {
                                        result.timedOut -> "[timeout]"
                                        result.exitCode == 0 -> "[exit 0]"
                                        else -> "[exit ${result.exitCode}]"
                                    }
                                    Text(
                                        label,
                                        color = if (result.ok) TerminalOk else TerminalDim,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Commandes rapides
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ls -la ~", "df -h", "uname -a").forEach { quick ->
                    TextButton(
                        onClick = { execute(quick) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(quick, color = TerminalPrompt, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }

            Surface(color = TerminalBar, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (backend == TerminalBackend.ROOT) "#" else "$",
                        color = TerminalPrompt,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("commande…", color = TerminalDim, fontFamily = FontFamily.Monospace) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = TerminalText,
                            fontSize = 14.sp
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TerminalPrompt,
                            unfocusedBorderColor = Color(0xFF38383A),
                            cursorColor = TerminalPrompt,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { execute(input) })
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { execute(input) }, enabled = input.isNotBlank() && !running) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Exécuter",
                            tint = if (input.isNotBlank() && !running) TerminalPrompt else TerminalDim
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = TerminalDim,
            selectedContainerColor = TerminalPrompt.copy(alpha = 0.18f),
            selectedLabelColor = TerminalPrompt
        )
    )
}
