package com.clibeats.presentation.component

import com.clibeats.util.ARTWORK_FULL_PX as UTIL_FULL_PX
import com.clibeats.util.ARTWORK_THUMB_PX as UTIL_THUMB_PX
import com.clibeats.util.artworkUrlFor as utilArtworkUrlFor

/**
 * Re-exported for backwards compatibility in presentation components.
 */
fun artworkUrlFor(
    url: String?,
    sizePx: Int,
): String? = utilArtworkUrlFor(url, sizePx)

const val ARTWORK_THUMB_PX = UTIL_THUMB_PX
const val ARTWORK_FULL_PX = UTIL_FULL_PX
