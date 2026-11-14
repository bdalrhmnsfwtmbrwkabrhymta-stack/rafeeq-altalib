package com.example.data.quran

import android.content.Context
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

class QuranRepository(private val context: Context) {

    private var cachedSurahs: List<SurahDetail>? = null

    val allSurahList: List<SurahInfo> = listOf(
        SurahInfo(1, "الفاتحة", "Al-Fatihah", 7, "مكية", 1, 1),
        SurahInfo(2, "البقرة", "Al-Baqarah", 286, "مدنية", 1, 2),
        SurahInfo(3, "آل عمران", "Ali 'Imran", 200, "مدنية", 3, 50),
        SurahInfo(4, "النساء", "An-Nisa", 176, "مدنية", 4, 77),
        SurahInfo(5, "المائدة", "Al-Ma'idah", 120, "مدنية", 6, 106),
        SurahInfo(6, "الأنعام", "Al-An'am", 165, "مكية", 7, 128),
        SurahInfo(7, "الأعراف", "Al-A'raf", 206, "مكية", 8, 151),
        SurahInfo(8, "الأنفال", "Al-Anfal", 75, "مدنية", 9, 177),
        SurahInfo(9, "التوبة", "At-Tawbah", 129, "مدنية", 10, 187),
        SurahInfo(10, "يونس", "Yunus", 109, "مكية", 11, 208),
        SurahInfo(11, "هود", "Hud", 123, "مكية", 11, 221),
        SurahInfo(12, "يوسف", "Yusuf", 111, "مكية", 12, 235),
        SurahInfo(13, "الرعد", "Ar-Ra'd", 43, "مدنية", 13, 249),
        SurahInfo(14, "إبراهيم", "Ibrahim", 52, "مكية", 13, 255),
        SurahInfo(15, "الحجر", "Al-Hijr", 99, "مكية", 14, 262),
        SurahInfo(16, "النحل", "An-Nahl", 128, "مكية", 14, 267),
        SurahInfo(17, "الإسراء", "Al-Isra", 111, "مكية", 15, 282),
        SurahInfo(18, "الكهف", "Al-Kahf", 110, "مكية", 15, 293),
        SurahInfo(19, "مريم", "Maryam", 98, "مكية", 16, 305),
        SurahInfo(20, "طه", "Ta-Ha", 135, "مكية", 16, 312),
        SurahInfo(21, "الأنبياء", "Al-Anbiya", 112, "مكية", 17, 322),
        SurahInfo(22, "الحج", "Al-Hajj", 78, "مدنية", 17, 332),
        SurahInfo(23, "المؤمنون", "Al-Mu'minun", 118, "مكية", 18, 342),
        SurahInfo(24, "النور", "An-Nur", 64, "مدنية", 18, 350),
        SurahInfo(25, "الفرقان", "Al-Furqan", 77, "مكية", 18, 359),
        SurahInfo(26, "الشعراء", "Ash-Shu'ara", 227, "مكية", 19, 367),
        SurahInfo(27, "النمل", "An-Naml", 93, "مكية", 19, 377),
        SurahInfo(28, "القصص", "Al-Qasas", 88, "مكية", 20, 385),
        SurahInfo(29, "العنكبوت", "Al-'Ankabut", 69, "مكية", 20, 396),
        SurahInfo(30, "الروم", "Ar-Rum", 60, "مكية", 21, 404),
        SurahInfo(31, "لقمان", "Luqman", 34, "مكية", 21, 411),
        SurahInfo(32, "السجدة", "As-Sajdah", 30, "مكية", 21, 415),
        SurahInfo(33, "الأحزاب", "Al-Ahzab", 73, "مدنية", 21, 418),
        SurahInfo(34, "سبأ", "Saba", 54, "مكية", 22, 428),
        SurahInfo(35, "فاطر", "Fatir", 45, "مكية", 22, 434),
        SurahInfo(36, "يس", "Ya-Sin", 83, "مكية", 22, 440),
        SurahInfo(37, "الصافات", "As-Saffat", 182, "مكية", 23, 446),
        SurahInfo(38, "ص", "Sad", 88, "مكية", 23, 453),
        SurahInfo(39, "الزمر", "Az-Zumar", 75, "مكية", 23, 458),
        SurahInfo(40, "غافر", "Ghafir", 85, "مكية", 24, 467),
        SurahInfo(41, "فصلت", "Fussilat", 54, "مكية", 24, 477),
        SurahInfo(42, "الشورى", "Ash-Shura", 53, "مكية", 25, 483),
        SurahInfo(43, "الزخرف", "Az-Zukhruf", 89, "مكية", 25, 489),
        SurahInfo(44, "الدخان", "Ad-Dukhan", 59, "مكية", 25, 496),
        SurahInfo(45, "الجاثية", "Al-Jathiyah", 37, "مكية", 25, 499),
        SurahInfo(46, "الأحقاف", "Al-Ahqaf", 35, "مكية", 26, 502),
        SurahInfo(47, "محمد", "Muhammad", 38, "مدنية", 26, 507),
        SurahInfo(48, "الفتح", "Al-Fath", 29, "مدنية", 26, 511),
        SurahInfo(49, "الحجرات", "Al-Hujurat", 18, "مدنية", 26, 515),
        SurahInfo(50, "ق", "Qaf", 45, "مكية", 26, 518),
        SurahInfo(51, "الذاريات", "Adh-Dhariyat", 60, "مكية", 26, 520),
        SurahInfo(52, "الطور", "At-Tur", 49, "مكية", 27, 523),
        SurahInfo(53, "النجم", "An-Najm", 62, "مكية", 27, 526),
        SurahInfo(54, "القمر", "Al-Qamar", 55, "مكية", 27, 528),
        SurahInfo(55, "الرحمن", "Ar-Rahman", 78, "مدنية", 27, 531),
        SurahInfo(56, "الواقعة", "Al-Waqi'ah", 96, "مكية", 27, 534),
        SurahInfo(57, "الحديد", "Al-Hadid", 29, "مدنية", 27, 537),
        SurahInfo(58, "المجادلة", "Al-Mujadilah", 22, "مدنية", 28, 542),
        SurahInfo(59, "الحشر", "Al-Hashr", 24, "مدنية", 28, 545),
        SurahInfo(60, "الممتحنة", "Al-Mumtahanah", 13, "مدنية", 28, 549),
        SurahInfo(61, "الصف", "As-Saff", 14, "مدنية", 28, 551),
        SurahInfo(62, "الجمعة", "Al-Jumu'ah", 11, "مدنية", 28, 553),
        SurahInfo(63, "المنافقون", "Al-Munafiqun", 11, "مدنية", 28, 554),
        SurahInfo(64, "التغابن", "At-Taghabun", 18, "مدنية", 28, 556),
        SurahInfo(65, "الطلاق", "At-Talaq", 12, "مدنية", 28, 558),
        SurahInfo(66, "التحريم", "At-Tahrim", 12, "مدنية", 28, 560),
        SurahInfo(67, "الملك", "Al-Mulk", 30, "مكية", 29, 562),
        SurahInfo(68, "القلم", "Al-Qalam", 52, "مكية", 29, 564),
        SurahInfo(69, "الحاقة", "Al-Haqqah", 52, "مكية", 29, 566),
        SurahInfo(70, "المعارج", "Al-Ma'arij", 44, "مكية", 29, 568),
        SurahInfo(71, "نوح", "Nuh", 28, "مكية", 29, 570),
        SurahInfo(72, "الجن", "Al-Jinn", 28, "مكية", 29, 572),
        SurahInfo(73, "المزمل", "Al-Muzzammil", 20, "مكية", 29, 574),
        SurahInfo(74, "المدثر", "Al-Muddaththir", 56, "مكية", 29, 575),
        SurahInfo(75, "القيامة", "Al-Qiyamah", 40, "مكية", 29, 577),
        SurahInfo(76, "الإنسان", "Al-Insan", 31, "مدنية", 29, 578),
        SurahInfo(77, "المرسلات", "Al-Mursalat", 50, "مكية", 29, 580),
        SurahInfo(78, "النبأ", "An-Naba", 40, "مكية", 30, 582),
        SurahInfo(79, "النازعات", "An-Nazi'at", 46, "مكية", 30, 583),
        SurahInfo(80, "عبس", "'Abasa", 42, "مكية", 30, 585),
        SurahInfo(81, "التكوير", "At-Takwir", 29, "مكية", 30, 586),
        SurahInfo(82, "الانفطار", "Al-Infitar", 19, "مكية", 30, 587),
        SurahInfo(83, "المطففين", "Al-Mutaffifin", 36, "مكية", 30, 587),
        SurahInfo(84, "الانشقاق", "Al-Inshiqaq", 25, "مكية", 30, 589),
        SurahInfo(85, "البروج", "Al-Buruj", 22, "مكية", 30, 590),
        SurahInfo(86, "الطارق", "At-Tariq", 17, "مكية", 30, 591),
        SurahInfo(87, "الأعلى", "Al-A'la", 19, "مكية", 30, 591),
        SurahInfo(88, "الغاشية", "Al-Ghashiyah", 26, "مكية", 30, 592),
        SurahInfo(89, "الفجر", "Al-Fajr", 30, "مكية", 30, 593),
        SurahInfo(90, "البلد", "Al-Balad", 20, "مكية", 30, 594),
        SurahInfo(91, "الشمس", "Ash-Shams", 15, "مكية", 30, 595),
        SurahInfo(92, "الليل", "Al-Layl", 21, "مكية", 30, 595),
        SurahInfo(93, "الضحى", "Ad-Duha", 11, "مكية", 30, 596),
        SurahInfo(94, "الشرح", "Ash-Sharh", 8, "مكية", 30, 596),
        SurahInfo(95, "التين", "At-Tin", 8, "مكية", 30, 597),
        SurahInfo(96, "العلق", "Al-'Alaq", 19, "مكية", 30, 597),
        SurahInfo(97, "القدر", "Al-Qadr", 5, "مكية", 30, 598),
        SurahInfo(98, "البينة", "Al-Bayyinah", 8, "مدنية", 30, 598),
        SurahInfo(99, "الزلزلة", "Az-Zalzalah", 8, "مدنية", 30, 599),
        SurahInfo(100, "العاديات", "Al-'Adiyat", 11, "مكية", 30, 599),
        SurahInfo(101, "القارعة", "Al-Qari'ah", 11, "مكية", 30, 600),
        SurahInfo(102, "التكاثر", "At-Takathur", 8, "مكية", 30, 600),
        SurahInfo(103, "العصر", "Al-'Asr", 3, "مكية", 30, 601),
        SurahInfo(104, "الهمزة", "Al-Humazah", 9, "مكية", 30, 601),
        SurahInfo(105, "الفيل", "Al-Fil", 5, "مكية", 30, 601),
        SurahInfo(106, "قريش", "Quraysh", 4, "مكية", 30, 602),
        SurahInfo(107, "الماعون", "Al-Ma'un", 7, "مكية", 30, 602),
        SurahInfo(108, "الكوثر", "Al-Kawthar", 3, "مكية", 30, 602),
        SurahInfo(109, "الكافرون", "Al-Kafirun", 6, "مكية", 30, 603),
        SurahInfo(110, "النصر", "An-Nasr", 3, "مدنية", 30, 603),
        SurahInfo(111, "المسد", "Al-Masad", 5, "مكية", 30, 603),
        SurahInfo(112, "الإخلاص", "Al-Ikhlas", 4, "مكية", 30, 604),
        SurahInfo(113, "الفلق", "Al-Falaq", 5, "مكية", 30, 604),
        SurahInfo(114, "الناس", "An-Nas", 6, "مكية", 30, 604)
    )

