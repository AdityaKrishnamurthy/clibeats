package com.clibeats.presentation.component

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Short confirmations ("Added to queue") shown for a moment in the line above the player bar. */
@Singleton
class Notices
    @Inject
    constructor() {
        private val _messages = MutableSharedFlow<String>(extraBufferCapacity = BUFFER)
        val messages: SharedFlow<String> = _messages

        fun show(text: String) {
            _messages.tryEmit(text)
        }

        private companion object {
            const val BUFFER = 8
        }
    }
