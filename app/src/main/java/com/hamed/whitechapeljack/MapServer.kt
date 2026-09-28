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
<title>WhiteChapel Map</title><style>
html{--text-scale:1;--police-size:40px;--police-alpha:1;--victim-size:42px;--victim-alpha:1;--clue-size:32px;--clue-alpha:1}html,body{margin:0;background:#111;color:#eee;font-family:Arial,sans-serif;height:100%;overflow:hidden}#top{height:66px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;background:#16120e;color:#d6ad63;font-weight:700;padding:0 10px;box-sizing:border-box;font-size:calc(16px * var(--text-scale))}#headline{display:flex;align-items:center;justify-content:center;gap:24px;width:100%}#event{min-height:18px;color:#fff3c4;font-size:calc(13px * var(--text-scale));font-weight:600;text-align:center;direction:rtl;unicode-bidi:plaintext;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:96vw}#wrap{position:relative;width:100vw;height:calc(100vh - 66px);overflow:hidden;touch-action:none}#stage{position:absolute;transform-origin:0 0;left:0;top:0}.layer{position:absolute;left:0;top:0;width:100%;height:100%}#base{position:relative;display:block}.m{position:absolute;transform:translate(-50%,-50%);box-sizing:border-box;background:transparent;border-radius:50%}.woman{width:var(--victim-size);height:var(--victim-size);border:0!important;border-radius:0;font-size:var(--victim-size);line-height:var(--victim-size);text-align:center;font-family:Arial,sans-serif;opacity:var(--victim-alpha)}.woman::before{content:"♡"}.patrol{width:var(--police-size);height:var(--police-size);aspect-ratio:1/1;border:4px solid #111;border-radius:50%;box-shadow:0 0 0 1px #fff8;opacity:var(--police-alpha)}.crime{width:35px;height:35px;aspect-ratio:1/1;border:4px solid #d01818;border-radius:0}.clue{width:var(--clue-size);height:var(--clue-size);aspect-ratio:1/1;border:3px solid #f0c52b;border-radius:0;background:transparent;transform:translate(-50%,-50%) rotate(45deg);opacity:var(--clue-alpha)}#legend{position:fixed;right:10px;bottom:10px;background:#111d;padding:7px 10px;border:1px solid #d6ad63;border-radius:8px;font-size:calc(12px * var(--text-scale));z-index:50;direction:rtl;unicode-bidi:plaintext}
</style></head><body><div id="top"><div id="headline"><span>WhiteChapel Map</span><span id="game"></span><span id="status">در حال اتصال…</span></div><div id="event"></div></div><div id="wrap"><div id="stage"><img id="base" src="/map"><img class="layer" src="/numbers"><div id="marks" class="layer"></div></div></div><div id="legend">اطلاعات محرمانه جک روی این صفحه ارسال نمی‌شود.</div><script>
const stage=document.getElementById('stage'),base=document.getElementById('base'),marks=document.getElementById('marks'),wrap=document.getElementById('wrap');
let z=1,minZ=1,ox=0,oy=0;
function fit(){const w=base.naturalWidth||1536,h=base.naturalHeight||1024;minZ=Math.min(wrap.clientWidth/w,wrap.clientHeight/h);z=minZ;ox=(wrap.clientWidth-w*z)/2;oy=(wrap.clientHeight-h*z)/2;apply()}
function clampPan(){
  const w=(base.naturalWidth||1536)*z,h=(base.naturalHeight||1024)*z;
  const vw=wrap.clientWidth,vh=wrap.clientHeight;
  if(z<=minZ+0.0001){ox=(vw-w)/2;oy=(vh-h)/2;return}
  ox=w<=vw?(vw-w)/2:Math.max(vw-w,Math.min(0,ox));
  oy=h<=vh?(vh-h)/2:Math.max(vh-h,Math.min(0,oy));
}
function apply(){clampPan();stage.style.transform=`translate(${'$'}{ox}px,${'$'}{oy}px) scale(${'$'}{z})`;wrap.style.cursor=z<=minZ+0.0001?'default':(dragging?'grabbing':'grab')}
function zoomAt(clientX,clientY,newZ){const r=wrap.getBoundingClientRect();const px=clientX-r.left,py=clientY-r.top;const worldX=(px-ox)/z,worldY=(py-oy)/z;newZ=Math.max(minZ,Math.min(minZ*6,newZ));ox=px-worldX*newZ;oy=py-worldY*newZ;z=newZ;apply()}
base.onload=fit;
base.draggable=false;
function mk(cls,x,y,color){let e=document.createElement('div');e.className='m '+cls;e.style.left=(x*100)+'%';e.style.top=(y*100)+'%';if(color){if(cls==='woman')e.style.color=color;else e.style.borderColor=color}marks.appendChild(e)}
const defaultAppearance={policeColors:['#1976D2','#F9A825','#6D4C41','#D32F2F','#2E7D32','#111111'],policeSize:1,policeAlpha:1,victimRealColor:'#D32F2F',victimFakeColor:'#FFFFFF',victimSize:1,victimAlpha:1,clueColor:'#F0C52B',clueSize:1,clueAlpha:1,textScale:1};
function appearanceOf(s){return Object.assign({},defaultAppearance,s.appearance||{})}
function applyAppearance(a){const r=document.documentElement.style;r.setProperty('--text-scale',a.textScale||1);r.setProperty('--police-size',(40*(a.policeSize||1))+'px');r.setProperty('--police-alpha',a.policeAlpha??1);r.setProperty('--victim-size',(42*(a.victimSize||1))+'px');r.setProperty('--victim-alpha',a.victimAlpha??1);r.setProperty('--clue-size',(32*(a.clueSize||1))+'px');r.setProperty('--clue-alpha',a.clueAlpha??1)}
async function refresh(){try{const s=await fetch('/state?'+Date.now(),{cache:'no-store'}).then(r=>r.json());const a=appearanceOf(s);applyAppearance(a);marks.innerHTML='';(s.women||[]).forEach(v=>mk('woman',v.x,v.y,(v.revealed&&v.real)?a.victimRealColor:a.victimFakeColor));(s.digitalPolice||[]).forEach(p=>{const c=(p.revealed&&p.real)?(a.policeColors||defaultAppearance.policeColors)[Math.max(0,Math.min(4,(p.id||1)-1))]:(a.policeColors||defaultAppearance.policeColors)[5];mk('patrol',p.x,p.y,c)});(s.crime||[]).forEach(v=>mk('crime',v.x,v.y));(s.clues||[]).forEach(v=>mk('clue',v.x,v.y,a.clueColor));document.getElementById('game').textContent='شب '+(s.night||1)+' • '+(s.publicPhase||s.phase||'')+' • '+(['','I','II','III','IV','V'][s.time]||'');document.getElementById('event').textContent=s.publicMessage||'';document.getElementById('status').textContent='فعال'}catch(e){document.getElementById('status').textContent='قطع ارتباط'}}setInterval(refresh,500);refresh();

// Mouse wheel: zoom around the cursor.
wrap.addEventListener('wheel',e=>{e.preventDefault();const factor=e.deltaY<0?1.15:1/1.15;zoomAt(e.clientX,e.clientY,z*factor)},{passive:false});

// Mouse drag: pan.
let dragging=false,lastX=0,lastY=0;
wrap.addEventListener('mousedown',e=>{if(e.button!==0||z<=minZ+0.0001)return;e.preventDefault();dragging=true;lastX=e.clientX;lastY=e.clientY;apply()});
window.addEventListener('mousemove',e=>{if(!dragging)return;ox+=e.clientX-lastX;oy+=e.clientY-lastY;lastX=e.clientX;lastY=e.clientY;apply()});
window.addEventListener('mouseup',()=>{dragging=false;apply()});

// Touch: one finger pans; two fingers pinch-zoom and pan together.
let lastD=0,lastMidX=0,lastMidY=0;
wrap.addEventListener('touchstart',e=>{e.preventDefault();if(e.touches.length===1){lastX=e.touches[0].clientX;lastY=e.touches[0].clientY}else if(e.touches.length===2){const a=e.touches[0],b=e.touches[1];lastD=Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY);lastMidX=(a.clientX+b.clientX)/2;lastMidY=(a.clientY+b.clientY)/2}},{passive:false});
wrap.addEventListener('touchmove',e=>{e.preventDefault();if(e.touches.length===1){const t=e.touches[0];if(z>minZ+0.0001){ox+=t.clientX-lastX;oy+=t.clientY-lastY}lastX=t.clientX;lastY=t.clientY;apply()}else if(e.touches.length===2){const a=e.touches[0],b=e.touches[1];const d=Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY);const midX=(a.clientX+b.clientX)/2,midY=(a.clientY+b.clientY)/2;if(lastD>0){zoomAt(midX,midY,z*(d/lastD));ox+=midX-lastMidX;oy+=midY-lastMidY;apply()}lastD=d;lastMidX=midX;lastMidY=midY}},{passive:false});
wrap.addEventListener('touchend',e=>{lastD=0;if(e.touches.length===1){lastX=e.touches[0].clientX;lastY=e.touches[0].clientY}},{passive:false});

// Double-click returns to the full-board view.
wrap.addEventListener('dblclick',e=>{e.preventDefault();fit()});
</script></body></html>"""
}
