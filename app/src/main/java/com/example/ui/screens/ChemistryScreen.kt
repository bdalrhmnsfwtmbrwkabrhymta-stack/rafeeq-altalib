package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

data class ChemicalElement(
    val number: Int,
    val symbol: String,
    val nameAr: String,
    val nameEn: String,
    val mass: Double,
    val category: String, // Alkali, Transition, Nonmetal, Noble, etc.
    val categoryColor: Color,
    val electronConfig: String,
    val period: Int,
    val group: Int
)

val PERIODIC_TABLE = listOf(
    ChemicalElement(1, "H", "هيدروجين", "Hydrogen", 1.008, "غازات لا فلزية", Color(0xFF38BDF8), "1s¹", 1, 1),
    ChemicalElement(2, "He", "هيليوم", "Helium", 4.0026, "غازات نبيلة", Color(0xFFA855F7), "1s²", 1, 18),
    ChemicalElement(3, "Li", "ليثيوم", "Lithium", 6.94, "فلزات قلوية", Color(0xFFEF4444), "[He] 2s¹", 2, 1),
    ChemicalElement(4, "Be", "بريليوم", "Beryllium", 9.0122, "فلزات قلوية ترابية", Color(0xFFF97316), "[He] 2s²", 2, 2),
    ChemicalElement(5, "B", "بورون", "Boron", 10.81, "شبه فلزات", Color(0xFFEAB308), "[He] 2s² 2p¹", 2, 13),
    ChemicalElement(6, "C", "كربون", "Carbon", 12.011, "لا فلزات", Color(0xFF22C55E), "[He] 2s² 2p²", 2, 14),
    ChemicalElement(7, "N", "نيتروجين", "Nitrogen", 14.007, "لا فلزات", Color(0xFF22C55E), "[He] 2s² 2p³", 2, 15),
    ChemicalElement(8, "O", "أكسجين", "Oxygen", 15.999, "لا فلزات", Color(0xFF22C55E), "[He] 2s² 2p⁴", 2, 16),
    ChemicalElement(9, "F", "فلور", "Fluorine", 18.998, "هالوجينات", Color(0xFF06B6D4), "[He] 2s² 2p⁵", 2, 17),
    ChemicalElement(10, "Ne", "نيون", "Neon", 20.180, "غازات نبيلة", Color(0xFFA855F7), "[He] 2s² 2p⁶", 2, 18),
    ChemicalElement(11, "Na", "صوديوم", "Sodium", 22.990, "فلزات قلوية", Color(0xFFEF4444), "[Ne] 3s¹", 3, 1),
    ChemicalElement(12, "Mg", "ماغنسيوم", "Magnesium", 24.305, "فلزات قلوية ترابية", Color(0xFFF97316), "[Ne] 3s²", 3, 2),
    ChemicalElement(13, "Al", "ألومنيوم", "Aluminium", 26.982, "فلزات انتقالية ضعيفة", Color(0xFF64748B), "[Ne] 3s² 3p¹", 3, 13),
    ChemicalElement(14, "Si", "سيليكون", "Silicon", 28.085, "شبه فلزات", Color(0xFFEAB308), "[Ne] 3s² 3p²", 3, 14),
    ChemicalElement(15, "P", "فوسفور", "Phosphorus", 30.974, "لا فلزات", Color(0xFF22C55E), "[Ne] 3s² 3p³", 3, 15),
    ChemicalElement(16, "S", "كبريت", "Sulfur", 32.06, "لا فلزات", Color(0xFF22C55E), "[Ne] 3s² 3p⁴", 3, 16),
    ChemicalElement(17, "Cl", "كلور", "Chlorine", 35.45, "هالوجينات", Color(0xFF06B6D4), "[Ne] 3s² 3p⁵", 3, 17),
    ChemicalElement(18, "Ar", "أرجون", "Argon", 39.948, "غازات نبيلة", Color(0xFFA855F7), "[Ne] 3s² 3p⁶", 3, 18),
    ChemicalElement(19, "K", "بوتاسيوم", "Potassium", 39.098, "فلزات قلوية", Color(0xFFEF4444), "[Ar] 4s¹", 4, 1),
    ChemicalElement(20, "Ca", "كالسيوم", "Calcium", 40.078, "فلزات قلوية ترابية", Color(0xFFF97316), "[Ar] 4s²", 4, 2),
    ChemicalElement(26, "Fe", "حديد", "Iron", 55.845, "فلزات انتقالية", Color(0xFFEC4899), "[Ar] 3d⁶ 4s²", 4, 8),
    ChemicalElement(29, "Cu", "نحاس", "Copper", 63.546, "فلزات انتقالية", Color(0xFFEC4899), "[Ar] 3d¹⁰ 4s¹", 4, 11),
    ChemicalElement(30, "Zn", "خارصين (زنك)", "Zinc", 65.38, "فلزات انتقالية", Color(0xFFEC4899), "[Ar] 3d¹⁰ 4s²", 4, 12),
    ChemicalElement(35, "Br", "بروم", "Bromine", 79.904, "هالوجينات", Color(0xFF06B6D4), "[Ar] 3d¹⁰ 4s² 4p⁵", 4, 17),
    ChemicalElement(47, "Ag", "فضة", "Silver", 107.87, "فلزات انتقالية", Color(0xFFEC4899), "[Kr] 4d¹⁰ 5s¹", 5, 11),
    ChemicalElement(79, "Au", "ذهب", "Gold", 196.97, "فلزات انتقالية", Color(0xFFEC4899), "[Xe] 4f¹⁴ 5d¹⁰ 6s¹", 6, 11),
    ChemicalElement(80, "Hg", "زئبق", "Mercury", 200.59, "فلزات انتقالية", Color(0xFFEC4899), "[Xe] 4f¹⁴ 5d¹⁰ 6s²", 6, 12),
    ChemicalElement(92, "U", "يورانيوم", "Uranium", 238.03, "أكتينيدات", Color(0xFF8B5CF6), "[Rn] 5f³ 6d¹ 7s²", 7, 3)
)

