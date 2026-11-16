package com.phlox.tvwebbrowser.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.phlox.tvwebbrowser.webengine.PageVideo
import java.io.File

/**
 * Bounded disk cache for the built-in video player ([NativePlayer]), backed by Media3 [SimpleCache].
 *
 * Media segments and progressive video files are cached so seeking and short network stalls do not re-fetch
 * already downloaded data, while stream manifests (.m3u8, .mpd) and incognito sessions bypass disk caching.
 */
@OptIn(UnstableApi::class)
object PlayerCache {
    private const val CACHE_DIR_NAME = "player_cache"
    private const val MIN_FREE_SPACE_BYTES = 64L * 1024 * 1024
    private const val MIN_CACHE_BYTES = 32L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 150L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null
    private var dbProvider: StandaloneDatabaseProvider? = null

    /**
     * Wraps [upstreamFactory] with a bounded disk cache for [video], unless [incognito] is enabled or free
     * storage is too low.
     */
    fun dataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory,
        video: PageVideo,
        incognito: Boolean
    ): DataSource.Factory {
        if (incognito) return upstreamFactory
        val simpleCache = getOrCreate(context.applicationContext) ?: return upstreamFactory
        val cachedFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DataSource.Factory {
            SelectiveCacheDataSource(cachedFactory.createDataSource(), upstreamFactory.createDataSource(), video)
        }
    }

    /** Clears all cached media segments and files without disrupting an open player instance. */
    fun clear(context: Context) {
        synchronized(this) {
            val active = cache
            if (active != null) {
                for (key in active.keys.toList()) {
                    try {
                        active.removeResource(key)
                    } catch (_: Exception) {
                    }
                }
            } else {
                File(context.applicationContext.cacheDir, CACHE_DIR_NAME).deleteRecursively()
            }
        }
    }

    internal fun computeCacheBytes(usableSpaceBytes: Long): Long {
        if (usableSpaceBytes < MIN_FREE_SPACE_BYTES) return 0L
        return (usableSpaceBytes / 10L).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
    }

    internal fun shouldCache(uri: Uri, video: PageVideo): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        // Stream manifests (including live/dynamic playlists and rotating CDN tokens) must always be fetched fresh.
        if (video.kind != PageVideo.Kind.FILE && uri.toString() == video.url) return false
        val path = uri.path?.lowercase() ?: ""
        if (path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".xml") || path.endsWith(".ism/manifest")) {
            return false
        }
        return true
    }

    private fun getOrCreate(appContext: Context): SimpleCache? {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val dir = File(appContext.cacheDir, CACHE_DIR_NAME)
            if (!dir.exists() && !dir.mkdirs()) return null
            val maxBytes = computeCacheBytes(dir.usableSpace)
            if (maxBytes <= 0L) return null
            return try {
                val db = dbProvider ?: StandaloneDatabaseProvider(appContext).also { dbProvider = it }
                SimpleCache(dir, LeastRecentlyUsedCacheEvictor(maxBytes), db).also { cache = it }
            } catch (_: Exception) {
                null
            }
        }
    }

    private class SelectiveCacheDataSource(
        private val cached: DataSource,
        private val upstream: DataSource,
        private val video: PageVideo
    ) : DataSource {
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            cached.addTransferListener(transferListener)
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val target = if (shouldCache(dataSpec.uri, video)) cached else upstream
            active = target
            return target.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return (active ?: return C.RESULT_END_OF_INPUT).read(buffer, offset, length)
        }

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun close() {
            val target = active
            active = null
            target?.close()
        }
    }
}
