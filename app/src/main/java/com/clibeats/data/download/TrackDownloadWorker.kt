// ForbiddenImport: data-layer self-imports are legitimate
@file:Suppress("ForbiddenImport")

package com.clibeats.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.clibeats.domain.model.Track
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * WorkManager foreground worker that runs one durable track download even if the app leaves the
 * foreground, keeping the system notification in sync with download progress.
 */
class TrackDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun downloadManager(): TrackDownloadManager
    }

    override suspend fun doWork(): Result {
        val track = trackFromInput(inputData) ?: return Result.failure()
        val manager =
            EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java,
            ).downloadManager()

        ensureChannel()
        runCatching { setForeground(createForegroundInfo(track.title, 0)) }

        var ok = false
        coroutineScope {
            val progressJob =
                launch {
                    manager.downloads.collect { map ->
                        val status = map[track.id]
                        if (status is DownloadStatus.Downloading) {
                            runCatching { setForeground(createForegroundInfo(track.title, status.progressPercent)) }
                        }
                    }
                }
            try {
                ok = manager.executeWork(track)
            } finally {
                progressJob.cancel()
            }
        }

        return when {
            ok -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    private fun ensureChannel() {
        val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun createForegroundInfo(
        title: String,
        percent: Int,
    ): ForegroundInfo {
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading track")
                .setContentText(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(PERCENT_MAX, percent.coerceIn(0, PERCENT_MAX), percent == 0)
                .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "clibeats_downloads"
        private const val NOTIFICATION_ID = 4102
        private const val PERCENT_MAX = 100
        private const val MAX_RETRIES = 2

        private const val KEY_ID = "track_id"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ALBUM = "album"
        private const val KEY_DURATION_MS = "duration_ms"
        private const val KEY_ARTWORK_URL = "artwork_url"
        private const val KEY_STREAM_URL = "stream_url"
        private const val KEY_PROVIDER_ID = "provider_id"

        fun toInputData(track: Track): Data =
            Data.Builder()
                .putString(KEY_ID, track.id)
                .putString(KEY_TITLE, track.title)
                .putString(KEY_ARTIST, track.artist)
                .putString(KEY_ALBUM, track.album)
                .putLong(KEY_DURATION_MS, track.durationMs)
                .putString(KEY_ARTWORK_URL, track.artworkUrl)
                .putString(KEY_STREAM_URL, track.streamUrl)
                .putString(KEY_PROVIDER_ID, track.providerId)
                .build()

        fun trackFromInput(data: Data): Track? {
            val id = data.getString(KEY_ID) ?: return null
            return Track(
                id = id,
                title = data.getString(KEY_TITLE).orEmpty(),
                artist = data.getString(KEY_ARTIST).orEmpty(),
                album = data.getString(KEY_ALBUM).orEmpty(),
                durationMs = data.getLong(KEY_DURATION_MS, 0L),
                artworkUrl = data.getString(KEY_ARTWORK_URL),
                streamUrl = data.getString(KEY_STREAM_URL),
                providerId = data.getString(KEY_PROVIDER_ID) ?: "youtube_music",
            )
        }

        fun uniqueWorkName(trackId: String): String = "download_$trackId"
    }
}
