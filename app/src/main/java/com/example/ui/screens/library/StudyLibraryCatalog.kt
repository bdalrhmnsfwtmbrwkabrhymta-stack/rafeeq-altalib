package com.example.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * فهرس «المكتبة الدراسية» — المراحل والصفوف والمسارات والكتب الرسمية.
 *
 * المصادر الرسمية المعتمدة (وزارة التربية والتعليم المصرية):
 * - المكتبة الإلكترونية الشاملة: https://ellibrary.moe.gov.eg/books/
 * - المرحلة الابتدائية: https://ellibrary.moe.gov.eg/Books/Books-Primary
 * - المرحلة الإعدادية: https://ellibrary.moe.gov.eg/books/Books-Preparatory/
 * - المرحلة الثانوية: https://ellibrary.moe.gov.eg/Books-sec
 * - البكالوريا المصرية: https://ellibrary.moe.gov.eg/EgyptianBaccalaureate
 */

enum class SchoolStage(
    val id: String,
    val titleAr: String,
    val shortTitleAr: String,
    val officialCatalogUrl: String
) {
    PRIMARY(
        id = "primary",
        titleAr = "المرحلة الابتدائية",
        shortTitleAr = "الابتدائي",
        officialCatalogUrl = "https://ellibrary.moe.gov.eg/Books/Books-Primary"
    ),
    PREPARATORY(
        id = "preparatory",
        titleAr = "المرحلة الإعدادية",
        shortTitleAr = "الإعدادي",
        officialCatalogUrl = "https://ellibrary.moe.gov.eg/books/Books-Preparatory/"
    ),
    SECONDARY(
        id = "secondary",
        titleAr = "المرحلة الثانوية",
        shortTitleAr = "الثانوي",
        officialCatalogUrl = "https://ellibrary.moe.gov.eg/Books-sec"
    ),
    BACCALAUREATE(
        id = "baccalaureate",
        titleAr = "البكالوريا المصرية",
        shortTitleAr = "البكالوريا",
        officialCatalogUrl = "https://ellibrary.moe.gov.eg/EgyptianBaccalaureate"
    )
}

enum class StudyType(
    val id: String,
    val labelAr: String,
    val subtitleEn: String
) {
    ARABIC("arabic", "عربي", "مدارس عربي"),
    LANGUAGES("languages", "لغات", "Schools / Languages")
}

enum class BaccalaureateTrack(
    val id: String,
    val labelAr: String,
    val shortLabel: String
) {
    CORE("core", "المواد الأساسية المشتركة", "المشتركة"),
    MEDICINE_LIFE("medicine", "الطب وعلوم الحياة", "الطب وعلوم الحياة"),
    ENGINEERING_CS("engineering", "الهندسة وعلوم الحاسب", "الهندسة والحاسب"),
    BUSINESS("business", "الأعمال", "الأعمال"),
    ARTS_HUMANITIES("arts", "الآداب والفنون", "الآداب والفنون")
}

enum class FolderVisualVariant {
    OCEAN_ORANGE_WAVE,   // تدرج أزرق سماوي مع موجة برتقالية دافئة (مثل مجلد My Files العلوي)
    PAPER_ARCHIVE,       // ورقة بيضاء أنيقة تطل من خلف الزجاج (مثل مجلد Archives)
    CRIMSON_TEAL_WAVE,   // موجة حمراء برتقالية مع تيل عميق (تألق رائع في الوضعين الداكن والفاتح)
    SUNRISE_INDIGO_WAVE  // موجة برتقالية ذهبية مع كحلي هادئ
}

data class StudyGrade(
    val id: String,
    val labelAr: String,
    val shortLabelAr: String,
    val stage: SchoolStage,
    val officialCatalogUrl: String
)

data class SubjectFolderSpec(
    val id: String,
    val nameAr: String,
    val nameLanguages: String,
    val icon: ImageVector,
    val waveColorsLight: List<Color>,
    val waveColorsDark: List<Color>,
    val visualVariant: FolderVisualVariant,
    val badgeType: String = "count" // "count" or "plus" or "dots"
)

