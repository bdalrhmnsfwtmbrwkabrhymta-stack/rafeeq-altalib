package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.PrintWriter
import java.io.StringWriter

data class StartupCrashReport(
    val stage: String,
    val exceptionType: String,
    val message: String,
    val stackTrace: String,
    val threadName: String,
    val timestampMillis: Long
) {
    fun formatForCopy(): String = buildString {
        appendLine("Stage: $stage")
        appendLine("Thread: $threadName")
        appendLine("Error: $exceptionType: $message")
        appendLine("Timestamp: $timestampMillis")
        appendLine("--- Stack Trace ---")
        append(stackTrace)
    }
}

object StartupCrashStore {
    private const val TAG = "StartupCrashStore"
    private const val PREFS_NAME = "rafiq_startup_crash_store"
    private const val KEY_HAS_CRASH = "has_crash"
    private const val KEY_STAGE = "stage"
    private const val KEY_TYPE = "exception_type"
    private const val KEY_MESSAGE = "message"
    private const val KEY_STACK = "stack_trace"
    private const val KEY_THREAD = "thread_name"
    private const val KEY_TIME = "timestamp_millis"

    fun recordCrash(
        context: Context,
        threadName: String,
        throwable: Throwable,
        stage: String = "Startup"
    ) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val trace = sw.toString().take(3500)
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_HAS_CRASH, true)
                .putString(KEY_STAGE, stage)
                .putString(KEY_TYPE, throwable::class.java.name)
                .putString(KEY_MESSAGE, throwable.message ?: "Unknown error")
                .putString(KEY_STACK, trace)
                .putString(KEY_THREAD, threadName)
                .putLong(KEY_TIME, System.currentTimeMillis())
                .commit()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed persisting crash report", t)
        }
    }

    fun getLastCrash(context: Context): StartupCrashReport? {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_HAS_CRASH, false)) return null
            StartupCrashReport(
                stage = prefs.getString(KEY_STAGE, "Startup") ?: "Startup",
                exceptionType = prefs.getString(KEY_TYPE, "Throwable") ?: "Throwable",
                message = prefs.getString(KEY_MESSAGE, "") ?: "",
                stackTrace = prefs.getString(KEY_STACK, "") ?: "",
                threadName = prefs.getString(KEY_THREAD, "main") ?: "main",
                timestampMillis = prefs.getLong(KEY_TIME, 0L)
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun clearLastCrash(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (_: Throwable) {
        }
    }

    fun buildReport(stage: String, throwable: Throwable): StartupCrashReport {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return StartupCrashReport(
            stage = stage,
            exceptionType = throwable::class.java.name,
            message = throwable.message ?: "Unexpected error",
            stackTrace = sw.toString().take(3500),
            threadName = Thread.currentThread().name,
            timestampMillis = System.currentTimeMillis()
        )
    }
}

@Composable
fun StartupErrorFallbackScreen(
    report: StartupCrashReport,
    onRetry: () -> Unit,
    onResetToHome: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp)
            .testTag("startup_error_fallback_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "خطأ في التشغيل",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "حدث خطأ غير متوقع أثناء التشغيل",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "تم احتواء الخطأ بأمان لمنع إغلاق التطبيق. يمكنك إعادة المحاولة أو نسخ تفاصيل الخطأ.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 260.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${report.stage} • ${report.exceptionType}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
                if (report.message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = report.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = report.stackTrace,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .testTag("startup_error_retry_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إعادة المحاولة")
            }

            OutlinedButton(
                onClick = onResetToHome,
                modifier = Modifier
                    .weight(1f)
                    .testTag("startup_error_home_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("الرئيسية")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = {
                copyCrashToClipboard(context, report)
            },
            modifier = Modifier.testTag("startup_error_copy_button")
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("نسخ تفاصيل الخطأ")
        }
    }
}

@Composable
fun PreviousCrashReportDialog(
    report: StartupCrashReport,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "تقرير خطأ سابق",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "تم رصد خطأ في الجلسة السابقة",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${report.exceptionType}: ${report.message}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = report.stackTrace,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("متابعة التطبيق")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    copyCrashToClipboard(context, report)
                }
            ) {
                Text("نسخ التفاصيل", color = Color(0xFF2563EB))
            }
        }
    )
}

private fun copyCrashToClipboard(context: Context, report: StartupCrashReport) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("RafiqCrashReport", report.formatForCopy()))
        Toast.makeText(context, "تم نسخ تفاصيل الخطأ", Toast.LENGTH_SHORT).show()
    }
}
