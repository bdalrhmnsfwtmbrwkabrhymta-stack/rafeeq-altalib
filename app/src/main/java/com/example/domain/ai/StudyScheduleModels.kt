package com.example.domain.ai

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class SubjectDifficulty(val labelAr: String, val labelEn: String, val weight: Float) {
    HARD("صعبة / تركيز عالي", "Hard / High Focus", 1.5f),
    MEDIUM("متوسطة", "Medium", 1.0f),
    EASY("سهلة / مراجعة سريعة", "Easy / Quick Review", 0.7f)
}

enum class StudyPace(val labelAr: String, val labelEn: String) {
    BALANCED("متوازن ومستقر", "Balanced & Steady"),
    INTENSIVE("مكثف وسريع (ضغط امتحانات)", "Intensive (Exam Cramming)"),
    GRADUAL("تدريجي وتأسيسي", "Gradual & In-Depth")
}

enum class PreferredStudyPeriod(val labelAr: String, val labelEn: String) {
    MORNING("صباحي (بعد الفجر/الصباح الباكر)", "Morning (Early / Post-Fajr)"),
    AFTERNOON("ظهيرة وعصر", "Afternoon"),
    EVENING("مسائي (بعد المغرب والعشاء)", "Evening"),
    NIGHT("ليلي (ساعات الهدوء والتركيز)", "Late Night"),
    FLEXIBLE("مرن موزع على مدار اليوم", "Flexible Throughout Day")
}

data class StudentSubjectInput(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val topicsOrChapters: String = "",
    val difficulty: SubjectDifficulty = SubjectDifficulty.MEDIUM,
    val deadlineDays: Int = 7,
    val deadlineDateFormatted: String = "",
    val estimatedTotalHours: Int = 10,
    val colorHex: Long = 0xFF1E88E5
)

data class StudySchedulePreferences(
    val dailyAvailableHours: Float = 4.0f,
    val preferredPeriod: PreferredStudyPeriod = PreferredStudyPeriod.FLEXIBLE,
    val pace: StudyPace = StudyPace.BALANCED,
    val pomodoroMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val includePrayerBreaks: Boolean = true,
    val includeRevisionCheckpoints: Boolean = true,
    val studentNotes: String = ""
)

data class GeneratedScheduleSession(
    val id: String = UUID.randomUUID().toString(),
    val sessionNumber: Int,
    val timeSlot: String,
    val subjectName: String,
    val topicDescription: String,
    val durationMinutes: Int,
    val pomodorosCount: Int = 1,
    val isBreak: Boolean = false,
    val isMilestoneReview: Boolean = false,
    var isCompleted: Boolean = false
)

data class GeneratedDaySchedule(
    val dayNumber: Int,
    val dayTitle: String,
    val dateString: String = "",
    val focusGoal: String,
    val totalStudyHours: Float,
    val sessions: List<GeneratedScheduleSession>
)

data class SubjectAllocation(
    val subjectName: String,
    val totalHours: Float,
    val percentageOfTotal: Int,
    val difficulty: SubjectDifficulty,
    val deadlineText: String
)

data class PersonalizedStudyPlanResult(
    val planTitle: String,
    val strategySummary: String,
    val totalDays: Int,
    val totalEstimatedStudyHours: Float,
    val days: List<GeneratedDaySchedule>,
    val subjectAllocations: List<SubjectAllocation>,
    val highYieldTips: List<String>,
    val rawResponseText: String
)

// Models for Remaining Tasks Analysis & Daily Time-Blocked Schedule Suggestion
data class RemainingTaskAnalysisItem(
    val id: Long,
    val title: String,
    val description: String = "",
    val subjectName: String = "عام",
    val subjectId: Long? = null,
    val priority: String = "MEDIUM", // URGENT, HIGH, MEDIUM, LOW
    val dueDateMillis: Long = 0,
    val dueDateFormatted: String = "",
    val tag: String = "عام"
)

data class TimeBlockedStudyBlock(
    val id: String = UUID.randomUUID().toString(),
    val timeSlot: String,            // e.g. "04:30 م - 05:15 م"
    val startTime: String,           // "16:30"
    val endTime: String,             // "17:15"
    val taskTitle: String,           // e.g. "مذاكرة التفاضل وحل التمارين"
    val subjectName: String,         // e.g. "الرياضيات"
    val subjectId: Long? = null,
    val durationMinutes: Int = 45,
    val focusGoal: String,           // e.g. "إنهاء حل 15 مسألة على قاعدة السلسلة"
    val priority: String = "HIGH",   // URGENT, HIGH, MEDIUM, LOW
    val pomodorosCount: Int = 2,
    val recommendedTechnique: String = "Pomodoro 25/5 دقيقة",
    val breakMinutes: Int = 10,
    val isPrayerOrRestBreak: Boolean = false
)

