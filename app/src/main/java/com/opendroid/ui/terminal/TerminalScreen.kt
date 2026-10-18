package com.opendroid.ui.terminal

import android.os.Process
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.tool.impl.Shell
import com.opendroid.tool.impl.ShellResult
import kotlinx.coroutines.launch

private data class TerminalEntry(
    val id: Long,
    val command: String,
    val root: Boolean,
    val running: Boolean,
    val result: ShellResult? = null
)

private val TerminalBg = Color(0xFF0B0B0D)
private val TerminalText = Color(0xFFEDEDED)
private val TerminalDim = Color(0xFF8E8E93)
private val TerminalPrompt = Color(0xFF0A84FF)
private val TerminalError = Color(0xFFFF453A)
private val TerminalOk = Color(0xFF34C759)

/** Terminal intégré : exécute des commandes shell, avec option root (`su`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val hasRoot = remember { Shell.hasRoot() }
    val entries = remember { mutableStateListOf<TerminalEntry>() }
    var input by remember { mutableStateOf("") }
    var root by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }

    fun execute(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty() || running) return
        val entry = TerminalEntry(System.nanoTime(), cmd, root, true)
        entries.add(entry)
        input = ""
        running = true
        scope.launch {
            val result = Shell.run(cmd, timeoutSec = 60, root = root)
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
                            "UID ${Process.myUid()}${if (root) " · root" else " · utilisateur app"}",
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
                    if (hasRoot) {
                        Text("root", color = TerminalDim, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(6.dp))
                        Switch(checked = root, onCheckedChange = { root = it })
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { entries.clear() }) {
                        Icon(Icons.Default.DeleteSweep, "Effacer", tint = TerminalDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TerminalBg)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
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
                            if (hasRoot) "Root disponible — active l'interrupteur pour su."
                            else "Commandes exécutées avec l'UID de l'app (pas de root).",
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
                                    if (entry.root) "# " else "$ ",
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
                listOf("ls -la /sdcard", "df -h", "dumpsys battery | head -12").forEach { quick ->
                    TextButton(onClick = { execute(quick) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(quick, color = TerminalPrompt, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }

            Surface(color = Color(0xFF1C1C1E), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (root) "#" else "$",
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
