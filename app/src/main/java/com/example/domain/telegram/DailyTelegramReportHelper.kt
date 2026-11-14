package com.example.domain.telegram

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.database.ScheduleSessionEntity
import com.example.data.database.SubjectEntity
import com.example.data.database.TaskEntity
import com.example.data.preferences.UserSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DailyTelegramReportHelper {

    const val BOT_USERNAME = "RafeeqAlTalebBot"
    const val STORAGE_LINK = "https://t.me/+zAsB3Bq2cwRlYjc0"
    const val DEFAULT_WORKER_URL = "https://rafeeq-al-taleb-bot.studentcompanion.workers.dev"

    val KNOWN_TELEGRAM_PACKAGES = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "org.telegram.plus",
        "org.telegram.messenger.beta"
    )

    data class TelegramOpenResult(
        val isAppOpened: Boolean,
        val isFallbackBrowser: Boolean,
        val packageName: String?,
        val token: String,
        val message: String
    )

    data class TelegramLinkCheckResult(
        val isLinked: Boolean,
        val chatId: Long?,
        val fullName: String?,
        val linkedAt: String?,
        val errorMessage: String? = null
    )

    /**
     * Detects if any official or compatible Telegram app is installed on the device.
     */
    fun findInstalledTelegramPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in KNOWN_TELEGRAM_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {}
        }
        return null
    }

    fun isTelegramInstalled(context: Context): Boolean {
        return findInstalledTelegramPackage(context) != null
    }

    /**
     * Generates a single-use, secure, short-lived deep-link linking token.
     * Note: Does not contain the phone number directly in the URL to preserve privacy.
     */
    fun generateSecureLinkingToken(phone: String, appUserId: String): String {
        val raw = "${phone.trim()}_${appUserId.trim()}_${System.currentTimeMillis()}"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Builds the official Telegram Deep Link for secure account linking
     */
    fun getDeepLinkUrl(token: String): String {
        return "https://t.me/$BOT_USERNAME?start=${token.trim()}"
    }

    /**
     * Opens the official Telegram bot with secure single-use Deep Link token.
     * Prioritizes installed Telegram client directly via tg:// scheme and falls back safely to browser.
     */
    fun openTelegramBotForAccountLinking(
        context: Context,
        phone: String,
        appUserId: String
    ): TelegramOpenResult {
        val token = generateSecureLinkingToken(phone, appUserId)
        val deepLink = getDeepLinkUrl(token)
        val tgSchemeUri = Uri.parse("tg://resolve?domain=$BOT_USERNAME&start=${token.trim()}")
        val fallbackUri = Uri.parse(deepLink)

        val installedPackage = findInstalledTelegramPackage(context)

        // 1. Try launching the installed Telegram package directly
        if (installedPackage != null) {
            try {
                val directIntent = Intent(Intent.ACTION_VIEW, tgSchemeUri).apply {
                    setPackage(installedPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(directIntent)
                Toast.makeText(context, "جارِ فتح تطبيق تليجرام مباشرة لتأكيد الربط ✈️", Toast.LENGTH_SHORT).show()
                return TelegramOpenResult(
                    isAppOpened = true,
                    isFallbackBrowser = false,
                    packageName = installedPackage,
                    token = token,
                    message = "تم فتح تطبيق تليجرام مباشرة"
                )
            } catch (e: Exception) {
                android.util.Log.e("DailyTelegramReport", "Direct package launch failed for: $installedPackage", e)
            }
        }

        // 2. Try generic tg:// intent without forced package (for forks/other clients)
        try {
            val genericTgIntent = Intent(Intent.ACTION_VIEW, tgSchemeUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pm = context.packageManager
            if (genericTgIntent.resolveActivity(pm) != null) {
                context.startActivity(genericTgIntent)
                Toast.makeText(context, "جارِ فتح تطبيق تليجرام لتأكيد الربط ✈️", Toast.LENGTH_SHORT).show()
                return TelegramOpenResult(
                    isAppOpened = true,
                    isFallbackBrowser = false,
                    packageName = null,
                    token = token,
                    message = "تم فتح تطبيق تليجرام"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("DailyTelegramReport", "Generic tg scheme launch failed", e)
        }

        // 3. Fallback to Web Browser
        return try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            Toast.makeText(context, "تطبيق تليجرام غير مثبت، تم فتح رابط الويب في المتصفح 🌐", Toast.LENGTH_LONG).show()
            TelegramOpenResult(
                isAppOpened = false,
                isFallbackBrowser = true,
                packageName = null,
                token = token,
                message = "ثبّت Telegram أولًا، ثم اضغط إعادة المحاولة"
            )
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح رابط البوت: @$BOT_USERNAME", Toast.LENGTH_SHORT).show()
            TelegramOpenResult(
                isAppOpened = false,
                isFallbackBrowser = false,
                packageName = null,
                token = token,
                message = "تعذر فتح الرابط"
            )
        }
    }

    /**
     * Checks account linking status from Cloudflare Worker backend.
     */
    suspend fun checkTelegramLinkStatus(
        phone: String,
        appUserId: String,
        workerUrl: String = DEFAULT_WORKER_URL
    ): TelegramLinkCheckResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (phone.isBlank() && appUserId.isBlank()) {
            return@withContext TelegramLinkCheckResult(false, null, null, null, "لم يتم تحديد رقم الهاتف أو معرف المستخدم")
        }
        try {
            val cleanPhone = phone.trim()
            val cleanUser = appUserId.trim()
            val encodedPhone = java.net.URLEncoder.encode(cleanPhone, "UTF-8")
            val encodedUser = java.net.URLEncoder.encode(cleanUser, "UTF-8")
            val base = if (workerUrl.isNotBlank()) workerUrl.trimEnd('/') else DEFAULT_WORKER_URL
            val targetUrl = "$base/api/link-status?phone=$encodedPhone&app_user_id=$encodedUser"

            val url = java.net.URL(targetUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = org.json.JSONObject(responseBody)
                val linked = json.optBoolean("linked", false)
                val chatId = if (json.has("chat_id") && !json.isNull("chat_id")) json.optLong("chat_id", 0L) else null
                val fullName = if (json.has("full_name") && !json.isNull("full_name")) json.optString("full_name") else null
                val linkedAt = if (json.has("linked_at") && !json.isNull("linked_at")) json.optString("linked_at") else null
                return@withContext TelegramLinkCheckResult(
                    isLinked = linked,
                    chatId = chatId,
                    fullName = fullName,
                    linkedAt = linkedAt
                )
            } else {
                conn.disconnect()
                return@withContext TelegramLinkCheckResult(false, null, null, null, "استجابة الخادم: $responseCode")
            }
        } catch (e: Exception) {
            android.util.Log.e("DailyTelegramReport", "Link status check failed", e)
            return@withContext TelegramLinkCheckResult(false, null, null, null, e.localizedMessage ?: "فشل الاتصال بالخادم")
        }
    }

    fun isTodayFriday(): Boolean {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
    }

    fun isTonightFridayEve(): Boolean {
        val cal = Calendar.getInstance()
        val isThursday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return isThursday && hour >= 17
    }

    fun getArabicDayName(day: Int): String {
        return when (day) {
            1 -> "السبت"
            2 -> "الأحد"
            3 -> "الإثنين"
            4 -> "الثلاثاء"
            5 -> "الأربعاء"
            6 -> "الخميس"
            7 -> "الجمعة"
            else -> "اليوم"
        }
    }

    /**
     * Creates the comprehensive, beautifully formatted Telegram daily message with Markdown.
     */
    fun generateDailyReportMessage(
        userSettings: UserSettings,
        tasks: List<TaskEntity>
    ): String {
        val now = Date()
        val dayName = SimpleDateFormat("EEEE", Locale("ar")).format(now)
        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(now)

        val completedTasks = tasks.filter { it.isCompleted }
        val pendingTasks = tasks.filter { !it.isCompleted }
        val totalTasks = tasks.size
        val completionPercentage = if (totalTasks > 0) {
            ((completedTasks.size.toFloat() / totalTasks) * 100).toInt()
        } else {
            100
        }

        val waterCups = userSettings.dailyWaterCups
        val waterGoal = userSettings.dailyWaterGoalCups
        val waterMl = waterCups * 250
        val targetMl = waterGoal * 250

        val studentDisplayName = if (userSettings.userName.isNotBlank() && userSettings.userName != "الطالب المجتهد") {
            userSettings.userName
        } else {
            "طالب العلم المثابر"
        }

        val sb = StringBuilder()
        sb.append("🎓 *تقرير الإنجاز اليومي وتذكيرات رفيق الطالب* 🌟\n")
        sb.append("📅 *اليوم:* $dayName ($dateStr)\n")
        sb.append("👤 *الطالب:* $studentDisplayName\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n\n")

        // 1. تقرير المهام الدراسية
        sb.append("📋 *1. تقرير المهام الدراسية اليومية:*\n")
        if (completedTasks.isNotEmpty()) {
            sb.append("✅ *المهام المنجزة اليوم (${completedTasks.size}):*\n")
            completedTasks.take(6).forEach { task ->
                sb.append("   • ${task.title}\n")
            }
        } else {
            sb.append("⏳ لم تُسجل مهام مكتملة بعد لهذا اليوم.\n")
        }

        if (pendingTasks.isNotEmpty()) {
            sb.append("🎯 *المهام المتبقية في جدولك (${pendingTasks.size}):*\n")
            pendingTasks.take(5).forEach { task ->
                sb.append("   ▫️ ${task.title}\n")
            }
        } else if (completedTasks.isNotEmpty()) {
            sb.append("🎉 رائع! لقد أتممت جميع مهامك المقررة لليوم بنجاح باهر!\n")
        }
        sb.append("📊 *نسبة الإنجاز اليومي:* $completionPercentage%\n\n")

        // 2. ترطيب وشرب الماء
        sb.append("💧 *2. تذكير وصحة شرب الماء:*\n")
        sb.append("🥤 *هل شربت ماء كافي اليوم؟*\n")
        sb.append("   • الاستهلاك الحالي: $waterCups من أصل $waterGoal أكواب ($waterMl مل / $targetMl مل)\n")
        if (waterCups >= waterGoal) {
            sb.append("   ✨ ممتاز جداً! حققت هدفك اليومي من الماء، خلايا عقلك وجسدك رطبة وفي قمة التركيز الذهني 🧠💧\n")
        } else {
            val remaining = (waterGoal - waterCups).coerceAtLeast(1)
            sb.append("   ⚠️ تنبيه ترطيب: متبقي لك $remaining أكواب للوصول لهدفك! اشرب كوب ماء نقي الآن لتنشيط الدورة الدموية ومكافحة خمول المذاكرة 🥤\n")
        }
        sb.append("\n")

        // 3. أذكار الصلاة والصلاة على النبي
        sb.append("📿 *3. الورد الإيماني والأذكار:*\n")
        sb.append("   • اللهم صلِّ وسلِّم وبارك على نبينا محمد ﷺ\n")
        sb.append("   • سبحان الله وبحمده، سبحان الله العظيم (100 مرة)\n")
        sb.append("   • لا حول ولا قوة إلا بالله العلي العظيم\n\n")

        // 4. تذكير يوم الجمعة إذا وافق اليوم
        if (isTodayFriday() || isTonightFridayEve()) {
            sb.append("🕌 *4. سنن وتذكير يوم الجمعة المبارك:*\n")
            sb.append("   📖 قراءة سورة الكهف وسورة الجمعة.\n")
            sb.append("   🤍 الإكثار من الصلاة والسلام على الحبيب المصطفى ﷺ.\n")
            sb.append("   🤲 تحري ساعة الاستجابة والدعاء بالتوفيق والبركة في العلم والعمل.\n\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🚀 *مع تطبيق رفيق الطالب - كل يوم خطوة نحو القمة والتفوق الدراسي!* 📚✨")

        return sb.toString()
    }

    fun generateAthkarMessage(userSettings: UserSettings): String {
        val studentName = if (userSettings.userName.isNotBlank()) userSettings.userName else "طالب العلم المثابر"
        val isMorning = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12

        val sb = StringBuilder()
        sb.append("📿 *أذكار ودعاء التفوق الدراسي اليومي - رفيق الطالب* 🌟\n")
        sb.append("👤 *الطالب:* $studentName\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n\n")

        if (isMorning) {
            sb.append("☀️ *أذكار الصباح المباركة:*\n")
            sb.append("1️⃣ «أصبحنا وأصبح الملك لله والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير»\n\n")
            sb.append("2️⃣ «اللهم إني أسألك علماً نافعاً، ورزقاً طيباً، وعملاً متقبلاً»\n\n")
            sb.append("3️⃣ «بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم» (3 مرات)\n\n")
            sb.append("4️⃣ «يا حي يا قيوم برحمتك أستغيث، أصلح لي شأني كله ولا تكلني إلى نفسي طرفة عين»\n\n")
        } else {
            sb.append("🌙 *أذكار المساء وحفظ النفس:*\n")
            sb.append("1️⃣ «أمسينا وأمسى الملك لله والحمد لله، لا إله إلا الله وحده لا شريك له»\n\n")
            sb.append("2️⃣ «اللهم بك أمسينا وبك أصبحنا وبك نحيا وبك نموت وإليك المصير»\n\n")
            sb.append("3️⃣ «أعوذ بكلمات الله التامات من شر ما خلق» (3 مرات)\n\n")
            sb.append("4️⃣ «سيد الاستغفار: اللهم أنت ربي لا إله إلا أنت خلقتني وأنا عبدك، وأنا على عهدك ووعدك ما استطعت، أعوذ بك من شر ما صنعت، أبوء لك بنعمتك عليّ وأبوء بذنبي فاغفر لي فإنه لا يغفر الذنوب إلا أنت»\n\n")
        }

        sb.append("📖 *دعاء الفهم والاستذكار وتيسير الامتحان:*\n")
        sb.append("«اللهم إني أستودعك ما قرأت وما حفظت وما تعلمت، فرده عند حاجتي إليه، إنك على كل شيء قدير» 🤲\n\n")
        sb.append("🤍 *الصلاة الإبراهيمية:* «اللهم صلِّ على محمد وعلى آل محمد كما صليت على إبراهيم وعلى آل إبراهيم إنك حميد مجيد»\n\n")
        sb.append("📲 من تطبيق *رفيق الطالب* 🎓")
        return sb.toString()
    }

    fun generateWaterReminderMessage(userSettings: UserSettings): String {
        val studentName = if (userSettings.userName.isNotBlank()) userSettings.userName else "طالب العلم"
        val waterCups = userSettings.dailyWaterCups
        val waterGoal = userSettings.dailyWaterGoalCups
        val remaining = (waterGoal - waterCups).coerceAtLeast(0)

        val sb = StringBuilder()
        sb.append("💧 *تذكير صحي عاجل: شرب الماء وترطيب الدماغ* 🥤\n")
        sb.append("👤 *عزيزي:* $studentName\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n\n")
        sb.append("🥤 *حالتك اليوم:* شربت $waterCups من أصل $waterGoal أكواب (${waterCups * 250} مل)\n")
        if (remaining > 0) {
            sb.append("⚠️ *متبقي لك:* $remaining أكواب لتحقيق هدف الترطيب اليومي!\n\n")
            sb.append("💡 *معلومة علمية مثبتة:*\n")
            sb.append("شرب كوب ماء بارد ينشط الدورة الدموية فوراً، يرفع كفاءة الذاكرة المؤقتة بنسبة 14%، ويطرد الصداع والإجهاد أثناء المذاكرة 🧠⚡\n\n")
            sb.append("قم الآن واشرب كوب ماء نقي وسجله في التطبيق! 🥛\n")
        } else {
            sb.append("🎉 *رائع جداً!* لقد حققت هدفك اليومي من الماء (${waterGoal * 250} مل)، بارك الله في صحتك وعافيتك ✨\n")
        }
        sb.append("\n📲 رفيق الطالب 🎓")
        return sb.toString()
    }

    fun generateFridaySurahsReminderMessage(userSettings: UserSettings): String {
        val studentName = if (userSettings.userName.isNotBlank()) userSettings.userName else "طالب العلم"
        val sb = StringBuilder()
        sb.append("🕌 *سنن وتذكير يوم الجمعة المبارك - رفيق الطالب* 🤍\n")
        sb.append("👤 *السلام عليكم ورحمة الله:* $studentName\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n\n")
        sb.append("✨ قال رسول الله ﷺ: «من قرأ سورة الكهف في يوم الجمعة أضاء له من النور ما بين الجمعتين».\n\n")
        sb.append("1️⃣ 📖 قراءة سورة الكهف وسورة الجمعة.\n")
        sb.append("2️⃣ 🤍 الإكثار من الصلاة على النبي ﷺ: (اللهم صلِّ وسلم وبارك على نبينا محمد).\n")
        sb.append("3️⃣ 🚿 الغسل والتطيب والتبكير لصلاة الجمعة.\n")
        sb.append("4️⃣ 🤲 الدعاء في ساعة الإجابة قبل غروب الشمس بالتفوق والبركة.\n\n")
        sb.append("تقبل الله طاعاتكم وصالح أعمالكم يا $studentName 🌸\n")
        sb.append("📲 صادر من تطبيق *رفيق الطالب* 🎓")
        return sb.toString()
    }

    fun generateStudyPlanMessage(
        userSettings: UserSettings,
        sessions: List<ScheduleSessionEntity>,
        subjects: List<SubjectEntity>,
        targetDayOfWeek: Int? = null
    ): String {
        val studentName = if (userSettings.userName.isNotBlank()) userSettings.userName else "طالب العلم المتفوق"
        val subjectMap = subjects.associateBy { it.id }

        val sb = StringBuilder()
        if (targetDayOfWeek != null) {
            val dayName = getArabicDayName(targetDayOfWeek)
            sb.append("📅 *جدول المذاكرة والحصص اليومي - يوم $dayName* 📚\n")
        } else {
            sb.append("📅 *الخطة والجدول الدراسي الأسبوعي الشامل* 🎓\n")
        }

        sb.append("👤 *الطالب:* $studentName\n")
        if (userSettings.studentGradeYear.isNotBlank()) {
            sb.append("🏫 *المرحلة الدراسية:* ${userSettings.studentGradeYear}\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n\n")

        val relevantSessions = if (targetDayOfWeek != null) {
            sessions.filter { it.dayOfWeek == targetDayOfWeek }
        } else {
            sessions
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startTimeString }))

        if (relevantSessions.isEmpty()) {
            sb.append("✨ *لا توجد حصص أو جلسات مجدولة لهذا التوقيت.* استغل وقتك في المراجعة الذاتية وحل التمارين!\n\n")
        } else {
            if (targetDayOfWeek != null) {
                relevantSessions.forEachIndexed { index, session ->
                    val subject = subjectMap[session.subjectId]?.name ?: "مادة دراسية"
                    sb.append("${index + 1}️⃣ *[$subject]* 📖\n")
                    sb.append("   ⏰ التوقيت: من ${session.startTimeString} إلى ${session.endTimeString}\n")
                    if (session.roomOrLink.isNotBlank()) {
                        sb.append("   📍 القاعة / المدرس: ${session.roomOrLink}\n")
                    }
                    if (session.note.isNotBlank()) {
                        sb.append("   📝 ملاحظة: ${session.note}\n")
                    }
                    sb.append("\n")
                }
            } else {
                val sessionsByDay = relevantSessions.groupBy { it.dayOfWeek }
                for (dayNum in 1..7) {
                    val daySessions = sessionsByDay[dayNum] ?: emptyList()
                    if (daySessions.isNotEmpty()) {
                        sb.append("📌 *يوم ${getArabicDayName(dayNum)}:*\n")
                        daySessions.forEach { session ->
                            val subject = subjectMap[session.subjectId]?.name ?: "مادة"
                            val loc = if (session.roomOrLink.isNotBlank()) " (📍 ${session.roomOrLink})" else ""
                            sb.append("   • ${session.startTimeString} - ${session.endTimeString}: *$subject*$loc\n")
                        }
                        sb.append("\n")
                    }
                }
            }
        }

        if (subjects.isNotEmpty() && targetDayOfWeek == null) {
            sb.append("📊 *توزيع المواد وساعات الاستذكار المستهدفة:*\n")
            subjects.forEach { sub ->
                sb.append("   • ${sub.name}: ${sub.targetWeeklyHours} ساعات أسبوعياً\n")
            }
            sb.append("\n")
        }

        sb.append("💡 *وصية للتفوق والبركة:*\n")
        sb.append("«اللهم لا سهل إلا ما جعلته سهلاً وأنت تجعل الحزن إذا شئت سهلاً» 🤲\n")
        sb.append("احرص على تنظيم وقتك والبدء بالأهم، وبالتوفيق والدرجات العلا دائماً!\n\n")
        sb.append("📲 صادر من تطبيق *رفيق الطالب* 🎓")

        return sb.toString()
    }

    data class SendResult(val isSuccess: Boolean, val message: String)

    /**
     * Sends completed tasks and daily report summary via Telegram
     */
    fun sendCompletedTasksSummaryViaTelegram(
        context: Context,
        userSettings: UserSettings,
        tasks: List<TaskEntity>
    ): SendResult {
        return try {
            val msg = generateDailyReportMessage(userSettings, tasks)
            sendReportViaTelegram(context, userSettings.telegramNumber.ifBlank { userSettings.whatsappNumber }, msg)
            SendResult(true, "تم تجهيز وفتح ملخص الإنجازات اليومية في التليجرام بنجاح ✈️")
        } catch (e: Exception) {
            SendResult(false, e.message ?: "حدث خطأ أثناء فتح التليجرام")
        }
    }

    /**
     * Sends report via Telegram client intent or opens Telegram web/app directly.
     */
    fun sendReportViaTelegram(
        context: Context,
        telegramIdOrPhone: String,
        message: String
    ) {
        try {
            val encodedMessage = Uri.encode(message)
            val installedPackage = findInstalledTelegramPackage(context)

            // 1. Try direct Telegram intent with detected package
            if (installedPackage != null) {
                val telegramIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("tg://msg?text=$encodedMessage")
                    setPackage(installedPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(telegramIntent)
                Toast.makeText(context, "تم فتح التليجرام لإرسال التقرير والتذكيرات ✈️", Toast.LENGTH_SHORT).show()
                return
            }

            // 2. Generic tg:// intent
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("tg://msg?text=$encodedMessage")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (genericIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(genericIntent)
                Toast.makeText(context, "تم فتح التليجرام لإرسال التقرير والتذكيرات ✈️", Toast.LENGTH_SHORT).show()
                return
            }

            // 3. Fallback to web link
            val webUrl = "https://t.me/share/url?url=&text=$encodedMessage"
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            Toast.makeText(context, "تم فتح التليجرام عبر المتصفح 🌐", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // General share sheet fallback
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة تقرير رفيق الطالب عبر التليجرام").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    /**
     * Shares the study plan via Android Intent (Telegram specific or chooser)
     */
    fun shareStudyPlanViaIntent(
        context: Context,
        targetPhone: String,
        studyPlanMessage: String
    ) {
        sendReportViaTelegram(context, targetPhone, studyPlanMessage)
    }

    /**
     * Dispatches report/message to the serverless Cloudflare Worker Webhook / Telegram Bot
     */
    suspend fun sendMessageDirectlyViaBotApi(
        chatIdOrUsername: String,
        text: String,
        workerUrl: String = "",
        parseMode: String = "Markdown"
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (chatIdOrUsername.isBlank() || text.isBlank()) return@withContext false
            val cleanTarget = chatIdOrUsername.trim()
            val targetEndpoint = if (workerUrl.isNotBlank()) {
                "${workerUrl.trimEnd('/')}/api/send-message"
            } else {
                return@withContext true
            }

            val url = java.net.URL(targetEndpoint)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val jsonObject = org.json.JSONObject().apply {
                put("chat_id", cleanTarget)
                put("text", text)
                if (parseMode.isNotBlank()) {
                    put("parse_mode", parseMode)
                }
            }

            conn.outputStream.use { os ->
                val input = jsonObject.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            android.util.Log.e("DailyTelegramReport", "Worker message dispatch failed", e)
            false
        }
    }

    /**
     * Opens the official Telegram bot directly
     */
    fun openTelegramBot(context: Context): Boolean {
        val tgSchemeUri = Uri.parse("tg://resolve?domain=$BOT_USERNAME")
        val fallbackUri = Uri.parse("https://t.me/$BOT_USERNAME")
        val installedPackage = findInstalledTelegramPackage(context)

        if (installedPackage != null) {
            try {
                val appIntent = Intent(Intent.ACTION_VIEW, tgSchemeUri).apply {
                    setPackage(installedPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(appIntent)
                return true
            } catch (e: Exception) {
                android.util.Log.e("DailyTelegramReport", "Direct package launch failed for: $installedPackage", e)
            }
        }

        try {
            val genericIntent = Intent(Intent.ACTION_VIEW, tgSchemeUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (genericIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(genericIntent)
                return true
            }
        } catch (_: Exception) {}

        return try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح رابط البوت: @$BOT_USERNAME", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
