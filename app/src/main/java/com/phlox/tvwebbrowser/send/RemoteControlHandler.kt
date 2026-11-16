package com.phlox.tvwebbrowser.send

import android.view.KeyEvent
import com.phlox.tvwebbrowser.webengine.WebEngine
import com.phlox.tvwebbrowser.webengine.virtualCursor
import org.json.JSONObject

/**
 * Executes a [RemoteCommand] from a phone on the current [WebEngine] / browser commands.
 */
object RemoteControlHandler {
    fun handle(
        cmd: RemoteCommand,
        engine: WebEngine?,
        onBack: () -> Unit,
        onHideSendScreen: () -> Unit
    ) {
        when (cmd) {
            is RemoteCommand.Move -> {
                onHideSendScreen()
                val delegate = engine?.virtualCursor?.getCursorDrawerDelegate() ?: return
                delegate.moveTo(delegate.cursorPosition.x + cmd.dx, delegate.cursorPosition.y + cmd.dy)
            }
            is RemoteCommand.Click -> {
                onHideSendScreen()
                val delegate = engine?.virtualCursor?.getCursorDrawerDelegate() ?: return
                delegate.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
                delegate.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            }
            is RemoteCommand.Scroll -> {
                onHideSendScreen()
                val wv = engine ?: return
                val dx = cmd.dx.toInt()
                val dy = cmd.dy.toInt()
                wv.evaluateJavascript("window.scrollBy(${-dx}, ${-dy});")
            }
            is RemoteCommand.Back -> onBack()
            is RemoteCommand.InputText -> {
                onHideSendScreen()
                val wv = engine ?: return
                val escaped = JSONObject.quote(cmd.text)
                val submitJs = if (cmd.submit) {
                    "if(el.form){if(el.form.requestSubmit)el.form.requestSubmit();else el.form.submit();}" +
                        "else{el.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',keyCode:13,bubbles:true}));}"
                } else ""
                wv.evaluateJavascript(
                    "(function(){try{" +
                        "var el=document.activeElement;" +
                        "if(!el||!(el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable)){" +
                        "el=document.querySelector('input:not([type=hidden]):not([disabled]),textarea:not([disabled]),[contenteditable=true]');" +
                        "if(el)el.focus();" +
                        "}" +
                        "if(el){" +
                        "if(el.isContentEditable){el.textContent=$escaped;}else{el.value=$escaped;}" +
                        "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                        submitJs +
                        "}" +
                        "}catch(e){}})();"
                )
            }
        }
    }
}
