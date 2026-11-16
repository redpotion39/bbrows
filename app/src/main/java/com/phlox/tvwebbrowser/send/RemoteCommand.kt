package com.phlox.tvwebbrowser.send

import android.view.KeyEvent
import org.json.JSONObject

/**
 * Remote touchpad and keyboard commands sent from a phone browser on the local network (`POST /remote`).
 */
sealed class RemoteCommand {
    data class Move(val dx: Float, val dy: Float) : RemoteCommand()
    data object Click : RemoteCommand()
    data class Scroll(val dx: Float, val dy: Float) : RemoteCommand()
    data object Back : RemoteCommand()
    data class InputText(val text: String, val submit: Boolean = false) : RemoteCommand()
    data class Key(val keyCode: Int) : RemoteCommand()

    companion object {
        fun parse(body: String): RemoteCommand? {
            if (body.isBlank()) return null
            return try {
                val json = JSONObject(body)
                when (json.optString("type")) {
                    "move" -> {
                        val dx = json.optDouble("dx", 0.0).toFloat()
                        val dy = json.optDouble("dy", 0.0).toFloat()
                        if (!dx.isFinite() || !dy.isFinite()) null else Move(dx.coerceIn(-800f, 800f), dy.coerceIn(-800f, 800f))
                    }
                    "click" -> Click
                    "scroll" -> {
                        val dx = json.optDouble("dx", 0.0).toFloat()
                        val dy = json.optDouble("dy", 0.0).toFloat()
                        if (!dx.isFinite() || !dy.isFinite()) null else Scroll(dx.coerceIn(-1200f, 1200f), dy.coerceIn(-1200f, 1200f))
                    }
                    "back" -> Back
                    "input" -> {
                        val text = json.optString("text", "")
                        if (text.isEmpty() || text.length > SendRules.MAX_TEXT_LENGTH) null
                        else InputText(text, json.optBoolean("submit", false))
                    }
                    "key" -> {
                        val code = when (json.optString("key")) {
                            "up" -> KeyEvent.KEYCODE_DPAD_UP
                            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
                            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                            "enter", "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
                            "back" -> KeyEvent.KEYCODE_BACK
                            else -> json.optInt("code", 0)
                        }
                        if (code != 0) Key(code) else null
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
