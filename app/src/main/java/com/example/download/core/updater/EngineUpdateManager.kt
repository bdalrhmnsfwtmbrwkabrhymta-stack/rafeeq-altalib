package com.example.download.core.updater

import com.example.download.core.model.DownloadError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit

data class TrustedPublicKey(
    val keyId: String,
    val algorithm: String = "SHA256withECDSA",
    val keyFactoryAlgorithm: String = "EC",
    val spkiBase64: String
)

data class EngineUpdateManifest(
    val version: String,
    val versionCode: Long,
    val minAndroidApi: Int,
    val supportedAbis: List<String>,
    val artifactUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val keyId: String,
    val signatureBase64: String
) {
    fun canonicalPayload(): String =
        "$version|$versionCode|$minAndroidApi|${supportedAbis.sorted().joinToString(",")}|$artifactUrl|${sha256.lowercase()}|$sizeBytes|$keyId"

    companion object {
        fun fromJson(json: String): EngineUpdateManifest {
            val obj = JSONObject(json)
            val abisArr = obj.optJSONArray("supportedAbis") ?: JSONArray()
            val abis = buildList {
                for (i in 0 until abisArr.length()) {
                    val v = abisArr.optString(i).trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
            return EngineUpdateManifest(
                version = obj.getString("version"),
                versionCode = obj.getLong("versionCode"),
                minAndroidApi = obj.optInt("minAndroidApi", 26),
                supportedAbis = abis,
                artifactUrl = obj.getString("artifactUrl"),
                sha256 = obj.getString("sha256").lowercase(),
                sizeBytes = obj.getLong("sizeBytes"),
                keyId = obj.getString("keyId"),
                signatureBase64 = obj.getString("signatureBase64")
            )
        }
    }
}

data class EngineCompatibilityProfile(
    val androidApi: Int,
    val supportedAbis: List<String>
)

data class EngineInstallState(
    val currentVersion: String = "bundled-0",
    val currentVersionCode: Long = 0L,
    val previousVersion: String? = null,
    val previousVersionCode: Long = 0L,
    val manifestEtag: String? = null,
    val manifestLastModified: String? = null,
    val lastCheckMs: Long = 0L,
    val activeArtifactPath: String? = null,
    val backupArtifactPath: String? = null
)

interface EngineUpdateStateStore {
    suspend fun readState(): EngineInstallState
    suspend fun saveState(state: EngineInstallState)
}

class InMemoryEngineUpdateStore(
    initialState: EngineInstallState = EngineInstallState()
) : EngineUpdateStateStore {
    private var state: EngineInstallState = initialState
    override suspend fun readState(): EngineInstallState = state
    override suspend fun saveState(state: EngineInstallState) {
        this.state = state
    }
}

sealed class EngineUpdateResult {
    data class Throttled(val lastCheckedMs: Long, val activeVersionCode: Long) : EngineUpdateResult()
    data class NotModified304(val activeVersionCode: Long) : EngineUpdateResult()
    data class UpToDate(val version: String, val versionCode: Long) : EngineUpdateResult()
    data class Updated(
        val previousVersion: String,
        val newVersion: String,
        val newVersionCode: Long,
        val artifactFile: File
    ) : EngineUpdateResult()
    data class RolledBack(
        val failedVersion: String,
        val restoredVersion: String,
        val error: DownloadError
    ) : EngineUpdateResult()
    data class Rejected(val error: DownloadError) : EngineUpdateResult()
    data class OfflineFallback(val activeVersionCode: Long, val reason: String) : EngineUpdateResult()
}

object ManifestSignatureVerifier {

    fun parsePublicKey(entry: TrustedPublicKey): PublicKey {
        val decoded = Base64.getDecoder().decode(entry.spkiBase64)
        val spec = X509EncodedKeySpec(decoded)
        return KeyFactory.getInstance(entry.keyFactoryAlgorithm).generatePublic(spec)
    }

    fun verifyManifest(
        manifest: EngineUpdateManifest,
        trustedKeys: Map<String, TrustedPublicKey>
    ): Boolean {
        val keyEntry = trustedKeys[manifest.keyId] ?: return false
        return runCatching {
            val pubKey = parsePublicKey(keyEntry)
            val sig = Signature.getInstance(keyEntry.algorithm)
            sig.initVerify(pubKey)
            sig.update(manifest.canonicalPayload().toByteArray(Charsets.UTF_8))
            val sigBytes = Base64.getDecoder().decode(manifest.signatureBase64)
            sig.verify(sigBytes)
        }.getOrDefault(false)
    }

    fun computeSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Isolated video extraction engine auto-updater (yt-dlp artifact updates ONLY,
 * strictly decoupled from app APK updates and fixed runtime libraries like FFmpeg/aria2c).
 */
class EngineUpdateManager(
    private val engineWorkDir: File,
    private val stateStore: EngineUpdateStateStore,
    private val trustedKeys: Map<String, TrustedPublicKey>,
    private val compatibility: EngineCompatibilityProfile,
    private val httpClient: OkHttpClient = defaultClient(),
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val activeDir = File(engineWorkDir, "active").apply { mkdirs() }
    private val stagingDir = File(engineWorkDir, "staging").apply { mkdirs() }
    private val backupDir = File(engineWorkDir, "backup").apply { mkdirs() }

    suspend fun checkAndApplyUpdate(
        manifestUrl: String,
        minIntervalMs: Long = 24L * 60L * 60L * 1000L,
        forceCheck: Boolean = false,
        postInstallValidator: suspend (File) -> Boolean = { it.exists() && it.length() > 0L }
    ): EngineUpdateResult = withContext(Dispatchers.IO) {
        val state = stateStore.readState()
        val now = clockMs()

        if (!forceCheck && state.lastCheckMs > 0L && (now - state.lastCheckMs) < minIntervalMs) {
            return@withContext EngineUpdateResult.Throttled(state.lastCheckMs, state.currentVersionCode)
        }

        val reqBuilder = Request.Builder().url(manifestUrl).get()
        if (!forceCheck) {
            state.manifestEtag?.takeIf { it.isNotBlank() }?.let { reqBuilder.header("If-None-Match", it) }
            state.manifestLastModified?.takeIf { it.isNotBlank() }?.let { reqBuilder.header("If-Modified-Since", it) }
        }

        val manifestBody: String
        val responseEtag: String?
        val responseLastMod: String?

        try {
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.code == 304) {
                    stateStore.saveState(state.copy(lastCheckMs = now))
                    return@withContext EngineUpdateResult.NotModified304(state.currentVersionCode)
                }
                if (!resp.isSuccessful) {
                    return@withContext EngineUpdateResult.OfflineFallback(
                        activeVersionCode = state.currentVersionCode,
                        reason = "Manifest HTTP ${resp.code}"
                    )
                }
                manifestBody = resp.body?.string().orEmpty()
                responseEtag = resp.header("ETag")
                responseLastMod = resp.header("Last-Modified")
            }
        } catch (t: Throwable) {
            return@withContext EngineUpdateResult.OfflineFallback(
                activeVersionCode = state.currentVersionCode,
                reason = t.message ?: t.javaClass.simpleName
            )
        }

        val manifest = runCatching { EngineUpdateManifest.fromJson(manifestBody) }.getOrElse { err ->
            return@withContext EngineUpdateResult.Rejected(
                DownloadError.EngineUpdateFailed("Invalid engine manifest JSON: ${err.message}")
            )
        }

        // 1. Cryptographic signature verification (supports rotated keys via keyId)
        if (!ManifestSignatureVerifier.verifyManifest(manifest, trustedKeys)) {
            return@withContext EngineUpdateResult.Rejected(
                DownloadError.EngineUpdateFailed("Manifest cryptographic signature verification failed for keyId=${manifest.keyId}")
            )
        }

        // 2. Android API & ABI compatibility check
        if (compatibility.androidApi < manifest.minAndroidApi) {
            return@withContext EngineUpdateResult.Rejected(
                DownloadError.EngineCompatibilityError(
                    "Requires Android API ${manifest.minAndroidApi}, device is ${compatibility.androidApi}"
                )
            )
        }
        val abiMatch = compatibility.supportedAbis.any { abi ->
            manifest.supportedAbis.any { it.equals(abi, ignoreCase = true) }
        }
        if (manifest.supportedAbis.isNotEmpty() && !abiMatch) {
            return@withContext EngineUpdateResult.Rejected(
                DownloadError.EngineCompatibilityError(
                    "Device ABIs ${compatibility.supportedAbis} not in manifest ${manifest.supportedAbis}"
                )
            )
        }

        // 3. Version comparison & anti-downgrade guard
        if (manifest.versionCode < state.currentVersionCode) {
            return@withContext EngineUpdateResult.Rejected(
                DownloadError.EngineUpdateFailed(
                    "Anti-downgrade rejected manifest versionCode=${manifest.versionCode} < current=${state.currentVersionCode}"
                )
            )
        }
        if (manifest.versionCode == state.currentVersionCode) {
            stateStore.saveState(
                state.copy(
                    manifestEtag = responseEtag ?: state.manifestEtag,
                    manifestLastModified = responseLastMod ?: state.manifestLastModified,
                    lastCheckMs = now
                )
            )
            return@withContext EngineUpdateResult.UpToDate(state.currentVersion, state.currentVersionCode)
        }

        // 4. Download artifact into isolated staging area & verify size + SHA-256
        val stagedFile = File(stagingDir, "engine_${manifest.versionCode}.bin")
        val downloadOk = downloadToStaging(manifest.artifactUrl, stagedFile)
        if (!downloadOk.isSuccess) {
            runCatching { stagedFile.delete() }
            return@withContext EngineUpdateResult.OfflineFallback(
                activeVersionCode = state.currentVersionCode,
                reason = downloadOk.exceptionOrNull()?.message ?: "Artifact download interrupted"
            )
        }

        if (manifest.sizeBytes > 0L && stagedFile.length() != manifest.sizeBytes) {
            val actualLen = stagedFile.length()
            runCatching { stagedFile.delete() }
            return@withContext EngineUpdateResult.Rejected(
                DownloadError.IntegrityCheckFailed(
                    "Engine artifact size mismatch: expected=${manifest.sizeBytes}, actual=$actualLen"
                )
            )
        }

        val actualSha256 = ManifestSignatureVerifier.computeSha256Hex(stagedFile)
        if (!actualSha256.equals(manifest.sha256, ignoreCase = true)) {
            runCatching { stagedFile.delete() }
            return@withContext EngineUpdateResult.Rejected(
                DownloadError.IntegrityCheckFailed(
                    "Engine SHA-256 mismatch: expected=${manifest.sha256}, actual=$actualSha256"
                )
            )
        }

        // 5. Back up current healthy artifact, atomically swap staged -> active, and run validator with rollback
        val activeFile = File(activeDir, "engine_current.bin")
        val backupFile = File(backupDir, "engine_backup.bin")
        val hadPreviousActive = activeFile.exists() && activeFile.length() > 0L
        if (hadPreviousActive) {
            activeFile.copyTo(backupFile, overwrite = true)
        }

        try {
            if (activeFile.exists()) activeFile.delete()
            if (!stagedFile.renameTo(activeFile)) {
                stagedFile.copyTo(activeFile, overwrite = true)
                stagedFile.delete()
            }

            val validAfterSwap = runCatching { postInstallValidator(activeFile) }.getOrDefault(false)
            if (!validAfterSwap) {
                rollbackFromBackup(hadPreviousActive, backupFile, activeFile)
                return@withContext EngineUpdateResult.RolledBack(
                    failedVersion = manifest.version,
                    restoredVersion = state.currentVersion,
                    error = DownloadError.EngineUpdateFailed("Post-install validation failed for ${manifest.version}; rolled back to ${state.currentVersion}")
                )
            }

            val updatedState = EngineInstallState(
                currentVersion = manifest.version,
                currentVersionCode = manifest.versionCode,
                previousVersion = state.currentVersion,
                previousVersionCode = state.currentVersionCode,
                manifestEtag = responseEtag,
                manifestLastModified = responseLastMod,
                lastCheckMs = now,
                activeArtifactPath = activeFile.absolutePath,
                backupArtifactPath = if (backupFile.exists()) backupFile.absolutePath else null
            )
            stateStore.saveState(updatedState)

            EngineUpdateResult.Updated(
                previousVersion = state.currentVersion,
                newVersion = manifest.version,
                newVersionCode = manifest.versionCode,
                artifactFile = activeFile
            )
        } catch (t: Throwable) {
            rollbackFromBackup(hadPreviousActive, backupFile, activeFile)
            EngineUpdateResult.RolledBack(
                failedVersion = manifest.version,
                restoredVersion = state.currentVersion,
                error = DownloadError.EngineUpdateFailed("Atomic swap failed (${t.message}); rolled back cleanly")
            )
        }
    }

    private fun rollbackFromBackup(hadBackup: Boolean, backupFile: File, activeFile: File) {
        runCatching {
            if (activeFile.exists()) activeFile.delete()
            if (hadBackup && backupFile.exists()) {
                backupFile.copyTo(activeFile, overwrite = true)
            }
        }
    }

    private fun downloadToStaging(url: String, target: File): Result<Unit> {
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Artifact HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty engine artifact body")
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                }
            }
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
