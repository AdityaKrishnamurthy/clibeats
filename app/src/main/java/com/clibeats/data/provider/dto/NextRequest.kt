@file:Suppress("ForbiddenImport")

package com.clibeats.data.provider.dto

import kotlinx.serialization.Serializable

@Serializable
data class NextRequest(
    val context: InnerTubeContext,
    val videoId: String? = null,
    val playlistId: String? = null,
    /** The next page of a radio already started (from the previous response). */
    val continuation: String? = null,
) {
    companion object {
        fun radio(videoId: String): NextRequest =
            NextRequest(
                context = InnerTubeContext.default(),
                videoId = videoId,
                playlistId = "RDAMVM$videoId",
            )

        fun radioPage(continuation: String): NextRequest =
            NextRequest(
                context = InnerTubeContext.default(),
                videoId = null,
                playlistId = null,
                continuation = continuation,
            )
    }
}
