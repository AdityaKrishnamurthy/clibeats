@file:Suppress("ForbiddenImport")

package com.clibeats.data.provider.api

import com.clibeats.data.provider.dto.BrowseRequest
import com.clibeats.data.provider.dto.BrowseResponse
import com.clibeats.data.provider.dto.NextRequest
import com.clibeats.data.provider.dto.PlayerRequest
import com.clibeats.data.provider.dto.PlayerResponse
import com.clibeats.data.provider.dto.SearchRequest
import com.clibeats.data.provider.dto.SearchResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface InnerTubeApi {
    @POST("search")
    suspend fun search(
        @Body body: SearchRequest,
    ): SearchResponse

    @POST("browse")
    suspend fun browse(
        @Body body: BrowseRequest,
    ): BrowseResponse

    /** The next part of a feed; [token] is used as given (YouTube sends it already URL-encoded). */
    @POST("browse")
    suspend fun browseContinuation(
        @Query("ctoken", encoded = true) token: String,
        @Query("continuation", encoded = true) continuation: String,
        @Header("X-Goog-Visitor-Id") visitorData: String?,
        @Body body: BrowseRequest,
        @Query("type") type: String = "next",
    ): BrowseResponse

    @POST("player")
    suspend fun player(
        @Body body: PlayerRequest,
    ): PlayerResponse

    @POST("next")
    suspend fun next(
        @Body body: NextRequest,
    ): JsonElement
}
