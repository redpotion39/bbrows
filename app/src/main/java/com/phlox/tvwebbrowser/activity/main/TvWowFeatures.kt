package com.phlox.tvwebbrowser.activity.main

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.model.BrowserTab
import com.phlox.tvwebbrowser.send.QrBitmap
import com.phlox.tvwebbrowser.utils.themeColor
import com.phlox.tvwebbrowser.webengine.WebEngine
import org.json.JSONArray
import java.util.Locale

/**
 * High-impact TV browser features: TV Reader Mode, Thai/Auto TTS Read Aloud,
 * 2-Way TV-to-Phone QR Handoff, and Turbo Lite rendering CSS.
 */
object TvWowFeatures {
    private var tts: TextToSpeech? = null
    private var speaking = false

    fun toggleReaderMode(engine: WebEngine, onStatus: (Boolean) -> Unit) {
        val js = """
            (function() {
                var existing = document.getElementById('bbrows-reader-overlay');
                if (existing) {
                    existing.remove();
                    document.documentElement.style.overflow = '';
                    return 'off';
                }
                var root = document.querySelector('article') || document.querySelector('main') || document.body;
                var paragraphs = Array.from(root.querySelectorAll('h1, h2, h3, p, li')).filter(function(el) {
                    return el.innerText && el.innerText.trim().length > 20;
                });
                if (paragraphs.length === 0) {
                    paragraphs = Array.from(document.querySelectorAll('p')).filter(function(el) {
                        return el.innerText && el.innerText.trim().length > 15;
                    });
                }
                var title = document.title || '';
                var overlay = document.createElement('div');
                overlay.id = 'bbrows-reader-overlay';
                overlay.style.cssText = 'position:fixed;inset:0;z-index:2147483647;background:#161513;color:#EFE9DE;overflow-y:auto;padding:48px 10vw;font-family:system-ui,-apple-system,sans-serif;line-height:1.75;';
                var inner = document.createElement('div');
                inner.id = 'bbrows-reader-inner';
                inner.style.cssText = 'max-width:840px;margin:0 auto;font-size:24px;';

                var bar = document.createElement('div');
                bar.style.cssText = 'display:flex;gap:12px;align-items:center;margin-bottom:28px;padding-bottom:16px;border-bottom:1px solid rgba(239,233,222,0.1);';
                function mkBtn(label, fn) {
                    var b = document.createElement('button');
                    b.textContent = label;
                    b.style.cssText = 'background:#262421;color:#EFE9DE;border:1px solid rgba(239,233,222,0.12);border-radius:8px;padding:8px 16px;font-size:16px;font-weight:500;cursor:pointer;';
                    b.onclick = fn;
                    return b;
                }
                bar.appendChild(mkBtn('A−', function() {
                    var sz = parseInt(inner.style.fontSize) || 24;
                    inner.style.fontSize = Math.max(18, sz - 2) + 'px';
                }));
                bar.appendChild(mkBtn('A+', function() {
                    var sz = parseInt(inner.style.fontSize) || 24;
                    inner.style.fontSize = Math.min(42, sz + 2) + 'px';
                }));
                bar.appendChild(mkBtn('Close reader', function() {
                    overlay.remove();
                    document.documentElement.style.overflow = '';
                }));
                inner.appendChild(bar);

                var h1 = document.createElement('h1');
                h1.textContent = title;
                h1.style.cssText = 'font-size:1.4em;font-weight:500;line-height:1.25;margin:0 0 24px;color:#EFE9DE;';
                inner.appendChild(h1);

                if (paragraphs.length === 0) {
                    var p = document.createElement('p');
                    p.textContent = document.body ? document.body.innerText.slice(0, 6000) : '';
                    inner.appendChild(p);
                } else {
                    paragraphs.forEach(function(el) {
                        var tag = el.tagName.toLowerCase();
                        var clone = document.createElement(tag === 'li' ? 'p' : tag);
                        clone.textContent = el.innerText.trim();
                        clone.style.cssText = tag.startsWith('h')
                            ? 'margin:24px 0 12px;color:#F59E0B;font-size:1.15em;font-weight:500;'
                            : 'margin:0 0 16px;color:#EFE9DE;';
                        inner.appendChild(clone);
                    });
                }
                overlay.appendChild(inner);
                document.body.appendChild(overlay);
                document.documentElement.style.overflow = 'hidden';
                return 'on';
            })();
        """.trimIndent()
        engine.evaluateJavascriptWithResult(js) { res ->
            onStatus(res?.contains("on") == true)
        }
    }

