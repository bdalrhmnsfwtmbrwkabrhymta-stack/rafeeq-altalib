package com.example.ui.screens.ai

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.AiChatStudyHelper
import com.example.ui.MainViewModel

@Composable
fun ComplexTopicClarifierDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onRequireNetwork: () -> Unit
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    var topicInput by remember { mutableStateOf("") }
    var detailLevel by remember { mutableStateOf("SIMPLIFIED") } // SIMPLIFIED, ANALOGY, DEEP_DIVE, STEP_BY_STEP

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isEnglish) "Clarify Complex Concept 💡" else "تبسيط وشرح مفهوم معقد 💡",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isEnglish)
                        "Enter any difficult academic concept or rule. Gemini will break it down using analogies and clear examples:"
                    else
                        "اكتب أي مصطلح أو قاعدة أو مسألة صعبة الفهم، وسيقوم الذكاء الاصطناعي بشرحها بطريقة مبسطة وتشبيهات واقعية:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = topicInput,
                    onValueChange = { topicInput = it },
                    label = { Text(if (isEnglish) "Topic / Concept" else "المفهوم أو الموضوع المعقد") },
                    placeholder = { Text(if (isEnglish) "e.g. Quantum tunneling, Krebs Cycle, Integration by parts" else "مثال: الانقسام الميوزي، قاعدة كيرشوف، التكامل بالتعويض") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complex_topic_input"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                Text(
                    text = if (isEnglish) "Explanation Style:" else "أسلوب الشرح المطلوب:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = detailLevel == "SIMPLIFIED",
                        onClick = { detailLevel = "SIMPLIFIED" },
                        label = { Text(if (isEnglish) "ELI5 (Simplified)" else "مبسط جداً", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = detailLevel == "ANALOGY",
                        onClick = { detailLevel = "ANALOGY" },
                        label = { Text(if (isEnglish) "Real Analogy" else "تشبيه واقعي", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = detailLevel == "STEP_BY_STEP",
                        onClick = { detailLevel = "STEP_BY_STEP" },
                        label = { Text(if (isEnglish) "Step-by-Step" else "خطوة بخطوة", fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!AiChatStudyHelper.isNetworkAvailable(context)) {
                        onDismiss()
                        onRequireNetwork()
                        return@Button
                    }
                    if (topicInput.isNotBlank()) {
                        val prompt = when (detailLevel) {
                            "SIMPLIFIED" -> "اشرح لي المفهوم التالي بطريقة سهلة ومبسطة جداً لطالب: $topicInput"
                            "ANALOGY" -> "اشرح لي المفهوم التالي باستخدام أمثلة وتشبيهات من الحياة اليومية لترسيخ الفكرة: $topicInput"
                            "STEP_BY_STEP" -> "فصل واشرح المفهوم التالي خطوة بخطوة مع القوانين والأمثلة التوضيحية: $topicInput"
                            else -> "اشرح لي بالتفصيل المفهوم الأكاديمي التالي: $topicInput"
                        }
                        viewModel.sendMessageToAi(prompt)
                        Toast.makeText(context, if (isEnglish) "Explaining concept..." else "جارٍ تبسيط المفهوم...", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("confirm_clarify_topic_button"),
                enabled = topicInput.isNotBlank()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEnglish) "Clarify" else "تبسيط وشرح")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Cancel" else "إلغاء")
            }
        }
    )
}
