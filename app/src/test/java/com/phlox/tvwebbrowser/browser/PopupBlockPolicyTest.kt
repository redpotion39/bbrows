package com.phlox.tvwebbrowser.browser

import com.phlox.tvwebbrowser.model.HostConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PopupBlockPolicyTest {
    private fun blocked(level: Int?, dialog: Boolean, userGesture: Boolean) =
        PopupBlockPolicy.shouldBlockNewWindow(level, dialog, userGesture)

    /** Expected results for (dialog, userGesture) = (false,false), (false,true), (true,false), (true,true) */
    private fun assertLevel(level: Int?, vararg expected: Boolean) {
        val actual = listOf(
            blocked(level, dialog = false, userGesture = false),
            blocked(level, dialog = false, userGesture = true),
            blocked(level, dialog = true, userGesture = false),
            blocked(level, dialog = true, userGesture = true)
        )
        assertEquals("level $level", expected.toList(), actual)
    }

    @Test
    fun noBlocking() = assertLevel(HostConfig.POPUP_BLOCK_NONE, false, false, false, false)

    @Test
    fun dialogsOnly() = assertLevel(HostConfig.POPUP_BLOCK_DIALOGS, false, false, true, true)

    @Test
    fun dialogsAndAutoOpenedTabs() = assertLevel(HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS, true, false, true, true)

    @Test
    fun everything() = assertLevel(HostConfig.POPUP_BLOCK_NEW_TABS_EVEN_BY_USER_GESTURE, true, true, true, true)

    @Test
    fun hostWithoutSettingUsesDefault() {
        assertEquals(HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS, HostConfig.DEFAULT_BLOCK_POPUPS_VALUE)
        assertLevel(null, true, false, true, true)
    }

    @Test
    fun unknownLevelBlocksEverything() = assertLevel(42, true, true, true, true)

    @Test
    fun crossDomainHijackGuardBlocksForeignSiteWhileVideoPlays() {
        // Same site or subdomain is allowed
        assertEquals(
            false,
            PopupBlockPolicy.shouldBlockCrossDomainHijack(
                popupBlockLevel = null,
                hasActiveVideo = true,
                currentUrl = "https://stream.example.com/watch",
                targetUrl = "https://cdn.example.com/video",
                blockVideoRedirects = true,
                allowedHost = null
            )
        )
        // Different site while video plays is blocked
        assertEquals(
            true,
            PopupBlockPolicy.shouldBlockCrossDomainHijack(
                popupBlockLevel = null,
                hasActiveVideo = true,
                currentUrl = "https://stream.example.com/watch",
                targetUrl = "https://casino-ads.biz/click",
                blockVideoRedirects = true,
                allowedHost = null
            )
        )
        // Allowed once confirmed for that host
        assertEquals(
            false,
            PopupBlockPolicy.shouldBlockCrossDomainHijack(
                popupBlockLevel = null,
                hasActiveVideo = true,
                currentUrl = "https://stream.example.com/watch",
                targetUrl = "https://casino-ads.biz/click",
                blockVideoRedirects = true,
                allowedHost = "casino-ads.biz"
            )
        )
        // Not blocked when no video is active on page
        assertEquals(
            false,
            PopupBlockPolicy.shouldBlockCrossDomainHijack(
                popupBlockLevel = null,
                hasActiveVideo = false,
                currentUrl = "https://stream.example.com/watch",
                targetUrl = "https://casino-ads.biz/click",
                blockVideoRedirects = true,
                allowedHost = null
            )
        )
    }
}
