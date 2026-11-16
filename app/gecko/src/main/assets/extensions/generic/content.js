// The content script of every frame. It reports to AppContentScriptPortDelegate the state of the media of
// the frame (media_controls.js) and runs the commands of the app, reports the videos of the frame that
// other apps can play (page_videos.js), and hides the elements of ads (element_hiding.js); the build copies
// them here, they run before this script.

const communicatePort = browser.runtime.connectNative("tvbro_content");
const send = message => communicatePort.postMessage(message);
const mediaCommand = tvBroWatchMedia(send);

// The element hiding asks the app over the port; the answers come back by the id of the request
const hidingCallbacks = new Map();
let hidingRequests = 0;

function askHiding(message, callback) {
    const id = ++hidingRequests;
    // the top frame is the page of the tab: the url of the engine may still be the previous page
    message.top = window === window.top;
    hidingCallbacks.set(id, callback);
    message.hidingRequest = id;
    send(JSON.stringify(message));
}

communicatePort.onMessage.addListener(message => {
    if (!message) return;
    if (message.hidingReply) {
        const callback = hidingCallbacks.get(message.hidingReply);
        hidingCallbacks.delete(message.hidingReply);
        if (callback) callback(message);
        return;
    }
    if (message.command) mediaCommand(message.command);
    if (typeof message.zapX === "number" && typeof message.zapY === "number") {
        let sel = "";
        try {
            let el = document.elementFromPoint(message.zapX, message.zapY);
            if (el && el.tagName !== "VIDEO" && el.tagName !== "AUDIO" && el.tagName !== "HTML" && el.tagName !== "BODY") {
                let cur = el;
                while (cur && cur !== document.body && cur !== document.documentElement) {
                    const t = cur.tagName;
                    if (t === "VIDEO" || t === "AUDIO") { cur = null; break; }
                    if ((t === "SPAN" || t === "I" || t === "SVG" || t === "PATH") && cur.parentElement && cur.parentElement !== document.body) {
                        cur = cur.parentElement;
                        continue;
                    }
                    break;
                }
                if (cur && cur !== document.body && cur !== document.documentElement) {
                    if (cur.id && /^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(cur.id)) {
                        sel = "#" + cur.id;
                    } else {
                        const parts = [];
                        let node = cur;
                        for (let d = 0; node && node !== document.body && d < 4; d++) {
                            let part = node.tagName.toLowerCase();
                            if (node.id && /^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(node.id)) {
                                parts.unshift("#" + node.id);
                                break;
                            }
                            const cls = Array.from(node.classList || []).filter(c => /^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(c)).slice(0, 2);
                            if (cls.length) {
                                part += "." + cls.join(".");
                            } else if (node.parentElement) {
                                const sibs = Array.from(node.parentElement.children).filter(ch => ch.tagName === node.tagName);
                                if (sibs.length > 1) part += ":nth-of-type(" + (sibs.indexOf(node) + 1) + ")";
                            }
                            parts.unshift(part);
                            node = node.parentElement;
                        }
                        sel = parts.join(" > ");
                    }
                    if (sel && sel !== "body" && sel !== "html") {
                        cur.style.setProperty("display", "none", "important");
                    } else {
                        sel = "";
                    }
                }
            }
        } catch (e) { sel = ""; }
        send(JSON.stringify({ zapReply: sel }));
    }
});

tvBroHideElements({
    styles: callback => askHiding({ hidingStyles: location.href }, reply => callback(reply)),
    generic: (classes, ids, callback) =>
        askHiding({ genericHiding: location.href, classes: classes, ids: ids }, reply => callback(reply.css))
});

tvBroWatchPageVideos(send);

// The background script tells the top frame of the tab what it blocked, for the count of the site panel
browser.runtime.onMessage.addListener(message => {
    if (message && message.blockedUrl) send(JSON.stringify({ blockedUrl: message.blockedUrl }));
});