data class DailyTaskScheduleSuggestion(
    val title: String,
    val executiveSummary: String,
    val analyzedTasksCount: Int,
    val totalStudyHours: Double,
    val urgentTasksNotes: String = "",
    val timeBlocks: List<TimeBlockedStudyBlock>,
    val motivationalAdvice: String,
    val rawMarkdown: String
)

data class DailySyllabusLesson(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val subject: String,
    val category: String = "مذاكرة",
    val durationMinutes: Int = 30,
    var isDone: Boolean = false,
    val colorHex: String = "#3B82F6"
)

object ScheduleJsonSerializer {
    fun serializeDailyLessons(lessons: List<DailySyllabusLesson>): String {
        val array = org.json.JSONArray()
        for (l in lessons) {
            val obj = org.json.JSONObject()
            obj.put("id", l.id)
            obj.put("title", l.title)
            obj.put("subject", l.subject)
            obj.put("category", l.category)
            obj.put("durationMinutes", l.durationMinutes)
            obj.put("isDone", l.isDone)
            obj.put("colorHex", l.colorHex)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeDailyLessons(jsonStr: String): List<DailySyllabusLesson> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        val list = mutableListOf<DailySyllabusLesson>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DailySyllabusLesson(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        title = obj.optString("title", ""),
                        subject = obj.optString("subject", "عام"),
                        category = obj.optString("category", "مذاكرة"),
                        durationMinutes = obj.optInt("durationMinutes", 30),
                        isDone = obj.optBoolean("isDone", false),
                        colorHex = obj.optString("colorHex", "#3B82F6")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun serializeStudentSubjects(subjects: List<StudentSubjectInput>): String {
        val array = org.json.JSONArray()
        for (s in subjects) {
            val obj = org.json.JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("topicsOrChapters", s.topicsOrChapters)
            obj.put("difficulty", s.difficulty.name)
            obj.put("deadlineDays", s.deadlineDays)
            obj.put("deadlineDateFormatted", s.deadlineDateFormatted)
            obj.put("estimatedTotalHours", s.estimatedTotalHours)
            obj.put("colorHex", s.colorHex)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeStudentSubjects(jsonStr: String): List<StudentSubjectInput> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        val list = mutableListOf<StudentSubjectInput>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val diffStr = obj.optString("difficulty", "MEDIUM")
                val diff = try { SubjectDifficulty.valueOf(diffStr) } catch (_: Exception) { SubjectDifficulty.MEDIUM }
                list.add(
                    StudentSubjectInput(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        topicsOrChapters = obj.optString("topicsOrChapters", ""),
                        difficulty = diff,
                        deadlineDays = obj.optInt("deadlineDays", 7),
                        deadlineDateFormatted = obj.optString("deadlineDateFormatted", ""),
                        estimatedTotalHours = obj.optInt("estimatedTotalHours", 10),
                        colorHex = obj.optLong("colorHex", 0xFF1E88E5)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun serializePlanResult(plan: PersonalizedStudyPlanResult?): String {
        if (plan == null) return ""
        val root = org.json.JSONObject()
        root.put("planTitle", plan.planTitle)
        root.put("strategySummary", plan.strategySummary)
        root.put("totalDays", plan.totalDays)
        root.put("totalEstimatedStudyHours", plan.totalEstimatedStudyHours.toDouble())
        root.put("rawResponseText", plan.rawResponseText)

        val daysArray = org.json.JSONArray()
        for (d in plan.days) {
            val dObj = org.json.JSONObject()
            dObj.put("dayNumber", d.dayNumber)
            dObj.put("dayTitle", d.dayTitle)
            dObj.put("dateString", d.dateString)
            dObj.put("focusGoal", d.focusGoal)
            dObj.put("totalStudyHours", d.totalStudyHours.toDouble())
            val sessArr = org.json.JSONArray()
            for (s in d.sessions) {
                val sObj = org.json.JSONObject()
                sObj.put("id", s.id)
                sObj.put("sessionNumber", s.sessionNumber)
                sObj.put("timeSlot", s.timeSlot)
                sObj.put("subjectName", s.subjectName)
                sObj.put("topicDescription", s.topicDescription)
                sObj.put("durationMinutes", s.durationMinutes)
                sObj.put("pomodorosCount", s.pomodorosCount)
                sObj.put("isBreak", s.isBreak)
                sObj.put("isMilestoneReview", s.isMilestoneReview)
                sObj.put("isCompleted", s.isCompleted)
                sessArr.put(sObj)
            }
            dObj.put("sessions", sessArr)
            daysArray.put(dObj)
        }
        root.put("days", daysArray)

        val tipsArray = org.json.JSONArray()
        for (t in plan.highYieldTips) {
            tipsArray.put(t)
        }
        root.put("highYieldTips", tipsArray)

        val subAllocArr = org.json.JSONArray()
        for (sa in plan.subjectAllocations) {
            val saObj = org.json.JSONObject()
            saObj.put("subjectName", sa.subjectName)
            saObj.put("totalHours", sa.totalHours.toDouble())
            saObj.put("percentageOfTotal", sa.percentageOfTotal)
            saObj.put("difficulty", sa.difficulty.name)
            saObj.put("deadlineText", sa.deadlineText)
            subAllocArr.put(saObj)
        }
        root.put("subjectAllocations", subAllocArr)

        return root.toString()
    }

    fun deserializePlanResult(jsonStr: String): PersonalizedStudyPlanResult? {
        if (jsonStr.isBlank()) return null
        return try {
            val root = org.json.JSONObject(jsonStr)
            val daysList = mutableListOf<GeneratedDaySchedule>()
            val daysArr = root.optJSONArray("days")
            if (daysArr != null) {
                for (i in 0 until daysArr.length()) {
                    val dObj = daysArr.getJSONObject(i)
                    val sessList = mutableListOf<GeneratedScheduleSession>()
                    val sessArr = dObj.optJSONArray("sessions")
                    if (sessArr != null) {
                        for (j in 0 until sessArr.length()) {
                            val sObj = sessArr.getJSONObject(j)
                            sessList.add(
                                GeneratedScheduleSession(
                                    id = sObj.optString("id", UUID.randomUUID().toString()),
                                    sessionNumber = sObj.optInt("sessionNumber", j + 1),
                                    timeSlot = sObj.optString("timeSlot", ""),
                                    subjectName = sObj.optString("subjectName", ""),
                                    topicDescription = sObj.optString("topicDescription", ""),
                                    durationMinutes = sObj.optInt("durationMinutes", 45),
                                    pomodorosCount = sObj.optInt("pomodorosCount", 1),
                                    isBreak = sObj.optBoolean("isBreak", false),
                                    isMilestoneReview = sObj.optBoolean("isMilestoneReview", false),
                                    isCompleted = sObj.optBoolean("isCompleted", false)
                                )
                            )
                        }
                    }
                    daysList.add(
                        GeneratedDaySchedule(
                            dayNumber = dObj.optInt("dayNumber", i + 1),
                            dayTitle = dObj.optString("dayTitle", "اليوم ${i + 1}"),
                            dateString = dObj.optString("dateString", ""),
                            focusGoal = dObj.optString("focusGoal", ""),
                            totalStudyHours = dObj.optDouble("totalStudyHours", 4.0).toFloat(),
                            sessions = sessList
                        )
                    )
                }
            }

            val tipsList = mutableListOf<String>()
            val tipsArr = root.optJSONArray("highYieldTips")
            if (tipsArr != null) {
                for (i in 0 until tipsArr.length()) {
                    tipsList.add(tipsArr.getString(i))
                }
            }

            val allocList = mutableListOf<SubjectAllocation>()
            val allocArr = root.optJSONArray("subjectAllocations")
            if (allocArr != null) {
                for (i in 0 until allocArr.length()) {
                    val aObj = allocArr.getJSONObject(i)
                    val diffStr = aObj.optString("difficulty", "MEDIUM")
                    val diff = try { SubjectDifficulty.valueOf(diffStr) } catch (_: Exception) { SubjectDifficulty.MEDIUM }
                    allocList.add(
                        SubjectAllocation(
                            subjectName = aObj.optString("subjectName", ""),
                            totalHours = aObj.optDouble("totalHours", 0.0).toFloat(),
                            percentageOfTotal = aObj.optInt("percentageOfTotal", 0),
                            difficulty = diff,
                            deadlineText = aObj.optString("deadlineText", "")
                        )
                    )
                }
            }

            PersonalizedStudyPlanResult(
                planTitle = root.optString("planTitle", "خطة المذاكرة الذكية"),
                strategySummary = root.optString("strategySummary", ""),
                totalDays = root.optInt("totalDays", daysList.size),
                totalEstimatedStudyHours = root.optDouble("totalEstimatedStudyHours", 0.0).toFloat(),
                days = daysList,
                subjectAllocations = allocList,
                highYieldTips = tipsList,
                rawResponseText = root.optString("rawResponseText", "")
            )
        } catch (_: Exception) {
            null
        }
    }
}