data class LibraryBookRecord(
    val id: String,
    val title: String,
    val subjectId: String,
    val subjectName: String,
    val gradeId: String,
    val studyType: StudyType,
    val academicYear: String,
    val term: String = "الترم الأول",          // "الترم الأول" | "الترم الثاني" | "العام كامل"
    val bookType: String = "كتاب الوزارة",    // "كتاب الوزارة" | "ملخص" | "مراجعة" | "دليل معلم"
    val track: BaccalaureateTrack? = null,
    val officialPdfUrl: String,
    val officialPageUrl: String? = null,
    val officialCoverUrl: String? = null,
    val sourceName: String = "وزارة التربية والتعليم المصرية"
)

object StudyLibraryCatalog {

    const val MOE_MAIN_BOOKS_URL = "https://ellibrary.moe.gov.eg/books/"
    const val MOE_HOME_URL = "https://moe.gov.eg/"

    val grades: List<StudyGrade> = listOf(
        // المرحلة الابتدائية
        StudyGrade("p1", "الصف الأول الابتدائي", "1 ابتدائي", SchoolStage.PRIMARY, SchoolStage.PRIMARY.officialCatalogUrl),
        StudyGrade("p2", "الصف الثاني الابتدائي", "2 ابتدائي", SchoolStage.PRIMARY, SchoolStage.PRIMARY.officialCatalogUrl),
        StudyGrade("p3", "الصف الثالث الابتدائي", "3 ابتدائي", SchoolStage.PRIMARY, SchoolStage.PRIMARY.officialCatalogUrl),
        StudyGrade("p4", "الصف الرابع الابتدائي", "4 ابتدائي", SchoolStage.PRIMARY, SchoolStage.PRIMARY.officialCatalogUrl),
        StudyGrade("p5", "الصف الخامس الابتدائي", "5 ابتدائي", SchoolStage.PRIMARY, SchoolStage.PRIMARY.officialCatalogUrl),
        StudyGrade("p6", "الصف السادس الابتدائي", "6 ابتدائي", SchoolStage.PRIMARY, SchoolStage.PRIMARY.officialCatalogUrl),

        // المرحلة الإعدادية
        StudyGrade("m1", "الصف الأول الإعدادي", "1 إعدادي", SchoolStage.PREPARATORY, SchoolStage.PREPARATORY.officialCatalogUrl),
        StudyGrade("m2", "الصف الثاني الإعدادي", "2 إعدادي", SchoolStage.PREPARATORY, SchoolStage.PREPARATORY.officialCatalogUrl),
        StudyGrade("m3", "الصف الثالث الإعدادي", "3 إعدادي", SchoolStage.PREPARATORY, SchoolStage.PREPARATORY.officialCatalogUrl),

        // المرحلة الثانوية
        StudyGrade("s1", "الصف الأول الثانوي", "1 ثانوي", SchoolStage.SECONDARY, SchoolStage.SECONDARY.officialCatalogUrl),
        StudyGrade("s2", "الصف الثاني الثانوي", "2 ثانوي", SchoolStage.SECONDARY, SchoolStage.SECONDARY.officialCatalogUrl),
        StudyGrade("s3", "الصف الثالث الثانوي", "3 ثانوي", SchoolStage.SECONDARY, SchoolStage.SECONDARY.officialCatalogUrl),

        // نظام البكالوريا المصرية الجديدة
        StudyGrade("bac", "نظام البكالوريا المصرية الجديدة", "البكالوريا", SchoolStage.BACCALAUREATE, SchoolStage.BACCALAUREATE.officialCatalogUrl)
    )

    fun gradesForStage(stage: SchoolStage): List<StudyGrade> =
        grades.filter { it.stage == stage }

