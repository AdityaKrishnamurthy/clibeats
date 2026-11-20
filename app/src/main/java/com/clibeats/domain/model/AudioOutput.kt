package com.clibeats.domain.model

/** Where music is coming out right now, as shown on Now Playing. */
data class AudioOutput(
    val name: String,
    val kind: Kind,
) {
    enum class Kind { PHONE, WIRED, BLUETOOTH, OTHER }

    companion object {
        val PHONE_SPEAKER = AudioOutput("Phone speaker", Kind.PHONE)
    }
}
