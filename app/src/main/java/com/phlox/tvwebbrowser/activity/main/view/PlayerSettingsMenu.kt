package com.phlox.tvwebbrowser.activity.main.view

import android.content.Context
import androidx.media3.common.C
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.browser.StreamDownloads
import com.phlox.tvwebbrowser.player.NativePlayer
import com.phlox.tvwebbrowser.player.PlayerTracks
import com.phlox.tvwebbrowser.webengine.PageVideo

/**
 * Builds the [PlayerPanelView] pages for [PlayerScreenView] so the screen view stays focused on playback
 * and lifecycle while keeping all player settings rows in one place.
 */
internal class PlayerSettingsMenu(
    private val context: Context,
    private val panel: PlayerPanelView,
    private val texts: PlayerTracks.Texts,
    private val host: Host
) {
    interface Host {
        fun player(): NativePlayer
        fun pageMedia(): Boolean
        fun caption(video: PageVideo, height: Int?, site: String?): String
        fun resizeMode(): Config.PlayerResizeMode
        fun setResizeMode(mode: Config.PlayerResizeMode)
        fun subtitleSize(): Config.SubtitleSize
        fun setSubtitleSize(size: Config.SubtitleSize)
        fun audioBoost(): Config.AudioBoost
        fun setAudioBoost(boost: Config.AudioBoost)
        fun sleepTimerMinutes(): Int
        fun setSleepTimerMinutes(minutes: Int)
        fun skipIntro()
        fun enterPictureInPicture()
        fun sitePlayer(): PlayerScreenView.SitePlayer?
        fun setSitePlayer(builtIn: Boolean?)
        fun close(exit: PlayerScreenView.Exit)
        fun openInOtherApp(video: PageVideo): Boolean
        fun download(video: PageVideo)
        fun pipAvailable(): Boolean
    }

    fun open(focus: Int = 0) {
        val page = buildRootPage(focus) ?: return
        panel.open(page)
    }

    private fun buildRootPage(focus: Int): PlayerPanelView.Page? {
        val player = host.player()
        val state = player.state.value ?: return null
        val parameters = player.trackSelection ?: return null
        val rows = ArrayList<PlayerPanelView.Row>()

        fun choice(icon: Int, name: String, value: String, options: List<Pair<String, Boolean>>, choose: (Int) -> Unit) {
            val index = rows.size
            rows += PlayerPanelView.Row(icon, name, value = value, chevron = true) {
                panel.push(PlayerPanelView.Page(name, null, options.mapIndexed { i, (label, checked) ->
                    PlayerPanelView.Row(R.drawable.ic_check, label, checked = checked) {
                        choose(i)
                        buildRootPage(index)?.let { panel.open(it) }
                    }
                }, context.getString(R.string.player_panel_back), options.indexOfFirst { it.second }.coerceAtLeast(0)))
            }
        }

        fun tracks(icon: Int, name: Int, type: Int, options: List<PlayerTracks.Option>) {
            val current = options.firstOrNull { it.selected }?.label ?: return
            choice(icon, context.getString(name), current, options.map { it.label to it.selected }) {
                player.choose(type, options[it].choice)
            }
        }

        tracks(R.drawable.ic_quality, R.string.player_quality, C.TRACK_TYPE_VIDEO,
            PlayerTracks.qualities(state.tracks, parameters, texts))
        tracks(R.drawable.ic_audio_track, R.string.player_audio, C.TRACK_TYPE_AUDIO,
            PlayerTracks.audioTracks(state.tracks, parameters, texts))
        val subtitleOptions = PlayerTracks.subtitles(state.tracks, parameters, texts)
        tracks(R.drawable.ic_subtitles, R.string.player_subtitles, C.TRACK_TYPE_TEXT, subtitleOptions)
        if (subtitleOptions.isNotEmpty()) {
            val currentSubSize = host.subtitleSize()
            choice(R.drawable.ic_subtitles, context.getString(R.string.player_subtitle_size),
                subtitleSizeLabel(currentSubSize),
                Config.SubtitleSize.entries.map { subtitleSizeLabel(it) to (it == currentSubSize) }) {
                host.setSubtitleSize(Config.SubtitleSize.entries[it])
            }
        }

        val currentResize = host.resizeMode()
        choice(R.drawable.ic_video, context.getString(R.string.player_aspect_ratio),
            resizeModeLabel(currentResize),
            Config.PlayerResizeMode.entries.map { resizeModeLabel(it) to (it == currentResize) }) {
            host.setResizeMode(Config.PlayerResizeMode.entries[it])
        }

        val speed = player.speed
        choice(R.drawable.ic_speed, context.getString(R.string.player_speed), PlayerTracks.speedLabel(speed),
            PlayerTracks.SPEEDS.map { PlayerTracks.speedLabel(it) to (it == speed) }) {
            player.setSpeed(PlayerTracks.SPEEDS[it])
        }

        val currentBoost = host.audioBoost()
        choice(R.drawable.ic_audio_track, context.getString(R.string.player_audio_boost),
            audioBoostLabel(currentBoost),
            Config.AudioBoost.entries.map { audioBoostLabel(it) to (it == currentBoost) }) {
            host.setAudioBoost(Config.AudioBoost.entries[it])
        }

        if (state.media.duration != null) {
            val looping = player.looping
            val labels = listOf(context.getString(R.string.player_repeat_off), context.getString(R.string.player_repeat_on))
            choice(R.drawable.ic_repeat, context.getString(R.string.player_repeat), labels[if (looping) 1 else 0],
                listOf(labels[0] to !looping, labels[1] to looping)) { player.looping = it == 1 }
            rows += PlayerPanelView.Row(R.drawable.ic_fast_forward, context.getString(R.string.player_skip_intro)) {
                host.skipIntro()
                panel.close()
            }
        }

        val sleepMinutes = host.sleepTimerMinutes()
        choice(R.drawable.ic_history, context.getString(R.string.player_sleep_timer),
            sleepTimerLabel(sleepMinutes),
            SLEEP_TIMER_OPTIONS.map { sleepTimerLabel(it) to (it == sleepMinutes) }) {
            host.setSleepTimerMinutes(SLEEP_TIMER_OPTIONS[it])
        }

        if (host.pipAvailable()) {
            rows += PlayerPanelView.Row(R.drawable.ic_picture_in_picture, context.getString(R.string.player_picture_in_picture)) {
                host.enterPictureInPicture()
            }
        }

        val site = if (host.pageMedia()) host.sitePlayer() else null
        if (site != null) {
            val value = context.getString(if (site.builtIn) R.string.player_for_site_built_in else R.string.player_for_site_site)
            val index = rows.size
            rows += PlayerPanelView.Row(R.drawable.ic_globe, context.getString(R.string.player_for_site), value = value,
                chevron = true, dividerBefore = true) { panel.push(sitePlayerPage(site, index)) }
        }
        rows += PlayerPanelView.Row(R.drawable.ic_open_in_new, context.getString(R.string.player_open_in_other_app),
            dividerBefore = site == null) {
            if (host.openInOtherApp(state.video)) host.close(PlayerScreenView.Exit.ELSEWHERE)
        }
        if (state.video.kind == PageVideo.Kind.FILE ||
            (StreamDownloads.isDownloadable(state.video) && !state.media.live && !state.media.drm)) {
            rows += PlayerPanelView.Row(R.drawable.ic_downloads, context.getString(R.string.download)) {
                host.download(state.video)
            }
        }
        return PlayerPanelView.Page(context.getString(R.string.player_menu_hint),
            host.caption(state.video, state.videoHeight, null),
            rows, context.getString(R.string.player_panel_close), focus.coerceIn(0, rows.lastIndex))
    }

    private fun sitePlayerPage(site: PlayerScreenView.SitePlayer, settingsRow: Int): PlayerPanelView.Page {
        val options = listOf(null, true, false)
        val labels = listOf(context.getString(R.string.player_for_site_default), context.getString(R.string.built_in_player),
            context.getString(R.string.site_player))
        val captions = listOf(context.getString(if (site.byDefault) R.string.player_default_built_in else R.string.player_default_site),
            context.getString(R.string.built_in_player_summary), context.getString(R.string.site_player_summary))
        return PlayerPanelView.Page(context.getString(R.string.player_for_site), site.host, options.mapIndexed { i, option ->
            PlayerPanelView.Row(R.drawable.ic_check, labels[i], caption = captions[i], checked = option == site.setting) {
                host.setSitePlayer(option)
                if (!(option ?: site.byDefault)) host.close(PlayerScreenView.Exit.SITE_PLAYER)
                else buildRootPage(settingsRow)?.let { panel.open(it) }
            }
        }, context.getString(R.string.player_panel_back), options.indexOf(site.setting))
    }

    private fun resizeModeLabel(mode: Config.PlayerResizeMode): String = when (mode) {
        Config.PlayerResizeMode.FIT -> context.getString(R.string.player_aspect_fit)
        Config.PlayerResizeMode.FILL -> context.getString(R.string.player_aspect_fill)
        Config.PlayerResizeMode.ZOOM -> context.getString(R.string.player_aspect_zoom)
    }

    private fun subtitleSizeLabel(size: Config.SubtitleSize): String = when (size) {
        Config.SubtitleSize.SMALL -> context.getString(R.string.player_subtitle_small)
        Config.SubtitleSize.NORMAL -> context.getString(R.string.player_subtitle_normal)
        Config.SubtitleSize.LARGE -> context.getString(R.string.player_subtitle_large)
        Config.SubtitleSize.EXTRA_LARGE -> context.getString(R.string.player_subtitle_extra_large)
    }

    private fun audioBoostLabel(boost: Config.AudioBoost): String = when (boost) {
        Config.AudioBoost.OFF -> context.getString(R.string.player_off)
        Config.AudioBoost.LOW -> context.getString(R.string.player_audio_boost_low)
        Config.AudioBoost.HIGH -> context.getString(R.string.player_audio_boost_high)
    }

    private fun sleepTimerLabel(minutes: Int): String =
        if (minutes <= 0) context.getString(R.string.player_off)
        else context.getString(R.string.player_sleep_timer_minutes, minutes)

    companion object {
        val SLEEP_TIMER_OPTIONS = listOf(0, 15, 30, 60)
    }
}
