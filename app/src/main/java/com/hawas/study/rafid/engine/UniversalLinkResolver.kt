package com.hawas.study.rafid.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

data class ResolvedLinkResult(
    val originalUrl: String,
    val resolvedUrl: String,
    val redirectHops: Int
)

class UniversalLinkResolver(
    private val httpClient: OkHttpClient = defaultClient()
) {
    companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }

    fun resolve(rawInput: String, cookiesHeader: String? = null, maxHops: Int = 8): ResolvedLinkResult {
        val extracted = AdAndTrackerShield.extractFirstUrl(rawInput)
            ?: rawInput.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }

        var currentUrl = normalizeShortDomain(extracted)
        var hops = 0

        val noRedirectClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        while (hops < maxHops) {
            val reqBuilder = Request.Builder()
                .url(currentUrl)
                .get()
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")

            if (!cookiesHeader.isNullOrBlank()) {
                reqBuilder.header("Cookie", cookiesHeader)
            }

            val nextLocation = try {
                noRedirectClient.newCall(reqBuilder.build()).execute().use { response ->
                    val code = response.code
                    if (code in 300..399) {
                        response.header("Location")
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }

            if (nextLocation.isNullOrBlank()) break
            val resolvedNext = try {
                URI(currentUrl).resolve(nextLocation).toString()
            } catch (_: Exception) {
                nextLocation
            }
            if (resolvedNext == currentUrl) break
            currentUrl = resolvedNext
            hops++
        }

        return ResolvedLinkResult(
            originalUrl = extracted,
            resolvedUrl = currentUrl,
            redirectHops = hops
        )
    }

    private fun normalizeShortDomain(url: String): String {
        return url.trim()
    }
}
