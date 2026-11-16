package com.phlox.tvwebbrowser.activity.main.view

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R

/**
 * Shows a concise TV remote control guide ("Remote guide"), either on first run or from the ⋮ menu.
 */
object RemoteHintDialog {
    fun maybeShowOnFirstRun(context: Context, config: Config) {
        if (config.remoteHintShown) return
        config.remoteHintShown = true
        show(context)
    }

    fun show(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.remote_guide_title)
            .setMessage(R.string.remote_guide_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
