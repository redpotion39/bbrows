package com.phlox.tvwebbrowser.widgets.cursor

import android.graphics.PointF

/**
 * Helper for D-pad Magnetic Link Snap: finds the nearest visible interactive element
 * (`a`, `button`, `input`, `[role=button]`, etc.) in the direction of a D-pad step
 * within a short distance of the virtual cursor.
 */
object CursorMagneticSnap {
    fun snapScript(curXCss: Int, curYCss: Int, dirX: Float, dirY: Float, maxDistCss: Int = 220): String =
        "(function(){" +
            "try{" +
                "var cx=$curXCss,cy=$curYCss,dx=$dirX,dy=$dirY,maxD=$maxDistCss;" +
                "var nodes=document.querySelectorAll('a[href],button,input,select,textarea,[role=\"button\"],[onclick]');" +
                "var best=null,bestScore=1e9;" +
                "for(var i=0;i<nodes.length&&i<250;i++){" +
                    "var r=nodes[i].getBoundingClientRect();" +
                    "if(r.width<8||r.height<8||r.bottom<0||r.right<0||r.top>window.innerHeight||r.left>window.innerWidth)continue;" +
                    "if(cx>=r.left&&cx<=r.right&&cy>=r.top&&cy<=r.bottom)continue;" +
                    "var tx=Math.max(r.left+4,Math.min(cx,r.right-4));" +
                    "var ty=Math.max(r.top+4,Math.min(cy,r.bottom-4));" +
                    "var vx=tx-cx,vy=ty-cy;" +
                    "var dot=vx*dx+vy*dy;" +
                    "if(dot<=6)continue;" +
                    "var dist=Math.hypot(vx,vy);" +
                    "if(dist>maxD)continue;" +
                    "var cos=dot/(dist*Math.hypot(dx,dy));" +
                    "if(cos<0.65)continue;" +
                    "var score=dist*(2.0-cos);" +
                    "if(score<bestScore){bestScore=score;best=(r.left+r.width/2).toFixed(1)+','+(r.top+r.height/2).toFixed(1);}" +
                "}" +
                "return best||'';" +
            "}catch(e){return '';}" +
        "})();"

    fun parseSnapPoint(raw: String?, density: Float): PointF? {
        if (raw == null) return null
        val cleaned = raw.trim().removeSurrounding("\"").trim()
        if (cleaned.isEmpty() || cleaned == "null") return null
        val parts = cleaned.split(',')
        if (parts.size != 2) return null
        val xCss = parts[0].toFloatOrNull() ?: return null
        val yCss = parts[1].toFloatOrNull() ?: return null
        val d = density.coerceAtLeast(1f)
        return PointF(xCss * d, yCss * d)
    }
}
