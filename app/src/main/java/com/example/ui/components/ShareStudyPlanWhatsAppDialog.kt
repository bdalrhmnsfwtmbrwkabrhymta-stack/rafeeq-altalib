package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ScheduleSessionEntity
import com.example.data.database.SubjectEntity
import com.example.data.preferences.UserSettings
import com.example.ui.MainViewModel

@Composable
fun ShareStudyPlanWhatsAppDialog(
    userSettings: UserSettings,
    subjects: List<SubjectEntity>,
    sessions: List<ScheduleSessionEntity>,
    tasks: List<com.example.data.database.TaskEntity> = emptyList(),
    initialDayOfWeek: Int? = null,
    viewModel: MainViewModel? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isEnglish = userSettings.appLanguage == "en"

    var targetNumber by remember { mutableStateOf(userSettings.whatsappNumber.ifBlank { userSettings.teacherWhatsappNumber }) }
    var additionalNotes by remember { mutableStateOf("") }

    val formattedPlanText = remember(subjects, sessions, additionalNotes) {
        buildString {
            append("📅 *جدول الخطة الدراسية - ${userSettings.userName}*\n")
            append("المرحلة: ${userSettings.educationalStage} - ${userSettings.studentGradeYear}\n\n")
            append("📚 *المواد الدراسية:*\n")
            subjects.forEach { sub ->
                append("• ${sub.name} (${sub.targetWeeklyHours} س أسبوعياً)\n")
            }
            if (sessions.isNotEmpty()) {
                append("\n⏰ *الحصص والجلسات المجدولة:*\n")
                sessions.forEach { sess ->
                    val subName = subjects.find { it.id == sess.subjectId }?.name ?: "مادة"
                    append("• يوم ${sess.dayOfWeek}: $subName من ${sess.startTimeString} إلى ${sess.endTimeString}\n")
                }
            }
            if (additionalNotes.isNotBlank()) {
                append("\n📝 *ملاحظات إضافية:*\n$additionalNotes\n")
            }
            append("\n✨ تم الإنشاء عبر تطبيق رفيق الطالب")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = Color(0xFF25D366)
                )
                Text(
                    text = if (isEnglish) "Share Study Plan via WhatsApp 📱" else "مشاركة الخطة والجدول عبر واتساب 📱",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = targetNumber,
                    onValueChange = { targetNumber = it },
                    label = { Text(if (isEnglish) "WhatsApp Phone Number" else "رقم واتساب المستلم (مع كود الدولة)") },
                    placeholder = { Text("+201012345678") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whatsapp_number_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = additionalNotes,
                    onValueChange = { additionalNotes = it },
                    label = { Text(if (isEnglish) "Additional Notes" else "ملاحظات إضافية على الخطة") },
                    placeholder = { Text("مثال: تم إنجاز مراجعة الباب الأول بنجاح") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                Text(
                    text = if (isEnglish) "Preview:" else "معاينة الرسالة:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formattedPlanText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val cleanNumber = targetNumber.replace("+", "").replace(" ", "").trim()
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(formattedPlanText)}")
                        }
                        context.startActivity(intent)
                        onDismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "تعذر فتح تطبيق واتساب مباشرة", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                modifier = Modifier.testTag("send_whatsapp_button")
            ) {
                Text(if (isEnglish) "Send WhatsApp" else "إرسال عبر واتساب")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Cancel" else "إلغاء")
            }
        }
    )
}
