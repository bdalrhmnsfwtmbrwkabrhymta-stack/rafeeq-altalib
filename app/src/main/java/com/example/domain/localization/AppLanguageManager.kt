package com.example.domain.localization

object AppLanguageManager {

    data class SupportedLanguage(
        val code: String,
        val nativeName: String,
        val englishName: String,
        val flagEmoji: String,
        val landmark: String,
        val isRtl: Boolean = false
    )

    val supportedLanguages = listOf(
        SupportedLanguage("ar", "العربية", "Arabic", "🇸🇦", "الكعبة المشرفة (مكة المكرمة)", isRtl = true),
        SupportedLanguage("en", "English", "English", "🇬🇧", "Big Ben (London)", isRtl = false),
        SupportedLanguage("fr", "Français", "French", "🇫🇷", "Tour Eiffel (Paris)", isRtl = false),
        SupportedLanguage("zh", "中文", "Chinese (Mandarin)", "🇨🇳", "万里长城 (Great Wall)", isRtl = false),
        SupportedLanguage("de", "Deutsch", "German", "🇩🇪", "Brandenburger Tor (Berlin)", isRtl = false),
        SupportedLanguage("es", "Español", "Spanish", "🇪🇸", "La Sagrada Família (Madrid)", isRtl = false),
        SupportedLanguage("tr", "Türkçe", "Turkish", "🇹🇷", "Sultanahmet Camii (Istanbul)", isRtl = false),
        SupportedLanguage("id", "Bahasa Indonesia", "Indonesian", "🇮🇩", "Candi Borobudur (Jakarta)", isRtl = false),
        SupportedLanguage("ru", "Русский", "Russian", "🇷🇺", "Московский Кремль (Moscow)", isRtl = false)
    )

    fun isRtlLanguage(langCode: String): Boolean {
        return langCode == "ar"
    }

    fun getLanguage(code: String): SupportedLanguage {
        return supportedLanguages.firstOrNull { it.code == code } ?: supportedLanguages.first()
    }

