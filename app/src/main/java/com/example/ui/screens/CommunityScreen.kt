package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.CurvedHeader
import com.example.ui.components.Premium3DIcon
import com.example.ui.theme.*

data class CommunityPost(
    val author: String,
    val avatar: String,
    val grade: String,
    val timeAgo: String,
    val content: String,
    val tag: String,
    var likes: Int,
    var commentsCount: Int
)

@Composable
fun CommunityScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val posts = remember {
        mutableStateListOf(
            CommunityPost(
                author = "أحمد محمد",
                avatar = "👨‍🎓",
                grade = "الصف الثالث الثانوي",
                timeAgo = "منذ 15 دقيقة",
                content = "يا شباب حد عنده طريقة كويسة لتثبيت وحفظ معادلات الكيمياء العضوية؟ بحس إني بنساها بسرعة بعد ما أذاكرها بيومين.",
                tag = "كيمياء",
                likes = 12,
                commentsCount = 6
            ),
            CommunityPost(
                author = "سارة علي",
                avatar = "👩‍🎓",
                grade = "الصف الثاني الثانوي",
                timeAgo = "منذ ساعة",
                content = "الحمد لله أتممت اليوم مراجعة الفصل الأول أحياء وحليت 50 سؤال على هوس الدراسة، النقاط والبطاقات ساعدتني جداً في المراجعة!",
                tag = "إنجاز 🏆",
                likes = 28,
                commentsCount = 4
            ),
            CommunityPost(
                author = "عمر خالد",
                avatar = "🧑‍💻",
                grade = "الصف الأول الثانوي",
                timeAgo = "منذ 3 ساعات",
                content = "نصيحة لكل طالب: قسم وقتك باستخدام البومودورو 25 دقيقة تركيز و5 دقائق راحة، الفرق كبير جداً في التركيز وعدم التشتت بالموبايل.",
                tag = "نصيحة دراسية 💡",
                likes = 35,
                commentsCount = 9
            )
        )
    }

    var newPostText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = BackgroundLight
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                CurvedHeader(
                    title = "مجتمع هوس الدراسة 👥",
                    subtitle = "تواصل مع زملائك، تبادلوا النصائح والخبرات، وشجعوا بعضكم على التفوق",
                    gradientColors = listOf(Color(0xFF4A148C), NavPurple),
                    onMenuClick = onOpenDrawer
                )
            }

            // Create post input card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = NavPurpleBg,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✍️", fontSize = 18.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "شارك سؤالاً أو نصيحة لزملائك الطلاب",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = newPostText,
                            onValueChange = { newPostText = it },
                            placeholder = { Text("اكتب سؤالك أو مشاركتك هنا...", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            minLines = 2
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    if (newPostText.isNotBlank()) {
                                        posts.add(
                                            0,
                                            CommunityPost(
                                                author = "أنت (طالب مجتهد)",
                                                avatar = "🎓",
                                                grade = "طالب",
                                                timeAgo = "الآن",
                                                content = newPostText.trim(),
                                                tag = "مشاركة",
                                                likes = 1,
                                                commentsCount = 0
                                            )
                                        )
                                        newPostText = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NavPurple)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نشر للجميع", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Posts feed
            items(posts.size) { idx ->
                val post = posts[idx]
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = PastelPurple,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(post.avatar, fontSize = 20.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = post.author,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${post.grade} • ${post.timeAgo}",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PastelPurple
                            ) {
                                Text(
                                    text = post.tag,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavPurple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = post.content,
                            fontSize = 13.5.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Divider)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { post.likes++ }
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = PlayRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "${post.likes} إعجاب", fontSize = 12.sp, color = TextSecondary)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = NavPurple, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "${post.commentsCount} تعليق", fontSize = 12.sp, color = TextSecondary)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "مشاركة", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
