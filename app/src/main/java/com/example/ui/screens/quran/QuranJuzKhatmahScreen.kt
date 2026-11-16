package com.example.ui.screens.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.UserSettings
import com.example.data.quran.JuzInfo
import com.example.data.quran.MushafPagePagination
import com.example.data.quran.SurahInfo
import com.example.ui.components.AutoSizeText
import com.example.ui.screens.formatArabicDigits
import com.example.ui.theme.AmiriQuranFontFamily
import com.example.ui.theme.ReadexProFontFamily
import kotlin.math.roundToInt

@Composable
fun QuranJuzTabContent(
    allJuzList: List<JuzInfo>,
    allSurahInfoList: List<SurahInfo>,
    userSettings: UserSettings,
    isEnglish: Boolean,
    currentPlayingJuzNum: Int?,
    isAudioPlaying: Boolean,
    onOpenJuzPage: (startPage: Int) -> Unit,
    onPlayJuzAudio: (juzNumber: Int) -> Unit,
    onContinueDailyWird: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dailyGoal = userSettings.quranDailyPagesGoal.coerceAtLeast(1)
    val pagesRead = userSettings.quranPagesReadToday
    val progressFraction = (pagesRead.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
    val progressPercent = (progressFraction * 100f).roundToInt()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(QuranPalette.Background)
            .testTag("quran_juz_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Royal Blue Daily Wird & Khatmah Hero Card matching Mockup 4
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(QuranPalette.HeroGradient)
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Daily Reading & Khatmah" else "ورد القراءة والختمة اليومية",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (isEnglish)
                                "Goal: $dailyGoal pages daily"
                            else
                                "الهدف: ${formatArabicDigits(dailyGoal)} صفحات يومياً",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onContinueDailyWird() }
                                .testTag("juz_continue_wird_button"),
                            shape = RoundedCornerShape(22.dp),
                            color = Color.White
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isEnglish) "+ Continue Daily Wird" else "+ متابعة الورد اليومي",
                                    fontFamily = ReadexProFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = QuranPalette.RoyalBlue,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    QuranProgressRing(
                        progress = progressFraction,
                        percentText = "$progressPercent%",
                        ringSize = 86.dp,
                        strokeWidth = 7.dp
                    )
                }
            }
        }

        // Sequential 30 Juz White Cards with #1A6FE3 rounded square number badges
        items(allJuzList, key = { it.number }) { juz ->
            val pageRange = MushafPagePagination.getJuzPageRange(juz.number, allJuzList)
            val startSurahName = allSurahInfoList.firstOrNull { it.number == juz.startSurahNumber }?.nameAr ?: ""
            val isThisPlaying = isAudioPlaying && currentPlayingJuzNum == juz.number

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenJuzPage(juz.startPage) }
                    .testTag("juz_card_${juz.number}"),
                shape = RoundedCornerShape(18.dp),
                color = QuranPalette.Surface,
                border = BorderStroke(
                    width = if (isThisPlaying) 1.5.dp else 1.dp,
                    color = if (isThisPlaying) QuranPalette.RoyalBlue else QuranPalette.Border
                ),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Royal Blue Number Badge
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(QuranPalette.RoyalBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatNumberLocale(juz.number, isEnglish),
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        AutoSizeText(
                            text = if (isEnglish) juz.nameEn else juz.nameAr,
                            maxFontSize = 15.5.sp,
                            minFontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ReadexProFontFamily,
                            color = QuranPalette.NavyInk,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val subtitleText = if (isEnglish) {
                            "Surah $startSurahName • Pages ${pageRange.first} - ${pageRange.last}"
                        } else {
                            "سورة $startSurahName • صفحة ${formatArabicDigits(pageRange.first)} إلى ${formatArabicDigits(pageRange.last)}"
                        }
                        AutoSizeText(
                            text = subtitleText,
                            maxFontSize = 12.5.sp,
                            minFontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = ReadexProFontFamily,
                            color = QuranPalette.SlateMuted,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Circular Royal Blue Outlined Play Button matching Mockup 4 (48.dp touch target)
                    IconButton(
                        onClick = { onPlayJuzAudio(juz.number) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("juz_play_button_${juz.number}")
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = if (isThisPlaying) QuranPalette.RoyalBlue else Color.White,
                            border = BorderStroke(1.8.dp, QuranPalette.RoyalBlue)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isEnglish) "Play Juz Audio" else "تشغيل تلاوة الجزء",
                                    tint = if (isThisPlaying) Color.White else QuranPalette.RoyalBlue,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuranKhatmahTabContent(
    userSettings: UserSettings,
    todayVerse: Triple<String, String, String>,
    isEnglish: Boolean,
    onContinueBookmark: () -> Unit,
    onIncrementPageRead: () -> Unit,
    onResetPagesRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dailyGoal = userSettings.quranDailyPagesGoal.coerceAtLeast(1)
    val pagesRead = userSettings.quranPagesReadToday
    val progressFraction = (pagesRead.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
    val progressPercent = (progressFraction * 100f).roundToInt()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(QuranPalette.Background)
            .testTag("quran_khatmah_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Royal Blue Daily Goal & Progress Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(QuranPalette.HeroGradient)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isEnglish) "Daily Khatmah Progress" else "متابعة الورد والختمة اليومية",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (isEnglish)
                                    "Read today: $pagesRead / $dailyGoal pages"
                                else
                                    "قرأت اليوم ${formatArabicDigits(pagesRead)} من ${formatArabicDigits(dailyGoal)} صفحات",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        QuranProgressRing(
                            progress = progressFraction,
                            percentText = "$progressPercent%",
                            ringSize = 84.dp,
                            strokeWidth = 7.dp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onIncrementPageRead,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = QuranPalette.RoyalBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("khatmah_increment_page")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Add 1 Page" else "قرأت صفحة",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onResetPagesRead,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("khatmah_reset_pages")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Reset" else "تصفير",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Last Bookmark Resume Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onContinueBookmark() },
                shape = RoundedCornerShape(18.dp),
                color = QuranPalette.Surface,
                border = BorderStroke(1.dp, QuranPalette.Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(QuranPalette.RoyalBlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = QuranPalette.RoyalBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isEnglish) "Last Saved Bookmark" else "آخر علامة مرجعية",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = QuranPalette.NavyInk
                            )
                            Text(
                                text = "سورة ${userSettings.quranBookmarkSurahName} • آية ${formatArabicDigits(userSettings.quranBookmarkAyah)}",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.5.sp,
                                color = QuranPalette.SlateMuted
                            )
                        }
                    }

                    Button(
                        onClick = onContinueBookmark,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QuranPalette.RoyalBlue)
                    ) {
                        Text(
                            text = if (isEnglish) "Open" else "متابعة",
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }

        // Daily Verse Reflection Card in Royal Blue & White
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = QuranPalette.Surface,
                border = BorderStroke(1.dp, QuranPalette.Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = QuranPalette.RoyalBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Verse of the Day" else "آية وتدبر اليوم",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = QuranPalette.RoyalBlue
                            )
                        }
                        Text(
                            text = todayVerse.second,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = QuranPalette.SlateMuted
                        )
                    }

                    Text(
                        text = todayVerse.first,
                        fontFamily = AmiriQuranFontFamily,
                        fontSize = 21.sp,
                        lineHeight = 36.sp,
                        color = QuranPalette.NavyInk,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = todayVerse.third,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = QuranPalette.SlateBody,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 30-Day Khatmah Guide
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = QuranPalette.RoyalBlueSoft,
                border = BorderStroke(1.dp, QuranPalette.RoyalBlueTint)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "خطة ختم القرآن الكريم",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = QuranPalette.RoyalBlueDark
                    )
                    Text(
                        text = "• ختمة في شهر: ٢٠ صفحة يومياً (٤ صفحات بعد كل صلاة مكتوبة).\n• ختمة في شهرين: ١٠ صفحات يومياً.\n• الورد الخفيف الثابت: ٤ صفحات يومياً تحفظ قلبك متصلًا بالقرآن.",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                        color = QuranPalette.NavyInk
                    )
                }
            }
        }
    }
}
