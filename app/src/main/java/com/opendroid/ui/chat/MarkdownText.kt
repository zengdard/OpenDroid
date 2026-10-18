package com.opendroid.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Rendu markdown minimal (gras, italique, code, blocs de code). */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val codeColor = MaterialTheme.colorScheme.secondary
    val styled = buildAnnotatedString {
        var inCode = false
        val codeBlock = StringBuilder()
        text.split("\n").forEach { line ->
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize * 0.9f, color = codeColor)) {
                        append(codeBlock.toString().trimEnd())
                    }
                    append("\n")
                    codeBlock.clear()
                    inCode = false
                } else inCode = true
                return@forEach
            }
            if (inCode) {
                if (codeBlock.isNotEmpty()) codeBlock.append("\n")
                codeBlock.append(line)
                return@forEach
            }
            if (line.isBlank()) { append("\n"); return@forEach }
            var i = 0
            while (i < line.length) {
                when {
                    line.startsWith("`", i) -> {
                        val end = line.indexOf('`', i + 1)
                        if (end > i) {
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize * 0.9f, color = MaterialTheme.colorScheme.primary)) {
                                append(line.substring(i + 1, end))
                            }
                            i = end + 1
                        } else { append(line[i]); i++ }
                    }
                    i + 1 < line.length && line.startsWith("**", i) -> {
                        val end = line.indexOf("**", i + 2)
                        if (end > i) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(i + 2, end)) }
                            i = end + 2
                        } else { append(line[i]); i++ }
                    }
                    else -> { append(line[i]); i++ }
                }
            }
            append("\n")
        }
    }
    Text(text = styled, modifier = modifier, fontSize = fontSize, color = color)
}
