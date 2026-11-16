package com.phlox.tvwebbrowser.player

import android.content.Context
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.webengine.PageVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackProgressStoreTest {
    private lateinit var store: PlaybackProgressStore

    @Before
    fun setUp() {
        val prefs = AppContext.get().getSharedPreferences("playback_progress_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = PlaybackProgressStore(prefs)
    }

    @Test
    fun savesAndRestoresPositionForLongVideos() {
        val v1 = PageVideo(url = "https://cdn.example.com/v1.m3u8", kind = PageVideo.Kind.HLS, pageUrl = "https://example.com/movie#frag")
        val v2 = PageVideo(url = "https://cdn.example.com/v2.m3u8", kind = PageVideo.Kind.HLS, pageUrl = "https://example.com/movie")
        store.save(v1, positionSeconds = 120.0, durationSeconds = 600.0, live = false, incognito = false)
        assertEquals(120.0, store.getPosition(v2)!!, 0.01)
    }

    @Test
    fun clearsPositionWhenFinishedOrNearEnd() {
        val video = PageVideo(url = "https://cdn.example.com/ep.mp4", kind = PageVideo.Kind.FILE, pageUrl = "https://example.com/episode")
        store.save(video, positionSeconds = 180.0, durationSeconds = 600.0, live = false, incognito = false)
        store.save(video, positionSeconds = 590.0, durationSeconds = 600.0, live = false, incognito = false)
        assertNull(store.getPosition(video))
    }

    @Test
    fun skipsIncognitoAndShortClips() {
        val video = PageVideo(url = "https://cdn.example.com/clip.mp4", kind = PageVideo.Kind.FILE, pageUrl = "https://example.com/clip")
        store.save(video, positionSeconds = 30.0, durationSeconds = 45.0, live = false, incognito = false)
        assertNull(store.getPosition(video))

        store.save(video, positionSeconds = 120.0, durationSeconds = 600.0, live = false, incognito = true)
        assertNull(store.getPosition(video))
    }
}