    fun getRawResForJuz(juzNumber: Int): Int? {
        return when (juzNumber) {
            1 -> R.raw.juz_01
            29 -> R.raw.juz_29
            30 -> R.raw.juz_30
            else -> null
        }
    }

    val allJuzList: List<JuzInfo> = listOf(
        JuzInfo(1, "الجزء الأول (الم)", "Juz 1 (Al-Fatihah - Al-Baqarah)", 1, 1, 2, 141, "سورة الفاتحة إلى آية ١٤١ من سورة البقرة", "Al-Fatihah 1 - Al-Baqarah 141", 1),
        JuzInfo(2, "الجزء الثاني (سيقول السفهاء)", "Juz 2 (Sayaqul)", 2, 142, 2, 252, "سورة البقرة (١٤٢ - ٢٥٢)", "Al-Baqarah 142 - 252", 22),
        JuzInfo(3, "الجزء الثالث (تلك الرسل)", "Juz 3 (Tilka ar-Rusul)", 2, 253, 3, 92, "البقرة ٢٥٣ إلى آل عمران ٩٢", "Al-Baqarah 253 - Ali 'Imran 92", 42),
        JuzInfo(4, "الجزء الرابع (لن تنالوا البر)", "Juz 4 (Lan Tanalu)", 3, 93, 4, 23, "آل عمران ٩٣ إلى النساء ٢٣", "Ali 'Imran 93 - An-Nisa 23", 62),
        JuzInfo(5, "الجزء الخامس (والمحصنات)", "Juz 5 (Wal-Muhsanat)", 4, 24, 4, 147, "سورة النساء (٢٤ - ١٤٧)", "An-Nisa 24 - 147", 82),
        JuzInfo(6, "الجزء السادس (لا يحب الله)", "Juz 6 (La Yuhibbullahu)", 4, 148, 5, 81, "النساء ١٤٨ إلى المائدة ٨١", "An-Nisa 148 - Al-Ma'idah 81", 102),
        JuzInfo(7, "الجزء السابع (لتجدن أشد الناس)", "Juz 7 (Wa Iza Sami'u)", 5, 82, 6, 110, "المائدة ٨٢ إلى الأنعام ١١٠", "Al-Ma'idah 82 - Al-An'am 110", 121),
        JuzInfo(8, "الجزء الثامن (ولو أننا نزلنا)", "Juz 8 (Wa Law Annana)", 6, 111, 7, 87, "الأنعام ١١١ إلى الأعراف ٨٧", "Al-An'am 111 - Al-A'raf 87", 142),
        JuzInfo(9, "الجزء التاسع (قال الملأ)", "Juz 9 (Qal Al-Mala'u)", 7, 88, 8, 40, "الأعراف ٨٨ إلى الأنفال ٤٠", "Al-A'raf 88 - Al-Anfal 40", 162),
        JuzInfo(10, "الجزء العاشر (واعلموا)", "Juz 10 (Wa'lamu)", 8, 41, 9, 92, "الأنفال ٤١ إلى التوبة ٩٢", "Al-Anfal 41 - At-Tawbah 92", 182),
        JuzInfo(11, "الجزء الحادي عشر (يعتذرون إليكم)", "Juz 11 (Ya'tadhiruna)", 9, 93, 11, 5, "التوبة ٩٣ إلى هود ٥", "At-Tawbah 93 - Hud 5", 201),
        JuzInfo(12, "الجزء الثاني عشر (وما من دابة)", "Juz 12 (Wa Mamin Dabbatin)", 11, 6, 12, 52, "هود ٦ إلى يوسف ٥٢", "Hud 6 - Yusuf 52", 222),
        JuzInfo(13, "الجزء الثالث عشر (وما أبرئ نفسي)", "Juz 13 (Wa Ma Ubarri'u)", 12, 53, 14, 52, "يوسف ٥٣ إلى نهاية إبراهيم", "Yusuf 53 - Ibrahim 52", 242),
        JuzInfo(14, "الجزء الرابع عشر (ربما يود)", "Juz 14 (Rubama)", 15, 1, 16, 128, "سورة الحجر وسورة النحل كاملتين", "Al-Hijr 1 - An-Nahl 128", 262),
        JuzInfo(15, "الجزء الخامس عشر (سبحان الذي أسرى)", "Juz 15 (Subhanalladhi)", 17, 1, 18, 74, "الإسراء كاملة إلى الكهف ٧٤", "Al-Isra 1 - Al-Kahf 74", 282),
        JuzInfo(16, "الجزء السادس عشر (قال ألم أقل لك)", "Juz 16 (Qal Alam)", 18, 75, 20, 135, "الكهف ٧٥، ومريم وطه كاملتين", "Al-Kahf 75 - Ta-Ha 135", 302),
        JuzInfo(17, "الجزء السابع عشر (اقترب للناس)", "Juz 17 (Iqtaraba)", 21, 1, 22, 78, "سورة الأنبياء وسورة الحج كاملتين", "Al-Anbiya 1 - Al-Hajj 78", 322),
        JuzInfo(18, "الجزء الثامن عشر (قد أفلح المؤمنون)", "Juz 18 (Qad Aflaha)", 23, 1, 25, 20, "المؤمنون، النور، وبداية الفرقان", "Al-Mu'minun 1 - Al-Furqan 20", 342),
        JuzInfo(19, "الجزء التاسع عشر (وقال الذين لا يرجون)", "Juz 19 (Wa Qalalladhina)", 25, 21, 27, 55, "بقية الفرقان، الشعراء، وبداية النمل", "Al-Furqan 21 - An-Naml 55", 362),
        JuzInfo(20, "الجزء العشرون (فما كان جواب قومه)", "Juz 20 (Fama Kana)", 27, 56, 29, 45, "بقية النمل، القصص، وبداية العنكبوت", "An-Naml 56 - Al-'Ankabut 45", 382),
        JuzInfo(21, "الجزء الحادي والعشرون (ولا تجادلوا)", "Juz 21 (Utlu Ma Oohiya)", 29, 46, 33, 30, "العنكبوت، الروم، لقمان، السجدة، وبداية الأحزاب", "Al-'Ankabut 46 - Al-Ahzab 30", 402),
        JuzInfo(22, "الجزء الثاني والعشرون (ومن يقنت)", "Juz 22 (Wa Man Yaqnut)", 33, 31, 36, 27, "بقية الأحزاب، سبأ، فاطر، وبداية يس", "Al-Ahzab 31 - Ya-Sin 27", 422),
        JuzInfo(23, "الجزء الثالث والعشرون (وما أنزلنا)", "Juz 23 (Wa Ma Anzalna)", 36, 28, 39, 31, "بقية يس، الصافات، ص، وبداية الزمر", "Ya-Sin 28 - Az-Zumar 31", 442),
        JuzInfo(24, "الجزء الرابع والعشرون (فمن أظلم)", "Juz 24 (Faman Azlamu)", 39, 32, 41, 46, "بقية الزمر، غافر، وفصلت ٤٦", "Az-Zumar 32 - Fussilat 46", 462),
        JuzInfo(25, "الجزء الخامس والعشرون (إليه يرد)", "Juz 25 (Ilayhi Yuraddu)", 41, 47, 45, 37, "فصلت، الشورى، الزخرف، الدخان، الجاثية", "Fussilat 47 - Al-Jathiyah 37", 482),
        JuzInfo(26, "الجزء السادس والعشرون (حم الأحقاف)", "Juz 26 (Ha Meem)", 46, 1, 51, 30, "الأحقاف، محمد، الفتح، الحجرات، ق، الذاريات ٣٠", "Al-Ahqaf 1 - Adh-Dhariyat 30", 502),
        JuzInfo(27, "الجزء السابع والعشرون (قال فما خطبكم)", "Juz 27 (Qala Fama Khatbukum)", 51, 31, 57, 29, "الذاريات، الطور، النجم، القمر، الرحمن، الواقعة، الحديد", "Adh-Dhariyat 31 - Al-Hadid 29", 522),
        JuzInfo(28, "الجزء الثامن والعشرون (قد سمع الله)", "Juz 28 (Qad Sami'a)", 58, 1, 66, 12, "المجادلة إلى التحريم (٩ سور مباركة)", "Al-Mujadilah 1 - At-Tahrim 12", 542),
        JuzInfo(29, "الجزء التاسع والعشرون (تبارك الذي)", "Juz 29 (Tabarak)", 67, 1, 77, 50, "الملك إلى المرسلات (١١ سورة مباركة)", "Al-Mulk 1 - Al-Mursalat 50", 562),
        JuzInfo(30, "الجزء الثلاثون (عمّ يتساءلون)", "Juz 30 ('Amma)", 78, 1, 114, 6, "النبأ إلى الناس (٣٧ سورة مباركة)", "An-Naba 1 - An-Nas 6", 582)
    )

