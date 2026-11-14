package com.example.ui.security

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.preferences.UserSettings
import com.example.domain.security.BiometricAuthManager
import com.example.domain.security.BiometricAvailabilityStatus
import com.example.ui.MainViewModel

@Composable
fun AppLockScreen(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val isEnglish = userSettings.appLanguage == "en"

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPinDialog by remember { mutableStateOf(false) }
    var recoveryPasswordInput by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf(false) }

    val biometricStatus = remember { BiometricAuthManager.checkBiometricAvailability(context) }
    val isBiometricReady = userSettings.isBiometricEnabled && biometricStatus == BiometricAvailabilityStatus.AVAILABLE

    // Pulsing animation for fingerprint button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    fun triggerBiometricAuth() {
        if (activity != null && isBiometricReady) {
            errorMessage = null
            BiometricAuthManager.authenticate(
                activity = activity,
                title = if (isEnglish) "Hawas Al-Dirasa App Lock 🔒" else "قفل تطبيق هوس الدراسة 🔒",
                subtitle = if (isEnglish) "Confirm fingerprint or face to access your study platform" else "استخدم بصمة الإصبع أو الوجه لتأكيد هويتك والدخول",
                negativeButtonText = if (isEnglish) "Use PIN / Cancel" else "إلغاء / استخدام PIN",
                onSuccess = {
                    Toast.makeText(
                        context,
                        if (isEnglish) "Biometric unlocked successfully! ✨" else "تم فتح القفل بالبصمة بنجاح ✨",
                        Toast.LENGTH_SHORT
                    ).show()
                    onUnlocked()
                },
                onError = { errorCode, errString ->
                    // Code 10 or 13 is user canceled / canceled by negative button
                    if (errorCode != 10 && errorCode != 13) {
                        errorMessage = errString
                    }
                },
                onFailed = {
                    errorMessage = if (isEnglish) "Biometric not recognized. Try again." else "لم يتم التعرف على البصمة، يرجى المحاولة مرة أخرى."
                }
            )
        }
    }

    // Auto-trigger biometric prompt when lock screen first appears if enabled
    LaunchedEffect(isBiometricReady) {
        if (isBiometricReady && activity != null) {
            triggerBiometricAuth()
        }
    }

    fun submitPin(pin: String) {
        if (pin.isEmpty()) return
        val correctPin = userSettings.pinCode
        val isMatch = (correctPin.isNotEmpty() && com.example.domain.security.LightweightSecurityGuard.verifyPinOrPassword(pin, correctPin)) ||
                (userSettings.adminPin.isNotEmpty() && com.example.domain.security.LightweightSecurityGuard.verifyPinOrPassword(pin, userSettings.adminPin)) ||
                (userSettings.studentPassword.isNotEmpty() && com.example.domain.security.LightweightSecurityGuard.verifyPinOrPassword(pin, userSettings.studentPassword))

        if (isMatch) {
            errorMessage = null
            Toast.makeText(
                context,
                if (isEnglish) "Unlocked successfully! Welcome back 🎓" else "تم فتح القفل بنجاح، أهلاً بك مجدداً 🎓",
                Toast.LENGTH_SHORT
            ).show()
            onUnlocked()
        } else {
            errorMessage = if (isEnglish) "Incorrect PIN code! Please try again." else "رمز PIN غير صحيح! يرجى إعادة المحاولة."
            enteredPin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Student greeting & Security Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_student_logo_1786045057086),
                            contentDescription = "شعار التطبيق",
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isEnglish) "App Locked 🔒" else "التطبيق مقفل 🔒",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (isEnglish) "Welcome back, ${userSettings.userName}" else "مرحباً بك يا ${userSettings.userName} 🎓",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Text(
                    text = if (isEnglish) "Authenticate via Biometrics or PIN to continue" else "أكّد هويتك ببصمة الإصبع/الوجه أو رمز PIN للمتابعة",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Middle Section: Biometric Button or PIN Dots
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Biometric Unlock Hero Action Button
                if (isBiometricReady) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .size(90.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .clickable { triggerBiometricAuth() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = "بصمة الإصبع",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(onClick = { triggerBiometricAuth() }) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Touch Biometric Sensor 👆" else "اضغط لفتح القفل بالبصمة / الوجه 👆",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = if (isEnglish) " OR ENTER PIN " else " أو أدخل رمز PIN ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // PIN Dots Display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    val maxDots = 4
                    for (i in 0 until maxDots) {
                        val isFilled = i < enteredPin.length
                        Surface(
                            shape = CircleShape,
                            color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.5.dp, if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.size(18.dp)
                        ) {}
                    }
                }

                // Error Message text
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                // Numeric Keypad (1 to 9, Biometric/Clear, 0, Backspace)
                KeypadView(
                    onDigitClick = { digit ->
                        if (enteredPin.length < 6) {
                            errorMessage = null
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4 && userSettings.pinCode.length == 4) {
                                submitPin(newPin)
                            }
                        }
                    },
                    onDeleteClick = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            errorMessage = null
                        }
                    },
                    onBiometricClick = if (isBiometricReady) { { triggerBiometricAuth() } } else null,
                    onSubmitClick = { submitPin(enteredPin) },
                    hasEnteredDigits = enteredPin.isNotEmpty(),
                    isEnglish = isEnglish
                )
            }

            // Bottom Actions: Forgot PIN / Reset
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showForgotPinDialog = true }) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "Forgot PIN? (Recover via Password)" else "نسيت رمز PIN؟ (استرجاع بكلمة السر)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Forgot PIN Dialog
    if (showForgotPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showForgotPinDialog = false
                recoveryPasswordInput = ""
                recoveryError = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Recover PIN Code 🔑" else "استرجاع رمز PIN 🔑",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isEnglish) "Enter your student account password or admin PIN (1234) to unlock and reset your PIN:"
                        else "أدخل كلمة سر حساب الطالب الخاصة بك أو رمز المسؤول (الافتراضي 1234) لفتح القفل وإعادة تعيين رمزك:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = recoveryPasswordInput,
                        onValueChange = {
                            recoveryPasswordInput = it
                            recoveryError = false
                        },
                        label = { Text(if (isEnglish) "Password or Admin PIN" else "كلمة السر أو رمز المسؤول") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = recoveryError,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (recoveryError) {
                        Text(
                            text = if (isEnglish) "Password incorrect!" else "كلمة السر غير مطابقة!",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = recoveryPasswordInput.trim()
                        val isValid = (userSettings.studentPassword.isNotBlank() && com.example.domain.security.LightweightSecurityGuard.verifyPinOrPassword(trimmed, userSettings.studentPassword)) ||
                                (userSettings.adminPin.isNotBlank() && com.example.domain.security.LightweightSecurityGuard.verifyPinOrPassword(trimmed, userSettings.adminPin))

                        if (isValid) {
                            showForgotPinDialog = false
                            Toast.makeText(
                                context,
                                if (isEnglish) "Identity verified! App unlocked." else "تم تأكيد الهوية بنجاح وتم فتح التطبيق ✨",
                                Toast.LENGTH_SHORT
                            ).show()
                            onUnlocked()
                        } else {
                            recoveryError = true
                        }
                    }
                ) {
                    Text(if (isEnglish) "Verify & Unlock" else "تأكيد وفتح القفل")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showForgotPinDialog = false
                    recoveryPasswordInput = ""
                }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }
}

@Composable
private fun KeypadView(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onBiometricClick: (() -> Unit)?,
    onSubmitClick: () -> Unit,
    hasEnteredDigits: Boolean,
    isEnglish: Boolean
) {
    val digits = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("BIOMETRIC", "0", "DELETE")
    )

    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        digits.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { item ->
                    when (item) {
                        "BIOMETRIC" -> {
                            if (onBiometricClick != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .clickable { onBiometricClick() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "بصمة",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.size(62.dp))
                            }
                        }
                        "DELETE" -> {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .clickable { onDeleteClick() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = "حذف",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .clickable { onDigitClick(item) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = item,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
