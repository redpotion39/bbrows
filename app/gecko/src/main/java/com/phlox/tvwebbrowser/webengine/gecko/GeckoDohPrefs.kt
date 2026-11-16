package com.phlox.tvwebbrowser.webengine.gecko

import android.util.Log
import androidx.annotation.UiThread
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.net.AppProxy
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoPreferenceController.SetGeckoPreference
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Configures GeckoView's DNS-over-HTTPS (`network.trr.*`) on the default branch.
 * When a manual proxy is active ([AppProxy.settings] != null), TRR is left disabled so DNS follows the proxy.
 */
object GeckoDohPrefs {
    private const val TAG = "GeckoDohPrefs"
    private const val MODE_OFF = 0
    private const val MODE_FIRST = 2
    private const val CLOUDFLARE_URI = "https://mozilla.cloudflare-dns.com/dns-query"
    private const val ADGUARD_URI = "https://dns.adguard-dns.com/dns-query"

    @UiThread
    suspend fun apply(mode: Config.DohMode) {
        val effective = if (AppProxy.settings != null) Config.DohMode.OFF else mode
        val trrMode = if (effective == Config.DohMode.OFF) MODE_OFF else MODE_FIRST
        val trrUri = when (effective) {
            Config.DohMode.OFF -> ""
            Config.DohMode.CLOUDFLARE -> CLOUDFLARE_URI
            Config.DohMode.ADGUARD -> ADGUARD_URI
        }
        val branch = GeckoPreferenceController.PREF_BRANCH_DEFAULT
        val list = listOf(
            SetGeckoPreference.setIntPref("network.trr.mode", trrMode, branch),
            SetGeckoPreference.setStringPref("network.trr.uri", trrUri, branch)
        )
        suspendCoroutine { cont ->
            GeckoPreferenceController.setGeckoPrefs(list).then({
                cont.resume(Unit)
                GeckoResult.fromValue<Void>(null)
            }, { e ->
                Log.e(TAG, "Can not set DoH preferences", e)
                cont.resume(Unit)
                GeckoResult.fromValue<Void>(null)
            })
        }
    }
}
