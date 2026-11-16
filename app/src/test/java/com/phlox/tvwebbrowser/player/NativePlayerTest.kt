package com.phlox.tvwebbrowser.player

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import com.phlox.tvwebbrowser.webengine.PageVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class NativePlayerTest {
    @Test
    fun normalDeviceLoadControlKeepsLargerBuffersAndBackBuffer() {
        val control = NativePlayer.createLoadControl(lowRam = false)
        assertEquals(30_000L * 1000L, control.getBackBufferDurationUs(PlayerId.UNSET))
        assertTrue(control.retainBackBufferFromKeyframe(PlayerId.UNSET))
    }

    @Test
    fun lowRamDeviceLoadControlUsesConservativeBackBuffer() {
        val control = NativePlayer.createLoadControl(lowRam = true)
        assertEquals(15_000L * 1000L, control.getBackBufferDurationUs(PlayerId.UNSET))
        assertTrue(control.retainBackBufferFromKeyframe(PlayerId.UNSET))
    }

    @Test
    fun mimeTypesMatchVideoKinds() {
        assertEquals(MimeTypes.APPLICATION_M3U8, NativePlayer.mimeType(PageVideo.Kind.HLS))
        assertEquals(MimeTypes.APPLICATION_MPD, NativePlayer.mimeType(PageVideo.Kind.DASH))
        assertEquals(MimeTypes.APPLICATION_MPD, NativePlayer.mimeType(PageVideo.Kind.XML))
        assertNull(NativePlayer.mimeType(PageVideo.Kind.FILE))
    }
}