    fun toggleSpeakPage(context: Context, engine: WebEngine, onMessage: (String) -> Unit) {
        if (speaking) {
            stopSpeaking()
            onMessage(context.getString(R.string.tts_stopped))
            return
        }
        val js = """
            (function() {
                var reader = document.getElementById('bbrows-reader-inner');
                var root = reader || document.querySelector('article') || document.querySelector('main') || document.body;
                if (!root) return '';
                var text = (root.innerText || '').replace(/\s+/g, ' ').trim();
                return text.slice(0, 3800);
            })();
        """.trimIndent()
        engine.evaluateJavascriptWithResult(js) { raw ->
            val text = try {
                if (raw.isNullOrBlank() || raw == "null") ""
                else JSONArray("[$raw]").getString(0)
            } catch (_: Exception) {
                raw?.trim('"') ?: ""
            }
            if (text.isBlank()) {
                onMessage(context.getString(R.string.tts_no_text))
                return@evaluateJavascriptWithResult
            }
            speakText(context, text, onMessage)
        }
    }

    private fun speakText(context: Context, text: String, onMessage: (String) -> Unit) {
        val appCtx = context.applicationContext
        val hasThai = text.any { it in '\u0E00'..'\u0E7F' }
        val targetLocale = if (hasThai) Locale("th", "TH") else Locale.getDefault()

        val startSpeech: (TextToSpeech) -> Unit = { engine ->
            engine.language = targetLocale
            speaking = true
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bbrows_tts")
            onMessage(context.getString(R.string.tts_started))
        }

        val current = tts
        if (current != null) {
            startSpeech(current)
            return
        }
        tts = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let(startSpeech)
            } else {
                speaking = false
                onMessage(context.getString(R.string.tts_unavailable))
            }
        }
    }

    fun stopSpeaking() {
        speaking = false
        tts?.stop()
    }

    fun shutdown() {
        speaking = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    fun showQrForCurrentPage(context: Context, tab: BrowserTab?) {
        val url = tab?.url ?: return
        val qrBitmap = QrBitmap.of(url)
        val density = context.resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val qrSize = (240 * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad / 2)
        }
        val iv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(qrSize, qrSize)
            setImageBitmap(qrBitmap)
            (drawable as? BitmapDrawable)?.isFilterBitmap = false
        }
        val tvDesc = TextView(context).apply {
            text = context.getString(R.string.handoff_qr_caption)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(context.themeColor(R.attr.tbColorDsInk))
            gravity = Gravity.CENTER
            setPadding(0, (16 * density).toInt(), 0, (6 * density).toInt())
        }
        val tvUrl = TextView(context).apply {
            text = url
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(context.themeColor(R.attr.tbColorDsInkFaint))
            gravity = Gravity.CENTER
            maxLines = 2
        }
        container.addView(iv)
        container.addView(tvDesc)
        container.addView(tvUrl)

        AlertDialog.Builder(context)
            .setTitle(R.string.handoff_qr_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    const val TURBO_LITE_JS = """
        (function() {
            if (document.getElementById('bbrows-turbo-css')) return;
            var s = document.createElement('style');
            s.id = 'bbrows-turbo-css';
            s.textContent = '*, *::before, *::after { animation-duration: 0.001ms !important; animation-iteration-count: 1 !important; transition-duration: 0.001ms !important; scroll-behavior: auto !important; }';
            (document.head || document.documentElement).appendChild(s);
        })();
    """
}
