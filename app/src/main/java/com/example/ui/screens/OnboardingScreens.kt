package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.outlined.Code
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.preferences.UserSettings
import com.example.ui.MainViewModel
import com.example.ui.components.AutoSizeText
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import com.example.ui.theme.ReadexProFontFamily
import kotlinx.coroutines.delay

/**
 * Modern Onboarding & Auth Flow matching the user's screenshots:
 * 
 * 1. OnboardingScreen: 4 Slides with illustrations, skip button, indicators, and "التالي / يلا نبدأ"
 * 2. StudentLoginScreen: Clean UI, top squircle book badge, "رفيق الطالب", "ادخل وذاكر", "أنشئ حساب جديد"
 * 3. StudentRegisterScreen: Full sign up form with Name, Phone, Password, Confirm Password, Referral Code
 * 4. SplashScreen: Elegant launch gradient
 */

data class OnboardingSlideData(
    val imageResId: Int,
    val title: String,
    val description: String,
    val accentColor: Color = Color(0xFF7C3AED)
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val slides = remember {
        listOf(
            OnboardingSlideData(
                imageResId = R.drawable.img_onboard_books_1790833711336,
                title = "ارفع محاضرتك",
                description = "صوت، فيديو، أو حتى صورة — وإحنا نحولها لنص تذاكر منه.",
                accentColor = Color(0xFF7C3AED)
            ),
            OnboardingSlideData(
                imageResId = R.drawable.img_onboard_ai_1790833727348,
                title = "الذكاء يشتغل معاك",
                description = "ملخصات، ملازم، بنوك أسئلة، فلاش كاردز، خرائط ذهنية، وشات يجاوبك من محاضرتك.",
                accentColor = Color(0xFF6366F1)
            ),
            OnboardingSlideData(
                imageResId = R.drawable.img_onboard_streak_1790833741799,
                title = "ذاكر كل يوم",
                description = "مؤقت مذاكرة، مراجعة متباعدة، سلسلة وإنجازات تحفزك — والتقدم بيتسجل ليك.",
                accentColor = Color(0xFF8B5CF6)
            ),
            OnboardingSlideData(
                imageResId = R.drawable.img_onboard_library_1790833757182,
                title = "مكتبتك معاك",
                description = "كل اللي بتعمله بيتحفظ منظم حسب المادة — تفتحه وتراجعه في أي وقت.",
                accentColor = Color(0xFF7C3AED)
            )
        )
    }

    var currentSlideIndex by remember { mutableIntStateOf(0) }
    val currentSlide = slides[currentSlideIndex]

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEDE9FE).copy(alpha = 0.45f),
                            Color(0xFFF8FAFC),
                            Color(0xFFF1F5F9)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Top Bar: "تخطي" Button on top start
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onFinishOnboarding,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "تخطي",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C3AED)
                    )
                }
            }

            // Main Content: Animated Illustration + Title + Description
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = currentSlideIndex,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.92f))
                            .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.05f))
                    },
                    label = "slide_image"
                ) { targetIndex ->
                    val slide = slides[targetIndex]
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(280.dp)
                            .padding(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = slide.imageResId),
                            contentDescription = slide.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedContent(
                    targetState = currentSlideIndex,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300))
                            .togetherWith(fadeOut(animationSpec = tween(200)))
                    },
                    label = "slide_text"
                ) { targetIndex ->
                    val slide = slides[targetIndex]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = slide.title,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E1B4B),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = slide.description,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }
                }
            }

            // Bottom Section: Indicators + Primary Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    slides.forEachIndexed { index, _ ->
                        val isSelected = index == currentSlideIndex
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(if (isSelected) 28.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(0xFF7C3AED)
                                    else Color(0xFFCBD5E1)
                                )
                        )
                    }
                }

                // Primary Next / Get Started Pill Button
                Button(
                    onClick = {
                        if (currentSlideIndex < slides.size - 1) {
                            currentSlideIndex++
                        } else {
                            onFinishOnboarding()
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C3AED)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0xFF7C3AED).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (currentSlideIndex == slides.size - 1) "يلا نبدأ" else "التالي",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Student Login Screen matching Screenshot 1 exactly
 */
@Composable
fun StudentLoginScreen(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf(userSettings.whatsappNumber.ifBlank { userSettings.savedLoginPhone }) }
    var password by remember { mutableStateOf(userSettings.studentPassword.ifBlank { userSettings.savedLoginPin }) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEDE9FE).copy(alpha = 0.5f),
                            Color(0xFFF8FAFC),
                            Color(0xFFF1F5F9)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Top Squircle Badge with Book Icon
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .size(96.dp)
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0xFF7C3AED).copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "شعار مُتعلِّم",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // App Brand Name: "مُتعلِّم"
                Text(
                    text = "مُتعلِّم",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A6FE3),
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "طريقك نحو مستقبل أفضل",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Input Field 1: Phone Number (رقم الهاتف)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        placeholder = {
                            Text(
                                text = "رقم الهاتف",
                                color = Color(0xFF94A3B8),
                                fontSize = 15.sp
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Smartphone,
                                contentDescription = null,
                                tint = Color(0xFF64748B)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Field 2: Password (كلمة السر)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = {
                            Text(
                                text = "كلمة السر",
                                color = Color(0xFF94A3B8),
                                fontSize = 15.sp
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFF64748B)
                            )
                        },
                        leadingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Forgot Password link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(
                        onClick = {
                            Toast.makeText(context, "يمكنك تسجيل الدخول مباشرة برقم هاتفك أو إنشاء حساب جديد", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "نسيت كلمة السر؟",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Button: "ادخل وذاكر"
                Button(
                    onClick = {
                        val trimmedPhone = phoneNumber.trim()
                        if (trimmedPhone.isBlank()) {
                            Toast.makeText(context, "يرجى كتابة رقم الهاتف", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (password.isBlank()) {
                            Toast.makeText(context, "يرجى كتابة الرقم السري", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        viewModel.loginStudentAccount(
                            phone = trimmedPhone,
                            passwordPin = password,
                            onResult = { isSuccess, message ->
                                if (isSuccess) {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    onLoginSuccess()
                                } else {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0xFF7C3AED).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "ادخل وذاكر",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Separator: "أول مرة هنا؟"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCBD5E1))
                    Text(
                        text = "أول مرة هنا؟",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCBD5E1))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Secondary Button: "أنشئ حساب جديد 👤+"
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFF1F5F9).copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onNavigateToRegister() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "أنشئ حساب جديد",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1B4B)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Student Registration Screen matching Screenshot 2 exactly
 */
@Composable
fun StudentRegisterScreen(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(if (userSettings.userName != "الطالب المجتهد" && userSettings.userName != "طالب زائر") userSettings.userName else "") }
    var phone by remember { mutableStateOf(userSettings.whatsappNumber) }
    var password by remember { mutableStateOf(userSettings.studentPassword) }
    var confirmPassword by remember { mutableStateOf(userSettings.studentPassword) }
    var referralCode by remember { mutableStateOf("") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateToLogin()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEDE9FE).copy(alpha = 0.5f),
                            Color(0xFFF8FAFC),
                            Color(0xFFF1F5F9)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with Back Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "رجوع",
                            tint = Color(0xFF1E1B4B),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Brand Title: "مُتعلِّم"
                Text(
                    text = "مُتعلِّم",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A6FE3),
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "اعمل حسابك في ثواني — ومجاناً",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Field 1: Name (الاسم)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = {
                            Text("الاسم", color = Color(0xFF94A3B8), fontSize = 15.sp)
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B))
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Field 2: Phone Number (رقم الهاتف)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        placeholder = {
                            Text("رقم الهاتف", color = Color(0xFF94A3B8), fontSize = 15.sp)
                        },
                        trailingIcon = {
                            Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = Color(0xFF64748B))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Field 3: Password (كلمة السر)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = {
                            Text("كلمة السر", color = Color(0xFF94A3B8), fontSize = 15.sp)
                        },
                        trailingIcon = {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFF64748B))
                        },
                        leadingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Field 4: Confirm Password (تأكيد كلمة السر)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = {
                            Text("تأكيد كلمة السر", color = Color(0xFF94A3B8), fontSize = 15.sp)
                        },
                        trailingIcon = {
                            Icon(Icons.Default.LockPerson, contentDescription = null, tint = Color(0xFF64748B))
                        },
                        leadingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Field 5: Referral Code (كود دعوة اختياري)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = referralCode,
                        onValueChange = { referralCode = it },
                        placeholder = {
                            Text("كود دعوة (اختياري)", color = Color(0xFF94A3B8), fontSize = 15.sp)
                        },
                        trailingIcon = {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFF64748B))
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Button: "أنشئ حسابي"
                Button(
                    onClick = {
                        val trimmedName = name.trim().ifBlank { "الطالب المثالي" }
                        val trimmedPhone = phone.trim()
                        if (trimmedPhone.isBlank() || trimmedPhone.length < 10) {
                            Toast.makeText(context, "يرجى كتابة رقم الهاتف بشكل صحيح", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (password.isBlank() || password.length < 4) {
                            Toast.makeText(context, "يرجى كتابة الرقم السري (4 أرقام أو أكثر)", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (password != confirmPassword) {
                            Toast.makeText(context, "كلمتا السر غير متطابقتين، يرجى التأكد", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        viewModel.registerStudentAccount(
                            name = trimmedName,
                            phone = trimmedPhone,
                            passwordPin = password,
                            stage = userSettings.educationalStage,
                            branch = userSettings.highSchoolBranch,
                            age = userSettings.studentAge,
                            gradeYear = userSettings.studentGradeYear,
                            gmail = userSettings.userGmail,
                            onResult = { isSuccess, message ->
                                if (isSuccess) {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    viewModel.completeOnboarding()
                                    onRegisterSuccess()
                                } else {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0xFF7C3AED).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "أنشئ حسابي",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Separator: "عندك حساب؟"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCBD5E1))
                    Text(
                        text = "عندك حساب؟",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCBD5E1))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Secondary Button: "سجل دخولك"
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFF1F5F9).copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onNavigateToLogin() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سجل دخولك",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1B4B)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Gateway Container that handles switching between Onboarding -> Login -> Sign Up
 */
@Composable
fun StudentGatewayScreen(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    onLoginSuccess: () -> Unit
) {
    var currentAuthStep by remember {
        mutableStateOf(
            if (!userSettings.isOnboardingCompleted) "ONBOARDING"
            else "LOGIN"
        )
    }

    when (currentAuthStep) {
        "ONBOARDING" -> {
            OnboardingScreen(
                onFinishOnboarding = {
                    currentAuthStep = "LOGIN"
                }
            )
        }
        "LOGIN" -> {
            StudentLoginScreen(
                viewModel = viewModel,
                userSettings = userSettings,
                onNavigateToRegister = {
                    currentAuthStep = "REGISTER"
                },
                onLoginSuccess = onLoginSuccess
            )
        }
        "REGISTER" -> {
            StudentRegisterScreen(
                viewModel = viewModel,
                userSettings = userSettings,
                onNavigateToLogin = {
                    currentAuthStep = "LOGIN"
                },
                onRegisterSuccess = onLoginSuccess
            )
        }
    }
}

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    var hasFinished by remember { mutableStateOf(false) }
    var animateIn by remember { mutableStateOf(false) }

    val finishOnce = remember(onSplashFinished) {
        {
            if (!hasFinished) {
                hasFinished = true
                onSplashFinished()
            }
        }
    }

    LaunchedEffect(Unit) {
        animateIn = true
        delay(1800)
        finishOnce()
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0.90f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    val splashBrandTitle = remember {
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF1A6FE3), Color(0xFF0EA5E9))
                    )
                )
            ) {
                append("م\u064Fتعل")
            }
            // Pure color-only SpanStyle preserves HarfBuzz Arabic diacritic ligatures
            withStyle(SpanStyle(color = Color(0xFF1E1B18))) {
                append("\u0651\u0650")
            }
            withStyle(
                SpanStyle(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF1A6FE3), Color(0xFF0EA5E9))
                    )
                )
            ) {
                append("م")
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF8FAFC),
                            Color(0xFFEFF6FF)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = finishOnce
                )
                .testTag("splash_screen_root"),
            contentAlignment = Alignment.Center
        ) {
            // Soft atmospheric royal-blue background glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFDBEAFE).copy(alpha = 0.55f),
                            Color(0xFF3B82F6).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.38f),
                        radius = size.minDimension * 0.68f
                    )
                )
            }

            val isLandscape = maxWidth > maxHeight

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .alpha(contentAlpha)
                        .scale(contentScale),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally)
                ) {
                    SplashHeroIconBadge()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        SplashBrandTextsAndBadge(
                            splashBrandTitle = splashBrandTitle,
                            isCompactHeight = true
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(horizontal = 24.dp)
                        .alpha(contentAlpha)
                        .scale(contentScale)
                ) {
                    SplashHeroIconBadge()
                    Spacer(modifier = Modifier.height(28.dp))
                    SplashBrandTextsAndBadge(
                        splashBrandTitle = splashBrandTitle,
                        isCompactHeight = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashHeroIconBadge() {
    Box(
        modifier = Modifier.size(196.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer soft sky-blue halo (#DBEAFE)
        Box(
            modifier = Modifier
                .size(196.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFDBEAFE).copy(alpha = 0.85f),
                            Color(0xFFDBEAFE).copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Inner luminous blue ring (#3B82F6)
        Box(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .background(Color(0xFF3B82F6).copy(alpha = 0.12f))
                .border(1.5.dp, Color(0xFF3B82F6).copy(alpha = 0.25f), CircleShape)
        )
        // Official 3D Launcher Artwork (148.dp, 32.dp rounded corners)
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            shadowElevation = 14.dp,
            border = BorderStroke(2.dp, Color.White),
            modifier = Modifier
                .size(148.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color(0xFF3B82F6).copy(alpha = 0.40f)
                )
                .testTag("splash_3d_icon")
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_art),
                contentDescription = "شعار مُتعلِّم",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
            )
        }
    }
}

@Composable
private fun SplashBrandTextsAndBadge(
    splashBrandTitle: androidx.compose.ui.text.AnnotatedString,
    isCompactHeight: Boolean
) {
    AutoSizeText(
        text = splashBrandTitle,
        fontFamily = HeaderBrandAmiriFontFamily,
        fontWeight = FontWeight.Bold,
        maxFontSize = 34.sp,
        minFontSize = 24.sp,
        lineHeightRatio = 1.28f,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Center,
        overflowToTwoLinesAtMinSize = false,
        modifier = Modifier.testTag("splash_brand_title")
    )

    Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

    AutoSizeText(
        text = "منظمتك الدراسية والدينية الشاملة",
        fontFamily = ReadexProFontFamily,
        fontWeight = FontWeight.Medium,
        maxFontSize = 15.sp,
        minFontSize = 12.sp,
        color = Color(0xFF475569),
        textAlign = TextAlign.Center,
        overflowToTwoLinesAtMinSize = false,
        modifier = Modifier.testTag("splash_subtitle")
    )

    Spacer(modifier = Modifier.height(if (isCompactHeight) 20.dp else 40.dp))

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.2.dp, Color(0xFFBFDBFE)),
        shadowElevation = 3.dp,
        modifier = Modifier.testTag("splash_developer_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = null,
                tint = Color(0xFF1A6FE3),
                modifier = Modifier.size(18.dp)
            )
            AutoSizeText(
                text = "المطور: عبد الرحمن صفوت عطا",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                maxFontSize = 14.sp,
                minFontSize = 11.5.sp,
                color = Color(0xFF1A6FE3),
                textAlign = TextAlign.Center,
                overflowToTwoLinesAtMinSize = false
            )
        }
    }
}
