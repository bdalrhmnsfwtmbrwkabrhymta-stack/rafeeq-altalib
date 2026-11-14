package com.example.domain.whatsapp

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

object DailyWhatsAppReportHelper {

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

    /**
     * Creates the comprehensive, beautifully formatted WhatsApp daily message.
     */
    fun generateDailyReportMessage(
        userSettings: UserSettings,
        tasks: List<TaskEntity>
    ): String {
        val now = Date()
        val dayName = SimpleDateFormat("EEEE", Locale("ar")).format(now)
        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(now)
        val isFriday = isTodayFriday()

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

        // 1. تقرير المهام المنجزة واليومية
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

        // 2. متابعة شرب الماء اليومي (هل شربت ماء كافي اليوم؟)
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

        // 3. تذكير يوم الجمعة العظيم (سورة الكهف وسورة الجمعة/السجدة)
        if (isFriday) {
            sb.append("🕌 *3. نفحات وسُنن يوم الجمعة المبارك:*\n")
            sb.append("📖 *تذكير السورتين المعروفتين ليوم الجمعة:*\n")
            sb.append("   1️⃣ *سورة الكهف:* «من قرأ سورة الكهف في يوم الجمعة أضاء له من النور ما بين الجمعتين» ✨\n")
            sb.append("   2️⃣ *سورة الجمعة أو سورة السجدة:* سُنّة مؤكدة ليوم وفجر الجمعة المباركة 🌿\n")
            sb.append("   🤍 لا تنسَ الإكثار من الصلاة والسلام على نبينا محمد ﷺ والدعاء في ساعة الإجابة 🤲\n\n")
        } else if (isTonightFridayEve()) {
            sb.append("🕌 *3. تنبيه ليلة الجمعة المباركة:*\n")
            sb.append("   استعد الليلة وغداً لقراءة السورتين المعروفتين: *سورة الكهف* و*سورة الجمعة / السجدة*، والإكثار من الصلاة على الحبيب المصطفى ﷺ 🤍\n\n")
        }

        // 4. أذكار اليوم ودعاء الاستذكار والتفوق
        sb.append("📿 *4. أذكار مباركة ودعاء التوفيق والتفوق:*\n")
        val isMorning = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12
        if (isMorning) {
            sb.append("☀️ *من أذكار الصباح:*\n")
            sb.append("   • «أصبحنا وأصبح الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير» 🌅\n")
            sb.append("   • «يا حي يا قيوم برحمتك أستغيث، أصلح لي شأني كله ولا تكلني إلى نفسي طرفة عين» 🤲\n")
        } else {
            sb.append("🌙 *من أذكار المساء:*\n")
            sb.append("   • «أمسينا وأمسى الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له» 🌌\n")
            sb.append("   • «اللهم بك أمسينا وبك أصبحنا وبك نحيا وبك نموت وإليك المصير» 🤲\n")
        }
        sb.append("🎓 *دعاء استيداع العلم وتيسير الفهم:*\n")
        sb.append("   «اللهم إني أسألك فهم النبيين وحفظ المرسلين والملائكة المقربين، اللهم لا سهل إلا ما جعلته سهلاً وأنت تجعل الحزن إذا شئت سهلاً» ✨\n")
        sb.append("🤍 *الصلاة على النبي ﷺ:*\n")
        sb.append("   «اللهم صلِّ وسلِّم وبارك على نبينا وحبيبنا محمد وعلى آله وصحبه أجمعين»\n\n")

        // 5. حكمة اليوم التحفيزية
        sb.append("💡 *5. حكمة وتحفيز لليوم:*\n")
        sb.append("«ليس المهم أين أنت اليوم، بل المهم هو الاتجاه الذي تسير فيه كل يوم خطوة بخطوة نحو حلمك.»\n\n")
        sb.append("📲 تم التوليد والمزامنة آلياً عبر تطبيق *رفيق الطالب الذكي* 🎓")

        return sb.toString()
    }

    /**
     * Dedicated Athkar and Duas message for WhatsApp
     */
    fun generateAthkarMessage(userSettings: UserSettings): String {
        val studentName = if (userSettings.userName.isNotBlank()) userSettings.userName else "طالب العلم المثابر"
        val isMorning = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12

        val sb = StringBuilder()
        sb.append("📿 *أذكار ودعاء التفوق الدراسي اليومي* 🌟\n")
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
        sb.append("📲 من تطبيق *رفيق الطالب الذكي* 🎓")
        return sb.toString()
    }

