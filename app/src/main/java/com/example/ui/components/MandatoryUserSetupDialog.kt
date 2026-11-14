package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Validates Egyptian WhatsApp phone number.
 * Must be exactly 11 digits, starting with 01 (e.g. 010, 011, 012, 015).
 */
fun validateEgyptianPhone(phone: String): Pair<Boolean, String> {
    val clean = phone.trim().replace(" ", "").replace("-", "")
    if (clean.isBlank()) {
        return Pair(false, "يرجى كتابة رقم الهاتف (11 رقماً)")
    }
    if (!clean.all { it.isDigit() }) {
        return Pair(false, "❌ خطأ: رقم الهاتف يجب أن يتكون من أرقام فقط بدون أحرف أو رموز.")
    }
    if (clean.length < 11) {
        return Pair(false, "❌ خطأ: رقم الهاتف غير مكتمل (أنت أدخلت ${clean.length} أرقام فقط). يجب أن يتكون من 11 رقماً ويبدأ بـ 01 (مثال: 01012345678).")
    }
    if (clean.length > 11) {
        return Pair(false, "❌ خطأ: رقم الهاتف تجاوز 11 رقماً (${clean.length} رقماً). يرجى كتابة الرقم الصحيح.")
    }
    if (!clean.startsWith("01")) {
        return Pair(false, "❌ خطأ: يجب أن يبدأ رقم الهاتف بـ 01 (مثل: 010 أو 011 أو 012 أو 015).")
    }
    return Pair(true, "✓ رقم هاتف صحيح ومكتمل (11 رقماً)")
}

/**
 * Validates student full name.
 * Must contain at least 2 words (e.g., "أحمد محمد").
 */
