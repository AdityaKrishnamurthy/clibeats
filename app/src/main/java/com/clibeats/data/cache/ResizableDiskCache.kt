@file:Suppress("ForbiddenImport")
@file:OptIn(ExperimentalCoilApi::class)

package com.clibeats.data.cache

import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import okio.FileSystem
import okio.Path
import java.io.File

/**
 * Coil disk cache whose size limit can change while the app runs.
 *
 * Coil fixes a disk cache's limit when it is built, so [resize] swaps in a new cache over the same
 * directory (entries are kept) and makes it trim to the new limit right away.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class ResizableDiskCache(
    private val dir: File,
    maxBytes: Long,
) : DiskCache {
    @Volatile
    private var delegate: DiskCache = build(maxBytes)

    private fun build(maxBytes: Long): DiskCache = DiskCache.Builder().directory(dir).maxSizeBytes(maxBytes).build()

    /** Applies a new limit now; shrinking evicts the oldest covers until the cache fits. */
    @Synchronized
    fun resize(maxBytes: Long) {
        if (maxBytes == delegate.maxSize) return
        delegate = build(maxBytes)
        // Coil only trims after a write, so commit (and drop) a tiny marker entry to start the trim.
        runCatching {
            delegate.openEditor(TRIM_KEY)?.let { editor ->
                delegate.fileSystem.write(editor.data) {}
                delegate.fileSystem.write(editor.metadata) {}
                editor.commit()
            }
            delegate.remove(TRIM_KEY)
        }
    }

    override val size: Long get() = delegate.size
    override val maxSize: Long get() = delegate.maxSize
    override val directory: Path get() = delegate.directory
    override val fileSystem: FileSystem get() = delegate.fileSystem

    override fun openSnapshot(key: String): DiskCache.Snapshot? = delegate.openSnapshot(key)

    override fun get(key: String): DiskCache.Snapshot? = delegate.openSnapshot(key)

    override fun openEditor(key: String): DiskCache.Editor? = delegate.openEditor(key)

    override fun edit(key: String): DiskCache.Editor? = delegate.openEditor(key)

    override fun remove(key: String): Boolean = delegate.remove(key)

    override fun clear() = delegate.clear()

    private companion object {
        const val TRIM_KEY = "clibeats_trim_marker"
    }
}
