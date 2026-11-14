package com.example.domain.firebase

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class FirebaseAuthRepository(private val context: Context) {

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUserProfile?>(null)
    val currentUser: StateFlow<FirebaseUserProfile?> = _currentUser.asStateFlow()

    init {
        try {
            auth?.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                _currentUser.value = user?.toDomainModel()
            }
            // Initial check
            _currentUser.value = auth?.currentUser?.toDomainModel()
        } catch (e: Throwable) {
            // Graceful fallback if services not ready
        }
    }

    fun isConfigured(): Boolean = auth != null

    /**
     * Sign in with Google using Android Credential Manager and Google Identity Services.
     */
    suspend fun signInWithGoogle(
        context: Context,
        serverClientId: String
    ): Result<FirebaseUserProfile> = withContext(Dispatchers.IO) {
        val fbAuth = auth ?: return@withContext Result.failure(
            IllegalStateException("Firebase Auth غير مهيأ حالياً. تأكد من إعدادات Firebase.")
        )

        if (serverClientId.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("يرجى إدخال Web Client ID الخاص بحساب Firebase للربط بتسجيل الدخول بـ Google.")
            )
        }

        try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId.trim())
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = fbAuth.signInWithCredential(authCredential).awaitResult()
                val profile = authResult.user?.toDomainModel()
                    ?: throw IllegalStateException("لم يتم استرجاع بيانات المستخدم من Firebase.")
                _currentUser.value = profile
                Result.success(profile)
            } else {
                Result.failure(IllegalStateException("نوع بيانات الاعتماد غير مدعوم لتسجيل الدخول بـ Google."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("تم إلغاء عملية تسجيل الدخول بواسطة المستخدم."))
        } catch (e: GetCredentialException) {
            Result.failure(Exception("خطأ في خدمات الاعتماد (Credential Manager): ${e.localizedMessage}"))
        } catch (e: Throwable) {
            Result.failure(Exception("فشل تسجيل الدخول بواسطة Google: ${e.localizedMessage}"))
        }
    }

    /**
     * Sign in with Email and Password
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUserProfile> = withContext(Dispatchers.IO) {
        val fbAuth = auth ?: return@withContext Result.failure(
            IllegalStateException("Firebase Auth غير مهيأ.")
        )
        if (email.isBlank() || password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("البريد الإلكتروني وكلمة المرور مطلوبان."))
        }

        try {
            val authResult = fbAuth.signInWithEmailAndPassword(email.trim(), password).awaitResult()
            val profile = authResult.user?.toDomainModel()
                ?: throw IllegalStateException("تعذر استرجاع الحساب.")
            _currentUser.value = profile
            Result.success(profile)
        } catch (e: Throwable) {
            Result.failure(Exception("خطأ أثناء تسجيل الدخول: ${e.localizedMessage}"))
        }
    }

    /**
     * Create Account with Email, Password and Display Name
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<FirebaseUserProfile> = withContext(Dispatchers.IO) {
        val fbAuth = auth ?: return@withContext Result.failure(
            IllegalStateException("Firebase Auth غير مهيأ.")
        )
        if (email.isBlank() || password.length < 6) {
            return@withContext Result.failure(
                IllegalArgumentException("يرجى إدخال بريد صحيح وكلمة مرور من 6 أحرف على الأقل.")
            )
        }

        try {
            val authResult = fbAuth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
            val user = authResult.user

            if (user != null && displayName.isNotBlank()) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                user.updateProfile(profileUpdates).awaitResult()
            }

            val profile = user?.toDomainModel()
                ?: throw IllegalStateException("تعذر إنشاء الحساب.")
            _currentUser.value = profile
            Result.success(profile)
        } catch (e: Throwable) {
            Result.failure(Exception("خطأ أثناء إنشاء الحساب: ${e.localizedMessage}"))
        }
    }

    /**
     * Sign in anonymously (for instant cloud storage sync without entering credentials)
     */
    suspend fun signInAnonymously(): Result<FirebaseUserProfile> = withContext(Dispatchers.IO) {
        val fbAuth = auth ?: return@withContext Result.failure(
            IllegalStateException("Firebase Auth غير مهيأ.")
        )
        try {
            val authResult = fbAuth.signInAnonymously().awaitResult()
            val profile = authResult.user?.toDomainModel()
                ?: throw IllegalStateException("تعذر تسجيل الدخول كضيف.")
            _currentUser.value = profile
            Result.success(profile)
        } catch (e: Throwable) {
            Result.failure(Exception("تعذر تسجيل الدخول كضيف: ${e.localizedMessage}"))
        }
    }

    /**
     * Sign out current user
     */
    fun signOut() {
        try {
            auth?.signOut()
            _currentUser.value = null
        } catch (e: Throwable) {
            _currentUser.value = null
        }
    }

    private fun FirebaseUser.toDomainModel(): FirebaseUserProfile {
        return FirebaseUserProfile(
            uid = uid,
            email = email,
            displayName = displayName ?: if (isAnonymous) "طالب زائر (مجهول)" else "طالب مسجل",
            photoUrl = photoUrl?.toString(),
            isAnonymous = isAnonymous,
            lastLoginMillis = System.currentTimeMillis()
        )
    }
}
