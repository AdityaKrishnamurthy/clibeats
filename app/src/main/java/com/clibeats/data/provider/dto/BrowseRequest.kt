@file:Suppress("ForbiddenImport")

package com.clibeats.data.provider.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** InnerTube `browse` request: a page by id (charts, a playlist as `VL<id>`, ...). */
@Serializable
data class BrowseRequest(
    val context: InnerTubeContext,
    val browseId: String,
    val formData: BrowseFormData? = null,
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
    }
}

@Serializable
data class BrowseFormData(
    val selectedValues: List<String>,
)

@Serializable
data class BrowseResponse(
    val contents: JsonElement? = null,
)
