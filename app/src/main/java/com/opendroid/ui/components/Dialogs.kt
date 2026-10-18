package com.opendroid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opendroid.agent.PermissionRequest
import com.opendroid.tool.UserQuestion

/** Demande d'autorisation d'un outil (permission « ask »). */
@Composable
fun PermissionDialog(
    request: PermissionRequest,
    onDecision: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        title = { Text("Autoriser « ${request.toolName} » ?") },
        text = {
            Column {
                Text("L'agent souhaite exécuter cet outil.", style = MaterialTheme.typography.bodyMedium)
                if (!request.input.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(request.input, style = MaterialTheme.typography.bodySmall, fontFamily = MaterialTheme.typography.bodySmall.fontFamily)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDecision(true) }) { Text("Autoriser une fois") } },
        dismissButton = { TextButton(onClick = { onDecision(false) }) { Text("Refuser") } }
    )
}

/** Question posée par l'agent (outil `question`). */
@Composable
fun QuestionDialog(
    question: UserQuestion,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var freeText by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(question.header.ifBlank { "Question" }) },
        text = {
            Column {
                Text(question.question)
                Spacer(Modifier.height(8.dp))
                if (question.options.isEmpty()) {
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { freeText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Votre réponse…") }
                    )
                } else {
                    question.options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected.value.contains(option),
                                    onClick = {
                                        selected.value = if (question.multiple) {
                                            if (selected.value.contains(option)) selected.value - option else selected.value + option
                                        } else setOf(option)
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (question.multiple) Checkbox(checked = selected.value.contains(option), onCheckedChange = null)
                            else RadioButton(selected = selected.value.contains(option), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val answer = if (question.options.isEmpty()) freeText else selected.value.joinToString(", ")
                if (answer.isNotBlank()) onSubmit(answer)
            }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
