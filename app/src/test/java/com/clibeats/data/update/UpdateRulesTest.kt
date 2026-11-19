package com.clibeats.data.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateRulesTest {
    private val hashA = "baf06bf5cfc024438c6133874d0924101011043a4546ac6fa057a7ef1f703d96"

    @Test
    fun `newer versions are detected across major minor and patch`() {
        assertThat(UpdateRules.isNewer("2.1.0", "2.0.0")).isTrue()
        assertThat(UpdateRules.isNewer("v2.0.1", "2.0.0")).isTrue()
        assertThat(UpdateRules.isNewer("3.0.0", "2.9.9")).isTrue()
        assertThat(UpdateRules.isNewer("2.10.0", "2.9.0")).isTrue()
    }

    @Test
    fun `same or older versions are not updates`() {
        assertThat(UpdateRules.isNewer("2.1.0", "2.1.0")).isFalse()
        assertThat(UpdateRules.isNewer("v2.0.0", "2.1.0")).isFalse()
        assertThat(UpdateRules.isNewer("2.1", "2.1.0")).isFalse()
    }

    @Test
    fun `version suffixes and prefixes are ignored`() {
        assertThat(UpdateRules.parseVersion("V2.1.0-beta+5")).containsExactly(2, 1, 0).inOrder()
        assertThat(UpdateRules.isNewer("2.1.0-rc1", "2.0.0")).isTrue()
    }

    @Test
    fun `check is due only after the chosen interval`() {
        val now = 10 * DAY_MS
        assertThat(UpdateRules.isCheckDue(now, now - DAY_MS + 1, UpdateFrequency.DAILY)).isFalse()
        assertThat(UpdateRules.isCheckDue(now, now - DAY_MS, UpdateFrequency.DAILY)).isTrue()
        assertThat(UpdateRules.isCheckDue(now, now - 3 * DAY_MS, UpdateFrequency.WEEKLY)).isFalse()
        assertThat(UpdateRules.isCheckDue(now, now - 7 * DAY_MS, UpdateFrequency.WEEKLY)).isTrue()
    }

    @Test
    fun `check is due when never checked or the clock went back, never when off`() {
        val now = 10 * DAY_MS
        assertThat(UpdateRules.isCheckDue(now, 0L, UpdateFrequency.WEEKLY)).isTrue()
        assertThat(UpdateRules.isCheckDue(now, now + DAY_MS, UpdateFrequency.DAILY)).isTrue()
        assertThat(UpdateRules.isCheckDue(now, 0L, UpdateFrequency.OFF)).isFalse()
    }

    @Test
    fun `later snoozes until the stored time`() {
        assertThat(UpdateRules.isSnoozed(now = 100, snoozeUntil = 200)).isTrue()
        assertThat(UpdateRules.isSnoozed(now = 200, snoozeUntil = 200)).isFalse()
    }

    @Test
    fun `checksum is found for the exact file name in sha256sum format`() {
        val sums = "${hashA.uppercase()}  CLIBeats-v2.1.0.apk\n${"0".repeat(64)} *other.apk\n"
        assertThat(UpdateRules.sha256For(sums, "CLIBeats-v2.1.0.apk")).isEqualTo(hashA)
        assertThat(UpdateRules.sha256For(sums, "other.apk")).isEqualTo("0".repeat(64))
    }

    @Test
    fun `missing file or malformed hash gives no checksum`() {
        assertThat(UpdateRules.sha256For("$hashA  CLIBeats-v2.0.0.apk", "CLIBeats-v2.1.0.apk")).isNull()
        assertThat(UpdateRules.sha256For("nothex  CLIBeats-v2.1.0.apk", "CLIBeats-v2.1.0.apk")).isNull()
        assertThat(UpdateRules.sha256For("", "CLIBeats-v2.1.0.apk")).isNull()
    }

    @Test
    fun `release json is parsed with apk and checksum assets`() {
        val release = UpdateRules.parseRelease(releaseJson())!!
        assertThat(release.version).isEqualTo("2.1.0")
        assertThat(release.apkName).isEqualTo("CLIBeats-v2.1.0.apk")
        assertThat(release.apkUrl).isEqualTo("https://example.com/CLIBeats-v2.1.0.apk")
        assertThat(release.checksumsUrl).isEqualTo("https://example.com/SHA256SUMS.txt")
        assertThat(release.pageUrl).isEqualTo("https://github.com/o/r/releases/tag/v2.1.0")
    }

    @Test
    fun `release without apk, drafts and garbage are ignored`() {
        assertThat(UpdateRules.parseRelease(releaseJson(assets = "[]"))).isNull()
        assertThat(UpdateRules.parseRelease(releaseJson(prerelease = true))).isNull()
        assertThat(UpdateRules.parseRelease("not json")).isNull()
    }

    @Test
    fun `cached release survives an encode and decode round trip`() {
        val release = UpdateRules.parseRelease(releaseJson())!!
        assertThat(UpdateRules.decode(UpdateRules.encode(release))).isEqualTo(release)
        assertThat(UpdateRules.decode("{broken")).isNull()
        assertThat(UpdateRules.decode(null)).isNull()
    }

    companion object {
        fun releaseJson(
            tag: String = "v2.1.0",
            prerelease: Boolean = false,
            assets: String =
                """[
                  {"name":"CLIBeats-v2.1.0.apk","browser_download_url":"https://example.com/CLIBeats-v2.1.0.apk","size":1},
                  {"name":"SHA256SUMS.txt","browser_download_url":"https://example.com/SHA256SUMS.txt","size":1}
                ]""",
        ): String =
            """{"tag_name":"$tag","html_url":"https://github.com/o/r/releases/tag/$tag","draft":false,
               "prerelease":$prerelease,"body":"notes","assets":$assets}"""
    }
}