fun validateFullName(name: String): Pair<Boolean, String> {
    val trimmed = name.trim()
    if (trimmed.isBlank()) {
        return Pair(false, "يرجى كتابة الاسم بالكامل")
    }
    val words = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
    if (words.size < 2) {
        return Pair(false, "❌ خطأ: يرجى كتابة الاسم بالكامل (الاسم الثنائي أو الثلاثي على الأقل مثل: أحمد علي).")
    }
    if (words.any { it.length < 2 }) {
        return Pair(false, "❌ خطأ: يرجى كتابة مقاطع الاسم بشكل كامل.")
    }
    return Pair(true, "✓ الاسم بالكامل مكتمل وموثق")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandatoryUserSetupDialog(
    viewModel: MainViewModel,
    initialName: String = "",
    initialGmail: String = "",
    initialPhone: String = "",
    initialStage: String = "HIGH_SCHOOL",
    initialBranch: String = "SCIENCE",
    initialAge: Int = 17,
    initialGradeYear: String = "الصف الثالث الثانوي",
    onConfirm: ((name: String, gmail: String, phone: String, isLinked: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Mode: 0 = إنشاء حساب جديد, 1 = تسجيل الدخول
    var selectedTab by remember { mutableIntStateOf(0) }

    // Registration Form States
    var fullName by remember { mutableStateOf(if (initialName == "الطالب المجتهد" || initialName == "طالب زائر") "" else initialName) }
    var phone by remember { mutableStateOf(if (initialPhone == "01000000000") "" else initialPhone) }
    var passwordPin by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var educationalStage by remember { mutableStateOf(initialStage.ifBlank { "HIGH_SCHOOL" }) }
    var highSchoolBranch by remember { mutableStateOf(initialBranch.ifBlank { "SCIENCE" }) }
    var studentGradeYear by remember { mutableStateOf(initialGradeYear.ifBlank { "الصف الثالث الثانوي" }) }
    var studentAge by remember { mutableIntStateOf(if (initialAge > 0) initialAge else 17) }
    var gmail by remember { mutableStateOf(initialGmail) }

    // Registration Step: 0 = Input Form, 1 = Waiting & OTP Verification
    var regStep by remember { mutableIntStateOf(0) }
    var isWaitingForOtp by remember { mutableStateOf(false) }
    var otpWaitingSeconds by remember { mutableIntStateOf(5) }
    var generatedOtp by remember { mutableStateOf("7730") }
    var enteredOtp by remember { mutableStateOf("") }
    var otpErrorText by remember { mutableStateOf<String?>(null) }
    var isOtpVerified by remember { mutableStateOf(false) }
    var isRegisteringLoading by remember { mutableStateOf(false) }

    // Login Form States
    var loginPhone by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var isLoginPasswordVisible by remember { mutableStateOf(false) }
    var isLoginLoading by remember { mutableStateOf(false) }
    var loginErrorText by remember { mutableStateOf<String?>(null) }

    // Validation Results
    val nameValidation = remember(fullName) { validateFullName(fullName) }
    val phoneValidation = remember(phone) { validateEgyptianPhone(phone) }
    val loginPhoneValidation = remember(loginPhone) { validateEgyptianPhone(loginPhone) }
    val isPasswordValid = passwordPin.trim().length >= 4

    val isRegFormValid = nameValidation.first && phoneValidation.first && isPasswordValid

    Dialog(
        onDismissRequest = {
            // Cannot dismiss outside until registered, logged in, or skipped
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Branding
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "منظومة رفيق الطالب الذكي 🎓",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "حفظ البيانات والمزامنة السحابية عبر Firebase & WhatsApp",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tabs: إنشاء حساب جديد / تسجيل الدخول
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "إنشاء حساب جديد 📝",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "تسجيل الدخول 🔑",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // TAB 0: إنشاء حساب جديد
                if (selectedTab == 0) {
                    if (regStep == 0) {
                        // Phase 1: Input Form
                        Text(
                            text = "سجل بياناتك المدرسية ورقم الواتساب لحفظ خطتك وجدولك الدراسي في سحابة Firebase ☁️",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 1. Full Name Field (Mandatory - at least 2 words)
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("الاسم بالكامل (الاسم الثنائي على الأقل) *") },
                            placeholder = { Text("مثال: أحمد محمد علي") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                if (nameValidation.first) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                } else if (fullName.isNotBlank()) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            },
                            isError = fullName.isNotBlank() && !nameValidation.first,
                            supportingText = {
                                if (fullName.isNotBlank()) {
                                    Text(
                                        text = nameValidation.second,
                                        color = if (nameValidation.first) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text("يجب كتابة الاسم بالكامل للتعرف عليك في التقارير", fontSize = 11.sp)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mandatory_name_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Educational Stage Selection
                        Text(
                            text = "المرحلة الدراسية والصف:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = educationalStage == "HIGH_SCHOOL",
                                onClick = { educationalStage = "HIGH_SCHOOL" },
                                label = { Text("الثانوية العامة", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = educationalStage == "MIDDLE",
                                onClick = { educationalStage = "MIDDLE" },
                                label = { Text("الإعدادية", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = educationalStage == "UNIVERSITY",
                                onClick = { educationalStage = "UNIVERSITY" },
                                label = { Text("الجامعة", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (educationalStage == "HIGH_SCHOOL") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = highSchoolBranch == "SCIENCE",
                                    onClick = { highSchoolBranch = "SCIENCE" },
                                    label = { Text("علمي علوم 🧬", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = highSchoolBranch == "MATH",
                                    onClick = { highSchoolBranch = "MATH" },
                                    label = { Text("علمي رياضة 📐", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = highSchoolBranch == "LITERARY",
                                    onClick = { highSchoolBranch = "LITERARY" },
                                    label = { Text("أدبي 📖", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Age and Grade Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = studentGradeYear,
                                onValueChange = { studentGradeYear = it },
                                label = { Text("الصف الدراسي") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.3f)
                            )

                            // Student Age Counter
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { if (studentAge > 6) studentAge-- },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "تقليل العمر", modifier = Modifier.size(16.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("العمر", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$studentAge سنة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = { if (studentAge < 60) studentAge++ },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "زيادة العمر", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Telegram Phone / Username (Strict validation)
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { input: String ->
                                phone = input.trim()
                            },
                            label = { Text("رقم أو معرف التليجرام (إجباري) *") },
                            placeholder = { Text("مثال: 01007730570 أو @username") },
                            leadingIcon = {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF0284C7))
                            },
                            trailingIcon = {
                                if (phone.length >= 5) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                } else if (phone.isNotBlank()) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            },
                            isError = phone.isNotBlank() && phone.length < 5,
                            supportingText = {
                                if (phone.isNotBlank() && phone.length < 5) {
                                    Text(
                                        text = "❌ يجب إدخال 5 أحرف/أرقام على الأقل لرقم أو معرف التليجرام",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text("يستخدم لربط البوت وإرسال التذكيرات وجدول المذاكرة", fontSize = 11.sp)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mandatory_whatsapp_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. Password / PIN Field (For recovery on reinstall)
                        OutlinedTextField(
                            value = passwordPin,
                            onValueChange = { passwordPin = it },
                            label = { Text("الرقم السري للحساب (كلمة المرور) *") },
                            placeholder = { Text("أدخل رقم سري لا يقل عن 4 خانات") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "إخفاء الرقم السري" else "إظهار الرقم السري"
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            supportingText = {
                                Text(
                                    text = if (isPasswordValid) "✓ رقم سري معتمد لحفظ الحساب" else "الرقم السري ضروري لاسترجاع بياناتك إذا حذفت التطبيق وأعدت تثبيته",
                                    fontSize = 11.sp,
                                    color = if (isPasswordValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 5. Gmail Field (Optional)
                        OutlinedTextField(
                            value = gmail,
                            onValueChange = { gmail = it },
                            label = { Text("البريد الإلكتروني Gmail (اختياري)") },
                            placeholder = { Text("student@gmail.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Action Button: Send WhatsApp OTP and proceed
                        Button(
                            onClick = {
                                if (!nameValidation.first) {
                                    Toast.makeText(context, nameValidation.second, Toast.LENGTH_LONG).show()
                                } else if (!phoneValidation.first) {
                                    Toast.makeText(context, phoneValidation.second, Toast.LENGTH_LONG).show()
                                } else if (!isPasswordValid) {
                                    Toast.makeText(context, "يرجى كتابة رقم سري مكون من 4 خانات على الأقل للحساب", Toast.LENGTH_LONG).show()
                                } else {
                                    // Generate random 4-digit code
                                    val newOtp = (1000..9999).random().toString()
                                    generatedOtp = newOtp
                                    regStep = 1
                                    isWaitingForOtp = true
                                    otpWaitingSeconds = 5
                                    otpErrorText = null

                                    // Launch countdown & send via Telegram
                                    coroutineScope.launch {
                                        try {
                                            val msg = "🔐 *رمز التأكيد السري لتطبيق رفيق الطالب* 🎓\n\nالرمز: *$newOtp*\n\nيرجى كتابة هذا الرمز في التطبيق لإتمام إنشاء حسابك ومزامنة بياناتك بنجاح."
                                            com.example.domain.telegram.DailyTelegramReportHelper.sendReportViaTelegram(context, phone, msg)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "رمز التأكيد السري عبر التليجرام: $newOtp", Toast.LENGTH_LONG).show()
                                        }

                                        // Countdown simulation
                                        for (i in 5 downTo 1) {
                                            otpWaitingSeconds = i
                                            delay(1000)
                                        }
                                        isWaitingForOtp = false
                                    }
                                }
                            },
                            enabled = isRegFormValid,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_proceed_whatsapp_otp")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إرسال رمز التأكيد والمتابعة عبر Telegram ✈️", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                    } else {
                        // Phase 2: Waiting for OTP & Verification
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (isWaitingForOtp) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(42.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "⏳ انتظر عند إرسال الرسالة...",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "جارٍ تجهيز وإرسال رمز التأكيد السري إلى تليجرام ($phone) خلال $otpWaitingSeconds ثوانٍ...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MarkEmailRead,
                                        contentDescription = null,
                                        modifier = Modifier.size(46.dp),
                                        tint = Color(0xFF2E7D32)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "تم إرسال رمز التأكيد بنجاح 📲",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "يرجى التحقق من رسالة التليجرام وكتابة رمز التأكيد السري المكون من 4 أرقام:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "رمز التأكيد الخاص بك هو: $generatedOtp",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // OTP Input
                                OutlinedTextField(
                                    value = enteredOtp,
                                    onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) enteredOtp = it },
                                    label = { Text("أدخل رمز التأكيد (4 أرقام)") },
                                    placeholder = { Text("مثال: $generatedOtp") },
                                    leadingIcon = {
                                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(0.75f)
                                )

                                if (otpErrorText != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = otpErrorText!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Confirm OTP and create account in Firebase
                                Button(
                                    onClick = {
                                        if (enteredOtp.trim() != generatedOtp) {
                                            otpErrorText = "❌ رمز التأكيد غير صحيح، يرجى كتابة الرمز ($generatedOtp) بشكل صحيح."
                                        } else {
                                            otpErrorText = null
                                            isOtpVerified = true
                                            isRegisteringLoading = true

                                            viewModel.registerStudentAccount(
                                                name = fullName,
                                                phone = phone,
                                                passwordPin = passwordPin,
                                                stage = educationalStage,
                                                branch = highSchoolBranch,
                                                age = studentAge,
                                                gradeYear = studentGradeYear,
                                                gmail = gmail
                                            ) { success, msg ->
                                                isRegisteringLoading = false
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                onConfirm?.invoke(fullName, gmail, phone, gmail.isNotBlank())
                                            }
                                        }
                                    },
                                    enabled = enteredOtp.length == 4 && !isRegisteringLoading,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isRegisteringLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("جارٍ حفظ وتأمين الحساب في Firebase...")
                                    } else {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("تأكيد الرمز وإنشاء الحساب في Firebase ✅", fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = { regStep = 0 }) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تعديل البيانات ✏️", fontSize = 12.sp)
                                    }

                                    TextButton(
                                        onClick = {
                                            val newOtp = (1000..9999).random().toString()
                                            generatedOtp = newOtp
                                            enteredOtp = ""
                                            otpErrorText = null
                                            try {
                                                val msg = "🔐 *رمز التأكيد السري لتطبيق رفيق الطالب* 🎓\n\nالرمز: *$newOtp*"
                                                com.example.domain.telegram.DailyTelegramReportHelper.sendReportViaTelegram(context, phone, msg)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "الرمز الجديد: $newOtp", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إعادة إرسال الرمز 🔄", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: تسجيل الدخول (استرجاع الحساب)
                if (selectedTab == 1) {
                    Text(
                        text = "إذا قمت بحذف التطبيق أو إعادة تثبيته، أدخل رقم هاتفك المسجل والرقم السري لاسترجاع حسابك وجدولك من Firebase ☁️",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Phone Field (Strict 11 digits validation)
                    OutlinedTextField(
                        value = loginPhone,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() || it == ' ' || it == '-' }) {
                                loginPhone = input.replace(" ", "").replace("-", "")
                            }
                        },
                        label = { Text("رقم الهاتف المسجل (11 رقماً) *") },
                        placeholder = { Text("01007730570") },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (loginPhoneValidation.first) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            } else if (loginPhone.isNotBlank()) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        isError = loginPhone.isNotBlank() && !loginPhoneValidation.first,
                        supportingText = {
                            if (loginPhone.isNotBlank()) {
                                Text(
                                    text = loginPhoneValidation.second,
                                    color = if (loginPhoneValidation.first) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password PIN
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("الرقم السري للحساب *") },
                        placeholder = { Text("أدخل رقمك السري") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isLoginPasswordVisible = !isLoginPasswordVisible }) {
                                Icon(
                                    imageVector = if (isLoginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (isLoginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (loginErrorText != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = loginErrorText!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (!loginPhoneValidation.first) {
                                Toast.makeText(context, loginPhoneValidation.second, Toast.LENGTH_LONG).show()
                            } else if (loginPassword.isBlank()) {
                                Toast.makeText(context, "يرجى كتابة الرقم السري للحساب", Toast.LENGTH_SHORT).show()
                            } else {
                                isLoginLoading = true
                                loginErrorText = null
                                viewModel.loginStudentAccount(loginPhone, loginPassword) { success, msg ->
                                    isLoginLoading = false
                                    if (success) {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        onConfirm?.invoke(loginPhone, "", loginPhone, false)
                                    } else {
                                        loginErrorText = msg
                                    }
                                }
                            }
                        },
                        enabled = loginPhoneValidation.first && loginPassword.isNotBlank() && !isLoginLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isLoginLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جارٍ التحقق واسترجاع الحساب من Firebase...")
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل الدخول واسترجاع بياناتي من Firebase 🔄", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // CLEAN SKIP AS GUEST SECTION (التخطي كزائر)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تخطي الآن والاستمرار كزائر ⏩",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "يمكنك تجربة جميع مميزات التطبيق وحفظ جداولك محلياً، مع إمكانية إنشاء حسابك ومزامنته مع سحابة Firebase لاحقاً من شاشة الإعدادات في أي وقت.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.skipStudentAsGuest()
                                onConfirm?.invoke("طالب زائر", "", "01000000000", false)
                                Toast.makeText(context, "تم الدخول كزائر بنجاح 🚶‍♂️! أهلاً بك في رفيق الطالب الذكي", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("المتابعة كزائر بدون حساب الآن 🚶‍♂️", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
