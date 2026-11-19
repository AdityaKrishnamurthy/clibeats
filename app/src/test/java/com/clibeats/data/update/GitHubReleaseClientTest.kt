package com.clibeats.data.update

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class GitHubReleaseClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitHubReleaseClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitHubReleaseClient(OkHttpClient())
        client.latestReleaseUrl = server.url("/releases/latest").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `found release returns parsed info and etag`() =
        runTest {
            server.enqueue(MockResponse().setBody(UpdateRulesTest.releaseJson()).setHeader("ETag", "\"abc\""))

            val result = client.fetchLatest(etag = null) as GitHubReleaseClient.Result.Found

            assertThat(result.release?.version).isEqualTo("2.1.0")
            assertThat(result.etag).isEqualTo("\"abc\"")
            val request = server.takeRequest()
            assertThat(request.getHeader("If-None-Match")).isNull()
            assertThat(request.getHeader("Accept")).isEqualTo("application/vnd.github+json")
        }

    @Test
    fun `stored etag is sent and 304 means not modified`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(304))

            val result = client.fetchLatest(etag = "\"abc\"")

            assertThat(result).isEqualTo(GitHubReleaseClient.Result.NotModified)
            assertThat(server.takeRequest().getHeader("If-None-Match")).isEqualTo("\"abc\"")
        }

    @Test
    fun `no releases yet is a found result without a release`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))

            assertThat(client.fetchLatest(null)).isEqualTo(GitHubReleaseClient.Result.Found(null, null))
        }

    @Test
    fun `rate limit and server errors are failures`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403))

            assertThat(client.fetchLatest(null)).isInstanceOf(GitHubReleaseClient.Result.Failed::class.java)
        }

    @Test
    fun `fetchText returns body or null on error`() =
        runTest {
            server.enqueue(MockResponse().setBody("hash  file.apk"))
            server.enqueue(MockResponse().setResponseCode(500))

            assertThat(client.fetchText(server.url("/sums").toString())).isEqualTo("hash  file.apk")
            assertThat(client.fetchText(server.url("/sums").toString())).isNull()
        }
}
