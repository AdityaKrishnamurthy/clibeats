package com.clibeats

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.clibeats.data.backup.LibrarySnapshotManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CLIBeatsApp :
    Application(),
    ImageLoaderFactory {
    // Use the tuned Hilt ImageLoader (memory + disk cache) for every AsyncImage.
    @Inject lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject lateinit var snapshotManager: LibrarySnapshotManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Daily, silent safety net so an uninstall/reinstall doesn't lose the library.
        appScope.launch { runCatching { snapshotManager.snapshotIfDue() } }
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()
}
