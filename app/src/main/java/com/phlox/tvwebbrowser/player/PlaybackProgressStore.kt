package com.phlox.tvwebbrowser.player

import android.content.Context
import android.content.SharedPreferences
import com.phlox.tvwebbrowser.webengine.PageVideo
import org.json.JSONObject

/**
 * Persists recent video playback positions so users can resume where they left off.
 *
 * Entries are keyed by the video's page URL (without fragment) when available because streaming CDNs often
 * rotate token query parameters on video URLs, falling back to the stripped video URL otherwise.
 */
class PlaybackProgressStore(
    private val prefs: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun getPosition(video: PageVideo): Double? {
        val key = keyFor(video) ?: return null
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val pos = json.optDouble(KEY_POSITION, 0.0)
            if (pos >= MIN_RESUME_SECONDS) pos else null
        } catch (_: Exception) {
            null
        }
    }

    fun save(video: PageVideo, positionSeconds: Double, durationSeconds: Double?, live: Boolean, incognito: Boolean) {
        if (incognito || live) return
        val key = keyFor(video) ?: return
        val duration = durationSeconds ?: return
        if (duration < MIN_VIDEO_DURATION_SECONDS) return
        if (positionSeconds < MIN_RESUME_SECONDS || positionSeconds >= duration - FINISH_MARGIN_SECONDS) {
            prefs.edit().remove(key).apply()
            return
        }
        val payload = JSONObject()
            .put(KEY_POSITION, positionSeconds)
            .put(KEY_DURATION, duration)
            .put(KEY_UPDATED_AT, now())
            .toString()
        val editor = prefs.edit().putString(key, payload)
        trimExcessEntries(editor, key)
        editor.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun trimExcessEntries(editor: SharedPreferences.Editor, keepKey: String) {
        val all = prefs.all
        if (all.size < MAX_ENTRIES) return
        val stamped = all.entries.mapNotNull { (k, v) ->
            if (k == keepKey) return@mapNotNull null
            val ts = try {
                (v as? String)?.let { JSONObject(it).optLong(KEY_UPDATED_AT, 0L) } ?: 0L
            } catch (_: Exception) {
                0L
            }
            k to ts
        }.sortedBy { it.second }
        val toRemove = (all.size + 1) - MAX_ENTRIES
        for (i in 0 until toRemove.coerceAtMost(stamped.size)) {
            editor.remove(stamped[i].first)
        }
    }

    companion object {
        private const val PREFS_NAME = "playback_progress"
        private const val KEY_POSITION = "pos"
        private const val KEY_DURATION = "dur"
        private const val KEY_UPDATED_AT = "at"
        internal const val MIN_VIDEO_DURATION_SECONDS = 60.0
        internal const val MIN_RESUME_SECONDS = 10.0
        internal const val FINISH_MARGIN_SECONDS = 15.0
        internal const val MAX_ENTRIES = 100

        internal fun keyFor(video: PageVideo): String? {
            val page = video.pageUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (page != null && page.startsWith("http", ignoreCase = true)) {
                return "page:${page.substringBefore('#')}"
            }
            val url = video.url.trim().takeIf { it.startsWith("http", ignoreCase = true) } ?: return null
            return "url:${url.substringBefore('#').substringBefore('?')}"
        }
    }
}