    val audioJuzList: List<JuzAudio> = (1..30).map { jNum ->
        val juzInfo = allJuzList.firstOrNull { it.number == jNum }
        JuzAudio(
            juzNumber = jNum,
            titleAr = juzInfo?.nameAr ?: "الجزء $jNum",
            titleEn = juzInfo?.nameEn ?: "Juz $jNum",
            rawResId = getRawResForJuz(jNum),
            surahsDesc = juzInfo?.descriptionAr ?: ""
        )
    } + listOf(
        JuzAudio(0, "آية الكرسي كاملة", "Ayat Al-Kursi", R.raw.juz_01, "سورة البقرة - آية 255 (بصوت خاشع)")
    )

    suspend fun getAllSurahs(): List<SurahDetail> = withContext(Dispatchers.IO) {
        cachedSurahs?.let { return@withContext it }

        val surahsMap = mutableMapOf<Int, MutableList<Ayah>>()
        
        // Priority list of asset filenames
        val assetNames = listOf(
            "quran.json",
            "quran_simple.txt",
            "quran-simple.txt",
            "quran-simple-clean (1).txt",
            "quran_text.txt"
        )

        var loaded = false

        for (assetName in assetNames) {
            if (loaded) break
            try {
                if (assetName.endsWith(".json")) {
                    val jsonString = context.assets.open(assetName).bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    for (i in 0 until jsonArray.length()) {
                        val surahObj = jsonArray.getJSONObject(i)
                        val surahNum = surahObj.getInt("surah")
                        val ayahsArray = surahObj.getJSONArray("ayahs")
                        val ayahsList = mutableListOf<Ayah>()
                        for (j in 0 until ayahsArray.length()) {
                            val ayahObj = ayahsArray.getJSONObject(j)
                            val ayahNum = ayahObj.getInt("ayah")
                            var ayahText = ayahObj.getString("text").replace("\ufeff", "").trim()

                            if (surahNum > 1 && ayahNum == 1) {
                                val bismillahPrefixes = listOf(
                                    "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ ",
                                    "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                                    "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ",
                                    "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                    "بسم الله الرحمن الرحيم ",
                                    "بسم الله الرحمن الرحيم",
                                    "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ ",
                                    "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ"
                                )
                                for (bPrefix in bismillahPrefixes) {
                                    if (ayahText.startsWith(bPrefix)) {
                                        ayahText = ayahText.removePrefix(bPrefix).trim()
                                        break
                                    }
                                }
                            }

                            ayahsList.add(Ayah(ayahNum, ayahText, surahNum))
                        }
                        surahsMap[surahNum] = ayahsList
                    }
                    if (surahsMap.size >= 114) {
                        loaded = true
                    }
                } else if (assetName.endsWith(".txt")) {
                    context.assets.open(assetName).bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                val parts = trimmed.split("|")
                                if (parts.size >= 3) {
                                    val sNum = parts[0].toIntOrNull() ?: 0
                                    val aNum = parts[1].toIntOrNull() ?: 0
                                    var text = parts.drop(2).joinToString("|").replace("\ufeff", "").trim()
                                    
                                    if (sNum > 1 && aNum == 1) {
                                        val bismillahPrefixes = listOf(
                                            "بسم الله الرحمن الرحيم ",
                                            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ",
                                            "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ "
                                        )
                                        for (bPrefix in bismillahPrefixes) {
                                            if (text.startsWith(bPrefix)) {
                                                text = text.removePrefix(bPrefix).trim()
                                                break
                                            }
                                        }
                                    }
                                    
                                    if (sNum in 1..114 && aNum > 0 && text.isNotEmpty()) {
                                        surahsMap.getOrPut(sNum) { mutableListOf() }
                                            .add(Ayah(aNum, text, sNum))
                                    }
                                }
                            }
                        }
                    }
                    if (surahsMap.size >= 114) {
                        loaded = true
                    }
                }
            } catch (e: Exception) {
                // Try next file format
            }
        }

        val result = allSurahList.map { info ->
            val ayahs = surahsMap[info.number] ?: emptyList()
            SurahDetail(info, ayahs)
        }
        cachedSurahs = result
        result
    }

    suspend fun getSurahDetail(surahNumber: Int): SurahDetail? {
        val list = getAllSurahs()
        return list.firstOrNull { it.info.number == surahNumber }
    }

    suspend fun getJuzDetail(juzNumber: Int): JuzDetail? {
        val juzInfo = allJuzList.firstOrNull { it.number == juzNumber } ?: return null
        val allSurahs = getAllSurahs()
        val sections = mutableListOf<JuzSection>()

        for (sNum in juzInfo.startSurahNumber..juzInfo.endSurahNumber) {
            val surahDetail = allSurahs.firstOrNull { it.info.number == sNum } ?: continue
            val startAyah = if (sNum == juzInfo.startSurahNumber) juzInfo.startAyahNumber else 1
            val endAyah = if (sNum == juzInfo.endSurahNumber) juzInfo.endAyahNumber else surahDetail.ayahs.size

            val filteredAyahs = surahDetail.ayahs.filter { it.numberInSurah in startAyah..endAyah }
            if (filteredAyahs.isNotEmpty()) {
                sections.add(JuzSection(surahDetail.info, filteredAyahs))
            }
        }

        return JuzDetail(juzInfo, sections)
    }

    suspend fun getAllJuzDetails(): List<JuzDetail> {
        return (1..30).mapNotNull { getJuzDetail(it) }
    }

    val dailyVerseList: List<Triple<String, String, String>> = listOf(
        Triple("﴿ وَقُل رَّبِّ زِدْنِي عِلْمًا ﴾", "سورة طه: 114", "دعاء مبارك لطلب العلم والتفوق والبركة في الفهم."),
        Triple("﴿ إِنَّ مَعَ الْعُسْرِ يُسْرًا ﴾", "سورة الشرح: 6", "بشارة من الله أن بعد كل صعوبة ومذاكرة شاقة فرجاً ونجاحاً باهراً."),
        Triple("﴿ وَأَن لَّيْسَ لِلْإِنسَانِ إِلَّا مَا سَعَىٰ ﴾", "سورة النجم: 39", "تذكير بأن السعي والاجتهاد محفوظ عند الله وسيجازى عليه صاحبه."),
        Triple("﴿ وَمَن يَتَّقِ اللَّهَ يَجْعَل لَّهُ مَخْرَجًا ﴾", "سورة الطلاق: 2", "التقوى مفتاح الفرج وفتح أبواب التوفيق والبركة في المذاكرة والحياة."),
        Triple("﴿ الَّذِينَ آمَنُوا وَتَطْمَئِنُّ قُلُوبُهُم بِذِكْرِ اللَّهِ ۗ أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ ﴾", "سورة الرعد: 28", "سكينة وطمأنينة تزيل القلق والتوتر قبل الامتحانات وأثناء الدراسة."),
        Triple("﴿ رَبِّ اشْرَحْ لِي صَدْرِي ۝ وَيَسِّرْ لِي أَمْرِي ۝ وَاحْلُلْ عُقْدَةً مِّن لِّسَانِي ۝ يَفْقَهُوا قَوْلِي ﴾", "سورة طه: 25-28", "دعاء التيسير وانشراح الصدر لطلب العلم وحسن التعبير والفهم."),
        Triple("﴿ فَإِذَا عَزَمْتَ فَتَوَكَّلْ عَلَى اللَّهِ ۚ إِنَّ اللَّهَ يُحِبُّ الْمُتَوَكِّلِينَ ﴾", "سورة آل عمران: 159", "اعمل بالأسباب وتوكل على الحي الذي لا يموت.")
    )

    fun getTodayVerse(): Triple<String, String, String> {
        val dayIndex = (System.currentTimeMillis() / (1000 * 60 * 60 * 24)).toInt() % dailyVerseList.size
        return dailyVerseList[dayIndex]
    }
}
