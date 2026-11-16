package com.example.domain.prayer

data class CountryCityInfo(
    val countryArabic: String,
    val countryEnglish: String,
    val cityArabic: String,
    val cityEnglish: String,
    val latitude: Double,
    val longitude: Double,
    val defaultMethod: CalculationMethod,
    val methodId: Int
)

data class MuezzinVoice(
    val id: String,
    val nameArabic: String,
    val nameEnglish: String,
    val subtitleArabic: String = "نقاء استوديو 320 kbps • يعمل بدون إنترنت",
    val bitrateLabel: String = "320\nkbps\nHD",
    val rawResName: String = "adhan_egypt_mbc_hq",
    val isDefaultPrimary: Boolean = false,
    val isOnlineAvailable: Boolean = true,
    val audioUrl: String = "",
    val previewUrl: String = ""
)

object PrayerDataConstants {

    val MUEZZIN_VOICES = listOf(
        MuezzinVoice(
            id = "egypt_mbc_hq",
            nameArabic = "الأذان المصري الأصيل (MBC وإذاعة القرآن)",
            nameEnglish = "Authentic Egyptian Adhan (MBC & Quran Radio)",
            subtitleArabic = "الصوت الافتراضي الأساسي • يعمل بدون إنترنت",
            bitrateLabel = "320\nkbps\nHD",
            rawResName = "adhan_egypt_mbc_hq",
            isDefaultPrimary = true,
            isOnlineAvailable = true,
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Muhammad_Refaat_-_Cairo_(%D9%85%D8%AD%D9%85%D8%AF_%D8%B1%D9%81%D8%B9%D8%AA_-_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9).mp3",
            previewUrl = ""
        ),
        MuezzinVoice(
            id = "makkah_hq",
            nameArabic = "أذان الحرم المكي الشريف",
            nameEnglish = "Makkah Al-Haram Adhan (Sheikh Ali Mulla)",
            subtitleArabic = "بصوت الشيخ علي أحمد ملا • يعمل بدون إنترنت",
            bitrateLabel = "320\nkbps\nHD",
            rawResName = "adhan_makkah_hq",
            isDefaultPrimary = false,
            isOnlineAvailable = true,
            audioUrl = "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ali_Ibn_Ahmad_Mala_1_-_Al_Haram_Al_Maki_(%D8%B9%D9%84%D9%8A_%D8%A8%D9%86_%D8%A3%D8%AD%D9%85%D8%AF_%D9%85%D9%84%D8%A7_-_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D9%83%D9%8A).mp3",
            previewUrl = ""
        ),
        MuezzinVoice(
            id = "madinah_hq",
            nameArabic = "الأذان الخاشع فائق النقاء",
            nameEnglish = "Serene Pure Adhan (Al-Madinah & Alafasy)",
            subtitleArabic = "أذان المسجد النبوي والشيخ العفاسي • يعمل بدون إنترنت",
            bitrateLabel = "320\nkbps\nHD",
            rawResName = "adhan_madinah_hq",
            isDefaultPrimary = false,
            isOnlineAvailable = true,
            audioUrl = "https://archive.org/download/Azan_Madina_HQ/Azan_Madina_HQ.mp3",
            previewUrl = ""
        )
    )

