package com.clibeats.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Pure decision logic for the updater, kept free of Android types so it is unit-tested. */
object UpdateRules {
    const val CHECKSUMS_FILE = "SHA256SUMS.txt"
    private const val SHA256_HEX_LENGTH = 64

    private val json = Json { ignoreUnknownKeys = true }

    /** "v2.1.0" / "2.1.0-beta" / "2.1" -> [2, 1, 0]; missing or non-numeric parts count as 0. */
    fun parseVersion(version: String): List<Int> =
        version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { it.trim().toIntOrNull() ?: 0 }

    /** True only when [remote] is strictly newer than [local]. */
    fun isNewer(
        remote: String,
        local: String,
    ): Boolean {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val diff = r.getOrElse(i) { 0 } - l.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    /** An automatic check is due when [frequency] allows it and its interval has passed (or the clock went back). */
    fun isCheckDue(
        now: Long,
        lastCheckAt: Long,
        frequency: UpdateFrequency,
    ): Boolean =
        when {
            frequency == UpdateFrequency.OFF -> false
            lastCheckAt <= 0L || lastCheckAt > now -> true
            else -> now - lastCheckAt >= frequency.intervalMs
        }

    /** A kept update APK is a leftover once the installed app is at least that version (the update was installed). */
    fun isLeftoverDownload(
        downloadVersion: String?,
        installedVersion: String,
    ): Boolean = downloadVersion != null && !isNewer(downloadVersion, installedVersion)

    fun isSnoozed(
        now: Long,
        snoozeUntil: Long,
    ): Boolean = now < snoozeUntil

    /**
     * Finds the hash for [fileName] in a `sha256sum`-style file ("<hash>  <name>" or "<hash> *<name>").
     * Returns it lowercased, or null when the file isn't listed or the hash is malformed.
     */
    fun sha256For(
        checksums: String,
        fileName: String,
    ): String? =
        checksums.lineSequence().firstNotNullOfOrNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) return@firstNotNullOfOrNull null
            val (hash, name) = parts
            val isMatch = name.removePrefix("*").trim() == fileName
            val isHash = hash.length == SHA256_HEX_LENGTH && hash.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            if (isMatch && isHash) hash.lowercase() else null
        }

    /** Parses GitHub's "latest release" JSON. Null when it has no APK asset or can't be read. */
    @Suppress("ReturnCount")
    fun parseRelease(body: String): ReleaseInfo? {
        val release = runCatching { json.decodeFromString<GitHubRelease>(body) }.getOrNull() ?: return null
        if (release.draft || release.prerelease) return null
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val apk = apks.firstOrNull { it.name.startsWith("CLIBeats", ignoreCase = true) } ?: apks.firstOrNull()
        apk ?: return null
        val checksums = release.assets.firstOrNull { it.name.equals(CHECKSUMS_FILE, ignoreCase = true) }
        return ReleaseInfo(
            version = parseVersion(release.tagName).joinToString("."),
            apkName = apk.name,
            apkUrl = apk.downloadUrl,
            checksumsUrl = checksums?.downloadUrl,
            pageUrl = release.pageUrl,
        )
    }

    fun encode(release: ReleaseInfo): String = json.encodeToString(ReleaseInfo.serializer(), release)

    fun decode(value: String?): ReleaseInfo? =
        value?.let { runCatching { json.decodeFromString(ReleaseInfo.serializer(), it) }.getOrNull() }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val pageUrl: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )
}
