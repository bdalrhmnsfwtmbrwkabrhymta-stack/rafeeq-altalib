package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.MainViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatCalculatorScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    // Inputs matching Screenshot 3
    var goldGramPriceCurrent by remember { mutableStateOf("3200") } // سعر الجرام الحالي عيار 24
    var cashMoneyOwned by remember { mutableStateOf("") } // زكاة المال: ادخل المبلغ الذي تملكه
    var stockMarketValue by remember { mutableStateOf("") } // زكاة الأسهم: قيمه الأسهم التى تمتلكها فى السوق
    var stockProfitsValue by remember { mutableStateOf("") } // قيمه الارباح التى حصلت عليها
    var gold18Weight by remember { mutableStateOf("") } // ماهو وزن الذهب التى تملكه عيار 18؟
    var gold21Weight by remember { mutableStateOf("") } // ماهو وزن الذهب تملكه عيار 21؟
    var gold24Weight by remember { mutableStateOf("") } // ماهو وزن الذهب الذي تملكه عيار 24؟
    var monthlyRentIncome by remember { mutableStateOf("") } // زكاة العقارات: قيمة الايجار الشهرى للعقار

    var showExplanationDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }

    // Calculated values
    val gramPrice24 = goldGramPriceCurrent.toDoubleOrNull() ?: 0.0
    val cash = cashMoneyOwned.toDoubleOrNull() ?: 0.0
    val stocks = (stockMarketValue.toDoubleOrNull() ?: 0.0) + (stockProfitsValue.toDoubleOrNull() ?: 0.0)
    val gold18 = gold18Weight.toDoubleOrNull() ?: 0.0
    val gold21 = gold21Weight.toDoubleOrNull() ?: 0.0
    val gold24 = gold24Weight.toDoubleOrNull() ?: 0.0

    // Equivalent pure 24k gold grams:
    val equivalentPureGold = (gold18 * (18.0 / 24.0)) + (gold21 * (21.0 / 24.0)) + gold24
    val goldTotalValue = equivalentPureGold * gramPrice24

    // Real estate: Rental cash accumulated during the year
    val annualRentalIncome = (monthlyRentIncome.toDoubleOrNull() ?: 0.0) * 12.0

    // Total zakatable wealth
    val totalWealth = cash + stocks + goldTotalValue + annualRentalIncome
    val nisab24k = 85.0 * gramPrice24
    val hasReachedNisab = totalWealth >= nisab24k && totalWealth > 0 && gramPrice24 > 0
    val zakatDue = if (hasReachedNisab) totalWealth * 0.025 else 0.0

    val nf = remember { NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 } }

    Scaffold(
        containerColor = Color(0xFFF9F7F2) // Beige background matching design
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header matching Screenshot 3 (Beige curved top bar with back arrow, 3D calculator icon, title, and "كيفية حساب الزكاة؟")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFEDE6D9),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDED6C7))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color(0xFF4A4A4A)
                            )
                        }

                        // Calculator 3D Icon & Title matching screenshot 3
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(4.dp, RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF26A69A),
                                border = BorderStroke(1.5.dp, Color(0xFF80CBC4))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Calculate,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "حاسبة الزكاة",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A34)
                            )
                        }

                        Spacer(modifier = Modifier.size(42.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // "كيفية حساب الزكاة؟" clickable link matching image 3
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showExplanationDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = Color(0xFF00796B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "كيفية حساب الزكاة؟ (الشروط والنصاب)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00796B)
                        )
                    }
                }
            }

            // Scrollable Form matching Screenshot 3
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Mandatory Gram Price Input
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💰", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ادخل سعر الجرام الحالي (عيار 24)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ ملاحظة هذا الحقل إلزامي لتحديد النصاب الشرعي (85 جرام)",
                                fontSize = 11.5.sp,
                                color = Color(0xFFD97706),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = goldGramPriceCurrent,
                                onValueChange = { goldGramPriceCurrent = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("مثال: 3200") },
                                trailingIcon = { Text("ج.م / جرام", fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(end = 12.dp)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )
                        }
                    }
                }

                // Section 1: زكاة المال
                item {
                    ZakatSectionCard(
                        title = "زكاة المال والمدخرات 💵",
                        subtitle = "النقد السائل في البنك أو المنزل أو الودائع"
                    ) {
                        ZakatInputField(
                            label = "ادخل المبلغ الذي تملكه",
                            value = cashMoneyOwned,
                            onValueChange = { cashMoneyOwned = it },
                            unit = "ج.م"
                        )
                    }
                }

                // Section 2: زكاة الأسهم
                item {
                    ZakatSectionCard(
                        title = "زكاة الأسهم والاستثمارات 📈",
                        subtitle = "الأسهم بغرض التجارة والمضاربة والأرباح السنوية"
                    ) {
                        ZakatInputField(
                            label = "قيمه الأسهم التى تمتلكها فى السوق",
                            value = stockMarketValue,
                            onValueChange = { stockMarketValue = it },
                            unit = "ج.م"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ZakatInputField(
                            label = "قيمه الارباح التى حصلت عليها",
                            value = stockProfitsValue,
                            onValueChange = { stockProfitsValue = it },
                            unit = "ج.م"
                        )
                    }
                }

                // Section 3: زكاة الذهب
                item {
                    ZakatSectionCard(
                        title = "زكاة الذهب والسبائك 🪙",
                        subtitle = "الذهب المكنوز للتجارة أو الادخار"
                    ) {
                        ZakatInputField(
                            label = "ماهو وزن الذهب التى تملكه عيار 18؟",
                            value = gold18Weight,
                            onValueChange = { gold18Weight = it },
                            unit = "جرام"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ZakatInputField(
                            label = "ماهو وزن الذهب تملكه عيار 21؟",
                            value = gold21Weight,
                            onValueChange = { gold21Weight = it },
                            unit = "جرام"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ZakatInputField(
                            label = "ماهو وزن الذهب الذي تملكه عيار 24؟",
                            value = gold24Weight,
                            onValueChange = { gold24Weight = it },
                            unit = "جرام"
                        )
                    }
                }

                // Section 4: زكاة العقارات المملوكة
                item {
                    ZakatSectionCard(
                        title = "زكاة العقارات المملوكة والمؤجرة 🏢",
                        subtitle = "الريع والعائد الإيجاري المحتفظ به بعد مرور الحول"
                    ) {
                        ZakatInputField(
                            label = "قيمة الايجار الشهرى للعقار",
                            value = monthlyRentIncome,
                            onValueChange = { monthlyRentIncome = it },
                            unit = "ج.م / شهر"
                        )
                    }
                }

                // Calculate Button
                item {
                    Button(
                        onClick = { showResultDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00897B)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "احسب الزكاة 🧮",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // Explanation Dialog
    if (showExplanationDialog) {
        Dialog(onDismissRequest = { showExplanationDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 20.dp),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📖", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "كيفية حساب الزكاة وشروطها الشرعية",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "1️⃣ **بلوغ النصاب**:\nالنصاب الشرعي للمال والذهب هو ما يعادل 85 جراماً من الذهب الخالص عيار 24.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "2️⃣ **حولان الحول**:\nمرور عام هجري كامل (354 يوماً) على بقاء المال بالغاً النصاب في حوزتك زائداً عن حوائجك الأصلية.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "3️⃣ **نسبة الزكاة الواجبة**:\n2.5% (ربع العشر) من إجمالي الأموال والذهب والأسهم والأرباح المدخرة.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showExplanationDialog = false },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                    ) {
                        Text("فهمت ذلك")
                    }
                }
            }
        }
    }

    // Result Calculation Dialog
    if (showResultDialog) {
        Dialog(onDismissRequest = { showResultDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 16.dp),
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (hasReachedNisab) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (hasReachedNisab) "✅" else "ℹ️", fontSize = 26.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (hasReachedNisab) "بلغت أموالك النصاب الشرعي" else "لم تبلغ أموالك النصاب الشرعي بعد",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasReachedNisab) Color(0xFF1B5E20) else Color(0xFFB45309)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Due Amount Big Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (hasReachedNisab) Color(0xFF004D40) else Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "مقدار الزكاة الواجبة (2.5%):",
                                fontSize = 13.sp,
                                color = if (hasReachedNisab) Color.White.copy(alpha = 0.85f) else Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (hasReachedNisab) "${nf.format(zakatDue)} ج.م" else "0.00 ج.م",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = if (hasReachedNisab) Color(0xFFFFD54F) else Color(0xFF0F172A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Breakdown Details
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ResultDetailRow("إجمالي أموالك وأصولك:", "${nf.format(totalWealth)} ج.م")
                        ResultDetailRow("قيمة النصاب الشرعي (85 جم ذهب):", "${nf.format(nisab24k)} ج.م")
                        if (goldTotalValue > 0) {
                            ResultDetailRow("قيمة الذهب المملوك بالكامل:", "${nf.format(goldTotalValue)} ج.م")
                        }
                        if (stocks > 0) {
                            ResultDetailRow("قيمة الأسهم والأرباح:", "${nf.format(stocks)} ج.م")
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { showResultDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                    ) {
                        Text("تم، حسناً", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ZakatSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ZakatInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String
) {
    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF334155)
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}

@Composable
fun ResultDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.5.sp, color = Color(0xFF475569))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}
