package com.hamed.whitechapeljack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Executors

object TvMapHub {
    @Volatile private var state: String = JSONObject()
        .put("police", JSONArray())
        .put("events", JSONArray())
        .put("hellPolice", JSONArray())
        .put("hellVictims", JSONArray())
        .put("hellReveal", false)
        .toString()

    fun setState(json: JSONObject) { state = json.toString() }
    fun getState(): String = state
}

object TvServerInfo {
    @Volatile var url: String = ""
}

class TvMapServer(private val context: Context, private val port: Int = 8765) {
    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        pool.execute {
            runCatching {
                serverSocket = ServerSocket(port)
                TvServerInfo.url = "http://${localIp()}:$port"
                while (running) {
                    val socket = serverSocket?.accept() ?: break
                    pool.execute { handle(socket) }
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        pool.shutdownNow()
    }

    private fun localIp(): String {
        return runCatching {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val ordered = interfaces.sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            for (ni in ordered) {
                val addresses = Collections.list(ni.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address && address.isSiteLocalAddress) {
                        return@runCatching address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val request = reader.readLine() ?: return
            val path = request.split(" ").getOrNull(1) ?: "/"
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
            }
            when (path.substringBefore("?")) {
                "/", "/index.html" -> sendText(s, "text/html; charset=utf-8", html())
                "/state" -> sendText(s, "application/json; charset=utf-8", TvMapHub.getState(), noCache = true)
                "/map" -> sendDrawable(s, R.drawable.whitechapel_board_base, "image/png")
                "/numbers" -> sendDrawable(s, R.drawable.whitechapel_house_numbers_overlay, "image/png")
                else -> sendText(s, "text/plain; charset=utf-8", "Not found", status = "404 Not Found")
            }
        }
    }

    private fun sendDrawable(socket: Socket, resId: Int, contentType: String) {
        val bytes = context.resources.openRawResource(resId).use { input ->
            val out = ByteArrayOutputStream(); input.copyTo(out); out.toByteArray()
        }
        val header = "HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nCache-Control: public, max-age=3600\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
    }

    private fun sendText(socket: Socket, contentType: String, body: String, status: String = "200 OK", noCache: Boolean = false) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val cache = if (noCache) "Cache-Control: no-store\r\n" else ""
        val header = "HTTP/1.1 $status\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\n${cache}Connection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
    }

    private fun html(): String = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=yes">
<title>Whitechapel Public Board</title><style>
html,body{margin:0;background:#111;color:#eee;font-family:Arial,sans-serif;height:100%;overflow:hidden}#top{height:42px;display:flex;align-items:center;justify-content:center;gap:24px;background:#16120e;color:#d6ad63;font-weight:700}#wrap{position:relative;width:100vw;height:calc(100vh - 42px);overflow:hidden;touch-action:none}#stage{position:absolute;transform-origin:0 0;left:0;top:0}.layer{position:absolute;left:0;top:0;width:100%;height:100%}#base{position:relative;display:block}.m{position:absolute;transform:translate(-50%,-50%);box-sizing:border-box;background:transparent;border-radius:50%}.woman{width:44px;height:32px;border:6px solid #fff}.patrol{width:42px;height:42px;border:6px solid #111;border-radius:50%;box-shadow:0 0 0 1px #fff8}.crime{width:36px;height:36px;border:5px solid #d01818;border-radius:0}.clue{width:30px;height:30px;border:4px solid #f0c52b;border-radius:50%;background:rgba(240,197,43,.24)}#legend{position:fixed;right:10px;bottom:10px;background:#111d;padding:7px 10px;border:1px solid #d6ad63;border-radius:8px;font-size:12px;z-index:50}
</style></head><body><div id="top"><span>Whitechapel Public Board</span><span id="game"></span><span id="status">Connecting…</span></div><div id="wrap"><div id="stage"><img id="base" src="/map"><img class="layer" src="/numbers"><div id="marks" class="layer"></div></div></div><div id="legend">اطلاعات محرمانه جک روی این صفحه ارسال نمی‌شود.</div><script>
const stage=document.getElementById('stage'),base=document.getElementById('base'),marks=document.getElementById('marks'),wrap=document.getElementById('wrap');let z=1,ox=0,oy=0;function fit(){const w=base.naturalWidth||1536,h=base.naturalHeight||1024;z=Math.min(wrap.clientWidth/w,wrap.clientHeight/h);ox=(wrap.clientWidth-w*z)/2;oy=(wrap.clientHeight-h*z)/2;apply()}function apply(){stage.style.transform=`translate(${'$'}{ox}px,${'$'}{oy}px) scale(${'$'}{z})`}base.onload=fit;addEventListener('resize',fit);function mk(cls,x,y,color){let e=document.createElement('div');e.className='m '+cls;e.style.left=(x*100)+'%';e.style.top=(y*100)+'%';if(color)e.style.borderColor=color;marks.appendChild(e)}async function refresh(){try{const s=await fetch('/state?'+Date.now(),{cache:'no-store'}).then(r=>r.json());marks.innerHTML='';(s.women||[]).forEach(v=>mk('woman',v.x,v.y));(s.digitalPolice||[]).forEach(p=>mk('patrol',p.x,p.y,p.revealed?(p.color||'#111'):'#111'));(s.crime||[]).forEach(v=>mk('crime',v.x,v.y));(s.clues||[]).forEach(v=>mk('clue',v.x,v.y));document.getElementById('game').textContent='Night '+(s.night||1)+' • '+(s.phase||'')+' • '+(['','I','II','III','IV','V'][s.time]||'');document.getElementById('status').textContent='Live'}catch(e){document.getElementById('status').textContent='Disconnected'}}setInterval(refresh,500);refresh();let lastD=0,lastX=0,lastY=0;wrap.addEventListener('touchstart',e=>{if(e.touches.length===2)lastD=Math.hypot(e.touches[0].clientX-e.touches[1].clientX,e.touches[0].clientY-e.touches[1].clientY);else if(e.touches.length===1){lastX=e.touches[0].clientX;lastY=e.touches[0].clientY}},{passive:false});wrap.addEventListener('touchmove',e=>{e.preventDefault();if(e.touches.length===2){let d=Math.hypot(e.touches[0].clientX-e.touches[1].clientX,e.touches[0].clientY-e.touches[1].clientY);z=Math.max(.3,Math.min(6,z*d/lastD));lastD=d;apply()}else if(e.touches.length===1){ox+=e.touches[0].clientX-lastX;oy+=e.touches[0].clientY-lastY;lastX=e.touches[0].clientX;lastY=e.touches[0].clientY;apply()}},{passive:false});
</script></body></html>"""
}
