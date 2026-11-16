package com.phlox.tvwebbrowser.webengine

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Optional engine capability for one-click hiding of ad/banner overlays on a page */
interface ElementZapperSupport {
    /** Hides the visual element at view pixels ([x], [y]) and returns its generated CSS selector, or null */
    fun zapElementAt(x: Int, y: Int, onZapped: (String?) -> Unit)
}

val WebEngine.elementZapper: ElementZapperSupport? get() = this as? ElementZapperSupport

/**
 * Stores per-host CSS selectors hidden manually by the user ("Zap overlay") and generates safe JS/CSS
 * snippets for both WebView and GeckoView. Skips saving in incognito mode.
 */
object ElementZapper {
    private const val PREFS_NAME = "element_zapper"
    private const val MAX_SELECTORS_PER_HOST = 50
    private const val MAX_HOSTS = 200

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addSelector(context: Context, host: String?, selector: String?, incognito: Boolean) {
        if (incognito || host.isNullOrBlank() || selector.isNullOrBlank()) return
        val normalizedHost = host.lowercase()
        val p = prefs(context)
        val current = selectorsFor(p, normalizedHost).toMutableList()
        if (selector in current) return
        if (current.size >= MAX_SELECTORS_PER_HOST) current.removeAt(0)
        current.add(selector)
        val editor = p.edit().putString(normalizedHost, JSONArray(current).toString())
        val allKeys = p.all.keys
        if (normalizedHost !in allKeys && allKeys.size >= MAX_HOSTS) {
            allKeys.firstOrNull { it != normalizedHost }?.let { editor.remove(it) }
        }
        editor.apply()
    }

    fun selectorsFor(context: Context, host: String?): List<String> {
        if (host.isNullOrBlank()) return emptyList()
        return selectorsFor(prefs(context), host.lowercase())
    }

    fun hasSelectors(context: Context, host: String?): Boolean = selectorsFor(context, host).isNotEmpty()

    fun cssFor(context: Context, host: String?): String {
        val selectors = selectorsFor(context, host)
        if (selectors.isEmpty()) return ""
        return selectors.joinToString(", ") + " { display: none !important; }\n"
    }

    fun clearHost(context: Context, host: String?) {
        if (host.isNullOrBlank()) return
        prefs(context).edit().remove(host.lowercase()).apply()
    }

    private fun selectorsFor(prefs: SharedPreferences, normalizedHost: String): List<String> {
        val raw = prefs.getString(normalizedHost, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * JS snippet executed in the page to pick a safe overlay element at CSS coordinates ([xCss], [yCss]),
     * hide it immediately, and return the CSS selector string (or null if unsafe/not found).
     */
    fun zapAtScript(xCss: Int, yCss: Int): String = """
        (function() {
            var el = document.elementFromPoint($xCss, $yCss);
            if (!el) return null;
            var tag = el.tagName;
            if (tag === 'VIDEO' || tag === 'AUDIO' || tag === 'HTML' || tag === 'BODY') return null;
            var cur = el;
            while (cur && cur !== document.body && cur !== document.documentElement) {
                var t = cur.tagName;
                if (t === 'VIDEO' || t === 'AUDIO') return null;
                if (t === 'SPAN' || t === 'I' || t === 'SVG' || t === 'PATH') {
                    if (cur.parentElement && cur.parentElement !== document.body) { cur = cur.parentElement; continue; }
                }
                break;
            }
            if (!cur || cur === document.body || cur === document.documentElement) return null;
            var sel = null;
            if (cur.id && /^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(cur.id)) {
                sel = '#' + cur.id;
            } else {
                var parts = [];
                var node = cur;
                for (var depth = 0; node && node !== document.body && depth < 4; depth++) {
                    var part = node.tagName.toLowerCase();
                    if (node.id && /^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(node.id)) {
                        parts.unshift('#' + node.id);
                        break;
                    }
                    var cls = Array.prototype.filter.call(node.classList || [], function(c) {
                        return /^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(c);
                    }).slice(0, 2);
                    if (cls.length) {
                        part += '.' + cls.join('.');
                    } else if (node.parentElement) {
                        var sibs = Array.prototype.filter.call(node.parentElement.children, function(ch) {
                            return ch.tagName === node.tagName;
                        });
                        if (sibs.length > 1) part += ':nth-of-type(' + (sibs.indexOf(node) + 1) + ')';
                    }
                    parts.unshift(part);
                    node = node.parentElement;
                }
                sel = parts.join(' > ');
            }
            if (!sel || sel === 'body' || sel === 'html') return null;
            cur.style.setProperty('display', 'none', 'important');
            return sel;
        })()
    """.trimIndent()

    /** Parses the JSON-quoted string returned by `WebView.evaluateJavascript` */
    fun parseEvalResult(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            JSONObject("{\"s\":$raw}").optString("s").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
