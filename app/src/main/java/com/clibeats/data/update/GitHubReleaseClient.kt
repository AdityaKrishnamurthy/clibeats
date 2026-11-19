package com.clibeats.data.update

import com.clibeats.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the GitHub Releases API without a token (60 requests/hour per IP).
 * Sends the last ETag so an unchanged release answers 304, which GitHub doesn't count against that limit.
 */
@Singleton
class GitHubReleaseClient
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        /** Overridden in tests to point at a local server. */
        internal var latestReleaseUrl: String = LATEST_RELEASE_URL

        sealed interface Result {
            /** The release hasn't changed since [etag] was stored. */
            data object NotModified : Result

            /** [release] is null when the repo has no usable release. */
            data class Found(
                val release: ReleaseInfo?,
                val etag: String?,
            ) : Result

            data class Failed(
                val message: String,
            ) : Result
        }

        suspend fun fetchLatest(etag: String?): Result =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url(latestReleaseUrl)
                            .header("Accept", "application/vnd.github+json")
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .header("User-Agent", USER_AGENT)
                            .apply { if (!etag.isNullOrBlank()) header("If-None-Match", etag) }
                            .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        when {
                            response.code == HTTP_NOT_MODIFIED -> Result.NotModified
                            response.code == HTTP_NOT_FOUND -> Result.Found(null, null)
                            !response.isSuccessful -> Result.Failed("GitHub returned ${response.code}")
                            else ->
                                Result.Found(
                                    release = UpdateRules.parseRelease(response.body?.string().orEmpty()),
                                    etag = response.header("ETag"),
                                )
                        }
                    }
                }.getOrElse { e -> Result.Failed(e.message ?: "No connection") }
            }

        /** Downloads a small text file such as `SHA256SUMS.txt`; null on any failure. */
        suspend fun fetchText(url: String): String? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                }.getOrNull()
            }

        private companion object {
            const val LATEST_RELEASE_URL = "https://api.github.com/repos/Omprakash-p06/clibeats/releases/latest"
            const val HTTP_NOT_MODIFIED = 304
            const val HTTP_NOT_FOUND = 404
            val USER_AGENT = "CLIBeats/${BuildConfig.VERSION_NAME} (Android)"
        }
    }
