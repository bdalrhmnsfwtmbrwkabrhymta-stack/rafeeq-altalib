package com.example.download.core

import com.example.download.core.model.DownloadError
import com.example.download.core.updater.EngineCompatibilityProfile
import com.example.download.core.updater.EngineInstallState
import com.example.download.core.updater.EngineUpdateManifest
import com.example.download.core.updater.EngineUpdateManager
import com.example.download.core.updater.EngineUpdateResult
import com.example.download.core.updater.InMemoryEngineUpdateStore
import com.example.download.core.updater.TrustedPublicKey
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class EngineUpdateManagerTest {

    private lateinit var workDir: File
    private lateinit var server: HttpServer
    private var port: Int = 0
    private lateinit var keyPair1: KeyPair
    private lateinit var keyPair2: KeyPair
    private lateinit var trustedKeys: Map<String, TrustedPublicKey>

    @Before
    fun setup() {
        workDir = Files.createTempDirectory("engine_updater_test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newFixedThreadPool(4)
        server.start()
        port = server.address.port

        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        keyPair1 = gen.generateKeyPair()
        keyPair2 = gen.generateKeyPair()

        trustedKeys = mapOf(
            "key-2026-01" to TrustedPublicKey(
                keyId = "key-2026-01",
                algorithm = "SHA256withECDSA",
                keyFactoryAlgorithm = "EC",
                spkiBase64 = Base64.getEncoder().encodeToString(keyPair1.public.encoded)
            ),
            "key-2026-02" to TrustedPublicKey(
                keyId = "key-2026-02",
                algorithm = "SHA256withECDSA",
                keyFactoryAlgorithm = "EC",
                spkiBase64 = Base64.getEncoder().encodeToString(keyPair2.public.encoded)
            )
        )
    }

    @After
    fun teardown() {
        runCatching { server.stop(0) }
        workDir.deleteRecursively()
    }

    @Test
    fun signedNewUpdate_isVerifiedInstalledAndSupportsKeyRotation() = runBlocking {
        val v1Artifact = "YTDLP_ENGINE_V1_BINARY".toByteArray()
        val v2Artifact = "YTDLP_ENGINE_V2_ROTATED_KEY".toByteArray()

        val manifestJsonRef = AtomicReference<String>("")
        val artifactRef = AtomicReference(v1Artifact)

        server.createContext("/manifest.json") { ex ->
            val body = manifestJsonRef.get().toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.responseHeaders.add("ETag", "\"m-etag-${body.size}\"")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/engine.bin") { ex ->
            val bytes = artifactRef.get()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }

        val store = InMemoryEngineUpdateStore(EngineInstallState(currentVersion = "2026.01", currentVersionCode = 1))
        val manager = EngineUpdateManager(
            engineWorkDir = workDir,
            stateStore = store,
            trustedKeys = trustedKeys,
            compatibility = EngineCompatibilityProfile(androidApi = 34, supportedAbis = listOf("arm64-v8a"))
        )

        // Update 1 signed with key-2026-01
        manifestJsonRef.set(
            buildSignedManifestJson(
                version = "2026.02",
                versionCode = 2L,
                minApi = 26,
                abis = listOf("arm64-v8a", "armeabi-v7a"),
                artifactUrl = "http://127.0.0.1:$port/engine.bin",
                payload = v1Artifact,
                keyId = "key-2026-01",
                keyPair = keyPair1
            )
        )
        val res1 = manager.checkAndApplyUpdate("http://127.0.0.1:$port/manifest.json", forceCheck = true)
        assertTrue(res1 is EngineUpdateResult.Updated)
        res1 as EngineUpdateResult.Updated
        assertEquals("2026.02", res1.newVersion)
        assertArrayEquals(v1Artifact, res1.artifactFile.readBytes())

        // Update 2 signed with rotated key-2026-02
        artifactRef.set(v2Artifact)
        manifestJsonRef.set(
            buildSignedManifestJson(
                version = "2026.03",
                versionCode = 3L,
                minApi = 26,
                abis = listOf("arm64-v8a"),
                artifactUrl = "http://127.0.0.1:$port/engine.bin",
                payload = v2Artifact,
                keyId = "key-2026-02",
                keyPair = keyPair2
            )
        )
        val res2 = manager.checkAndApplyUpdate("http://127.0.0.1:$port/manifest.json", forceCheck = true)
        assertTrue(res2 is EngineUpdateResult.Updated)
        assertEquals(3L, store.readState().currentVersionCode)
    }

    @Test
    fun conditional304AndUpToDateAndOfflineFallback_behaveCleanly() = runBlocking {
        val payload = "ENGINE_V10".toByteArray()
        val manifest = buildSignedManifestJson(
            version = "2026.10",
            versionCode = 10L,
            minApi = 26,
            abis = listOf("arm64-v8a"),
            artifactUrl = "http://127.0.0.1:$port/unused.bin",
            payload = payload,
            keyId = "key-2026-01",
            keyPair = keyPair1
        )
        server.createContext("/m304.json") { ex ->
            val inm = ex.requestHeaders.getFirst("If-None-Match")
            if (inm == "\"etag-10\"") {
                ex.sendResponseHeaders(304, -1)
                ex.close()
            } else {
                val bytes = manifest.toByteArray()
                ex.responseHeaders.add("ETag", "\"etag-10\"")
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
        }

        val store = InMemoryEngineUpdateStore(
            EngineInstallState(currentVersion = "2026.10", currentVersionCode = 10L, lastCheckMs = 0L)
        )
        val manager = EngineUpdateManager(
            engineWorkDir = workDir,
            stateStore = store,
            trustedKeys = trustedKeys,
            compatibility = EngineCompatibilityProfile(34, listOf("arm64-v8a"))
        )

        val first = manager.checkAndApplyUpdate("http://127.0.0.1:$port/m304.json", minIntervalMs = 0L)
        assertTrue(first is EngineUpdateResult.UpToDate)

        val second = manager.checkAndApplyUpdate("http://127.0.0.1:$port/m304.json", minIntervalMs = 0L)
        assertTrue(second is EngineUpdateResult.NotModified304)

        val offline = manager.checkAndApplyUpdate("http://127.0.0.1:1/unreachable.json", forceCheck = true)
        assertTrue(offline is EngineUpdateResult.OfflineFallback)
        assertEquals(10L, (offline as EngineUpdateResult.OfflineFallback).activeVersionCode)
    }

    @Test
    fun tamperedSignature_shaMismatch_incompatibleAbi_antiDowngrade_allRejected() = runBlocking {
        val goodPayload = "GOOD_ENGINE".toByteArray()
        val badSigManifest = buildSignedManifestJson(
            version = "2026.5",
            versionCode = 5L,
            minApi = 26,
            abis = listOf("arm64-v8a"),
            artifactUrl = "http://127.0.0.1:$port/art.bin",
            payload = goodPayload,
            keyId = "key-2026-01",
            keyPair = keyPair2 // Wrong private key for keyId!
        )

        server.createContext("/bad_sig.json") { ex ->
            val b = badSigManifest.toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.createContext("/wrong_sha.json") { ex ->
            val m = buildSignedManifestJson(
                version = "2026.5",
                versionCode = 5L,
                minApi = 26,
                abis = listOf("arm64-v8a"),
                artifactUrl = "http://127.0.0.1:$port/tampered_art.bin",
                payload = goodPayload,
                keyId = "key-2026-01",
                keyPair = keyPair1
            )
            val b = m.toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.createContext("/tampered_art.bin") { ex ->
            val b = "TAMPERED_XX".toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.createContext("/incompat.json") { ex ->
            val m = buildSignedManifestJson(
                version = "2026.5",
                versionCode = 5L,
                minApi = 26,
                abis = listOf("x86_64"),
                artifactUrl = "http://127.0.0.1:$port/art.bin",
                payload = goodPayload,
                keyId = "key-2026-01",
                keyPair = keyPair1
            )
            val b = m.toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.createContext("/downgrade.json") { ex ->
            val m = buildSignedManifestJson(
                version = "2025.1",
                versionCode = 2L,
                minApi = 26,
                abis = listOf("arm64-v8a"),
                artifactUrl = "http://127.0.0.1:$port/art.bin",
                payload = goodPayload,
                keyId = "key-2026-01",
                keyPair = keyPair1
            )
            val b = m.toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }

        val store = InMemoryEngineUpdateStore(EngineInstallState(currentVersion = "2026.4", currentVersionCode = 4L))
        val manager = EngineUpdateManager(
            engineWorkDir = workDir,
            stateStore = store,
            trustedKeys = trustedKeys,
            compatibility = EngineCompatibilityProfile(34, listOf("arm64-v8a"))
        )

        val rSig = manager.checkAndApplyUpdate("http://127.0.0.1:$port/bad_sig.json", forceCheck = true)
        assertTrue((rSig as EngineUpdateResult.Rejected).error is DownloadError.EngineUpdateFailed)

        val rSha = manager.checkAndApplyUpdate("http://127.0.0.1:$port/wrong_sha.json", forceCheck = true)
        assertTrue((rSha as EngineUpdateResult.Rejected).error is DownloadError.IntegrityCheckFailed)

        val rAbi = manager.checkAndApplyUpdate("http://127.0.0.1:$port/incompat.json", forceCheck = true)
        assertTrue((rAbi as EngineUpdateResult.Rejected).error is DownloadError.EngineCompatibilityError)

        val rDowngrade = manager.checkAndApplyUpdate("http://127.0.0.1:$port/downgrade.json", forceCheck = true)
        assertTrue((rDowngrade as EngineUpdateResult.Rejected).error is DownloadError.EngineUpdateFailed)
    }

    @Test
    fun postInstallValidationFailure_rollsBackToHealthyBackup() = runBlocking {
        val v1 = "HEALTHY_V1".toByteArray()
        val v2 = "BROKEN_RUNTIME_V2".toByteArray()

        server.createContext("/art1.bin") { ex ->
            ex.sendResponseHeaders(200, v1.size.toLong())
            ex.responseBody.use { it.write(v1) }
        }
        server.createContext("/art2.bin") { ex ->
            ex.sendResponseHeaders(200, v2.size.toLong())
            ex.responseBody.use { it.write(v2) }
        }
        server.createContext("/m1.json") { ex ->
            val b = buildSignedManifestJson("1.0", 1L, 26, listOf("arm64-v8a"), "http://127.0.0.1:$port/art1.bin", v1, "key-2026-01", keyPair1).toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.createContext("/m2.json") { ex ->
            val b = buildSignedManifestJson("2.0", 2L, 26, listOf("arm64-v8a"), "http://127.0.0.1:$port/art2.bin", v2, "key-2026-01", keyPair1).toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }

        val store = InMemoryEngineUpdateStore()
        val manager = EngineUpdateManager(
            engineWorkDir = workDir,
            stateStore = store,
            trustedKeys = trustedKeys,
            compatibility = EngineCompatibilityProfile(34, listOf("arm64-v8a"))
        )

        val install1 = manager.checkAndApplyUpdate("http://127.0.0.1:$port/m1.json", forceCheck = true) { true }
        assertTrue(install1 is EngineUpdateResult.Updated)
        val activeFile = (install1 as EngineUpdateResult.Updated).artifactFile

        val install2 = manager.checkAndApplyUpdate("http://127.0.0.1:$port/m2.json", forceCheck = true) { file ->
            !String(file.readBytes()).contains("BROKEN")
        }
        assertTrue(install2 is EngineUpdateResult.RolledBack)
        assertArrayEquals(v1, activeFile.readBytes())
        assertEquals(1L, store.readState().currentVersionCode)
    }

    private fun buildSignedManifestJson(
        version: String,
        versionCode: Long,
        minApi: Int,
        abis: List<String>,
        artifactUrl: String,
        payload: ByteArray,
        keyId: String,
        keyPair: KeyPair
    ): String {
        val shaHex = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val unsigned = EngineUpdateManifest(
            version = version,
            versionCode = versionCode,
            minAndroidApi = minApi,
            supportedAbis = abis,
            artifactUrl = artifactUrl,
            sha256 = shaHex,
            sizeBytes = payload.size.toLong(),
            keyId = keyId,
            signatureBase64 = ""
        )
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(unsigned.canonicalPayload().toByteArray(Charsets.UTF_8))
        val sigB64 = Base64.getEncoder().encodeToString(sig.sign())
        val abisJson = abis.joinToString(",") { "\"$it\"" }
        return """
            {
              "version": "$version",
              "versionCode": $versionCode,
              "minAndroidApi": $minApi,
              "supportedAbis": [$abisJson],
              "artifactUrl": "$artifactUrl",
              "sha256": "$shaHex",
              "sizeBytes": ${payload.size},
              "keyId": "$keyId",
              "signatureBase64": "$sigB64"
            }
        """.trimIndent()
    }
}