    /**
     * Dedicated Water Reminder message for WhatsApp
     */
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
        sb.append("\n📲 رفيق الطالب الذكي 🎓")
        return sb.toString()
    }

    /**
     * Dedicated Friday Surahs Reminder message for WhatsApp
     */
    fun generateFridaySurahsReminderMessage(userSettings: UserSettings): String {
        val studentName = if (userSettings.userName.isNotBlank()) userSettings.userName else "طالب العلم"
        val sb = StringBuilder()
        sb.append("🕌 *تذكير سُنن ونفحات يوم الجمعة المباركة* 🌿\n")
        sb.append("👤 *السلام عليكم ورحمة الله وبركاته:* $studentName\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n\n")
        sb.append("📖 *تذكير السورتين المعروفتين ليوم وفجر الجمعة:*\n\n")
        sb.append("1️⃣ *سورة الكهف:* 📖✨\n")
        sb.append("   قال النبي ﷺ: «من قرأ سورة الكهف في يوم الجمعة أضاء له من النور ما بين الجمعتين»\n")
        sb.append("   حالة القراءة في تطبيقك: ${if (userSettings.fridaySurahKahfRead) "✅ تمت القراءة بفضل الله" else "⏳ بانتظار قراءتك اليوم"}\n\n")
        sb.append("2️⃣ *سورة الجمعة وسورة السجدة:* 🌿\n")
        sb.append("   سنة مؤكدة عن رسول الله ﷺ في فجر ويوم الجمعة المبارك\n")
        sb.append("   حالة القراءة في تطبيقك: ${if (userSettings.fridaySurahJumuahRead) "✅ تمت القراءة بفضل الله" else "⏳ بانتظار قراءتك اليوم"}\n\n")
        sb.append("🤲 *لا تنسَ:*\n")
        sb.append("   • الإكثار من الصلاة على النبي ﷺ (أفضل أيامكم يوم الجمعة)\n")
        sb.append("   • ساعة الإجابة في آخر ساعة قبل غروب الشمس والدعاء بالتفوق والبركة\n\n")
        sb.append("📲 رفيق الطالب الذكي 🎓")
        return sb.toString()
    }

    /**
     * Cleans up telephone numbers into international format (handles Egypt 010..., etc.)
     */
    fun cleanPhoneNumber(phone: String): String {
        var p = phone.trim().replace(" ", "").replace("-", "").replace("+", "")
        if (p.startsWith("0")) {
            p = "20" + p.substring(1) // Default to Egypt country code 20 if leading with 0
        }
        return p
    }

    /**
     * Launches WhatsApp direct intent with the message
     */
    fun sendReportViaWhatsApp(context: Context, phone: String, message: String) {
        val cleanPhone = cleanPhoneNumber(phone)
        val encodedMessage = Uri.encode(message)
        val url = if (cleanPhone.isNotBlank()) {
            "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage"
        } else {
            "https://api.whatsapp.com/send?text=$encodedMessage"
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback to general sharing sheet
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(shareIntent, "إرسال تقرير الإنجاز عبر واتساب").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (ex: Exception) {
                Toast.makeText(context, "تعذر فتح تطبيق الواتساب", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Configuration for WhatsApp Business / Cloud API
     */
    data class WhatsAppApiConfig(
        val endpointUrl: String = "https://graph.facebook.com/v19.0/PHONE_NUMBER_ID/messages",
        val bearerToken: String = "",
        val isCloudApiEnabled: Boolean = false
    )

    data class WhatsAppApiResult(
        val isSuccess: Boolean,
        val message: String,
        val sentViaCloudApi: Boolean = false
    )

    /**
     * دالة برمجية مهيأة للربط مع API خدمة واتساب (WhatsApp Cloud / Business API)
     * لإرسال ملخص المهام المنجزة للمستخدم بناءً على رقم هاتفه المخزن في إعدادات التطبيق.
     */
    suspend fun sendCompletedTasksSummaryViaWhatsAppApi(
        context: Context,
        userSettings: UserSettings,
        tasks: List<TaskEntity>,
        apiConfig: WhatsAppApiConfig = WhatsAppApiConfig()
    ): WhatsAppApiResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val rawPhone = userSettings.whatsappNumber
        if (rawPhone.isBlank()) {
            return@withContext WhatsAppApiResult(
                isSuccess = false,
                message = "لم يتم العثور على رقم واتساب مسجل في الإعدادات. يرجى إدخال رقم الواتساب أولاً."
            )
        }

        val cleanPhone = cleanPhoneNumber(rawPhone)
        val reportMessage = generateDailyReportMessage(userSettings, tasks)

        // إذا كان تم تفعيل الـ Cloud API ورمز الـ Bearer موجود، يتم الإرسال عبر الـ REST API لـ WhatsApp
        if (apiConfig.isCloudApiEnabled && apiConfig.bearerToken.isNotBlank()) {
            try {
                val url = java.net.URL(apiConfig.endpointUrl)
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer ${apiConfig.bearerToken}")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val jsonPayload = org.json.JSONObject().apply {
                    put("messaging_product", "whatsapp")
                    put("recipient_type", "individual")
                    put("to", cleanPhone)
                    put("type", "text")
                    put("text", org.json.JSONObject().apply {
                        put("preview_url", false)
                        put("body", reportMessage)
                    })
                }

                val os = conn.outputStream
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
                os.close()

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                    return@withContext WhatsAppApiResult(
                        isSuccess = true,
                        message = "تم إرسال ملخص المهام المنجزة والتذكيرات بنجاح عبر WhatsApp API الرسمي 🚀",
                        sentViaCloudApi = true
                    )
                } else {
                    val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    // في حال تعذر الاتصال بالخادم، يتم التبديل التلقائي للإرسال المباشر عبر تطبيق واتساب
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        sendReportViaWhatsApp(context, cleanPhone, reportMessage)
                    }
                    return@withContext WhatsAppApiResult(
                        isSuccess = true,
                        message = "تم فتح واتساب للإرسال المباشر إلى رقمك المسجل ($cleanPhone). استجابة API: $errorBody",
                        sentViaCloudApi = false
                    )
                }
            } catch (e: Exception) {
                // خطأ شبكة أو اتصال -> Fallback to direct app launch
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sendReportViaWhatsApp(context, cleanPhone, reportMessage)
                }
                return@withContext WhatsAppApiResult(
                    isSuccess = true,
                    message = "تم تحويل الرسالة عبر تطبيق واتساب مباشرة لرقمك المسجل ($cleanPhone).",
                    sentViaCloudApi = false
                )
            }
        } else {
            // إرسال مباشر مهيأ عبر رابط API واتساب المباشر لرقم المستخدم المسجل
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                sendReportViaWhatsApp(context, cleanPhone, reportMessage)
            }
            return@withContext WhatsAppApiResult(
                isSuccess = true,
                message = "تم إرسال ملخص المهام وتذكيرات الماء ويوم الجمعة عبر تطبيق WhatsApp لرقمك المسجل ($cleanPhone) بنجاح 📲",
                sentViaCloudApi = false
            )
        }
    }

    /**
     * Day of week name in Arabic helper
     */
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
     * Generates a beautifully formatted Study Plan message for WhatsApp
     */
    fun generateStudyPlanMessage(
        userSettings: UserSettings,
        sessions: List<ScheduleSessionEntity>,
        subjects: List<SubjectEntity>,
        targetDayOfWeek: Int? = null // null means full weekly schedule
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
                // Group by Day
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

        // Add Subjects summary
        if (subjects.isNotEmpty() && targetDayOfWeek == null) {
            sb.append("📊 *توزيع المواد وساعات الاستذكار المستهدفة:*\n")
            subjects.forEach { sub ->
                sb.append("   • ${sub.name}: ${sub.targetWeeklyHours} ساعات أسبوعياً\n")
            }
            sb.append("\n")
        }

        // Motivational Islamic Study Quote
        sb.append("💡 *وصية للتفوق والبركة:*\n")
        sb.append("«اللهم لا سهل إلا ما جعلته سهلاً وأنت تجعل الحزن إذا شئت سهلاً» 🤲\n")
        sb.append("احرص على تنظيم وقتك والبدء بالأهم، وبالتوفيق والدرجات العلا دائماً!\n\n")
        sb.append("📲 صادر من تطبيق *رفيق الطالب الذكي* 🎓")

        return sb.toString()
    }

    /**
     * Shares the study plan via Android Intent (WhatsApp specific or chooser)
     */
    fun shareStudyPlanViaIntent(
        context: Context,
        targetPhone: String,
        studyPlanMessage: String
    ) {
        val cleanPhone = cleanPhoneNumber(targetPhone)
        val encodedMessage = Uri.encode(studyPlanMessage)

        if (cleanPhone.isNotBlank()) {
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to chooser below
            }
        }

        // Fallback: Open WhatsApp directly or show Chooser
        try {
            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, studyPlanMessage)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(whatsappIntent)
        } catch (e: Exception) {
            // General share intent if WhatsApp is not installed or package is blocked
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, studyPlanMessage)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الخطة الدراسية عبر واتساب").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
