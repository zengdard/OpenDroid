package com.opendroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendroid.data.model.McpServerConfig
import com.opendroid.ui.OpenDroidViewModel
import com.opendroid.ui.theme.CardShape
import com.opendroid.ui.theme.spaceLG
import com.opendroid.ui.theme.spaceSM
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(vm: OpenDroidViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val servers by vm.settings().mcpServers.collectAsStateWithLifecycle(initialValue = emptyList())
    val status by vm.mcpStatus.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Serveurs MCP") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    IconButton(onClick = { vm.refreshMcp() }) { Icon(Icons.Default.Refresh, "Reconnecter") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spaceLG),
            verticalArrangement = Arrangement.spacedBy(spaceSM)
        ) {
            item {
                Text(
                    "Model Context Protocol — connecte des outils externes (HTTP JSON-RPC). " +
                        "Chaque outil découvert devient disponible pour l'agent (clé de permission « mcp »).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(servers, key = { it.id }) { server ->
                Card(Modifier.fillMaxWidth(), shape = CardShape) {
                    Column(Modifier.padding(spaceLG)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    server.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                status[server.name]?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (it.startsWith("✅")) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Switch(
                                checked = server.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { vm.settings().setMcpEnabled(server.id, enabled) }
                                }
                            )
                            IconButton(onClick = {
                                scope.launch { vm.settings().removeMcpServer(server.id) }
                            }) { Icon(Icons.Default.Delete, "Supprimer") }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(spaceSM))
                Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(spaceSM))
                    Text("Ajouter un serveur MCP")
                }
            }
        }
    }

    if (showAdd) {
        AddMcpDialog(
            onDismiss = { showAdd = false },
            onSave = { name, url, auth ->
                val headers = buildMap {
                    val trimmed = auth.trim()
                    if (trimmed.isNotEmpty()) {
                        val idx = trimmed.indexOf(':')
                        if (idx > 0) put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim())
                        else put("Authorization", trimmed)
                    }
                }
                vm.settings().let {
                    scope.launch {
                        it.upsertMcpServer(
                            McpServerConfig(
                                id = UUID.randomUUID().toString().take(8),
                                name = name,
                                url = url,
                                headers = headers
                            )
                        )
                        showAdd = false
                    }
                }
            }
        )
    }
}

@Composable
private fun AddMcpDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, auth: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau serveur MCP") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nom") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(spaceSM))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("URL HTTP (ex. http://192.168.1.41:3000/mcp)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Spacer(Modifier.height(spaceSM))
                OutlinedTextField(
                    value = auth, onValueChange = { auth = it },
                    label = { Text("En-tête (optionnel)") },
                    placeholder = { Text("Authorization: Bearer …") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = { onSave(name.trim(), url.trim(), auth) }
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
