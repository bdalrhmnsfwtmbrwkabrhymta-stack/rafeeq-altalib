package com.example.domain.firebase

import android.content.Context
import com.example.data.database.*
import com.example.data.preferences.UserSettings
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirestorePersistenceRepository(private val context: Context) {

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    fun isConfigured(): Boolean = firestore != null

    /**
     * Save or update a single task in Firestore
     */
    suspend fun saveTask(userId: String, task: TaskEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore غير مهيأ."))
        if (userId.isBlank()) return@withContext Result.failure(IllegalArgumentException("معرف المستخدم غير متاح."))

        try {
            val taskMap = hashMapOf(
                "id" to task.id,
                "title" to task.title,
                "description" to task.description,
                "subjectId" to task.subjectId,
                "dueDateMillis" to task.dueDateMillis,
                "priority" to task.priority,
                "isCompleted" to task.isCompleted,
                "isArchived" to task.isArchived,
                "tag" to task.tag,
                "isRecurring" to task.isRecurring,
                "recurrenceFrequency" to task.recurrenceFrequency,
                "createdAtMillis" to task.createdAtMillis,
                "updatedAtMillis" to System.currentTimeMillis()
            )
            fs.collection("users")
                .document(userId)
                .collection("tasks")
                .document(task.id.toString())
                .set(taskMap, SetOptions.merge())
                .awaitResult()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Delete a task from Firestore
     */
    suspend fun deleteTask(userId: String, taskId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore غير مهيأ."))
        if (userId.isBlank()) return@withContext Result.failure(IllegalArgumentException("معرف المستخدم غير متاح."))

        try {
            fs.collection("users")
                .document(userId)
                .collection("tasks")
                .document(taskId.toString())
                .delete()
                .awaitResult()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Save a study note in Firestore
     */
    suspend fun saveNote(userId: String, note: NoteEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore غير مهيأ."))
        if (userId.isBlank()) return@withContext Result.failure(IllegalArgumentException("معرف المستخدم غير متاح."))

        try {
            val noteMap = hashMapOf(
                "id" to note.id,
                "title" to note.title,
                "content" to note.content,
                "subjectId" to note.subjectId,
                "isPinned" to note.isPinned,
                "isChecklist" to note.isChecklist,
                "checklistJson" to note.checklistJson,
                "imageUri" to note.imageUri,
                "updatedAtMillis" to note.updatedAtMillis
            )
            fs.collection("users")
                .document(userId)
                .collection("notes")
                .document(note.id.toString())
                .set(noteMap, SetOptions.merge())
                .awaitResult()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Delete a study note from Firestore
     */
    suspend fun deleteNote(userId: String, noteId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore غير مهيأ."))
        if (userId.isBlank()) return@withContext Result.failure(IllegalArgumentException("معرف المستخدم غير متاح."))

        try {
            fs.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId.toString())
                .delete()
                .awaitResult()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Synchronize all local Room database data (tasks, notes, subjects, sessions, settings) to Firestore
     */
    suspend fun syncAllLocalDataToFirestore(
        userId: String,
        db: AppDatabase,
        userSettings: UserSettings
    ): Result<FirestoreSyncResult> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(
            IllegalStateException("خدمة Cloud Firestore غير مهيأة على هذا الجهاز.")
        )
        if (userId.isBlank()) return@withContext Result.failure(
            IllegalArgumentException("يجب تسجيل الدخول بحساب Google أو كضيف أولاً لحفظ البيانات في Firestore.")
        )

        try {
            // 1. Sync User Profile & Preferences
            val profileData = hashMapOf(
                "userName" to userSettings.userName,
                "whatsappNumber" to userSettings.whatsappNumber,
                "dailyGoal" to userSettings.dailyGoal,
                "studentPoints" to userSettings.studentPoints,
                "appLanguage" to userSettings.appLanguage,
                "educationalStage" to userSettings.educationalStage,
                "studentGradeYear" to userSettings.studentGradeYear,
                "lastSyncedAt" to System.currentTimeMillis()
            )
            fs.collection("users")
                .document(userId)
                .collection("profile")
                .document("info")
                .set(profileData, SetOptions.merge())
                .awaitResult()

            // 2. Sync Subjects
            val subjects = db.subjectDao().getAllSubjectsList()
            for (subject in subjects) {
                val subMap = hashMapOf(
                    "id" to subject.id,
                    "name" to subject.name,
                    "colorHex" to subject.colorHex,
                    "iconName" to subject.iconName,
                    "targetWeeklyHours" to subject.targetWeeklyHours,
                    "notes" to subject.notes
                )
                fs.collection("users")
                    .document(userId)
                    .collection("subjects")
                    .document(subject.id.toString())
                    .set(subMap, SetOptions.merge())
                    .awaitResult()
            }

            // 3. Sync Tasks
            val tasks = db.taskDao().getAllActiveTasksList()
            for (task in tasks) {
                val taskMap = hashMapOf(
                    "id" to task.id,
                    "title" to task.title,
                    "description" to task.description,
                    "subjectId" to task.subjectId,
                    "dueDateMillis" to task.dueDateMillis,
                    "priority" to task.priority,
                    "isCompleted" to task.isCompleted,
                    "isArchived" to task.isArchived,
                    "tag" to task.tag,
                    "isRecurring" to task.isRecurring,
                    "recurrenceFrequency" to task.recurrenceFrequency,
                    "createdAtMillis" to task.createdAtMillis
                )
                fs.collection("users")
                    .document(userId)
                    .collection("tasks")
                    .document(task.id.toString())
                    .set(taskMap, SetOptions.merge())
                    .awaitResult()
            }

            // 4. Sync Notes
            val notes = db.noteDao().getAllNotesList()
            for (note in notes) {
                val noteMap = hashMapOf(
                    "id" to note.id,
                    "title" to note.title,
                    "content" to note.content,
                    "subjectId" to note.subjectId,
                    "isPinned" to note.isPinned,
                    "isChecklist" to note.isChecklist,
                    "checklistJson" to note.checklistJson,
                    "imageUri" to note.imageUri,
                    "updatedAtMillis" to note.updatedAtMillis
                )
                fs.collection("users")
                    .document(userId)
                    .collection("notes")
                    .document(note.id.toString())
                    .set(noteMap, SetOptions.merge())
                    .awaitResult()
            }

            // 5. Sync Schedule Sessions
            val sessions = db.scheduleDao().getAllSessionsList()
            for (session in sessions) {
                val sessionMap = hashMapOf(
                    "id" to session.id,
                    "subjectId" to session.subjectId,
                    "planId" to session.planId,
                    "dayOfWeek" to session.dayOfWeek,
                    "startTimeString" to session.startTimeString,
                    "endTimeString" to session.endTimeString,
                    "roomOrLink" to session.roomOrLink,
                    "note" to session.note
                )
                fs.collection("users")
                    .document(userId)
                    .collection("schedule_sessions")
                    .document(session.id.toString())
                    .set(sessionMap, SetOptions.merge())
                    .awaitResult()
            }

            val result = FirestoreSyncResult(
                success = true,
                tasksSynced = tasks.size,
                notesSynced = notes.size,
                subjectsSynced = subjects.size,
                sessionsSynced = sessions.size,
                timestamp = System.currentTimeMillis(),
                message = "تمت مزامنة ${tasks.size} مهمة، ${notes.size} ملخص، و ${sessions.size} حصة دراسية في سحابة Firestore بنجاح ☁️"
            )
            Result.success(result)
        } catch (e: Throwable) {
            Result.failure(Exception("خطأ في مزامنة البيانات مع Firestore: ${e.localizedMessage}"))
        }
    }

    /**
     * Restore and merge student data from Firestore into local Room database
     */
    suspend fun restoreAllDataFromFirestore(
        userId: String,
        db: AppDatabase
    ): Result<FirestoreSyncResult> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(
            IllegalStateException("Firestore غير مهيأ.")
        )
        if (userId.isBlank()) return@withContext Result.failure(
            IllegalArgumentException("معرف المستخدم غير متاح.")
        )

        try {
            // Restore subjects
            val subjectDocs = fs.collection("users")
                .document(userId)
                .collection("subjects")
                .get()
                .awaitResult()

            var restoredSubjects = 0
            for (doc in subjectDocs.documents) {
                val id = doc.getLong("id") ?: continue
                val name = doc.getString("name") ?: "مادة"
                val colorHex = doc.getString("colorHex") ?: "#3F51B5"
                val iconName = doc.getString("iconName") ?: "Book"
                val targetHours = doc.getLong("targetWeeklyHours")?.toInt() ?: 10
                val notes = doc.getString("notes") ?: ""
                db.subjectDao().insertSubject(
                    SubjectEntity(
                        id = id,
                        name = name,
                        colorHex = colorHex,
                        iconName = iconName,
                        targetWeeklyHours = targetHours,
                        notes = notes
                    )
                )
                restoredSubjects++
            }

            // Restore tasks
            val taskDocs = fs.collection("users")
                .document(userId)
                .collection("tasks")
                .get()
                .awaitResult()

            var restoredTasks = 0
            for (doc in taskDocs.documents) {
                val id = doc.getLong("id") ?: continue
                val title = doc.getString("title") ?: continue
                val description = doc.getString("description") ?: ""
                val subjectId = doc.getLong("subjectId")
                val dueDateMillis = doc.getLong("dueDateMillis") ?: 0L
                val priority = doc.getString("priority") ?: "MEDIUM"
                val isCompleted = doc.getBoolean("isCompleted") ?: false
                val isArchived = doc.getBoolean("isArchived") ?: false
                val tag = doc.getString("tag") ?: "عام"
                val isRecurring = doc.getBoolean("isRecurring") ?: false
                val recurrenceFrequency = doc.getString("recurrenceFrequency") ?: "NONE"
                val createdAt = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()

                db.taskDao().insertTask(
                    TaskEntity(
                        id = id,
                        title = title,
                        description = description,
                        subjectId = subjectId,
                        dueDateMillis = dueDateMillis,
                        priority = priority,
                        isCompleted = isCompleted,
                        isArchived = isArchived,
                        tag = tag,
                        isRecurring = isRecurring,
                        recurrenceFrequency = recurrenceFrequency,
                        createdAtMillis = createdAt
                    )
                )
                restoredTasks++
            }

            // Restore notes
            val noteDocs = fs.collection("users")
                .document(userId)
                .collection("notes")
                .get()
                .awaitResult()

            var restoredNotes = 0
            for (doc in noteDocs.documents) {
                val id = doc.getLong("id") ?: continue
                val title = doc.getString("title") ?: "بدون عنوان"
                val content = doc.getString("content") ?: ""
                val subjectId = doc.getLong("subjectId")
                val isPinned = doc.getBoolean("isPinned") ?: false
                val isChecklist = doc.getBoolean("isChecklist") ?: false
                val checklistJson = doc.getString("checklistJson") ?: ""
                val imageUri = doc.getString("imageUri")
                val updatedAt = doc.getLong("updatedAtMillis") ?: System.currentTimeMillis()

                db.noteDao().insertNote(
                    NoteEntity(
                        id = id,
                        title = title,
                        content = content,
                        subjectId = subjectId,
                        isPinned = isPinned,
                        isChecklist = isChecklist,
                        checklistJson = checklistJson,
                        imageUri = imageUri,
                        updatedAtMillis = updatedAt
                    )
                )
                restoredNotes++
            }

            // Restore sessions
            val sessionDocs = fs.collection("users")
                .document(userId)
                .collection("schedule_sessions")
                .get()
                .awaitResult()

            var restoredSessions = 0
            for (doc in sessionDocs.documents) {
                val id = doc.getLong("id") ?: continue
                val subjectId = doc.getLong("subjectId") ?: 0L
                val planId = doc.getLong("planId") ?: 1L
                val dayOfWeek = doc.getLong("dayOfWeek")?.toInt() ?: 1
                val startTime = doc.getString("startTimeString") ?: "08:00"
                val endTime = doc.getString("endTimeString") ?: "09:00"
                val roomOrLink = doc.getString("roomOrLink") ?: ""
                val note = doc.getString("note") ?: ""

                db.scheduleDao().insertSession(
                    ScheduleSessionEntity(
                        id = id,
                        planId = planId,
                        subjectId = subjectId,
                        dayOfWeek = dayOfWeek,
                        startTimeString = startTime,
                        endTimeString = endTime,
                        roomOrLink = roomOrLink,
                        note = note
                    )
                )
                restoredSessions++
            }

            val result = FirestoreSyncResult(
                success = true,
                tasksSynced = restoredTasks,
                notesSynced = restoredNotes,
                subjectsSynced = restoredSubjects,
                sessionsSynced = restoredSessions,
                timestamp = System.currentTimeMillis(),
                message = "تم استرجاع $restoredTasks مهمة و $restoredNotes ملخص من Cloud Firestore بنجاح ☁️"
            )
            Result.success(result)
        } catch (e: Throwable) {
            Result.failure(Exception("خطأ في استرجاع البيانات من Firestore: ${e.localizedMessage}"))
        }
    }

    /**
     * Realtime snapshot listener on cloud tasks
     */
    fun listenToCloudTasks(
        userId: String,
        onTasksUpdated: (List<Map<String, Any>>) -> Unit
    ): ListenerRegistration? {
        val fs = firestore ?: return null
        if (userId.isBlank()) return null
        return try {
            fs.collection("users")
                .document(userId)
                .collection("tasks")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val list = snapshot.documents.mapNotNull { it.data }
                    onTasksUpdated(list)
                }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Save student account to Cloud Firestore under student_accounts/{phone}
     */
    suspend fun saveStudentAccountToCloud(
        phone: String,
        passwordPin: String,
        fullName: String,
        educationalStage: String,
        highSchoolBranch: String,
        studentAge: Int,
        studentGradeYear: String,
        userGmail: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore غير مهيأ حالياً."))
        val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.isBlank()) return@withContext Result.failure(IllegalArgumentException("رقم الهاتف غير صالح."))

        try {
            val data = hashMapOf(
                "phone" to cleanPhone,
                "passwordPin" to passwordPin.trim(),
                "fullName" to fullName.trim(),
                "educationalStage" to educationalStage,
                "highSchoolBranch" to highSchoolBranch,
                "studentAge" to studentAge,
                "studentGradeYear" to studentGradeYear,
                "userGmail" to userGmail.trim(),
                "lastUpdatedMillis" to System.currentTimeMillis()
            )
            fs.collection("student_accounts")
                .document(cleanPhone)
                .set(data, SetOptions.merge())
                .awaitResult()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Login student using phone and secret PIN from Cloud Firestore
     */
    suspend fun loginStudentAccountFromCloud(
        phone: String,
        passwordPin: String
    ): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore غير مهيأ حالياً."))
        val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.isBlank()) return@withContext Result.failure(IllegalArgumentException("يرجى إدخال رقم الهاتف."))

        try {
            val doc = fs.collection("student_accounts")
                .document(cleanPhone)
                .get()
                .awaitResult()

            if (!doc.exists()) {
                return@withContext Result.failure(Exception("لم يتم العثور على حساب مسجل بهذا الرقم ($cleanPhone).\nيرجى التأكد من الرقم أو اختيار 'إنشاء حساب جديد'."))
            }

            val storedPin = doc.getString("passwordPin") ?: ""
            if (storedPin.isNotBlank() && storedPin != passwordPin.trim()) {
                return@withContext Result.failure(Exception("الرقم السري غير صحيح.\nيرجى التأكد من كتابة الرقم السري بشكل صحيح."))
            }

            val data = doc.data ?: emptyMap<String, Any>()
            Result.success(data)
        } catch (e: Throwable) {
            Result.failure(Exception("تعذر تسجيل الدخول من سحابة Firebase: ${e.localizedMessage}"))
        }
    }

    /**
     * Check and fetch student account profile from Cloud Firestore to auto-restore session.
     */
    suspend fun fetchStudentAccountData(phone: String): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore غير مهيأ حالياً."))
        val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.isBlank()) return@withContext Result.failure(IllegalArgumentException("رقم الهاتف غير صالح."))

        try {
            val doc = fs.collection("student_accounts")
                .document(cleanPhone)
                .get()
                .awaitResult()

            if (!doc.exists()) {
                return@withContext Result.failure(Exception("لا توجد بيانات مسجلة في السحابة للرقم $cleanPhone"))
            }

            val data = doc.data ?: emptyMap<String, Any>()
            Result.success(data)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Look up student account by Firebase user UID or email if phone is not readily known.
     */
    suspend fun fetchStudentAccountByUidOrEmail(uid: String, email: String = ""): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore غير مهيأ حالياً."))
        try {
            if (uid.isNotBlank()) {
                val userDoc = fs.collection("users").document(uid).get().awaitResult()
                if (userDoc.exists() && userDoc.data != null) {
                    return@withContext Result.success(userDoc.data!!)
                }
            }

            if (email.isNotBlank()) {
                val query = fs.collection("student_accounts")
                    .whereEqualTo("userGmail", email.trim())
                    .limit(1)
                    .get()
                    .awaitResult()

                if (!query.isEmpty) {
                    val firstDoc = query.documents.first()
                    return@withContext Result.success(firstDoc.data ?: emptyMap())
                }
            }

            Result.failure(Exception("لم يتم العثور على بيانات سحابية مطابقة."))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Save lightweight Anki flashcard metadata to Firestore (preserving 1GB free tier limit)
     */
    suspend fun saveFlashcardMetadata(
        userId: String,
        cardId: String,
        metadata: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore غير مهيأ."))
        if (userId.isBlank()) return@withContext Result.failure(IllegalArgumentException("معرف المستخدم غير متاح."))

        try {
            fs.collection("users")
                .document(userId)
                .collection("flashcard_metadata")
                .document(cardId)
                .set(metadata, SetOptions.merge())
                .awaitResult()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}