    /**
     * قائمة الكتب الرسمية الفردية الموثقة.
     *
     * تنبيه دقة البيانات:
     * تُترك فارغة عمداً إلى أن تتوفر روابط PDF فردية موثقة ومؤكدة من بوابة وزارة التربية والتعليم المصرية
     * حتى لا تُعرض أي روابط أو أغلفة وهمية.
     * لإضافة كتاب رسمي موثق، أضف كائن [LibraryBookRecord] هنا وسينعكس تلقائياً داخل مجلد المادة والبحث والفلاتر.
     */
    val books: List<LibraryBookRecord> = emptyList()

    // لوحات ألوان متدرجة مستوحاة مباشرة من الفولدرات الزجاجية المرفقة
    private val blueOrangeLight = listOf(Color(0xFF0EA5E9), Color(0xFF1D4ED8), Color(0xFFF97316))
    private val blueOrangeDark = listOf(Color(0xFF0F766E), Color(0xFF1E3A8A), Color(0xFFEA580C))

    private val archivePaperLight = listOf(Color(0xFF334155), Color(0xFF64748B), Color(0xFFFB923C))
    private val archivePaperDark = listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFFEA580C))

    private val crimsonTealLight = listOf(Color(0xFF0D9488), Color(0xFFEA580C), Color(0xFFE11D48))
    private val crimsonTealDark = listOf(Color(0xFF0F4C5C), Color(0xFFDC2626), Color(0xFFF97316))

    private val indigoAmberLight = listOf(Color(0xFF4F46E5), Color(0xFF0284C7), Color(0xFFF59E0B))
    private val indigoAmberDark = listOf(Color(0xFF312E81), Color(0xFF0369A1), Color(0xFFD97706))

    fun getSubjectsFor(
        gradeId: String,
        studyType: StudyType,
        bacTrack: BaccalaureateTrack = BaccalaureateTrack.CORE
    ): List<SubjectFolderSpec> {
        return when (gradeId) {
            "p1", "p2", "p3" -> listOf(
                SubjectFolderSpec("arabic", "اللغة العربية", "Arabic Language", Icons.Default.MenuBook, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "plus"),
                SubjectFolderSpec("math", "الرياضيات", "Mathematics (Math)", Icons.Default.Calculate, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("english", "اللغة الإنجليزية (Connect)", "English (Connect Plus)", Icons.Default.Translate, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("discover", "اكتشف", "Discover", Icons.Default.Explore, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("religion", "التربية الدينية", "Religious Education", Icons.Default.Mosque, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count"),
                SubjectFolderSpec("values", "القيم واحترام الآخر", "Values & Respect", Icons.Default.Favorite, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots")
            )

            "p4", "p5", "p6" -> listOf(
                SubjectFolderSpec("arabic", "اللغة العربية", "Arabic Language", Icons.Default.MenuBook, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "plus"),
                SubjectFolderSpec("math", "الرياضيات", "Mathematics (Math)", Icons.Default.Calculate, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("science", "العلوم", "Science", Icons.Default.Science, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("social", "الدراسات الاجتماعية", "Social Studies", Icons.Default.Public, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("english", "اللغة الإنجليزية (Connect)", "English (Connect Plus)", Icons.Default.Translate, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count"),
                SubjectFolderSpec("ict", "تكنولوجيا المعلومات والاتصالات", "ICT (Technology)", Icons.Default.Computer, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("skills", "المهارات المهنية", "Professional Skills", Icons.Default.Handyman, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("religion", "التربية الدينية", "Religious Education", Icons.Default.Mosque, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots")
            )

            "m1", "m2", "m3" -> listOf(
                SubjectFolderSpec("arabic", "اللغة العربية", "Arabic Language", Icons.Default.MenuBook, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "plus"),
                SubjectFolderSpec("math", "الرياضيات", "Mathematics (Math)", Icons.Default.Calculate, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("science", "العلوم", "Science", Icons.Default.Science, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("social", "الدراسات الاجتماعية", "Social Studies", Icons.Default.Public, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("english", "اللغة الإنجليزية", "English Language", Icons.Default.Translate, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count"),
                SubjectFolderSpec("ict", "الكمبيوتر وتكنولوجيا المعلومات", "Computer & ICT", Icons.Default.Computer, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("religion", "التربية الدينية", "Religious Education", Icons.Default.Mosque, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots")
            )

            "s1" -> listOf(
                SubjectFolderSpec("arabic", "اللغة العربية", "Arabic Language", Icons.Default.MenuBook, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "plus"),
                SubjectFolderSpec("english", "اللغة الأجنبية الأولى", "First Foreign Language", Icons.Default.Translate, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("math", "الرياضيات", "Mathematics", Icons.Default.Calculate, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("integrated_sciences", "العلوم المتكاملة", "Integrated Sciences", Icons.Default.Biotech, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("history", "التاريخ", "History", Icons.Default.AccountBalance, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("philosophy", "الفلسفة والمنطق", "Philosophy & Logic", Icons.Default.Psychology, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count"),
                SubjectFolderSpec("second_lang", "اللغة الأجنبية الثانية", "Second Foreign Language", Icons.Default.Language, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("religion", "التربية الدينية", "Religious Education", Icons.Default.Mosque, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count")
            )

            "s2" -> listOf(
                SubjectFolderSpec("arabic", "اللغة العربية", "Arabic Language", Icons.Default.MenuBook, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "plus"),
                SubjectFolderSpec("english", "اللغة الإنجليزية", "English Language", Icons.Default.Translate, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("math_pure", "الرياضيات البحتة والعامة", "Pure & General Math", Icons.Default.Calculate, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("physics", "الفيزياء", "Physics", Icons.Default.Bolt, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("chemistry", "الكيمياء", "Chemistry", Icons.Default.Science, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count"),
                SubjectFolderSpec("biology", "الأحياء", "Biology", Icons.Default.Biotech, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("history", "التاريخ", "History", Icons.Default.AccountBalance, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("geography", "الجغرافيا", "Geography", Icons.Default.Public, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("psychology", "علم النفس والاجتماع", "Psychology & Sociology", Icons.Default.Psychology, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count")
            )

            "s3" -> listOf(
                SubjectFolderSpec("arabic", "اللغة العربية", "Arabic Language", Icons.Default.MenuBook, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "plus"),
                SubjectFolderSpec("english", "اللغة الأجنبية الأولى", "First Foreign Language", Icons.Default.Translate, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("physics", "الفيزياء", "Physics", Icons.Default.Bolt, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("chemistry", "الكيمياء", "Chemistry", Icons.Default.Science, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count"),
                SubjectFolderSpec("biology", "الأحياء", "Biology", Icons.Default.Biotech, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("math_s3", "الرياضيات التطبيقية والبحتة", "Pure & Applied Math", Icons.Default.Functions, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("history", "التاريخ", "History", Icons.Default.AccountBalance, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("geography", "الجغرافيا السياسية", "Geography", Icons.Default.Public, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("statistics", "الإحصاء", "Statistics", Icons.Default.BarChart, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count")
            )

            "bac" -> getBaccalaureateSubjects(bacTrack)

            else -> emptyList()
        }
    }

    private fun getBaccalaureateSubjects(track: BaccalaureateTrack): List<SubjectFolderSpec> {
        return when (track) {
            BaccalaureateTrack.CORE -> listOf(
                SubjectFolderSpec("bac_arabic", "اللغة العربية", "Arabic Language", Icons.Default.MenuBook, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "plus"),
                SubjectFolderSpec("bac_english", "اللغة الأجنبية الأولى", "First Foreign Language", Icons.Default.Translate, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("bac_history", "التاريخ المصري", "Egyptian History", Icons.Default.AccountBalance, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("bac_religion", "التربية الدينية", "Religious Education", Icons.Default.Mosque, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("bac_programming", "البرمجة وعلوم الحاسب", "Programming & CS", Icons.Default.Code, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count")
            )
            BaccalaureateTrack.MEDICINE_LIFE -> listOf(
                SubjectFolderSpec("bac_biology", "الأحياء (متقدم)", "Biology (Advanced)", Icons.Default.Biotech, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "plus"),
                SubjectFolderSpec("bac_chemistry", "الكيمياء (متقدم)", "Chemistry (Advanced)", Icons.Default.Science, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count"),
                SubjectFolderSpec("bac_physics", "الفيزياء (تخصصي)", "Physics", Icons.Default.Bolt, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("bac_core_link", "المواد الأساسية المشتركة", "Core Subjects", Icons.Default.MenuBook, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots")
            )
            BaccalaureateTrack.ENGINEERING_CS -> listOf(
                SubjectFolderSpec("bac_math", "الرياضيات (متقدم)", "Mathematics (Advanced)", Icons.Default.Functions, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "plus"),
                SubjectFolderSpec("bac_physics_eng", "الفيزياء (متقدم)", "Physics (Advanced)", Icons.Default.Bolt, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "count"),
                SubjectFolderSpec("bac_chemistry_eng", "الكيمياء", "Chemistry", Icons.Default.Science, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count"),
                SubjectFolderSpec("bac_cs_eng", "البرمجة والذكاء الاصطناعي", "Programming & AI", Icons.Default.Memory, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots")
            )
            BaccalaureateTrack.BUSINESS -> listOf(
                SubjectFolderSpec("bac_economics", "الاقتصاد والإدارة", "Economics & Management", Icons.Default.TrendingUp, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "plus"),
                SubjectFolderSpec("bac_stats", "الرياضيات والإحصاء للأعمال", "Business Math & Stats", Icons.Default.BarChart, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count"),
                SubjectFolderSpec("bac_accounting", "مبادئ المحاسبة", "Accounting Principles", Icons.Default.ReceiptLong, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots")
            )
            BaccalaureateTrack.ARTS_HUMANITIES -> listOf(
                SubjectFolderSpec("bac_psychology", "علم النفس والاجتماع", "Psychology & Sociology", Icons.Default.Psychology, indigoAmberLight, indigoAmberDark, FolderVisualVariant.SUNRISE_INDIGO_WAVE, "plus"),
                SubjectFolderSpec("bac_geography", "الجغرافيا", "Geography", Icons.Default.Public, blueOrangeLight, blueOrangeDark, FolderVisualVariant.OCEAN_ORANGE_WAVE, "count"),
                SubjectFolderSpec("bac_philosophy", "الفلسفة والمنطق", "Philosophy & Logic", Icons.Default.AutoStories, archivePaperLight, archivePaperDark, FolderVisualVariant.PAPER_ARCHIVE, "dots"),
                SubjectFolderSpec("bac_second_lang", "اللغة الأجنبية الثانية", "Second Foreign Language", Icons.Default.Language, crimsonTealLight, crimsonTealDark, FolderVisualVariant.CRIMSON_TEAL_WAVE, "count")
            )
        }
    }

    fun filterBooks(
        gradeId: String,
        studyType: StudyType,
        subjectId: String? = null,
        bacTrack: BaccalaureateTrack? = null,
        term: String = "الكل",
        bookType: String = "الكل",
        query: String = ""
    ): List<LibraryBookRecord> {
        val cleanQuery = query.trim()
        return books.filter { book ->
            book.gradeId == gradeId &&
                book.studyType == studyType &&
                (subjectId == null || book.subjectId == subjectId) &&
                (bacTrack == null || book.track == null || book.track == bacTrack) &&
                (term == "الكل" || book.term == term) &&
                (bookType == "الكل" || book.bookType == bookType) &&
                (cleanQuery.isEmpty() ||
                    book.title.contains(cleanQuery, ignoreCase = true) ||
                    book.subjectName.contains(cleanQuery, ignoreCase = true))
        }
    }
}
