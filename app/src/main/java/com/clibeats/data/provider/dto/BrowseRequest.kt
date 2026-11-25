@file:Suppress("ForbiddenImport")

package com.clibeats.data.provider.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** InnerTube `browse` request: a page by id (charts, a playlist as `VL<id>`, ...). */
@Serializable
data class BrowseRequest(
    val context: InnerTubeContext,
    val browseId: String? = null,
    val formData: BrowseFormData? = null,
    /** A filter on the page, e.g. a mood chip ("Relax") on the home feed. */
    val params: String? = null,
) {
    companion object {
        private const val CHARTS_BROWSE_ID = "FEmusic_charts"

        /** YouTube Music's Charts page for [countryCode] (ISO 3166 alpha-2, e.g. "IN"). */
        fun charts(countryCode: String): BrowseRequest =
            BrowseRequest(
                context = InnerTubeContext.default(),
                browseId = CHARTS_BROWSE_ID,
                formData = BrowseFormData(selectedValues = listOf(countryCode)),
            )

        fun page(browseId: String): BrowseRequest = BrowseRequest(InnerTubeContext.default(), browseId)

        private const val HOME_BROWSE_ID = "FEmusic_home"

        /** A page reached from a button with [params], e.g. a genre on the Moods & genres page. */
        fun category(
            browseId: String,
            params: String,
        ): BrowseRequest = BrowseRequest(InnerTubeContext.default(), browseId, params = params)

        /** YouTube Music's home feed (for the phone's country), optionally filtered by a mood chip's [params]. */
        fun home(params: String? = null): BrowseRequest {
            val context = InnerTubeContext.default()
            return BrowseRequest(context, HOME_BROWSE_ID, params = params)
        }

        /**
         * The next part of a page (sent with the continuation token in the URL). [visitorData] comes from the
         * first part's response: YouTube only serves the rest of a feed to the same visitor.
         */
        fun continuation(visitorData: String?): BrowseRequest {
            val context = InnerTubeContext.default()
            return BrowseRequest(context.copy(client = context.client.copy(visitorData = visitorData)))
        }
    }
}

@Serializable
data class BrowseFormData(
    val selectedValues: List<String>,
)

@Serializable
data class BrowseResponse(
    val contents: JsonElement? = null,
    /** The rest of a feed, in a continuation response. */
    val continuationContents: JsonElement? = null,
    val responseContext: BrowseResponseContext? = null,
)

@Serializable
data class BrowseResponseContext(
    val visitorData: String? = null,
)
