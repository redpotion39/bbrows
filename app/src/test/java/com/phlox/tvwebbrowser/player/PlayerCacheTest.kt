package com.phlox.tvwebbrowser.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.phlox.tvwebbrowser.webengine.PageVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class PlayerCacheTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun cacheBudgetRespectsFreeSpaceBounds() {
        assertEquals(0L, PlayerCache.computeCacheBytes(50L * 1024 * 1024))
        assertEquals(32L * 1024 * 1024, PlayerCache.computeCacheBytes(100L * 1024 * 1024))
        assertEquals(100L * 1024 * 1024, PlayerCache.computeCacheBytes(1000L * 1024 * 1024))
        assertEquals(150L * 1024 * 1024, PlayerCache.computeCacheBytes(10_000L * 1024 * 1024))
    }

    @Test
    fun shouldCacheSkipsManifestsAndNonHttpSchemes() {
        val hls = PageVideo("https://cdn.example.com/live/stream", PageVideo.Kind.HLS)
        assertFalse(PlayerCache.shouldCache(Uri.parse("https://cdn.example.com/live/stream"), hls))
        assertFalse(PlayerCache.shouldCache(Uri.parse("https://cdn.example.com/live/chunklist.m3u8?token=1"), hls))
        assertFalse(PlayerCache.shouldCache(Uri.parse("https://cdn.example.com/dash/manifest.mpd"), hls))
        assertFalse(PlayerCache.shouldCache(Uri.parse("https://cdn.example.com/dash/manifest.xml"), hls))
        assertTrue(PlayerCache.shouldCache(Uri.parse("https://cdn.example.com/live/seg-001.ts"), hls))
        assertTrue(PlayerCache.shouldCache(Uri.parse("https://cdn.example.com/dash/seg-001.m4s"), hls))

        val file = PageVideo("https://cdn.example.com/movie.mp4", PageVideo.Kind.FILE)
        assertTrue(PlayerCache.shouldCache(Uri.parse("https://cdn.example.com/movie.mp4"), file))
        assertFalse(PlayerCache.shouldCache(Uri.parse("file:///sdcard/movie.mp4"), file))
    }

    @Test
    fun incognitoBypassesDiskCache() {
        val upstream: DataSource.Factory = DefaultDataSource.Factory(context)
        val file = PageVideo("https://cdn.example.com/movie.mp4", PageVideo.Kind.FILE)
        val wrapped = PlayerCache.dataSourceFactory(context, upstream, file, incognito = true)
        assertSame(upstream, wrapped)
    }

    @Test
    fun clearRemovesCachedFiles() {
        val dir = File(context.cacheDir, "player_cache").apply { mkdirs() }
        val dummy = File(dir, "dummy.exo").apply { writeText("data") }
        assertTrue(dummy.exists())
        PlayerCache.clear(context)
        assertFalse(dummy.exists())
    }
}