    fun applyLocale(context: android.content.Context, langCode: String) {
        try {
            val locale = java.util.Locale(langCode)
            java.util.Locale.setDefault(locale)
            val resources = context.resources
            val configuration = resources.configuration
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                configuration.setLocales(android.os.LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                configuration.locale = locale
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.createConfigurationContext(configuration)
            }
            resources.updateConfiguration(configuration, resources.displayMetrics)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getText(
        lang: String,
        ar: String,
        en: String,
        fr: String? = null,
        de: String? = null,
        es: String? = null,
        tr: String? = null,
        id: String? = null,
        ru: String? = null,
        zh: String? = null
    ): String {
        return when (lang) {
            "ar" -> ar
            "fr" -> fr ?: en
            "de" -> de ?: en
            "es" -> es ?: en
            "tr" -> tr ?: en
            "id" -> id ?: en
            "ru" -> ru ?: en
            "zh" -> zh ?: en
            else -> en
        }
    }

    fun t(key: String, lang: String = "ar"): String {
        return getString(key, lang)
    }

    // Comprehensive UI String translations for setup flow and main navigation
    fun getString(key: String, lang: String): String {
        val resolved = when (lang) {
            "en" -> enStrings[key] ?: arStrings[key]
            "fr" -> frStrings[key] ?: enStrings[key] ?: arStrings[key]
            "zh" -> zhStrings[key] ?: enStrings[key] ?: arStrings[key]
            "de" -> deStrings[key] ?: enStrings[key] ?: arStrings[key]
            "es" -> esStrings[key] ?: enStrings[key] ?: arStrings[key]
            "tr" -> trStrings[key] ?: enStrings[key] ?: arStrings[key]
            "id" -> idStrings[key] ?: enStrings[key] ?: arStrings[key]
            "ru" -> ruStrings[key] ?: enStrings[key] ?: arStrings[key]
            else -> arStrings[key]
        }
        if (resolved != null) return resolved

        return when (key) {
            "quests_title" -> when (lang) {
                "ar" -> "مهام المغامرة والأهداف ⚔️"; "fr" -> "Quêtes & Objectifs ⚔️"; "zh" -> "任务与目标 ⚔️"; "de" -> "Quests & Ziele ⚔️"; "es" -> "Misiones y Objetivos ⚔️"; else -> "Quests & Goals ⚔️"
            }
            "quests_new" -> when (lang) {
                "ar" -> "إنشاء مهمة جديدة ➕"; "fr" -> "Nouvelle quête ➕"; "zh" -> "新建任务 ➕"; "de" -> "Neue Quest ➕"; "es" -> "Nueva misión ➕"; else -> "New Quest ➕"
            }
            "quests_complete" -> when (lang) {
                "ar" -> "إكمال المهمة ✅"; "fr" -> "Terminer ✅"; "zh" -> "完成任务 ✅"; "de" -> "Abschließen ✅"; "es" -> "Completar ✅"; else -> "Complete Quest ✅"
            }
            "stats_level" -> when (lang) {
                "ar" -> "المستوى"; "fr" -> "Niveau"; "zh" -> "等级"; "de" -> "Stufe"; "es" -> "Nivel"; else -> "Level"
            }
            "step_language" -> when (lang) {
                "ar" -> "اللغة"; "fr" -> "Langue"; "zh" -> "语言"; "de" -> "Sprache"; "es" -> "Idioma"; "tr" -> "Dil"; "id" -> "Bahasa"; "ru" -> "Язык"; else -> "Language"
            }
            "step_location" -> when (lang) {
                "ar" -> "الموقع"; "fr" -> "Localisation"; "zh" -> "位置"; "de" -> "Standort"; "es" -> "Ubicación"; "tr" -> "Konum"; "id" -> "Lokasi"; "ru" -> "Локация"; else -> "Location"
            }
            "step_account" -> when (lang) {
                "ar" -> "الحساب"; "fr" -> "Compte"; "zh" -> "账户"; "de" -> "Konto"; "es" -> "Cuenta"; "tr" -> "Hesap"; "id" -> "Akun"; "ru" -> "Аккаунт"; else -> "Account"
            }
            "setup_flow_title" -> when (lang) {
                "ar" -> "إعداد رفيق الطالب"; "fr" -> "Configuration"; "zh" -> "学生伴侣设置"; "de" -> "Einrichtung"; "es" -> "Configuración"; "tr" -> "Kurulum"; "id" -> "Pengaturan"; "ru" -> "Настройка"; else -> "Setup Flow"
            }
            "welcome_greeting" -> when (lang) {
                "ar" -> "مرحباً بك"; "fr" -> "Bienvenue"; "zh" -> "欢迎"; "de" -> "Willkommen"; "es" -> "Bienvenido"; "tr" -> "Hoş Geldiniz"; "id" -> "Selamat Datang"; "ru" -> "Добро пожаловать"; else -> "Welcome"
            }
            "academic_journey" -> when (lang) {
                "ar" -> "رحلتك نحو التفوق والتميز الدراسي 🚀"; "fr" -> "Votre parcours vers l'excellence académique 🚀"; "zh" -> "通往学术卓越的旅程 🚀"; "de" -> "Ihr Weg zu akademischer Exzellenz 🚀"; "es" -> "Tu camino hacia la excelencia académica 🚀"; else -> "Your journey to academic excellence 🚀"
            }
            "battery_warning_title" -> when (lang) {
                "ar" -> "استقرار التنبيهات وتوفير الطاقة ⚡"; "fr" -> "Fiabilité & Économie d'Énergie ⚡"; "zh" -> "电池优化与闹钟稳定 ⚡"; "de" -> "Zuverlässigkeit & Energiesparmodus ⚡"; "es" -> "Fiabilidad & Ahorro de Energía ⚡"; else -> "Alarms Reliability & Battery ⚡"
            }
            "battery_warning_msg" -> when (lang) {
                "ar" -> "وضع توفير الطاقة مفعل! اضغط هنا للاستثناء لضمان عمل الأذان والتنبيهات ⚡"; "fr" -> "Mode économie d'énergie actif! Appuyez pour exempter et garantir les alarmes ⚡"; "zh" -> "省电模式开启中！点击排除以确保闹钟正常运行 ⚡"; "de" -> "Energiesparmodus aktiv! Tippen zum Ausnehmen für sichere Alarme ⚡"; else -> "Battery saver active! Tap here to exempt and guarantee reliable alarms ⚡"
            }
            "battery_exempted_msg" -> when (lang) {
                "ar" -> "التطبيق مستثنى وتعمل التنبيهات والأذان بدقة فائقة ✅"; "fr" -> "Application exemptée - les alertes fonctionnent parfaitement ✅"; "zh" -> "应用已排除优化，闹钟精准运行 ✅"; else -> "App is exempt - alarms trigger with extreme precision ✅"
            }
            else -> key
        }
    }

    private val arStrings = mapOf(
        "app_title" to "رفيق الطالب",
        "step_1_of_3" to "الخطوة 1 من 3: اختيار اللغة",
        "step_2_of_3" to "الخطوة 2 من 3: تحديد الموقع",
        "step_3_of_3" to "الخطوة 3 من 3: حساب الطالب والتسجيل",
        "step_language_title" to "اختر لغتك المفضلة",
        "step_language_subtitle" to "يدعم رفيق الطالب جميع اللغات بالكامل لتجربة دراسية سلسة",
        "step_location_title" to "مواقيت الصلاة والقبلة بدقة",
        "step_location_subtitle" to "تحديد موقعك تلقائياً لضبط مواقيت الأذان والصلوات الخمس بدقة متناهية",
        "step_account_title" to "تسجيل الدخول وملف الطالب",
        "step_account_subtitle" to "احفظ تقدمك الدراسي، ونافس زملاءك في لوحة الشرف العالمية",
        "btn_next" to "الخطوة التالية ➡️",
        "btn_back" to "الخطوة السابقة ⬅️",
        "btn_apply_next" to "تطبيق اللغة والمتابعة ➡️",
        "btn_finish_setup" to "إنهاء الإعداد وبدء الاستخدام 🚀",
        "btn_skip" to "تخطي",
        "btn_detect_gps" to "تحديد الموقع تلقائياً عبر GPS 📍",
        "btn_detecting_gps" to "جاري تحديد موقعك بدقة...",
        "btn_change_city_manually" to "موقعك غير صحيح؟ اختر المدينة يدوياً 🗺️",
        "current_location_label" to "الموقع الحالي المعتمد:",
        "student_name_label" to "اسم الطالب / الطالبة",
        "student_name_hint" to "أدخل اسمك الكريم هنا...",
        "educational_stage_label" to "المرحلة الدراسية",
        "stage_primary" to "المرحلة الابتدائية",
        "stage_middle" to "المرحلة الإعدادية / المتوسطة",
        "stage_high_school" to "المرحلة الثانوية",
        "stage_university" to "المرحلة الجامعية والدراسات",
        "btn_login" to "تسجيل الدخول الآن 🔐",
        "btn_continue_guest" to "المتابعة كطالب محلي (بدون حساب) 👤",
        "terms_notice" to "بالمتابعة فإنك توافق على شروط الاستخدام وسياسة الخصوصية الخاصة برفيق الطالب.",
        "nav_home" to "الرئيسية",
        "nav_dictionary" to "القاموس",
        "nav_schedule" to "الجدول",
        "nav_timer" to "المؤقت",
        "nav_docs" to "المذكرات والـ PDF",
        "nav_whiteboard" to "السبورة",
        "nav_quests" to "مهام المغامرة",
        "nav_settings" to "الإعدادات",
        "quests_title" to "مهام المغامرة والأهداف ⚔️",
        "quests_subtitle" to "أنجز مهامك الدراسية، واحمِ تتابعك اليومي، وارتقِ برتبتك إلى القمة 🏆",
        "quests_all" to "الكل",
        "quests_core" to "أساسية 💎",
        "quests_important" to "مهمة ⭐",
        "quests_normal" to "عادية 🎯",
        "quests_completed" to "مكتملة ✅",
        "quests_streak_shield_title" to "دروع الحماية 🛡️",
        "quests_shield_desc" to "تحميك الدروع تلقائياً من كسر الـ Streak عند تفويت موعد اليوم",
        "quests_streak_risk_warning" to "⚠️ تحذير: الـ Streak في خطر! أكمل المهام الأساسية قبل انتهاء اليوم",
        "quests_wake_up_time" to "وقت الاستيقاظ",
        "quests_daily_deadline" to "الموعد النهائي اليومي",
        "quests_level_up_congrats" to "🎉 مبارك! لقد ارتقيت إلى مستوى جديد!",
        "quests_rank_up_congrats" to "👑 ترقية شرفية! لقد وصلت إلى رتبة جديدة!",
        "quests_streak_recovery" to "استعادة الـ Streak 🔄",
        "quests_streak_recovery_desc" to "أكمل تحدياً سريعاً لاستعادة تتابعك المفقود وحماية تقدمك",
        "quests_collection_title" to "خزانة المقتنيات والألقاب 👑",
        "quests_sound_fx_label" to "المؤثرات الصوتية للألعاب 🔊",
        "quests_stats_tab" to "الإحصائيات والتحليلات 📊",
        "quests_history_tab" to "سجل الإنجازات 📜",
        "quests_logs_tab" to "سجل النظام 📝"
    )

    private val enStrings = mapOf(
        "app_title" to "Student Companion",
        "step_1_of_3" to "Step 1 of 3: Choose Language",
        "step_2_of_3" to "Step 2 of 3: Set Location",
        "step_3_of_3" to "Step 3 of 3: Student Account & Login",
        "step_language_title" to "Choose Your Preferred Language",
        "step_language_subtitle" to "Full multi-language support for a smooth, personalized study experience",
        "step_location_title" to "Accurate Prayer Times & Qibla",
        "step_location_subtitle" to "Automatically calibrate your location for precise prayer and study schedule alerts",
        "step_account_title" to "Student Login & Profile",
        "step_account_subtitle" to "Save your study progress, flashcards, and compete on the global student leaderboard",
        "btn_next" to "Next Step ➡️",
        "btn_back" to "⬅️ Previous Step",
        "btn_apply_next" to "Apply & Next Step ➡️",
        "btn_finish_setup" to "Finish Setup & Start 🚀",
        "btn_skip" to "Skip",
        "btn_detect_gps" to "Auto-Detect Location via GPS 📍",
        "btn_detecting_gps" to "Locating your position...",
        "btn_change_city_manually" to "Incorrect location? Choose City Manually 🗺️",
        "current_location_label" to "Current Selected Location:",
        "student_name_label" to "Student Name",
        "student_name_hint" to "Enter your name...",
        "educational_stage_label" to "Educational Stage",
        "stage_primary" to "Primary School",
        "stage_middle" to "Middle / Preparatory School",
        "stage_high_school" to "High School / Secondary",
        "stage_university" to "University / Higher Education",
        "btn_login" to "Log In Now 🔐",
        "btn_continue_guest" to "Continue as Local Student (Guest) 👤",
        "terms_notice" to "By continuing, you agree to the Student Companion Terms & Privacy Policy.",
        "nav_home" to "Home",
        "nav_dictionary" to "Dictionary",
        "nav_schedule" to "Schedule",
        "nav_timer" to "Timer",
        "nav_docs" to "PDF & Docs",
        "nav_whiteboard" to "Whiteboard",
        "nav_quests" to "Quests",
        "nav_settings" to "Settings",
        "quests_title" to "Adventure Quests & Goals ⚔️",
        "quests_subtitle" to "Complete study quests, protect your daily streak, and rise through the ranks 🏆",
        "quests_all" to "All",
        "quests_core" to "Core 💎",
        "quests_important" to "Important ⭐",
        "quests_normal" to "Normal 🎯",
        "quests_completed" to "Completed ✅",
        "quests_streak_shield_title" to "Streak Shields 🛡️",
        "quests_shield_desc" to "Shields automatically protect your streak when missing a daily deadline",
        "quests_streak_risk_warning" to "⚠️ Warning: Streak at risk! Complete core quests before deadline",
        "quests_wake_up_time" to "Wake-up Time",
        "quests_daily_deadline" to "Daily Deadline",
        "quests_level_up_congrats" to "🎉 Congratulations! You reached a new Level!",
        "quests_rank_up_congrats" to "👑 Rank Up! You unlocked a prestigious new Rank!",
        "quests_streak_recovery" to "Streak Recovery 🔄",
        "quests_streak_recovery_desc" to "Complete a quick recovery challenge to restore your broken streak",
        "quests_collection_title" to "Collection & Honor Titles 👑",
        "quests_sound_fx_label" to "Game Sound FX 🔊",
        "quests_stats_tab" to "Stats & Analytics 📊",
        "quests_history_tab" to "Achievement History 📜",
        "quests_logs_tab" to "System Logs 📝"
    )

    private val frStrings = mapOf(
        "app_title" to "Compagnon de l'Étudiant",
        "step_1_of_3" to "Étape 1 sur 3 : Choisir la langue",
        "step_2_of_3" to "Étape 2 sur 3 : Définir la localisation",
        "step_3_of_3" to "Étape 3 sur 3 : Compte étudiant & Connexion",
        "step_language_title" to "Choisissez votre langue préférée",
        "step_language_subtitle" to "Support complet multilingue pour une expérience d'étude personnalisée",
        "step_location_title" to "Horaires de prière précis & Qibla",
        "step_location_subtitle" to "Localisation automatique pour des rappels précis des prières et du planning",
        "step_account_title" to "Connexion & Profil de l'étudiant",
        "step_account_subtitle" to "Sauvegardez vos progrès et rejoignez le classement mondial des étudiants",
        "btn_next" to "Étape suivante ➡️",
        "btn_back" to "⬅️ Étape précédente",
        "btn_apply_next" to "Appliquer et continuer ➡️",
        "btn_finish_setup" to "Terminer et commencer 🚀",
        "btn_skip" to "Passer",
        "btn_detect_gps" to "Détecter la position par GPS 📍",
        "btn_detecting_gps" to "Localisation en cours...",
        "btn_change_city_manually" to "Emplacement incorrect ? Choisir manuellement 🗺️",
        "current_location_label" to "Position actuelle sélectionnée :",
        "student_name_label" to "Nom de l'étudiant",
        "student_name_hint" to "Entrez votre nom...",
        "educational_stage_label" to "Niveau d'études",
        "stage_primary" to "École primaire",
        "stage_middle" to "Collège",
        "stage_high_school" to "Lycée",
        "stage_university" to "Université / Études supérieures",
        "btn_login" to "Se connecter maintenant 🔐",
        "btn_continue_guest" to "Continuer en tant qu'invité 👤",
        "terms_notice" to "En continuant, vous acceptez les conditions d'utilisation et la politique de confidentialité.",
        "nav_home" to "Accueil",
        "nav_dictionary" to "Dictionnaire",
        "nav_schedule" to "Emploi",
        "nav_timer" to "Minuteur",
        "nav_docs" to "PDF & Docs",
        "nav_whiteboard" to "Tableau",
        "nav_settings" to "Paramètres"
    )

    private val zhStrings = mapOf(
        "app_title" to "学生伴侣 (Rafeeq)",
        "step_1_of_3" to "步骤 1/3：选择语言",
        "step_2_of_3" to "步骤 2/3：设置地理位置",
        "step_3_of_3" to "步骤 3/3：学生账户与登录",
        "step_language_title" to "选择您的偏好语言",
        "step_language_subtitle" to "全面支持多语言界面，打造专属高效学习体验",
        "step_location_title" to "精准作息与祷告时间",
        "step_location_subtitle" to "通过GPS自动校准位置，精准提供日程和时间提醒",
        "step_account_title" to "学生账户与登录",
        "step_account_subtitle" to "云端保存学习进度、记忆卡片并参与全球学生荣誉榜",
        "btn_next" to "下一步 ➡️",
        "btn_back" to "⬅️ 上一步",
        "btn_apply_next" to "应用语言并继续 ➡️",
        "btn_finish_setup" to "完成设置并开启学习 🚀",
        "btn_skip" to "跳过",
        "btn_detect_gps" to "GPS 自动定位 📍",
        "btn_detecting_gps" to "正在定位当前位置...",
        "btn_change_city_manually" to "位置有误？手动选择城市 🗺️",
        "current_location_label" to "当前已选位置：",
        "student_name_label" to "学生姓名",
        "student_name_hint" to "请输入您的姓名...",
        "educational_stage_label" to "学段与年级",
        "stage_primary" to "小学阶段",
        "stage_middle" to "初中阶段",
        "stage_high_school" to "高中阶段",
        "stage_university" to "大学及以上",
        "btn_login" to "立即登录 🔐",
        "btn_continue_guest" to "以访客身份继续 👤",
        "terms_notice" to "继续即表示您同意学生伴侣的服务条款和隐私政策。",
        "nav_home" to "首页",
        "nav_dictionary" to "词典",
        "nav_schedule" to "日程",
        "nav_timer" to "计时器",
        "nav_docs" to "文档 & PDF",
        "nav_whiteboard" to "白板",
        "nav_settings" to "设置"
    )

    private val deStrings = mapOf(
        "app_title" to "Studentenbegleiter",
        "step_1_of_3" to "Schritt 1 von 3: Sprache wählen",
        "step_2_of_3" to "Schritt 2 von 3: Standort festlegen",
        "step_3_of_3" to "Schritt 3 von 3: Studentenkonto & Login",
        "step_language_title" to "Wählen Sie Ihre Sprache",
        "step_language_subtitle" to "Vollständige Unterstützung aller Sprachen für Ihr Studium",
        "step_location_title" to "Genaue Gebets- und Studienzeiten",
        "step_location_subtitle" to "Automatische GPS-Ortung für präzise Zeitpläne und Benachrichtigungen",
        "step_account_title" to "Studentenkonto & Anmeldung",
        "step_account_subtitle" to "Speichern Sie Ihre Notizen und messen Sie sich in der Bestenliste",
        "btn_next" to "Nächster Schritt ➡️",
        "btn_back" to "⬅️ Vorheriger Schritt",
        "btn_apply_next" to "Sprache anwenden & Weiter ➡️",
        "btn_finish_setup" to "Einrichtung abschließen 🚀",
        "btn_skip" to "Überspringen",
        "btn_detect_gps" to "Standort per GPS erfassen 📍",
        "btn_detecting_gps" to "Standort wird ermittelt...",
        "btn_change_city_manually" to "Falscher Standort? Stadt manuell wählen 🗺️",
        "current_location_label" to "Aktueller Standort:",
        "student_name_label" to "Name des Schülers/Studenten",
        "student_name_hint" to "Namen eingeben...",
        "educational_stage_label" to "Bildungsstufe",
        "stage_primary" to "Grundschule",
        "stage_middle" to "Realschule / Mittelschule",
        "stage_high_school" to "Gymnasium / Oberstufe",
        "stage_university" to "Universität / Hochschule",
        "btn_login" to "Jetzt einloggen 🔐",
        "btn_continue_guest" to "Als Gast fortfahren 👤",
        "terms_notice" to "Mit dem Fortfahren akzeptieren Sie die Nutzungsbedingungen und Datenschutzrichtlinien.",
        "nav_home" to "Startseite",
        "nav_dictionary" to "Wörterbuch",
        "nav_schedule" to "Zeitplan",
        "nav_timer" to "Timer",
        "nav_docs" to "PDF & Dokumente",
        "nav_whiteboard" to "Whiteboard",
        "nav_settings" to "Einstellungen"
    )

    private val esStrings = mapOf(
        "app_title" to "Compañero del Estudiante",
        "step_1_of_3" to "Paso 1 de 3: Elegir idioma",
        "step_2_of_3" to "Paso 2 de 3: Establecer ubicación",
        "step_3_of_3" to "Paso 3 de 3: Cuenta de estudiante e inicio de sesión",
        "step_language_title" to "Elige tu idioma preferido",
        "step_language_subtitle" to "Soporte completo en varios idiomas para una experiencia de estudio óptima",
        "step_location_title" to "Horarios de oración precisos y Qibla",
        "step_location_subtitle" to "Calibra automáticamente tu ubicación para horarios de estudio y oración exactos",
        "step_account_title" to "Inicio de sesión y perfil del estudiante",
        "step_account_subtitle" to "Guarda tu progreso de estudio y compite en la clasificación mundial de estudiantes",
        "btn_next" to "Siguiente paso ➡️",
        "btn_back" to "⬅️ Paso anterior",
        "btn_apply_next" to "Aplicar y continuar ➡️",
        "btn_finish_setup" to "Finalizar y comenzar 🚀",
        "btn_skip" to "Omitir",
        "btn_detect_gps" to "Detectar ubicación por GPS 📍",
        "btn_detecting_gps" to "Detectando ubicación...",
        "btn_change_city_manually" to "¿Ubicación incorrecta? Elegir manualmente 🗺️",
        "current_location_label" to "Ubicación actual:",
        "student_name_label" to "Nombre del estudiante",
        "student_name_hint" to "Introduce tu nombre...",
        "educational_stage_label" to "Nivel educativo",
        "stage_primary" to "Educación primaria",
        "stage_middle" to "Educación secundaria obligatoria",
        "stage_high_school" to "Bachillerato",
        "stage_university" to "Universidad / Educación superior",
        "btn_login" to "Iniciar sesión ahora 🔐",
        "btn_continue_guest" to "Continuar como invitado 👤",
        "terms_notice" to "Al continuar, aceptas los términos y la política de privacidad.",
        "nav_home" to "Inicio",
        "nav_dictionary" to "Diccionario",
        "nav_schedule" to "Horario",
        "nav_timer" to "Temporizador",
        "nav_docs" to "PDF y Docs",
        "nav_whiteboard" to "Pizarra",
        "nav_settings" to "Ajustes"
    )

    private val trStrings = mapOf(
        "app_title" to "Öğrenci Yoldaşı",
        "step_1_of_3" to "1/3 Adım: Dil Seçimi",
        "step_2_of_3" to "2/3 Adım: Konum Belirleme",
        "step_3_of_3" to "3/3 Adım: Öğrenci Hesabı ve Giriş",
        "step_language_title" to "Tercih Ettiğiniz Dili Seçin",
        "step_language_subtitle" to "Kişiselleştirilmiş bir çalışma deneyimi için çok dilli tam destek",
        "step_location_title" to "Doğru Namaz Vakitleri ve Kıble",
        "step_location_subtitle" to "Konumunuzu otomatik olarak tespit ederek vakitleri hassas şekilde ayarlayın",
        "step_account_title" to "Giriş Yap ve Öğrenci Profili",
        "step_account_subtitle" to "Çalışma ilerlemenizi kaydedin ve öğrenci sıralamasında yerinizi alın",
        "btn_next" to "Sonraki Adım ➡️",
        "btn_back" to "⬅️ Önceki Adım",
        "btn_apply_next" to "Dili Uygula ve Devam Et ➡️",
        "btn_finish_setup" to "Kurulumu Bitir ve Başla 🚀",
        "btn_skip" to "Geç",
        "btn_detect_gps" to "GPS ile Konumu Bul 📍",
        "btn_detecting_gps" to "Konumunuz tespit ediliyor...",
        "btn_change_city_manually" to "Konum yanlış mı? Şehri Manuel Seç 🗺️",
        "current_location_label" to "Geçerli Konum:",
        "student_name_label" to "Öğrenci Adı",
        "student_name_hint" to "Adınızı girin...",
        "educational_stage_label" to "Eğitim Kademesi",
        "stage_primary" to "İlkokul",
        "stage_middle" to "Ortaokul",
        "stage_high_school" to "Lise",
        "stage_university" to "Üniversite ve Yükseköğrenim",
        "btn_login" to "Şimdi Giriş Yap 🔐",
        "btn_continue_guest" to "Misafir Olarak Devam Et 👤",
        "terms_notice" to "Devam ederek Kullanım Şartları ve Gizlilik Politikasını kabul etmiş olursunuz.",
        "nav_home" to "Ana Sayfa",
        "nav_dictionary" to "Sözlük",
        "nav_schedule" to "Program",
        "nav_timer" to "Sayaç",
        "nav_docs" to "PDF & Notlar",
        "nav_whiteboard" to "Tahta",
        "nav_settings" to "Ayarlar"
    )

    private val idStrings = mapOf(
        "app_title" to "Sahabat Belajar",
        "step_1_of_3" to "Langkah 1 dari 3: Pilih Bahasa",
        "step_2_of_3" to "Langkah 2 dari 3: Tentukan Lokasi",
        "step_3_of_3" to "Langkah 3 dari 3: Akun & Masuk Siswa",
        "step_language_title" to "Pilih Bahasa Pilihan Anda",
        "step_language_subtitle" to "Mendukung berbagai bahasa untuk kenyamanan belajar Anda",
        "step_location_title" to "Jadwal Sholat Akurat & Kiblat",
        "step_location_subtitle" to "Deteksi lokasi otomatis untuk perhitungan waktu sholat dan jadwal belajar yang tepat",
        "step_account_title" to "Masuk & Profil Siswa",
        "step_account_subtitle" to "Simpan kemajuan belajar dan bersaing di papan peringkat siswa",
        "btn_next" to "Langkah Berikutnya ➡️",
        "btn_back" to "⬅️ Langkah Sebelumnya",
        "btn_apply_next" to "Terapkan & Lanjutkan ➡️",
        "btn_finish_setup" to "Selesaikan & Mulai 🚀",
        "btn_skip" to "Lewati",
        "btn_detect_gps" to "Deteksi Lokasi Otomatis via GPS 📍",
        "btn_detecting_gps" to "Sedang mendeteksi lokasi...",
        "btn_change_city_manually" to "Lokasi salah? Pilih Kota Manual 🗺️",
        "current_location_label" to "Lokasi Terpilih:",
        "student_name_label" to "Nama Siswa",
        "student_name_hint" to "Masukkan nama Anda...",
        "educational_stage_label" to "Jenjang Pendidikan",
        "stage_primary" to "Sekolah Dasar (SD)",
        "stage_middle" to "Sekolah Menengah Pertama (SMP)",
        "stage_high_school" to "Sekolah Menengah Atas (SMA)",
        "stage_university" to "Universitas / Perguruan Tinggi",
        "btn_login" to "Masuk Sekarang 🔐",
        "btn_continue_guest" to "Lanjutkan sebagai Tamu 👤",
        "terms_notice" to "Dengan melanjutkan, Anda menyetujui Ketentuan Penggunaan dan Kebijakan Privasi.",
        "nav_home" to "Beranda",
        "nav_dictionary" to "Kamus",
        "nav_schedule" to "Jadwal",
        "nav_timer" to "Pengatur Waktu",
        "nav_docs" to "PDF & Dokumen",
        "nav_whiteboard" to "Papan Tulis",
        "nav_settings" to "Pengaturan"
    )

    private val ruStrings = mapOf(
        "app_title" to "Спутник Студента",
        "step_1_of_3" to "Шаг 1 из 3: Выбор языка",
        "step_2_of_3" to "Шаг 2 из 3: Определение местоположения",
        "step_3_of_3" to "Шаг 3 из 3: Профиль студента и вход",
        "step_language_title" to "Выберите предпочтительный язык",
        "step_language_subtitle" to "Полная поддержка языков для удобного и эффективного обучения",
        "step_location_title" to "Точное расписание и Кибла",
        "step_location_subtitle" to "Автоматическое определение локации для точного времени молитв и занятий",
        "step_account_title" to "Вход и профиль студента",
        "step_account_subtitle" to "Сохраняйте учебный прогресс и соревнуйтесь в рейтинге студентов",
        "btn_next" to "Следующий шаг ➡️",
        "btn_back" to "⬅️ Назад",
        "btn_apply_next" to "Применить и далее ➡️",
        "btn_finish_setup" to "Завершить и начать 🚀",
        "btn_skip" to "Пропустить",
        "btn_detect_gps" to "Определить по GPS 📍",
        "btn_detecting_gps" to "Определение геопозиции...",
        "btn_change_city_manually" to "Неверно? Выбрать город вручную 🗺️",
        "current_location_label" to "Текущая локация:",
        "student_name_label" to "Имя студента",
        "student_name_hint" to "Введите ваше имя...",
        "educational_stage_label" to "Ступень образования",
        "stage_primary" to "Начальная школа",
        "stage_middle" to "Средняя школа",
        "stage_high_school" to "Старшая школа / Колледж",
        "stage_university" to "Университет / ВУЗ",
        "btn_login" to "Войти в аккаунт 🔐",
        "btn_continue_guest" to "Продолжить как гость 👤",
        "terms_notice" to "Продолжая, вы принимаете Условия использования и Политику конфиденциальности.",
        "nav_home" to "Главная",
        "nav_dictionary" to "Словарь",
        "nav_schedule" to "Расписание",
        "nav_timer" to "Таймер",
        "nav_docs" to "PDF и Документы",
        "nav_whiteboard" to "Доска",
        "nav_settings" to "Настройки"
    )
}
