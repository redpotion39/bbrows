package com.phlox.tvwebbrowser.browser.adblock

import android.net.Uri
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.adblock.MatchResult
import com.phlox.tvwebbrowser.adblock.ResourceType
import com.phlox.tvwebbrowser.data.HostConfigRepository
import com.phlox.tvwebbrowser.webengine.EngineAdBlocker
import com.phlox.tvwebbrowser.webengine.PageHidingStyles

/**
 * What the engines ask ([com.phlox.tvwebbrowser.webengine.WebEngineFactory.adBlocker]): the engine of the
 * [service], unless ads are shown on the site of the tab (its own setting, else the global one).
 * The repository is given once: getting it from the container takes a lock, and this runs for every request.
 */
class AppEngineAdBlocker(
    private val service: AdBlockService,
    private val hostConfigs: HostConfigRepository,
    private val config: Config,
    private val zappedCss: (String) -> String = { "" },
) : EngineAdBlocker {

    override fun shouldBlock(url: Uri, type: String?, acceptHeader: String?, isMainFrame: Boolean, documentUrl: Uri?,
                             topUrl: Uri?): Boolean {
        val page = if (isMainFrame) url else pageOf(documentUrl, topUrl)
        if (!isMainFrame && isLiteModeOn(topUrl ?: documentUrl) && isExternalFontRequest(url, type)) {
            return true
        }
        val engine = service.engine ?: return false
        if (!isEnabledOn(if (isMainFrame) url else topUrl ?: documentUrl)) return false
        val host = url.host ?: return false
        val urlString = url.toString()
        val types = if (type != null) ResourceType.fromWebExtension(type) else
            ResourceType.guessFromAccept(acceptHeader, urlString, isMainFrame)
        //a page is first-party to itself
        return engine.match(urlString, host, page?.toString(), page?.host, types) == MatchResult.BLOCK
    }

    /**
     * The page whose filters apply in a document: the document, or for a frame of about:blank, about:srcdoc,
     * data: or blob: the page of the tab that made it (its ads would else be of no site, and no third-party or
     * site filter would apply)
     */
    private fun pageOf(documentUrl: Uri?, topUrl: Uri?): Uri? =
        if (documentUrl != null && isWebUrl(documentUrl)) documentUrl else topUrl ?: documentUrl

    override fun hidingStyles(documentUrl: Uri, topUrl: Uri?): PageHidingStyles {
        val page = pageOf(documentUrl, topUrl)?.takeIf(::isWebUrl) ?: return PageHidingStyles.NONE
        val host = page.host ?: return PageHidingStyles.NONE
        val extraCss = buildString {
            append(zappedCss(host))
            if (isLiteModeOn(topUrl ?: documentUrl)) append(LITE_MODE_CSS)
        }
        val engine = service.engine
        if (engine == null || !isEnabledOn(topUrl ?: documentUrl)) {
            return if (extraCss.isNotEmpty()) PageHidingStyles(extraCss, false) else PageHidingStyles.NONE
        }
        val styles = engine.hidingStyles(page.toString(), host)
        return PageHidingStyles(styles.css + extraCss, styles.generic)
    }

    override fun genericHidingStyles(documentUrl: Uri, topUrl: Uri?, classes: List<String>, ids: List<String>): String {
        val engine = service.engine ?: return ""
        if (!isEnabledOn(topUrl ?: documentUrl)) return ""
        val page = pageOf(documentUrl, topUrl)?.takeIf(::isWebUrl) ?: return ""
        return engine.genericHidingStyles(page.toString(), page.host!!, classes, ids)
    }

    private fun isWebUrl(uri: Uri): Boolean {
        val scheme = uri.scheme
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
    }

    override val blocksPages: Boolean get() = service.engine?.blocksPages ?: false

    /** Whether ads are blocked on the [page]: the setting of its host, else the global one; never on the home page */
    fun isEnabledOn(page: Uri?): Boolean {
        val pageString = page?.toString()
        if (pageString != null && pageString.startsWith(Config.HOME_PAGE_URL)) return false
        val host = page?.host?.takeIf { it.isNotEmpty() } ?: return config.adBlockEnabled
        return hostConfigs.adBlockSetting(host) ?: config.adBlockEnabled
    }

    private fun isLiteModeOn(page: Uri?): Boolean {
        val pageString = page?.toString()
        if (pageString != null && pageString.startsWith(Config.HOME_PAGE_URL)) return false
        val host = page?.host?.takeIf { it.isNotEmpty() } ?: return false
        return hostConfigs.liteModeSetting(host)
    }

    private fun isExternalFontRequest(url: Uri, type: String?): Boolean {
        val scheme = url.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (type == "font") return true
        val path = url.path?.lowercase() ?: return false
        return path.endsWith(".woff2") || path.endsWith(".woff") || path.endsWith(".ttf") || path.endsWith(".otf")
    }

    companion object {
        private const val LITE_MODE_CSS =
            "*, *::before, *::after { animation-duration: 0.001ms !important; animation-iteration-count: 1 !important; transition-duration: 0.001ms !important; }\n"
    }
}
