package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OpenSourceResource(
    val title: String,
    val category: String,
    val description: String,
    val url: String,
    val license: String
)

@Composable
fun OpenSourceScreen() {
    val context = LocalContext.current

    val resourcesList = listOf(
        OpenSourceResource(
            title = "AnkiDroid (Anki-Android)",
            category = "بطاقات المراجعة والتكرار المتباعد",
            description = "محرك بطاقات أنكي للتكرار المتباعد (SM-2 Algorithm). مرخص تحت GPL-3.0.",
            url = "https://github.com/ankidroid/Anki-Android",
            license = "GPL-3.0"
        ),
        OpenSourceResource(
            title = "Batoulapps Adhan-Kotlin",
            category = "مواقيت الصلاة واتجاه القبلة",
            description = "مكتبة دقيقة لحساب أوقات الصلاة واتجاه القبلة الجغرافي رياضياً على مستوى العالم.",
            url = "https://github.com/batoulapps/adhan-kotlin",
            license = "MIT"
        ),
        OpenSourceResource(
            title = "AndroidPdfViewer",
            category = "عرض المستندات وكتب PDF",
            description = "مكتبة خفيفة وسريعة لعرض ومعاينة ملفات PDF والتكبير والتصفيح.",
            url = "https://github.com/barteksc/AndroidPdfViewer",
            license = "Apache-2.0"
        ),
        OpenSourceResource(
            title = "ML Kit Vision OCR (Text Recognition)",
            category = "الذكاء الاصطناعي والمسح الضوئي",
            description = "تقنية جوجل للتعرف على النصوص واستخراج الكلام من صور الكتب والدفاتر.",
            url = "https://developers.google.com/ml-kit/vision/text-recognition/v2/android",
            license = "Google Developer License"
        ),
        OpenSourceResource(
            title = "Compact Dictionaries (JSON Datasets)",
            category = "قواميس إنجليزي / عربي مدمجة",
            description = "مجموعات بيانات قواميس مدمجة وخفيفة لتقديم ترجمات سريعة بدون إنترنت.",
            url = "https://github.com/tdulcet/compact-dictionaries",
            license = "MIT / Open"
        ),
        OpenSourceResource(
            title = "Android TextToSpeech API",
            category = "المحرك الصوتي والنطق",
            description = "واجهة نطق النصوص بالنظام الأصلي لتحويل الكلمات لقراءة صوتية بجودة عالية.",
            url = "https://developer.android.com/reference/android/speech/tts/TextToSpeech",
            license = "Android Open Source"
        ),
        OpenSourceResource(
            title = "Android RingtoneManager API",
            category = "نغمات التنبيه والمؤقت",
            description = "إدارة نغمات النظام للوصول لنغمات المنبه والمنبهات الصوتية مع المعاينة.",
            url = "https://developer.android.com/reference/android/media/RingtoneManager",
            license = "Android Open Source"
        )
    )

    fun openUrlInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "المصادر المفتوحة والتوثيق 🌐",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "دليل المكتبات والخوارزميات والتوثيق المفتوح المصدر المستخدم في رفيق الطالب",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(resourcesList) { res ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openUrlInBrowser(res.url) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = res.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = res.license,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = res.category,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = res.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = res.url,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.weight(1f)
                            )

                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "فتح الرابط",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
