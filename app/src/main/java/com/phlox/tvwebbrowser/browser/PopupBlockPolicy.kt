package com.phlox.tvwebbrowser.browser

import android.net.Uri
import com.phlox.tvwebbrowser.model.HostConfig

object PopupBlockPolicy {
    /**
     * @param popupBlockLevel one of HostConfig.POPUP_BLOCK_* values, null if not set for the host
     * @param dialog true for js dialogs, false for new windows (tabs)
     */
    fun shouldBlockNewWindow(popupBlockLevel: Int?, dialog: Boolean, userGesture: Boolean): Boolean {
        val level = popupBlockLevel ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE
        return when (level) {
            HostConfig.POPUP_BLOCK_NONE -> false
            HostConfig.POPUP_BLOCK_DIALOGS -> dialog
            HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS -> dialog || !userGesture
            else -> true
        }
    }

    /**
     * Blocks same-tab cross-domain redirects triggered while a video is playing, so click-jacking overlays
     * cannot hijack the tab away from playback.
     */
    fun shouldBlockCrossDomainHijack(
        popupBlockLevel: Int?,
        hasActiveVideo: Boolean,
        currentUrl: String?,
        targetUrl: String,
        blockVideoRedirects: Boolean,
        allowedHost: String?
    ): Boolean {
        if (!blockVideoRedirects || !hasActiveVideo) return false
        val level = popupBlockLevel ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE
        if (level == HostConfig.POPUP_BLOCK_NONE) return false
        val currentHost = hostOf(currentUrl) ?: return false
        val targetHost = hostOf(targetUrl) ?: return false
        if (allowedHost != null && targetHost.equals(allowedHost, ignoreCase = true)) return false
        return !isSameSite(currentHost, targetHost)
    }

    internal fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try { Uri.parse(url) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        return uri.host?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    internal fun isSameSite(hostA: String, hostB: String): Boolean {
        if (hostA == hostB) return true
        val partsA = hostA.split('.')
        val partsB = hostB.split('.')
        if (partsA.size < 2 || partsB.size < 2) return false
        val suffixA = partsA.takeLast(2).joinToString(".")
        val suffixB = partsB.takeLast(2).joinToString(".")
        return suffixA == suffixB
    }
}
