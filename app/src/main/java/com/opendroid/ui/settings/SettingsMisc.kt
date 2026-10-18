package com.opendroid.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendroid.data.model.PermissionConfig
import com.opendroid.data.model.PermissionLevel
import com.opendroid.data.registry.BuiltInAgents
import com.opendroid.tool.ToolRegistry
import com.opendroid.ui.OpenDroidViewModel
import com.opendroid.ui.theme.CardShape
import com.opendroid.ui.theme.spaceLG
import com.opendroid.ui.theme.spaceSM
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(vm: OpenDroidViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Agents") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(spaceLG), verticalArrangement = Arrangement.spacedBy(spaceSM)) {
            item { Text("Agents primaires", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
            items(BuiltInAgents.primary, key = { it.id }) { agent ->
                Card(Modifier.fillMaxWidth().clickable { scope.launch { vm.setAgent(agent.id) } }, shape = CardShape) {
                    Row(Modifier.padding(spaceLG), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(agent.name, style = MaterialTheme.typography.titleMedium)
                            Text(agent.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.agentId == agent.id) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Spacer(Modifier.height(spaceSM)); Text("Sous-agents", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
            items(BuiltInAgents.subagents, key = { it.id }) { agent ->
                Card(Modifier.fillMaxWidth(), shape = CardShape) {
                    Column(Modifier.padding(spaceLG)) {
                        Text("${agent.name}  ·  @${agent.id}", style = MaterialTheme.typography.titleMedium)
                        Text(agent.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (agent.permission.entries.isNotEmpty()) {
                            Spacer(Modifier.height(spaceSM))
                            Text(
                                agent.permission.entries.entries.joinToString("  ") { "${it.key}:${it.value.last().level.shortLabel}" },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(vm: OpenDroidViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val config by vm.settings().permissionConfig.collectAsStateWithLifecycle(initialValue = PermissionConfig())
    val autoApprove by vm.settings().autoApprove.collectAsStateWithLifecycle(initialValue = false)
    val levels = listOf(PermissionLevel.ALLOW, PermissionLevel.ASK, PermissionLevel.DENY)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Permissions") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(spaceLG), verticalArrangement = Arrangement.spacedBy(spaceSM)) {
            item {
                Card(shape = CardShape) {
                    Row(Modifier.padding(spaceLG), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-approuver", style = MaterialTheme.typography.titleMedium)
                            Text("Ignore les demandes « ask »", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoApprove, onCheckedChange = { vm.toggleAutoApprove(it) })
                    }
                }
            }
            item {
                Text(
                    "Les règles globales s'appliquent à tous les agents. « Refuser » masque l'outil du modèle.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(ToolRegistry.permissionKeys, key = { it }) { key ->
                val current = config.entries[key]?.lastOrNull()?.level ?: PermissionConfig.defaultFor(key)
                Card(Modifier.fillMaxWidth(), shape = CardShape) {
                    Column(Modifier.padding(spaceLG)) {
                        Text(key, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(spaceSM))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            levels.forEachIndexed { index, level ->
                                SegmentedButton(
                                    selected = current == level,
                                    onClick = { scope.launch { vm.settings().setPermission(key, level) } },
                                    shape = SegmentedButtonDefaults.itemShape(index, levels.size)
                                ) { Text(level.label) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onBack: () -> Unit) {
    val grouped = ToolRegistry.tools.groupBy { it.spec.category }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Outils (${ToolRegistry.tools.size})") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(spaceLG), verticalArrangement = Arrangement.spacedBy(spaceSM)) {
            grouped.forEach { (category, tools) ->
                item(key = "cat_$category") {
                    Text(category.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                items(tools, key = { it.spec.id }) { tool ->
                    Card(Modifier.fillMaxWidth(), shape = CardShape) {
                        Column(Modifier.padding(spaceLG)) {
                            Text(tool.spec.id, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                            Text(tool.spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (tool.spec.parameters.isNotEmpty()) {
                                Spacer(Modifier.height(spaceSM))
                                Text(
                                    tool.spec.parameters.joinToString(", ") { "${it.name}:${it.type}${if (it.required) "" else "?"}" },
                                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
