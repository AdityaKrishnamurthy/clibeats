package com.clibeats

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CLIBeatsApp :
    Application(),
    ImageLoaderFactory {
    // Use the tuned Hilt ImageLoader (memory + disk cache) for every AsyncImage.
    @Inject lateinit var imageLoader: dagger.Lazy<ImageLoader>

    override fun newImageLoader(): ImageLoader = imageLoader.get()
}
