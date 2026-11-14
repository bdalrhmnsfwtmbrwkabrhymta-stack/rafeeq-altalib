package com.example.domain.firebase

data class FirebaseUserProfile(
    val uid: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false,
    val lastLoginMillis: Long = System.currentTimeMillis()
)

data class FirestoreSyncResult(
    val success: Boolean,
    val tasksSynced: Int = 0,
    val notesSynced: Int = 0,
    val subjectsSynced: Int = 0,
    val sessionsSynced: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = ""
)

data class FirestoreSyncState(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val lastStatusMessage: String = "",
    val tasksSyncedCount: Int = 0,
    val notesSyncedCount: Int = 0,
    val error: String? = null
)