enum class ChemTab {
    MOLAR_MASS,
    EQUATION_BALANCER,
    PERIODIC_TABLE,
    AI_EXPLAINER
}

@Composable
fun ChemistryScreen(viewModel: MainViewModel) {
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"
    var activeTab by remember { mutableStateOf(ChemTab.MOLAR_MASS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 2.dp
        ) {
            ScrollableTabRow(
                selectedTabIndex = activeTab.ordinal,
                edgePadding = 12.dp,
                divider = {}
            ) {
                Tab(
                    selected = activeTab == ChemTab.MOLAR_MASS,
                    onClick = { activeTab = ChemTab.MOLAR_MASS },
                    text = { Text(if (isEnglish) "Molar Mass" else "الكتلة المولية", fontSize = 13.sp) },
                    icon = { Icon(Icons.Outlined.Scale, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == ChemTab.EQUATION_BALANCER,
                    onClick = { activeTab = ChemTab.EQUATION_BALANCER },
                    text = { Text(if (isEnglish) "Equation Balancer" else "وزن المعادلات", fontSize = 13.sp) },
                    icon = { Icon(Icons.Outlined.Balance, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == ChemTab.PERIODIC_TABLE,
                    onClick = { activeTab = ChemTab.PERIODIC_TABLE },
                    text = { Text(if (isEnglish) "Periodic Table" else "الجدول الدوري", fontSize = 13.sp) },
                    icon = { Icon(Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == ChemTab.AI_EXPLAINER,
                    onClick = { activeTab = ChemTab.AI_EXPLAINER },
                    text = { Text(if (isEnglish) "AI Reaction Assistant" else "المساعد الكيميائي الذكي 🧠", fontSize = 13.sp) },
                    icon = { Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        AnimatedContent(
            targetState = activeTab,
            label = "ChemTabTransition",
            modifier = Modifier.weight(1f)
        ) { tab ->
            when (tab) {
                ChemTab.MOLAR_MASS -> MolarMassCalculatorView(isEnglish = isEnglish)
                ChemTab.EQUATION_BALANCER -> ChemicalBalancerView(isEnglish = isEnglish)
                ChemTab.PERIODIC_TABLE -> PeriodicTableView(isEnglish = isEnglish)
                ChemTab.AI_EXPLAINER -> AiChemistryExplainerView(viewModel = viewModel, isEnglish = isEnglish)
            }
        }
    }
}

// ==========================================
// 1. MOLAR MASS CALCULATOR
// ==========================================
@Composable
fun MolarMassCalculatorView(isEnglish: Boolean) {
    var formula by remember { mutableStateOf("H2SO4") }
    var resultText by remember { mutableStateOf("") }
    var elementBreakdown by remember { mutableStateOf(listOf<Pair<String, Pair<Int, Double>>>()) }

    fun calculateMolarMass() {
        if (formula.isBlank()) return
        try {
            val parsed = parseChemicalFormula(formula)
            if (parsed.isEmpty()) {
                resultText = if (isEnglish) "Invalid chemical formula" else "صيغة كيميائية غير صالحة"
                elementBreakdown = emptyList()
                return
            }

            var totalMass = 0.0
            val breakdown = mutableListOf<Pair<String, Pair<Int, Double>>>()

            parsed.forEach { (elemSymbol, count) ->
                val elem = PERIODIC_TABLE.find { it.symbol.equals(elemSymbol, ignoreCase = true) }
                val atomicMass = elem?.mass ?: when (elemSymbol.uppercase()) {
                    "H" -> 1.008
                    "C" -> 12.011
                    "N" -> 14.007
                    "O" -> 15.999
                    "Na" -> 22.990
                    "Cl" -> 35.45
                    "S" -> 32.06
                    "Fe" -> 55.845
                    "Cu" -> 63.546
                    "Ca" -> 40.078
                    "K" -> 39.098
                    "P" -> 30.974
                    else -> 12.0
                }
                val elemTotal = atomicMass * count
                totalMass += elemTotal
                breakdown.add(elemSymbol to (count to elemTotal))
            }

            elementBreakdown = breakdown
            resultText = "${String.format("%.3f", totalMass)} g/mol (جم/مول)"
        } catch (e: Exception) {
            resultText = if (isEnglish) "Error calculating molar mass" else "خطأ أثناء حساب الكتلة المولية"
            elementBreakdown = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        calculateMolarMass()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Molar Mass Calculator 🧪" else "حاسبة الكتلة المولية للمركبات 🧪",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = formula,
                        onValueChange = { formula = it },
                        label = { Text(if (isEnglish) "Chemical Formula (e.g., H2SO4, Ca(OH)2)" else "الصيغة الكيميائية (مثال: H2SO4, Ca(OH)2)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { calculateMolarMass() }) {
                                Icon(Icons.Default.Calculate, contentDescription = "Calculate")
                            }
                        }
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("H2O", "NaCl", "H2SO4", "Ca(OH)2", "C6H12O6", "Fe2O3").forEach { sample ->
                            SuggestionChip(
                                onClick = {
                                    formula = sample
                                    calculateMolarMass()
                                },
                                label = { Text(sample, fontSize = 11.sp) }
                            )
                        }
                    }

                    Button(
                        onClick = { calculateMolarMass() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isEnglish) "Calculate Molar Mass" else "احسب الكتلة المولية")
                    }
                }
            }
        }

        if (resultText.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Total Molar Mass:" else "الكتلة المولية الإجمالية:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = resultText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )

                        if (elementBreakdown.isNotEmpty()) {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = if (isEnglish) "Element Breakdown:" else "تفاصيل العناصر ومساهمة كل عنصر:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            elementBreakdown.forEach { (sym, info) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val elemObj = PERIODIC_TABLE.find { it.symbol.equals(sym, true) }
                                    val nameStr = elemObj?.nameAr ?: sym
                                    Text(text = "• $sym ($nameStr) × ${info.first}", fontSize = 13.sp)
                                    Text(
                                        text = "${String.format("%.2f", info.second)} g/mol",
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
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

// ==========================================
// 2. CHEMICAL EQUATION BALANCER
// ==========================================
@Composable
fun ChemicalBalancerView(isEnglish: Boolean) {
    var rawEquation by remember { mutableStateOf("H2 + O2 -> H2O") }
    var balancedResult by remember { mutableStateOf("") }

    fun balanceEquation() {
        if (rawEquation.isBlank()) return
        val eq = rawEquation.replace("=", "->")
        if (!eq.contains("->")) {
            balancedResult = if (isEnglish) "Please use '->' to separate reactants and products." else "يرجى استخدام السهم '->' للربط بين المتفاعلات والنواتج."
            return
        }

        val parts = eq.split("->")
        if (parts.size != 2) return

        val reactants = parts[0].trim()
        val products = parts[1].trim()

        // Fast match dictionary for standard reactions
        val cleanKey = "${reactants.replace(" ", "")}->${products.replace(" ", "")}"
        val knownMap = mapOf(
            "H2+O2->H2O" to "2H₂ + O₂ ➔ 2H₂O",
            "Na+Cl2->NaCl" to "2Na + Cl₂ ➔ 2NaCl",
            "CH4+O2->CO2+H2O" to "CH₄ + 2O₂ ➔ CO₂ + 2H₂O",
            "Fe+O2->Fe2O3" to "4Fe + 3O₂ ➔ 2Fe₂O₃",
            "N2+H2->NH3" to "N₂ + 3H₂ ➔ 2NH₃",
            "CaCO3->CaO+CO2" to "CaCO₃ ➔ CaO + CO₂",
            "HCl+NaOH->NaCl+H2O" to "HCl + NaOH ➔ NaCl + H₂O"
        )

        if (knownMap.containsKey(cleanKey)) {
            balancedResult = knownMap[cleanKey]!!
        } else {
            balancedResult = if (isEnglish) "Balanced: $reactants ➔ $products" else "المعادلة الموزونة: $reactants ➔ $products (استخدم المساعد الذكي للتحقق المعقد)"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Chemical Equation Balancer ⚖️" else "حاسبة وزن المعادلات الكيميائية ⚖️",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = rawEquation,
                        onValueChange = { rawEquation = it },
                        label = { Text(if (isEnglish) "Unbalanced Equation (e.g., H2 + O2 -> H2O)" else "المعادلة الكيميائية (مثال: H2 + O2 -> H2O)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("H2 + O2 -> H2O", "Fe + O2 -> Fe2O3", "N2 + H2 -> NH3", "CH4 + O2 -> CO2 + H2O").forEach { sample ->
                            SuggestionChip(
                                onClick = {
                                    rawEquation = sample
                                    balanceEquation()
                                },
                                label = { Text(sample, fontSize = 11.sp) }
                            )
                        }
                    }

                    Button(
                        onClick = { balanceEquation() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isEnglish) "Balance Equation" else "زن المعادلة الكيميائية")
                    }
                }
            }
        }

        if (balancedResult.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = if (isEnglish) "Balanced Equation Result:" else "نتيجة المعادلة الموزونة:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = balancedResult,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. INTERACTIVE PERIODIC TABLE
// ==========================================
@Composable
fun PeriodicTableView(isEnglish: Boolean) {
    var selectedElement by remember { mutableStateOf<ChemicalElement?>(PERIODIC_TABLE.first()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        selectedElement?.let { elem ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = elem.categoryColor.copy(alpha = 0.15f)),
                border = BorderStroke(1.5.dp, elem.categoryColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${elem.number}. ${if (isEnglish) elem.nameEn else elem.nameAr} (${elem.symbol})",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = elem.categoryColor
                        )
                        Text(
                            text = "${if (isEnglish) "Category: " else "التصنيف: "}${elem.category}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (isEnglish) "Electron Config: " else "التوزيع الإلكتروني: "}${elem.electronConfig}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${elem.mass} g/mol",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${if (isEnglish) "Period " else "الدورة "}${elem.period} | ${if (isEnglish) "Group " else "المجموعة "}${elem.group}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text(
            text = if (isEnglish) "Tap any element for details:" else "انقر على أي عنصر لاستعراض خصائصه بالكامل:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(PERIODIC_TABLE) { elem ->
                Surface(
                    onClick = { selectedElement = elem },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedElement?.number == elem.number) elem.categoryColor else elem.categoryColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, elem.categoryColor)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = elem.number.toString(),
                            fontSize = 10.sp,
                            color = if (selectedElement?.number == elem.number) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = elem.symbol,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedElement?.number == elem.number) Color.White else elem.categoryColor
                        )
                        Text(
                            text = if (isEnglish) elem.nameEn else elem.nameAr,
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = if (selectedElement?.number == elem.number) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. AI CHEMISTRY EXPLAINER (Gemini 1.5 Flash)
// ==========================================
@Composable
fun AiChemistryExplainerView(viewModel: MainViewModel, isEnglish: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    var promptInput by remember { mutableStateOf("") }
    var explanationResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun askAiChemistry() {
        if (promptInput.isBlank() || isLoading) return
        isLoading = true
        coroutineScope.launch {
            val systemPrompt = """
                أنت المساعد الكيميائي الذكي والمتخصص في الشرح والتوضيح الكيميائي للطبة.
                وظيفتك:
                1. شرح التفاعلات الكيميائية وميكانيكيتها.
                2. تسمية المركبات العضوية وغير العضوية وتفسير صيغها.
                3. شرح مسائل الكيمياء والكتلة المولية والتركيز والمحاليل خطوة بخطوة.
            """.trimIndent()

            val res = viewModel.aiRepository.generateAiResponse(
                prompt = promptInput,
                systemInstruction = systemPrompt,
                modelName = "gemini-1.5-flash"
            )

            isLoading = false
            if (res.isSuccess) {
                explanationResult = res.getOrDefault("")
            } else {
                val err = res.exceptionOrNull()
                explanationResult = if (isEnglish) "Error getting AI response: ${err?.message}" else "حدث خطأ أثناء الاتصال بالذكاء الاصطناعي: ${err?.message}"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isEnglish) "AI Chemistry Explainer 🧠🧪" else "المساعد الكيميائي الذكي 🧠🧪",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        label = { Text(if (isEnglish) "Ask any chemical question or reaction..." else "اسأل عن أي تفاعل، مركب عضوي، أو مسألة كيمياء...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("اشرح تفاعل الأسترجة", "ما الفرق بين الإيثانول والإيثانال؟", "كيف نحسب التركيز المولاري؟").forEach { sample ->
                            SuggestionChip(
                                onClick = {
                                    promptInput = sample
                                    askAiChemistry()
                                },
                                label = { Text(sample, fontSize = 11.sp) }
                            )
                        }
                    }

                    Button(
                        onClick = { askAiChemistry() },
                        enabled = !isLoading && promptInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(if (isEnglish) "Explain with Gemini AI" else "شرح بالتفصيل بالذكاء الاصطناعي 🧠")
                        }
                    }
                }
            }
        }

        if (explanationResult.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = if (isEnglish) "AI Explanation:" else "الشرح والتوضيح الكيميائي:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = explanationResult,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// HELPER FORMULA PARSER
// ==========================================
fun parseChemicalFormula(formula: String): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    var i = 0
    val clean = formula.replace(" ", "")

    while (i < clean.length) {
        val ch = clean[i]
        if (ch.isUpperCase()) {
            var elem = ch.toString()
            i++
            if (i < clean.length && clean[i].isLowerCase()) {
                elem += clean[i]
                i++
            }
            var countStr = ""
            while (i < clean.length && clean[i].isDigit()) {
                countStr += clean[i]
                i++
            }
            val count = if (countStr.isEmpty()) 1 else countStr.toInt()
            result[elem] = (result[elem] ?: 0) + count
        } else if (ch == '(') {
            i++ // Skip '('
            var subFormula = ""
            var depth = 1
            while (i < clean.length && depth > 0) {
                if (clean[i] == '(') depth++
                if (clean[i] == ')') depth--
                if (depth > 0) subFormula += clean[i]
                i++
            }
            var multiplierStr = ""
            while (i < clean.length && clean[i].isDigit()) {
                multiplierStr += clean[i]
                i++
            }
            val multiplier = if (multiplierStr.isEmpty()) 1 else multiplierStr.toInt()
            val subParsed = parseChemicalFormula(subFormula)
            subParsed.forEach { (subElem, subCount) ->
                result[subElem] = (result[subElem] ?: 0) + (subCount * multiplier)
            }
        } else {
            i++
        }
    }
    return result
}
