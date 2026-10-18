package com.opendroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendroid.ui.OpenDroidViewModel
import com.opendroid.ui.theme.CardShape
import com.opendroid.ui.theme.spaceLG
import com.opendroid.ui.theme.spaceMD
import com.opendroid.ui.theme.spaceSM
import com.opendroid.ui.theme.spaceXXL
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: OpenDroidViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloaded by vm.settings().onDeviceDownloaded.collectAsStateWithLifecycle(initialValue = false)
    var progress by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.initialize() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Paramètres") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spaceLG),
            verticalArrangement = Arrangement.spacedBy(spaceLG)
        ) {
            item {
                SectionCard(Icons.Default.Memory, "Modèle on-device", "Gemma via LiteRT-LM — 100 % local") {
                    Text(
                        if (downloaded) "Modèle téléchargé" else "Non téléchargé",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (downloading) {
                        Spacer(Modifier.height(spaceSM))
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Téléchargement… $progress%", style = MaterialTheme.typography.labelSmall)
                    }
                    error?.let {
                        Spacer(Modifier.height(spaceSM))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(spaceMD))
                    Row(horizontalArrangement = Arrangement.spacedBy(spaceSM)) {
                        if (!downloaded) {
                            Button(onClick = {
                                scope.launch {
                                    downloading = true; error = null
                                    val result = ModelDownloader.downloadGemma(context) { progress = it }
                                    downloading = false
                                    result.onSuccess { vm.settings().setOnDeviceModel(it, true) }
                                        .onFailure { error = it.message }
                                }
                            }, enabled = !downloading) { Text("Télécharger") }
                        } else {
                            OutlinedButton(onClick = {
                                scope.launch { ModelDownloader.delete(context); vm.settings().clearOnDeviceModel() }
                            }) { Text("Supprimer") }
                        }
                    }
                }
            }

            item {
                NavigateCard(Icons.Default.Extension, "Providers", "Providers distants et clés API", onClick = { onNavigate("providers") })
            }
            item {
                NavigateCard(Icons.Default.SmartToy, "Agents", "Agents primaires et sous-agents", onClick = { onNavigate("agents") })
            }
            item {
                NavigateCard(Icons.Default.Lock, "Permissions", "Autoriser / demander / refuser par outil", onClick = { onNavigate("permissions") })
            }
            item {
                NavigateCard(Icons.Default.Build, "Outils", "Tous les outils disponibles", onClick = { onNavigate("tools") })
            }
            item {
                SectionCard(Icons.Default.TouchApp, "Accessibilité", "Gestes, saisie, navigation, capture") {
                    Text(
                        "Nécessaire pour que l'agent agisse sur l'écran.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(spaceMD))
                    Button(onClick = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Configurer") }
                }
            }
            item {
                SectionCard(Icons.Default.SmartToy, "Comportement", null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-approuver", style = MaterialTheme.typography.bodyMedium)
                            Text("Approuve automatiquement les outils en mode « demander »", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.autoApprove, onCheckedChange = { vm.toggleAutoApprove(it) })
                    }
                }
            }
            item {
                SectionCard(Icons.Default.Build, "À propos", "OpenDroid v1.0.0") {
                    Text("Agent autonome pour Android, inspiré d'opencode.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.height(spaceXXL)) }
        }
    }
}

@Composable
fun SectionCard(icon: ImageVector, title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = CardShape) {
        Column(Modifier.padding(spaceLG)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(spaceMD))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.height(spaceMD))
            content()
        }
    }
}

@Composable
private fun NavigateCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = CardShape) {
        Row(Modifier.padding(spaceLG), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(spaceMD))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
