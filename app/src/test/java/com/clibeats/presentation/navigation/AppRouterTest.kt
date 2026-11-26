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
        router.navigate(AppRoute.PlaylistDetail("p1", "Mix"))
        assertThat(router.currentRoute).isEqualTo(AppRoute.PlaylistDetail("p1", "Mix"))

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
    fun `popBack unwinds utility screens, then tab stack, then returns to Home`() {
        val router = AppRouter()
        router.selectTab(MainTab.SEARCH)
        router.navigate(AppRoute.ArtistDetail("Daft Punk"))
        router.navigate(AppRoute.Queue)
        router.navigate(AppRoute.Settings)

        assertThat(router.currentRoute).isEqualTo(AppRoute.Settings)
        assertThat(router.popBack()).isTrue()
        assertThat(router.currentRoute).isEqualTo(AppRoute.Queue)
        assertThat(router.popBack()).isTrue()
        assertThat(router.currentRoute).isEqualTo(AppRoute.ArtistDetail("Daft Punk"))
        assertThat(router.popBack()).isTrue()
        assertThat(router.currentRoute).isEqualTo(AppRoute.Search)
        assertThat(router.popBack()).isTrue()
        assertThat(router.activeTab).isEqualTo(MainTab.HOME)
        assertThat(router.popBack()).isFalse()
    }
}
