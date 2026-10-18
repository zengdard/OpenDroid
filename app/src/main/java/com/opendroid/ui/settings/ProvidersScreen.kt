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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendroid.data.model.Message
import com.opendroid.data.model.ProviderConfig
import com.opendroid.data.model.ProviderKind
import com.opendroid.provider.ChatRequest
import com.opendroid.provider.ProviderEvent
import com.opendroid.provider.ProviderFactory
import com.opendroid.ui.OpenDroidViewModel
import com.opendroid.ui.theme.CardShape
import com.opendroid.ui.theme.spaceLG
import com.opendroid.ui.theme.spaceSM
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(vm: OpenDroidViewModel, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val providers = vm.providerList()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Providers") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(spaceLG), verticalArrangement = Arrangement.spacedBy(spaceSM)) {
            items(providers, key = { it.id }) { provider ->
                Card(Modifier.fillMaxWidth().clickable { onEdit(provider.id) }, shape = CardShape) {
                    Row(Modifier.padding(spaceLG), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${provider.kind.name.lowercase()} · ${provider.baseUrl.ifBlank { "—" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (provider.kind.isRemote) {
                                Text(
                                    if (provider.apiKey.isBlank()) "Clé API manquante" else "Clé configurée",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (provider.apiKey.isBlank()) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                                )
                            }
                        }
                        if (provider.id == vm.uiState.collectAsStateWithLifecycle().value.providerId) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(spaceSM)) }
            item {
                Text("Ajouter un provider OpenAI-compatible personnalisé", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(vm: OpenDroidViewModel, providerId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val provider = remember(providerId) { vm.providerList().firstOrNull { it.id == providerId } }

    if (provider == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Provider") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }) }) {
            Text("Provider introuvable", Modifier.padding(spaceLG))
        }
        return
    }

    var name by remember { mutableStateOf(provider.name) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var model by remember { mutableStateOf(provider.model.ifBlank { provider.models.firstOrNull() ?: "" }) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(provider.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    if (provider.custom) {
                        IconButton(onClick = {
                            scope.launch { vm.settings().removeProvider(provider.id); onBack() }
                        }) { Icon(Icons.Default.Delete, "Supprimer") }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(spaceLG), verticalArrangement = Arrangement.spacedBy(spaceSM)) {
            if (provider.kind == ProviderKind.ON_DEVICE) {
                Text("Provider local. Installez le modèle depuis Paramètres.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("Clé API") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }
            OutlinedTextField(model, { model = it }, label = { Text("Modèle par défaut") }, modifier = Modifier.fillMaxWidth())

            if (provider.models.isNotEmpty()) {
                Text("Modèles connus", style = MaterialTheme.typography.labelMedium)
                provider.models.forEach { m ->
                    Text("• $m", style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { model = m })
                }
            }

            Spacer(Modifier.height(spaceSM))
            Row(horizontalArrangement = Arrangement.spacedBy(spaceSM)) {
                Button(onClick = {
                    scope.launch {
                        vm.settings().upsertProvider(
                            provider.copy(
                                name = name, baseUrl = baseUrl, apiKey = apiKey, model = model,
                                models = (provider.models + model).filter { it.isNotBlank() }.distinct()
                            )
                        )
                        vm.setProvider(provider.id)
                        onBack()
                    }
                }) { Text("Enregistrer") }
                if (provider.kind.isRemote) {
                    Button(onClick = { scope.launch { vm.setProvider(provider.id); vm.setModel(model); onBack() } }) {
                        Text("Utiliser")
                    }
                }
                Button(
                    enabled = !testing && provider.kind.isRemote,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = testProvider(
                                provider.copy(baseUrl = baseUrl, apiKey = apiKey, model = model),
                                model
                            )
                            testing = false
                        }
                    }
                ) { Text(if (testing) "Test…" else "Tester") }
            }
            testResult?.let {
                Spacer(Modifier.height(spaceSM))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("✅")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Envoie une requête minimale au provider pour valider la clé / l'endpoint.
 * Utile notamment pour OpenCode Go (header de session + User-Agent dédiés).
 */
private suspend fun testProvider(config: ProviderConfig, model: String): String = try {
    val provider = ProviderFactory.create(config, null)
    val request = ChatRequest(
        model = model,
        system = "You are a connectivity test.",
        messages = listOf(Message.user("Réponds simplement: ok")),
        tools = emptyList(),
        maxTokens = 16,
        sessionId = "opendroid-connection-test"
    )
    var error: String? = null
    var text = ""
    provider.stream(request).collect { event ->
        when (event) {
            is ProviderEvent.TextDelta -> text += event.text
            is ProviderEvent.ThinkingDelta -> {}
            is ProviderEvent.Completed -> if (text.isBlank()) text = event.text
            is ProviderEvent.Error -> error = event.message
        }
    }
    if (error != null) "❌ $error"
    else "✅ Connexion OK" + (if (text.isNotBlank()) " — ${text.trim().take(80)}" else "")
} catch (e: Exception) {
    "❌ ${e.message}"
}
