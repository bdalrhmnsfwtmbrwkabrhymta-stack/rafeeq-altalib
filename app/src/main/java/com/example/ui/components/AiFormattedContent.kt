package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Formats AI text response intelligently:
 * 1. Recognizes LaTeX / Math blocks ($$ ... $$ or $ ... $)
 * 2. Formats markdown bullet points, bold headers, and key step blocks
 * 3. Highlights code or equation snippets
 */
@Composable
fun AiFormattedContent(
    text: String,
    isUser: Boolean,
    onCopyMathBlock: (String) -> Unit = {},
    bodyFontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    bodyLineHeight: androidx.compose.ui.unit.TextUnit = 22.sp,
    bodyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    headerColor: Color = MaterialTheme.colorScheme.primary
) {
    if (isUser) {
        Text(
            text = text,
            fontSize = bodyFontSize,
            color = Color.White,
            lineHeight = bodyLineHeight
        )
        return
    }

    val blocks = parseAiResponseBlocks(text)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (block in blocks) {
            when (block) {
                is ContentBlock.MathFormula -> {
                    MathFormulaCard(formula = block.formula, onCopy = onCopyMathBlock)
                }
                is ContentBlock.CodeSnippet -> {
                    CodeSnippetCard(code = block.code, language = block.language, onCopy = onCopyMathBlock)
                }
                is ContentBlock.StepHeader -> {
                    Text(
                        text = block.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = (bodyFontSize.value + 1.5f).sp,
                        color = headerColor,
                        lineHeight = bodyLineHeight
                    )
                }
                is ContentBlock.StandardText -> {
                    Text(
                        text = block.content,
                        fontSize = bodyFontSize,
                        color = bodyColor,
                        lineHeight = bodyLineHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun MathFormulaCard(
    formula: String,
    onCopy: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = formula,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }
            IconButton(
                onClick = { onCopy(formula) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "نسخ المعادلة",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun CodeSnippetCard(
    code: String,
    language: String,
    onCopy: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (language.isNotBlank()) {
                Text(
                    text = language.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inversePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}

private sealed class ContentBlock {
    data class StandardText(val content: String) : ContentBlock()
    data class MathFormula(val formula: String) : ContentBlock()
    data class CodeSnippet(val code: String, val language: String) : ContentBlock()
    data class StepHeader(val title: String) : ContentBlock()
}

private fun parseAiResponseBlocks(text: String): List<ContentBlock> {
    val result = mutableListOf<ContentBlock>()
    val lines = text.split("\n")
    val buffer = StringBuilder()

    var inCodeBlock = false
    var codeLang = ""
    val codeBuffer = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()

        // Handle ``` code/latex blocks
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                // close code block
                val code = codeBuffer.toString().trim()
                if (codeLang.lowercase() in listOf("latex", "tex", "math")) {
                    result.add(ContentBlock.MathFormula(code))
                } else {
                    result.add(ContentBlock.CodeSnippet(code, codeLang))
                }
                codeBuffer.clear()
                inCodeBlock = false
            } else {
                if (buffer.isNotBlank()) {
                    result.add(ContentBlock.StandardText(buffer.toString().trim()))
                    buffer.clear()
                }
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeBuffer.append(line).append("\n")
            continue
        }

        // Handle standalone Math Formula $$ ... $$
        if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4) {
            if (buffer.isNotBlank()) {
                result.add(ContentBlock.StandardText(buffer.toString().trim()))
                buffer.clear()
            }
            val mathContent = trimmed.removeSurrounding("$$").trim()
            result.add(ContentBlock.MathFormula(mathContent))
            continue
        }

        // Handle Step Headers (e.g., "الخطوة 1:", "## الخطوة الأولى", "**الخطوة 1:**")
        val isStepHeader = trimmed.startsWith("###") || trimmed.startsWith("##") ||
                (trimmed.startsWith("**") && (trimmed.contains("خطوة") || trimmed.contains("الخطوة") || trimmed.contains("Step") || trimmed.contains("القانون") || trimmed.contains("المعطيات") || trimmed.contains("الحل:")))
        if (isStepHeader && trimmed.length < 90) {
            if (buffer.isNotBlank()) {
                result.add(ContentBlock.StandardText(buffer.toString().trim()))
                buffer.clear()
            }
            val cleanTitle = trimmed.replace(Regex("""[#\*]"""), "").trim()
            result.add(ContentBlock.StepHeader(cleanTitle))
            continue
        }

        // Handle pure equation lines like "F = m * a" or "v = d / t"
        val isEquationLine = (trimmed.startsWith("$$") || (trimmed.contains("=") && (trimmed.contains("\\frac") || trimmed.contains("\\sqrt") || trimmed.contains("\\cdot") || trimmed.contains("^2") || trimmed.contains("λ") || trimmed.contains("π") || trimmed.contains("Δ") || trimmed.contains("∑") || trimmed.contains("∫"))))
        if (isEquationLine) {
            if (buffer.isNotBlank()) {
                result.add(ContentBlock.StandardText(buffer.toString().trim()))
                buffer.clear()
            }
            val cleanFormula = trimmed.replace("$$", "").trim()
            result.add(ContentBlock.MathFormula(cleanFormula))
            continue
        }

        buffer.append(line).append("\n")
    }

    if (inCodeBlock && codeBuffer.isNotBlank()) {
        result.add(ContentBlock.CodeSnippet(codeBuffer.toString().trim(), codeLang))
    }

    if (buffer.isNotBlank()) {
        result.add(ContentBlock.StandardText(buffer.toString().trim()))
    }

    return if (result.isEmpty()) listOf(ContentBlock.StandardText(text)) else result
}
