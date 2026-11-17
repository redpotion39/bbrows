//download blobs support
if (!window.tvBroClicksListener) {
    window.tvBroClicksListener = function(e) {
        if (e.target.tagName.toUpperCase() == "A" && e.target.attributes.href.value.toLowerCase().startsWith("blob:")) {
            var fileName = e.target.download;
            var url = e.target.attributes.href.value;
            var xhr=new XMLHttpRequest();
            xhr.open('GET', e.target.attributes.href.value, true);
            xhr.responseType = 'blob';
            xhr.onload = function(e) {
                if (this.status == 200) {
                    var blob = this.response;
                    var reader = new FileReader();
                    reader.readAsDataURL(blob);
                    reader.onloadend = function() {
                        base64data = reader.result;
                        TVBro.takeBlobDownloadData(base64data, fileName, url, blob.type);
                    }
                }
            };
            xhr.send();
            e.stopPropagation();
            e.preventDefault();
        }
    };
    document.addEventListener("click", window.tvBroClicksListener);
}

// context menu support
window.addEventListener("touchstart", function(e) {
    window.TVBRO_activeElement = e.target;
    window.TVBRO_touchStartX = e.touches[0].clientX;
    window.TVBRO_touchStartY = e.touches[0].clientY;
});
// the virtual cursor is a mouse for pages in the desktop view: its long press touches nothing
window.addEventListener("mousemove", function(e) {
    window.TVBRO_activeElement = e.target;
}, {capture: true, passive: true});

// Anti-clickjacking transparent overlay defuser for streaming sites
(function() {
    function defuseOverlays() {
        try {
            var w = window.innerWidth;
            var h = window.innerHeight;
            if (!w || !h || !document.body) return;
            var all = document.querySelectorAll("div, a, span");
            for (var i = 0; i < all.length; i++) {
                var el = all[i];
                var s = window.getComputedStyle(el);
                if (s.position === "fixed" || s.position === "absolute") {
                    var z = parseInt(s.zIndex, 10);
                    if (z >= 9999) {
                        var rect = el.getBoundingClientRect();
                        if (rect.width >= w * 0.8 && rect.height >= h * 0.8) {
                            var op = parseFloat(s.opacity);
                            var bg = s.backgroundColor;
                            var isTransparent = op <= 0.05 || bg === "transparent" || bg === "rgba(0, 0, 0, 0)";
                            if (isTransparent && !el.querySelector("video, audio")) {
                                el.remove();
                            }
                        }
                    }
                }
            }
        } catch (e) {}
    }
    if (document.readyState === "complete" || document.readyState === "interactive") {
        defuseOverlays();
    } else {
        document.addEventListener("DOMContentLoaded", defuseOverlays);
    }
    window.addEventListener("load", defuseOverlays);
    setInterval(defuseOverlays, 2000);
})();
