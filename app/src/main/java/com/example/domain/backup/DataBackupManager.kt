package com.example.domain.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.database.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object DataBackupManager {

    suspend fun generateFullBackupJson(db: AppDatabase): String {
        val root = JSONObject()

        val timeStampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        root.put("appName", "Rafiq Al-Talib (رفيق الطالب)")
        root.put("exportDate", timeStampStr)
        root.put("timestampMillis", System.currentTimeMillis())
        root.put("version", "2.0")

        // 1. Export Active & Archived Tasks
        val activeTasks = db.taskDao().getAllActiveTasks().first()
        val archivedTasks = db.taskDao().getArchivedTasks().first()
        val allTasks = activeTasks + archivedTasks
        val tasksArray = JSONArray()
        for (t in allTasks) {
            val taskObj = JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("description", t.description)
                put("subjectId", t.subjectId ?: -1)
                put("dueDateMillis", t.dueDateMillis)
                put("priority", t.priority)
                put("tag", t.tag)
                put("isCompleted", t.isCompleted)
                put("isArchived", t.isArchived)
                put("isRecurring", t.isRecurring)
                put("recurrenceFrequency", t.recurrenceFrequency)
                put("createdAtMillis", t.createdAtMillis)
            }
            tasksArray.put(taskObj)
        }
        root.put("tasks", tasksArray)

        // 2. Export Subjects
        val subjects = db.subjectDao().getAllSubjects().first()
        val subjectsArray = JSONArray()
        for (s in subjects) {
            val subjObj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("colorHex", s.colorHex)
                put("iconName", s.iconName)
                put("targetWeeklyHours", s.targetWeeklyHours)
                put("notes", s.notes)
            }
            subjectsArray.put(subjObj)
        }
        root.put("subjects", subjectsArray)

        // 3. Export Schedule Plans
        val plans = db.scheduleDao().getAllPlans().first()
        val plansArray = JSONArray()
        for (p in plans) {
            val planObj = JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("isDefault", p.isDefault)
                put("createdAtMillis", p.createdAtMillis)
            }
            plansArray.put(planObj)
        }
        root.put("schedulePlans", plansArray)

        // 4. Export Schedule Sessions
        val sessions = db.scheduleDao().getAllSessions().first()
        val sessionsArray = JSONArray()
        for (ss in sessions) {
            val ssObj = JSONObject().apply {
                put("id", ss.id)
                put("planId", ss.planId)
                put("subjectId", ss.subjectId)
                put("dayOfWeek", ss.dayOfWeek)
                put("startTimeString", ss.startTimeString)
                put("endTimeString", ss.endTimeString)
                put("roomOrLink", ss.roomOrLink)
                put("note", ss.note)
            }
            sessionsArray.put(ssObj)
        }
        root.put("scheduleSessions", sessionsArray)

        // 5. Export Exams
        val exams = db.scheduleDao().getAllExams().first()
        val examsArray = JSONArray()
        for (e in exams) {
            val exObj = JSONObject().apply {
                put("id", e.id)
                put("subjectId", e.subjectId ?: -1)
                put("examTitle", e.examTitle)
                put("examTimestampMillis", e.examTimestampMillis)
                put("location", e.location)
                put("totalTopics", e.totalTopics)
                put("studiedTopics", e.studiedTopics)
            }
            examsArray.put(exObj)
        }
        root.put("examCountdowns", examsArray)

        // 6. Export Notes
        val notes = db.noteDao().getAllNotes().first()
        val notesArray = JSONArray()
        for (n in notes) {
            val nObj = JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("subjectId", n.subjectId ?: -1)
                put("isPinned", n.isPinned)
                put("updatedAtMillis", n.updatedAtMillis)
            }
            notesArray.put(nObj)
        }
        root.put("notes", notesArray)

        // 7. Export Habits
        val habits = db.habitDao().getAllHabits().first()
        val habitsArray = JSONArray()
        for (h in habits) {
            val hObj = JSONObject().apply {
                put("id", h.id)
                put("title", h.title)
                put("description", h.description)
                put("targetFrequencyPerWeek", h.targetFrequencyPerWeek)
                put("streakCount", h.streakCount)
            }
            habitsArray.put(hObj)
        }
        root.put("habits", habitsArray)

        return root.toString(2)
    }

    fun writeJsonToFile(context: Context, jsonContent: String): File? {
        return try {
            val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "rafiq_student_backup_$dateStamp.json"

            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fileDir = if (downloadsFolder != null && (downloadsFolder.exists() || downloadsFolder.mkdirs())) {
                downloadsFolder
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            }

            val targetFile = File(fileDir, fileName)
            targetFile.writeText(jsonContent, Charsets.UTF_8)
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val fallbackFile = File(context.filesDir, "student_data_backup.json")
                fallbackFile.writeText(jsonContent, Charsets.UTF_8)
                fallbackFile
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
        }
    }

    fun shareBackupFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "النسخة الاحتياطية لمهام وجدول الطالب - رفيق الطالب")
                putExtra(Intent.EXTRA_TEXT, "ملف النسخة الاحتياطية المحلي لبيانات الجدول والمهام الدراسية 📁")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "مشاركة أو حفظ ملف النسخة الاحتياطية 📤")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun restoreBackupFromJson(db: AppDatabase, jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)

            // Restore Subjects
            if (root.has("subjects")) {
                val array = root.getJSONArray("subjects")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val s = SubjectEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0,
                        name = obj.getString("name"),
                        colorHex = obj.optString("colorHex", "#3B82F6"),
                        iconName = obj.optString("iconName", "Book"),
                        targetWeeklyHours = obj.optInt("targetWeeklyHours", 10),
                        notes = obj.optString("notes", "")
                    )
                    db.subjectDao().insertSubject(s)
                }
            }

            // Restore Tasks
            if (root.has("tasks")) {
                val array = root.getJSONArray("tasks")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawSubjId = obj.optLong("subjectId", -1)
                    val t = TaskEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0,
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        subjectId = if (rawSubjId > 0) rawSubjId else null,
                        dueDateMillis = obj.optLong("dueDateMillis", System.currentTimeMillis()),
                        priority = obj.optString("priority", "MEDIUM"),
                        tag = obj.optString("tag", "عام"),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        isRecurring = obj.optBoolean("isRecurring", false),
                        recurrenceFrequency = obj.optString("recurrenceFrequency", "NONE"),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                    )
                    db.taskDao().insertTask(t)
                }
            }

            // Restore Schedule Plans
            if (root.has("schedulePlans")) {
                val array = root.getJSONArray("schedulePlans")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val p = SchedulePlanEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0,
                        title = obj.getString("title"),
                        isDefault = obj.optBoolean("isDefault", false),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                    )
                    db.scheduleDao().insertPlan(p)
                }
            }

            // Restore Schedule Sessions
            if (root.has("scheduleSessions")) {
                val array = root.getJSONArray("scheduleSessions")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val ss = ScheduleSessionEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0,
                        planId = obj.optLong("planId", 1),
                        subjectId = obj.optLong("subjectId", 1),
                        dayOfWeek = obj.optInt("dayOfWeek", 1),
                        startTimeString = obj.optString("startTimeString", "09:00"),
                        endTimeString = obj.optString("endTimeString", "10:00"),
                        roomOrLink = obj.optString("roomOrLink", ""),
                        note = obj.optString("note", "")
                    )
                    db.scheduleDao().insertSession(ss)
                }
            }

            // Restore Exams
            if (root.has("examCountdowns")) {
                val array = root.getJSONArray("examCountdowns")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawSubjId = obj.optLong("subjectId", -1)
                    val e = ExamCountdownEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0,
                        subjectId = if (rawSubjId > 0) rawSubjId else null,
                        examTitle = obj.getString("examTitle"),
                        examTimestampMillis = obj.optLong("examTimestampMillis", System.currentTimeMillis()),
                        location = obj.optString("location", ""),
                        totalTopics = obj.optInt("totalTopics", 10),
                        studiedTopics = obj.optInt("studiedTopics", 0)
                    )
                    db.scheduleDao().insertExam(e)
                }
            }

            // Restore Notes
            if (root.has("notes")) {
                val array = root.getJSONArray("notes")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawSubjId = obj.optLong("subjectId", -1)
                    val n = NoteEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0,
                        title = obj.getString("title"),
                        content = obj.optString("content", ""),
                        subjectId = if (rawSubjId > 0) rawSubjId else null,
                        isPinned = obj.optBoolean("isPinned", false),
                        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis())
                    )
                    db.noteDao().insertNote(n)
                }
            }

            // Restore Habits
            if (root.has("habits")) {
                val array = root.getJSONArray("habits")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val h = HabitEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0,
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        targetFrequencyPerWeek = obj.optInt("targetFrequencyPerWeek", 7),
                        streakCount = obj.optInt("streakCount", 0)
                    )
                    db.habitDao().insertHabit(h)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
