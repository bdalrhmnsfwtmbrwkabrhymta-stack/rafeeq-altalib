package com.example.ui.screens.ai

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.UserSettings
import com.example.domain.ai.FahimConfig
import com.example.domain.ai.GeminiConnectionTestResult
import com.example.ui.MainViewModel
import com.example.ui.theme.ReadexProFontFamily
import kotlinx.coroutines.launch

private val DevBlue = Color(0xFF1A6FE3)
private val DevEmerald = Color(0xFF10B981)
private val DevSurface = Color(0xFFFFFFFF)
private val DevInk = Color(0xFF1E1B18)
private val DevMuted = Color(0xFF64748B)

/**
 * لوحة تحكم المطور المخفية بالكامل (PIN: 2026) واختبار اتصال Gemini الحي.
 * لا تظهر أي أزرار أو تحذيرات للطلاب العاديين.
 */
@Composable
fun SecretDeveloperControlDialogs(
    showPinDialog: Boolean,
    onDismissPinDialog: () -> Unit,
    showPanelDialog: Boolean,
    onPinVerifiedOpenPanel: () -> Unit,
    onDismissPanelDialog: () -> Unit,
    userSettings: UserSettings,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var devPinInput by remember(showPinDialog) { mutableStateOf("") }
    var devPinError by remember(showPinDialog) { mutableStateOf(false) }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                devPinInput = ""
                devPinError = false
                onDismissPinDialog()
            },
            containerColor = DevSurface,
            icon = { Icon(Icons.Default.AdminPanelSettings, null, tint = DevBlue) },
            title = {
                Text(
                    text = "لوحة المطور السرية",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = DevInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "أدخل الرمز السري للمطور للتحكم في مفاتيح Gemini وروابط الصوت:",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 13.5.sp,
                        color = DevMuted
                    )
                    OutlinedTextField(
                        value = devPinInput,
                        onValueChange = {
                            devPinInput = it
                            devPinError = false
                        },
                        label = { Text("الرمز السري (PIN)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = devPinError,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("developer_pin_field")
                    )
                    if (devPinError) {
                        Text(
                            text = "الرمز غير صحيح",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 12.5.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (devPinInput.trim() == FahimConfig.DEVELOPER_CONTROL_PIN) {
                            devPinInput = ""
                            devPinError = false
                            onPinVerifiedOpenPanel()
                        } else {
                            devPinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DevBlue),
                    modifier = Modifier.testTag("developer_pin_confirm")
                ) {
                    Text("دخول", fontFamily = ReadexProFontFamily, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    devPinInput = ""
                    devPinError = false
                    onDismissPinDialog()
                }) {
                    Text("إلغاء", fontFamily = ReadexProFontFamily)
                }
            }
        )
    }

    if (showPanelDialog) {
        var devGeminiKey by remember(userSettings.geminiApiKey) { mutableStateOf(userSettings.geminiApiKey) }
        var devPiperUrl by remember(userSettings.piperTtsUrl) { mutableStateOf(userSettings.piperTtsUrl) }
        var devCoquiUrl by remember(userSettings.coquiTtsUrl) { mutableStateOf(userSettings.coquiTtsUrl) }
        var devVoiceKey by remember(userSettings.voiceTtsApiKey) { mutableStateOf(userSettings.voiceTtsApiKey) }

        var isTestingConnection by remember { mutableStateOf(false) }
        var connectionTestResult by remember { mutableStateOf<GeminiConnectionTestResult?>(null) }

        AlertDialog(
            onDismissRequest = onDismissPanelDialog,
            containerColor = DevSurface,
            title = {
                Text(
                    text = "إعدادات المطور (Gemini & TTS)",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = DevInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = devGeminiKey,
                        onValueChange = {
                            devGeminiKey = it
                            connectionTestResult = null
                        },
                        label = { Text("Gemini API Key (${FahimConfig.PRIMARY_MODEL})") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("developer_gemini_key_field")
                    )

                    // Live Gemini Connection Test Button
                    OutlinedButton(
                        onClick = {
                            if (!isTestingConnection) {
                                isTestingConnection = true
                                connectionTestResult = null
                                scope.launch {
                                    val candidate = devGeminiKey.trim().ifBlank {
                                        FahimConfig.resolveGeminiApiKey(userSettings.geminiApiKey)
                                    }
                                    connectionTestResult = viewModel.testDeveloperGeminiKey(candidate)
                                    isTestingConnection = false
                                }
                            }
                        },
                        enabled = !isTestingConnection,
                        border = BorderStroke(1.2.dp, DevBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("developer_test_connection_button")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = DevBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جارٍ اختبار الاتصال بـ Gemini...", fontFamily = ReadexProFontFamily, color = DevBlue)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = DevBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("اختبار اتصال المفتاح الآن", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold, color = DevBlue)
                        }
                    }

                    connectionTestResult?.let { result ->
                        val bg = if (result.isSuccess) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                        val border = if (result.isSuccess) Color(0xFFA7F3D0) else Color(0xFFFCA5A5)
                        val fg = if (result.isSuccess) Color(0xFF047857) else Color(0xFFB91C1C)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = bg,
                            border = BorderStroke(1.dp, border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("developer_connection_test_result")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = fg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = result.messageAr,
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = fg
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = devPiperUrl,
                        onValueChange = { devPiperUrl = it },
                        label = { Text("Piper TTS URL (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = devCoquiUrl,
                        onValueChange = { devCoquiUrl = it },
                        label = { Text("Coqui TTS URL (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = devVoiceKey,
                        onValueChange = { devVoiceKey = it },
                        label = { Text("Voice TTS API Key (اختياري)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveDeveloperFahimSettings(
                            geminiKey = FahimConfig.cleanApiKey(devGeminiKey),
                            piperUrl = devPiperUrl.trim(),
                            coquiUrl = devCoquiUrl.trim(),
                            voiceKey = devVoiceKey.trim()
                        )
                        onDismissPanelDialog()
                        Toast.makeText(context, "تم حفظ إعدادات المطور بأمان", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DevEmerald),
                    modifier = Modifier.testTag("developer_panel_save_button")
                ) {
                    Text("حفظ", fontFamily = ReadexProFontFamily, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPanelDialog) {
                    Text("إغلاق", fontFamily = ReadexProFontFamily)
                }
            }
        )
    }
}
