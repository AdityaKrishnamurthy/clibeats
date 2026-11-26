package com.clibeats.presentation.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppRouterTest {
    @Test
    fun `tabs preserve independent back stacks`() {
        val router = AppRouter()

        router.navigate(AppRoute.ArtistDetail("M83"))
        assertThat(router.activeTab).isEqualTo(MainTab.HOME)
        assertThat(router.currentRoute).isEqualTo(AppRoute.ArtistDetail("M83"))

        assertThat(router.selectTab(MainTab.LIBRARY)).isFalse()
        assertThat(router.currentRoute).isEqualTo(AppRoute.Library)
        router.navigate(AppRoute.AlbumDetail("Digital Shades", "M83"))
        assertThat(router.currentRoute).isEqualTo(AppRoute.AlbumDetail("Digital Shades", "M83"))

        assertThat(router.selectTab(MainTab.HOME)).isFalse()
        assertThat(router.currentRoute).isEqualTo(AppRoute.ArtistDetail("M83"))
    }

    @Test
    fun `re-tapping active tab pops to root before signaling root retap action`() {
        val router = AppRouter()
        router.navigate(AppRoute.AlbumDetail("Hurry Up", "M83"))

        assertThat(router.selectTab(MainTab.HOME)).isFalse()
        assertThat(router.currentRoute).isEqualTo(AppRoute.Home)
        assertThat(router.isAtTabRoot(MainTab.HOME)).isTrue()

        assertThat(router.selectTab(MainTab.HOME)).isTrue()
    }

    @Test
    fun `popBack unwinds queue overlay, then tab stack, then returns to Home`() {
        val router = AppRouter()
        router.selectTab(MainTab.SEARCH)
        router.navigate(AppRoute.ArtistDetail("Daft Punk"))
        router.navigate(AppRoute.Queue)

        assertThat(router.currentRoute).isEqualTo(AppRoute.Queue)
        assertThat(router.popBack()).isTrue()
        assertThat(router.currentRoute).isEqualTo(AppRoute.ArtistDetail("Daft Punk"))
        assertThat(router.popBack()).isTrue()
        assertThat(router.currentRoute).isEqualTo(AppRoute.Search)
        assertThat(router.popBack()).isTrue()
        assertThat(router.activeTab).isEqualTo(MainTab.HOME)
        assertThat(router.popBack()).isFalse()
    }

    @Test
    fun `settings lives in bottom tabs matching v2 6 1 navigation`() {
        val router = AppRouter()

        assertThat(MainTab.entries).containsExactly(
            MainTab.HOME,
            MainTab.SEARCH,
            MainTab.LIBRARY,
            MainTab.SETTINGS,
        ).inOrder()

        assertThat(router.selectTab(MainTab.SETTINGS)).isFalse()
        assertThat(router.currentRoute).isEqualTo(AppRoute.Settings)
        assertThat(router.popBack()).isTrue()
        assertThat(router.activeTab).isEqualTo(MainTab.HOME)
    }
}
