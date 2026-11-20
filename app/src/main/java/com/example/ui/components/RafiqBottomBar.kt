package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class RafiqTab(
    val route: String,
    val titleAr: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    HOME("home", "الرئيسية", Icons.Filled.Home, Icons.Outlined.Home),
    ACCOUNT("account", "شخصي", Icons.Filled.Person, Icons.Outlined.Person),
    QUESTS("quests", "الاختبار", Icons.Filled.Assignment, Icons.Outlined.Assignment),
    QURAN("quran", "القرآن", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    PAYMENTS("dofaati", "الدفعات", Icons.Filled.Groups, Icons.Outlined.Groups)
}

/**
 * Floating Rounded Pill Bottom Navigation Bar (5 exact tabs matching Zajil & screenshot layout)
 * الرئيسية | شخصي | الاختبار | القرآن | الدفعات
 */
@Composable
fun RafiqBottomBar(
    currentRoute: String,
    onTabSelected: (RafiqTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = RafiqTab.entries.indexOfFirst { isRafiqTabSelected(it, currentRoute) }
        .let { if (it >= 0) it else 0 }

    ElasticJumpingBottomBar(
        tabs = RafiqTab.entries,
        selectedIndex = selectedIndex,
        onTabSelected = onTabSelected,
        modifier = modifier
    )
}

private fun isRafiqTabSelected(tab: RafiqTab, currentRoute: String): Boolean {
    return when (tab) {
        RafiqTab.HOME -> currentRoute in listOf("home", "library", "plans", "tools", "schedule", "tasks")
        RafiqTab.ACCOUNT -> currentRoute in listOf("account", "settings", "app_theme", "theme")
        RafiqTab.QUESTS -> currentRoute in listOf("quests", "games", "brain_games", "chess")
        RafiqTab.QURAN -> currentRoute in listOf("quran", "quran_audio")
        RafiqTab.PAYMENTS -> currentRoute == "dofaati"
    }
}

/**
 * Sleek Vertical Navigation Rail displayed on the RTL start (right) edge in Landscape Mode.
 */
@Composable
fun RafiqNavigationRail(
    currentRoute: String,
    onTabSelected: (RafiqTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val railShape = RoundedCornerShape(28.dp)
    val accentColor = MaterialTheme.colorScheme.primary
    val isDarkSurface = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.45f
    }
    val containerColor = if (isDarkSurface) MaterialTheme.colorScheme.surface else Color.White
    val borderColor = if (isDarkSurface) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
    val inactiveColor = if (isDarkSurface) Color(0xFF94A3B8) else Color(0xFF475569)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
            .testTag("rafiq_navigation_rail"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(78.dp)
                .fillMaxHeight()
                .shadow(
                    elevation = 12.dp,
                    shape = railShape,
                    spotColor = accentColor.copy(alpha = 0.18f),
                    ambientColor = Color.Black.copy(alpha = 0.10f)
                ),
            shape = railShape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor.copy(alpha = 0.85f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                RafiqTab.entries.forEach { tab ->
                    val isSelected = isRafiqTabSelected(tab, currentRoute)
                    RailPillTabItem(
                        tab = tab,
                        isSelected = isSelected,
                        accentColor = accentColor,
                        inactiveTextColor = inactiveColor,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rail_tab_${tab.route}")
                    )
                }
            }
        }
    }
}

@Composable
private fun RailPillTabItem(
    tab: RafiqTab,
    isSelected: Boolean,
    accentColor: Color,
    inactiveTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        label = "railTabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else inactiveTextColor,
        label = "railTabContent"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                contentDescription = tab.titleAr,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            AutoSizeText(
                text = tab.titleAr,
                fontFamily = com.example.ui.theme.DisplayArabicFontFamily,
                maxFontSize = 11.sp,
                minFontSize = 8.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflowToTwoLinesAtMinSize = false
            )
        }
    }
}

@Composable
private fun FloatingPillTabItem(
    tab: RafiqTab,
    isSelected: Boolean,
    accentColor: Color,
    inactiveTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "floatingTabScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        label = "floatingTabBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else inactiveTextColor,
        label = "floatingTabContent"
    )

    val pillShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 52.dp)
            .clip(pillShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                contentDescription = tab.titleAr,
                tint = contentColor,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = tab.titleAr,
                fontFamily = com.example.ui.theme.DisplayArabicFontFamily,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
