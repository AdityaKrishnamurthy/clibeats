package com.clibeats.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkUrlTest {
    @Test
    fun `googleusercontent size spec is rewritten`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj",
            artworkUrlFor("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj", 1080),
        )
        assertEquals(
            "https://yt3.ggpht.com/xyz=w300-h300-l90-rj",
            artworkUrlFor("https://yt3.ggpht.com/xyz=s88-c-k-c0x00ffffff-no-rj", 300),
        )
    }

    @Test
    fun `googleusercontent without size spec is unchanged`() {
        val url = "https://lh3.googleusercontent.com/abc"
        assertEquals(url, artworkUrlFor(url, 1080))
    }

    @Test
    fun `ytimg picks hq for small and maxres for large`() {
        val url = "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg?sqp=abc"
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", artworkUrlFor(url, 120))
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", artworkUrlFor(url, 1080))
    }

    @Test
    fun `other hosts and null pass through`() {
        assertEquals("https://example.com/a.jpg", artworkUrlFor("https://example.com/a.jpg", 1080))
        assertNull(artworkUrlFor(null, 100))
    }
}
