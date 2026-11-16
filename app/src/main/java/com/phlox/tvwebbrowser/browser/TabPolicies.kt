package com.phlox.tvwebbrowser.browser

import android.os.SystemClock
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.model.BrowserTab
import com.phlox.tvwebbrowser.webengine.EnginePolicies
import com.phlox.tvwebbrowser.webengine.mediaPlayback

/**
 * Content blocking decisions for the engine of one tab (requests are checked by the global
 * [com.phlox.tvwebbrowser.webengine.WebEngineFactory.adBlocker]).
 */
class TabPolicies(
    private val tab: BrowserTab,
    private val controller: BrowserController,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : EnginePolicies {
    private var allowedRedirectHost: String? = null
    private var lastBlockedRedirectHost: String? = null
    private var lastBlockedRedirectAt: Long = 0L

    /** The setting of the host of this tab's page; asked on the engine's threads */
    fun isAdBlockingEnabled(): Boolean = controller.isAdBlockEnabled(tab)

    override fun isDialogsBlockingEnabled(): Boolean {
        if (tab.url == Config.HOME_PAGE_URL) return false
        return shouldBlockNewWindow(dialog = true, userGesture = false)
    }

    override fun shouldBlockNewWindow(dialog: Boolean, userGesture: Boolean): Boolean {
        val hostConfig = controller.findHostConfig(tab)
        return PopupBlockPolicy.shouldBlockNewWindow(hostConfig?.popupBlockLevel, dialog, userGesture)
    }

    override fun shouldBlockHijackRedirect(targetUrl: String): Boolean {
        if (tab.url == Config.HOME_PAGE_URL) return false
        val hostConfig = controller.findHostConfig(tab)
        val hasActiveVideo = tab.isWebEngineCreated && tab.webEngine.mediaPlayback?.mediaState?.value?.playing == true
        val blocked = PopupBlockPolicy.shouldBlockCrossDomainHijack(
            popupBlockLevel = hostConfig?.popupBlockLevel,
            hasActiveVideo = hasActiveVideo,
            currentUrl = tab.url,
            targetUrl = targetUrl,
            blockVideoRedirects = controller.isVideoRedirectBlockingEnabled(),
            allowedHost = allowedRedirectHost
        )
        if (!blocked) return false
        val targetHost = PopupBlockPolicy.hostOf(targetUrl) ?: return false
        val currentTime = now()
        if (targetHost == lastBlockedRedirectHost && currentTime - lastBlockedRedirectAt <= CONFIRM_WINDOW_MS) {
            allowedRedirectHost = targetHost
            lastBlockedRedirectHost = null
            lastBlockedRedirectAt = 0L
            return false
        }
        lastBlockedRedirectHost = targetHost
        lastBlockedRedirectAt = currentTime
        controller.onRedirectBlocked(tab, targetHost)
        return true
    }

    fun resetRedirectGuard() {
        allowedRedirectHost = null
        lastBlockedRedirectHost = null
        lastBlockedRedirectAt = 0L
    }

    override fun isDesktopView(url: String): Boolean = controller.isDesktopView(url)

    companion object {
        private const val CONFIRM_WINDOW_MS = 10_000L
    }
}