    val COUNTRIES_CITIES = listOf(
        // Egypt (كافة المحافظات والمدن المصرية بإحداثيات GPS بالغة الدقة)
        CountryCityInfo("مصر", "Egypt", "القاهرة", "Cairo", 30.0444, 31.2357, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "الإسكندرية", "Alexandria", 31.2001, 29.9187, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "الجيزة", "Giza", 30.0131, 31.2089, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "أسيوط", "Asyut", 27.1783, 31.1859, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "المنصورة (الدقهلية)", "Mansoura", 31.0409, 31.3785, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "طنطا (الغربية)", "Tanta", 30.7865, 31.0004, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "الزقازيق (الشرقية)", "Zagazig", 30.5877, 31.5020, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "بنها (القليوبية)", "Banha", 30.4660, 31.1853, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "شبين الكوم (المنوفية)", "Shibin El Kom", 30.5593, 31.0109, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "دمنهور (البحيرة)", "Damanhur", 31.0400, 30.4700, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "كفر الشيخ", "Kafr El Sheikh", 31.1107, 30.9388, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "دمياط", "Damietta", 31.4175, 31.8144, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "بور سعيد", "Port Said", 31.2653, 32.3019, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "الإسماعيلية", "Ismailia", 30.6043, 32.2723, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "السويس", "Suez", 29.9668, 32.5498, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "الفيوم", "Fayoum", 29.3084, 30.8428, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "بني سويف", "Beni Suef", 29.0661, 31.0994, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "المنيا", "Minya", 28.0871, 30.7618, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "سوهاج", "Sohag", 26.5570, 31.6948, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "قنا", "Qena", 26.1551, 32.7160, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "الأقصر", "Luxor", 25.6872, 32.6396, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "أسوان", "Aswan", 24.0889, 32.8998, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "الغردقة (البحر الأحمر)", "Hurghada", 27.2579, 33.8116, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "شرم الشيخ (جنوب سيناء)", "Sharm El Sheikh", 27.9158, 34.3299, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "العريش (شمال سيناء)", "El Arish", 31.1325, 33.7989, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "مرسى مطروح", "Marsa Matrouh", 31.3543, 27.2373, CalculationMethod.EGYPTIAN, 5),
        CountryCityInfo("مصر", "Egypt", "الخارجة (الوادي الجديد)", "El Kharga", 25.4514, 30.5472, CalculationMethod.EGYPTIAN, 5),

        // Saudi Arabia
        CountryCityInfo("السعودية", "Saudi Arabia", "مكة المكرمة", "Makkah", 21.3891, 39.8579, CalculationMethod.UMM_AL_QURA, 4),
        CountryCityInfo("السعودية", "Saudi Arabia", "المدينة المنورة", "Madinah", 24.5247, 39.5692, CalculationMethod.UMM_AL_QURA, 4),
        CountryCityInfo("السعودية", "Saudi Arabia", "الرياض", "Riyadh", 24.7136, 46.6753, CalculationMethod.UMM_AL_QURA, 4),
        CountryCityInfo("السعودية", "Saudi Arabia", "جدة", "Jeddah", 21.5433, 39.1728, CalculationMethod.UMM_AL_QURA, 4),
        CountryCityInfo("السعودية", "Saudi Arabia", "الدمام", "Dammam", 26.4207, 50.0888, CalculationMethod.UMM_AL_QURA, 4),

        // UAE
        CountryCityInfo("الإمارات", "UAE", "دبي", "Dubai", 25.2048, 55.2708, CalculationMethod.UMM_AL_QURA, 8),
        CountryCityInfo("الإمارات", "UAE", "أبو ظبي", "Abu Dhabi", 24.4539, 54.3773, CalculationMethod.UMM_AL_QURA, 8),
        CountryCityInfo("الإمارات", "UAE", "الشارقة", "Sharjah", 25.3463, 55.4209, CalculationMethod.UMM_AL_QURA, 8),

        // Kuwait
        CountryCityInfo("الكويت", "Kuwait", "الكويت", "Kuwait City", 29.3759, 47.9774, CalculationMethod.UMM_AL_QURA, 8),

        // Qatar
        CountryCityInfo("قطر", "Qatar", "الدوحة", "Doha", 25.2854, 51.5310, CalculationMethod.UMM_AL_QURA, 8),

        // Oman
        CountryCityInfo("عمان", "Oman", "مسقط", "Muscat", 23.5880, 58.3829, CalculationMethod.UMM_AL_QURA, 8),

        // Jordan
        CountryCityInfo("الأردن", "Jordan", "عمان", "Amman", 31.9454, 35.9284, CalculationMethod.MWL, 3),

        // Palestine
        CountryCityInfo("فلسطين", "Palestine", "القدس", "Jerusalem", 31.7683, 35.2137, CalculationMethod.MWL, 3),

        // Morocco
        CountryCityInfo("المغرب", "Morocco", "الرباط", "Rabat", 34.0209, -6.8416, CalculationMethod.MWL, 3),
        CountryCityInfo("المغرب", "Morocco", "الدار البيضاء", "Casablanca", 33.5731, -7.5898, CalculationMethod.MWL, 3),

        // Algeria
        CountryCityInfo("الجزائر", "Algeria", "الجزائر", "Algiers", 36.7538, 3.0588, CalculationMethod.MWL, 3),

        // Tunisia
        CountryCityInfo("تونس", "Tunisia", "تونس", "Tunis", 36.8065, 10.1815, CalculationMethod.MWL, 3),

        // Iraq
        CountryCityInfo("العراق", "Iraq", "بغداد", "Baghdad", 33.3152, 44.3661, CalculationMethod.MWL, 3),

        // USA & North America
        CountryCityInfo("الولايات المتحدة", "USA", "نيويورك", "New York", 40.7128, -74.0060, CalculationMethod.ISNA, 2),
        CountryCityInfo("الولايات المتحدة", "USA", "واشنطن", "Washington D.C.", 38.9072, -77.0369, CalculationMethod.ISNA, 2),
        CountryCityInfo("الولايات المتحدة", "USA", "شيكاغو", "Chicago", 41.8781, -87.6298, CalculationMethod.ISNA, 2),
        CountryCityInfo("كندا", "Canada", "تورونتو", "Toronto", 43.6532, -79.3832, CalculationMethod.ISNA, 2),

        // Europe
        CountryCityInfo("المملكة المتحدة", "UK", "لندن", "London", 51.5074, -0.1278, CalculationMethod.MWL, 3),
        CountryCityInfo("فرنسا", "France", "باريس", "Paris", 48.8566, 2.3522, CalculationMethod.MWL, 12),
        CountryCityInfo("ألمانيا", "Germany", "برلين", "Berlin", 52.5200, 13.4050, CalculationMethod.MWL, 3),
        CountryCityInfo("تركيا", "Turkey", "إسطنبول", "Istanbul", 41.0082, 28.9784, CalculationMethod.MWL, 13)
    )

    fun getMethodId(method: CalculationMethod): Int {
        return when (method) {
            CalculationMethod.EGYPTIAN -> 5
            CalculationMethod.UMM_AL_QURA -> 4
            CalculationMethod.MWL -> 3
            CalculationMethod.ISNA -> 2
            CalculationMethod.KARACHI -> 1
        }
    }
}
