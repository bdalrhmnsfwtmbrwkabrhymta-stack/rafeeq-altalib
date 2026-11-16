package com.hawas.study.dofaati.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.example.ui.theme.ReadexProFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudyActionsBottomSheet(
    fileTitle: String,
    onDismiss: () -> Unit,
    onInteractiveQuiz: () -> Unit,
    onQuestionBankAndFlashcards: () -> Unit,
    onMindMap: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .testTag("ai_study_actions_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Royal Blue Header inside Bottom Sheet matching Mockup 4
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = DofaatiColors.RoyalBlue
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "إجراءات الذكاء الاصطناعي",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = fileTitle,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AiActionSheetOption(
                    title = "اختبار تفاعلي",
                    subtitle = "توليد 100 سؤال ذكي وتقييم فوري من الملزمة",
                    icon = Icons.Default.Quiz,
                    testTag = "ai_sheet_action_quiz",
                    onClick = onInteractiveQuiz
                )
                AiActionSheetOption(
                    title = "بنك معلومات",
                    subtitle = "بطاقات استذكار ثلاثية الأبعاد وقوانين هامة",
                    icon = Icons.Default.LibraryBooks,
                    testTag = "ai_sheet_action_flashcards",
                    onClick = onQuestionBankAndFlashcards
                )
                AiActionSheetOption(
                    title = "خريطة ذهنية",
                    subtitle = "خريطة تفاعلية ملونة لربط جميع قوانين الباب",
                    icon = Icons.Default.AccountTree,
                    testTag = "ai_sheet_action_mindmap",
                    onClick = onMindMap
                )
            }
        }
    }
}

@Composable
private fun AiActionSheetOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    DofaatiCard(
        modifier = Modifier.testTag(testTag),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DofaatiColors.RoyalBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = DofaatiColors.RoyalBlue)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = DofaatiColors.NavyInk
                )
                Text(
                    text = subtitle,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 12.sp,
                    color = DofaatiColors.SlateMuted
                )
            }
        }
    }
}
