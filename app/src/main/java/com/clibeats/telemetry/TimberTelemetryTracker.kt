package com.clibeats.telemetry

import android.util.Log
import com.clibeats.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimberTelemetryTracker
    @Inject
    constructor() : TelemetryTracker {
        override fun trackEvent(event: AnalyticsEvent) {
            if (BuildConfig.DEBUG) {
                Log.d("CLIBeatsTelemetry", "event=${event.name} params=${event.params}")
            }
        }
    }
