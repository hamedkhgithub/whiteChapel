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
                "/houses" -> sendAsset(s, "houses.json", "application/json; charset=utf-8")
                "/move-track" -> sendAsset(s, "move_track_points.json", "application/json; charset=utf-8")
                "/jack-token" -> sendDrawable(s, R.drawable.jack_track_token, "image/png")
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


    private fun sendAsset(socket: Socket, assetName: String, contentType: String) {
        val bytes = context.assets.open(assetName).use { input ->
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
html{--police-size:40px;--victim-size:42px;--clue-size:32px;--house-number-diameter:23px;--house-number-font:11px}
html,body{margin:0;background:#111;color:#eee;font-family:Arial,sans-serif;height:100%;overflow:hidden}
#top{height:66px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;background:#16120e;color:#d6ad63;font-weight:700;padding:0 10px;box-sizing:border-box;font-size:16px}
#headline{display:flex;align-items:center;justify-content:center;gap:24px;width:100%}
#event{min-height:18px;color:#fff3c4;font-size:13px;font-weight:600;text-align:center;direction:rtl;unicode-bidi:plaintext;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:96vw}
#wrap{position:relative;width:100vw;height:calc(100vh - 118px);overflow:hidden;touch-action:none}
#stage{position:absolute;transform-origin:0 0;left:0;top:0}
.layer{position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none}
#base{position:relative;display:block}
.m{position:absolute;transform:translate(-50%,-50%);box-sizing:border-box}
.number-badge{position:absolute;transform:translate(-50%,-50%);width:var(--house-number-diameter);height:var(--house-number-diameter);border-radius:50%;background:rgba(255,255,255,.92);border:1px solid rgba(0,0,0,.45);color:#000;display:flex;align-items:center;justify-content:center;font-size:var(--house-number-font);font-weight:700;line-height:1;box-sizing:border-box}.number-badge.red-house{background:rgba(211,47,47,.94);border-color:rgba(122,12,12,.85);color:#fff}
.woman{width:var(--victim-size);height:var(--victim-size);border:0;border-radius:0;font-size:var(--victim-size);line-height:var(--victim-size);text-align:center;font-family:Arial,sans-serif}
.woman::before{content:"♥";color:var(--fill-color);-webkit-text-stroke:2px var(--marker-color);paint-order:stroke fill}
.patrol{width:var(--police-size);height:var(--police-size);aspect-ratio:1/1;border:4px solid var(--marker-color);border-radius:50%;background:var(--fill-color);box-shadow:0 0 0 1px #fff8}
.crime{width:35px;height:35px;aspect-ratio:1/1;border:4px solid #8b8b8b;border-radius:0;background:transparent}.crime.current{border-color:#d01818}
.clue{width:var(--clue-size);height:var(--clue-size);aspect-ratio:1/1;border:3px solid var(--marker-color);border-radius:0;background:var(--fill-color);transform:translate(-50%,-50%) rotate(45deg)}
#legend{position:fixed;right:10px;bottom:62px;background:#111d;padding:7px 10px;border:1px solid #d6ad63;border-radius:8px;font-size:12px;z-index:50;direction:rtl;unicode-bidi:plaintext}

/* Move Track markers are rendered directly on the printed board track. */
.track-jack-map{position:absolute;transform:translate(-50%,-50%);width:36px;height:36px;border-radius:50%;background-image:url('/jack-token');background-size:cover;background-position:center;border:2px solid #2b2b2b;box-shadow:0 1px 4px #000b;z-index:15;box-sizing:border-box}
.track-alley-map{position:absolute;transform:translate(-50%,-50%) rotate(45deg);width:30px;height:30px;background:#d32626;border:3px solid #2a1712;border-radius:5px;z-index:13;box-sizing:border-box;box-shadow:0 1px 3px #0008}
.track-alley-map::after{content:"";position:absolute;left:5px;right:5px;top:12px;height:3px;background:#ffd7c7;transform:rotate(-90deg);border-radius:2px}
.track-coach-map{position:absolute;transform:translate(-50%,-50%);height:31px;background:#d6ad63;border:3px solid #24170d;border-radius:9px;z-index:12;box-sizing:border-box;box-shadow:0 1px 4px #0009}
.track-coach-map::before,.track-coach-map::after{content:"";position:absolute;bottom:-6px;width:10px;height:10px;border-radius:50%;background:#24170d}
.track-coach-map::before{left:22%}.track-coach-map::after{right:22%}
</style></head><body>
<div id="top"><div id="headline"><span>WhiteChapel Map</span><span id="game"></span><span id="status">در حال اتصال…</span></div><div id="event"></div></div>
<div id="wrap"><div id="stage"><img id="base" src="/map"><div id="numbers" class="layer"></div><div id="marks" class="layer"></div></div></div>
<div id="legend">اطلاعات محرمانه جک روی این صفحه ارسال نمی‌شود.</div><script>
const stage=document.getElementById('stage'),base=document.getElementById('base'),marks=document.getElementById('marks'),numbers=document.getElementById('numbers'),wrap=document.getElementById('wrap');
let z=1,minZ=1,ox=0,oy=0,dragging=false,lastX=0,lastY=0;
let housePoints=[],trackPoints=[];
function fit(){const w=base.naturalWidth||1536,h=base.naturalHeight||1024;minZ=Math.min(wrap.clientWidth/w,wrap.clientHeight/h);z=minZ;ox=(wrap.clientWidth-w*z)/2;oy=(wrap.clientHeight-h*z)/2;apply()}
function clampPan(){const w=(base.naturalWidth||1536)*z,h=(base.naturalHeight||1024)*z,vw=wrap.clientWidth,vh=wrap.clientHeight;if(z<=minZ+0.0001){ox=(vw-w)/2;oy=(vh-h)/2;return}ox=w<=vw?(vw-w)/2:Math.max(vw-w,Math.min(0,ox));oy=h<=vh?(vh-h)/2:Math.max(vh-h,Math.min(0,oy))}
function apply(){clampPan();stage.style.transform='translate('+ox+'px,'+oy+'px) scale('+z+')';wrap.style.cursor=z<=minZ+0.0001?'default':(dragging?'grabbing':'grab')}
function zoomAt(clientX,clientY,newZ){const r=wrap.getBoundingClientRect(),px=clientX-r.left,py=clientY-r.top,worldX=(px-ox)/z,worldY=(py-oy)/z;newZ=Math.max(minZ,Math.min(minZ*6,newZ));ox=px-worldX*newZ;oy=py-worldY*newZ;z=newZ;apply()}
base.onload=fit;base.draggable=false;
function rgba(hex,a){let h=(hex||'#000000').replace('#','');if(h.length===3)h=h.split('').map(c=>c+c).join('');const n=parseInt(h,16);return 'rgba('+((n>>16)&255)+','+((n>>8)&255)+','+(n&255)+','+a+')'}
function mk(cls,x,y,color,fillAlpha){let e=document.createElement('div');e.className='m '+cls;e.style.left=(x*100)+'%';e.style.top=(y*100)+'%';if(color){e.style.setProperty('--marker-color',color);e.style.setProperty('--fill-color',rgba(color,fillAlpha==null?0:fillAlpha))}marks.appendChild(e)}
const redHouseIds=new Set([3,21,27,65,84,147,149,158]);function renderNumbers(){numbers.innerHTML='';housePoints.forEach(p=>{const e=document.createElement('div');e.className='number-badge'+(redHouseIds.has(Number(p.number))?' red-house':'');e.style.left=((p.norm_x||0)*100)+'%';e.style.top=((p.norm_y||0)*100)+'%';e.textContent=p.number;numbers.appendChild(e)})}
fetch('/houses').then(r=>r.json()).then(v=>{housePoints=v||[];renderNumbers()}).catch(()=>{});
fetch('/move-track').then(r=>r.json()).then(v=>{trackPoints=v||[]}).catch(()=>{});
const defaultAppearance={policeColors:['#1976D2','#F9A825','#6D4C41','#D32F2F','#2E7D32','#111111'],policeSize:1,policeFillAlpha:.42,victimRealColor:'#D32F2F',victimFakeColor:'#FFFFFF',victimSize:1,victimFillAlpha:.42,clueColor:'#F0C52B',clueSize:1,clueFillAlpha:.32,houseNumberScale:1};
const trackLabels=['V','IV','III','II','I',...Array.from({length:15},(_,i)=>String(i+1))];
function trackPointById(id){return trackPoints.find(p=>Number(p.id)===Number(id))}
function addTrackDiv(cls,x,y,w){
  const e=document.createElement('div');e.className=cls;e.style.left=(x*100)+'%';e.style.top=(y*100)+'%';if(w!=null)e.style.width=w+'px';marks.appendChild(e);return e
}
function renderBoardMoveTrack(s){
  if(!trackPoints.length)return;
  const time=Math.max(1,Math.min(5,Number(s.time||1))),start=5-time;
  const hunting=String(s.phase||'').startsWith('HUNT_')||String(s.phase||'').startsWith('GAME_OVER');
  const used=hunting?Math.max(0,Number(s.moveTrack||0)):0;
  const currentIndex=Math.max(0,Math.min(19,start+used));
  const currentPoint=trackPointById(currentIndex+1);
  if(hunting){
    (s.moveSpecials||[]).forEach(m=>{
      if(m.type==='ALLEY'){
        const idx=start+Number(m.from||0),p=trackPointById(idx+1);
        if(p)addTrackDiv('track-alley-map',Number(p.norm_x),Number(p.norm_y))
      }
      if(m.type==='COACH'){
        const ia=start+Number(m.from||0),ib=start+Number(m.to||0),a=trackPointById(ia+1),b=trackPointById(ib+1);
        if(a&&b){
          const mx=(Number(a.norm_x)+Number(b.norm_x))/2,my=(Number(a.norm_y)+Number(b.norm_y))/2;
          const nativeWidth=Math.abs(Number(b.x)-Number(a.x))+34;
          addTrackDiv('track-coach-map',mx,my,nativeWidth)
        }
      }
    })
  }
  if(currentPoint)addTrackDiv('track-jack-map',Number(currentPoint.norm_x),Number(currentPoint.norm_y))
}

function appearanceOf(s){return Object.assign({},defaultAppearance,s.appearance||{})}
function applyAppearance(a){const r=document.documentElement.style;r.setProperty('--police-size',(40*(a.policeSize||1))+'px');r.setProperty('--victim-size',(42*(a.victimSize||1))+'px');r.setProperty('--clue-size',(32*(a.clueSize||1))+'px');r.setProperty('--house-number-diameter',(23*(a.houseNumberScale||1))+'px');r.setProperty('--house-number-font',(11*(a.houseNumberScale||1))+'px')}
async function refresh(){try{const s=await fetch('/state?'+Date.now(),{cache:'no-store'}).then(r=>r.json()),a=appearanceOf(s);applyAppearance(a);marks.innerHTML='';(s.women||[]).forEach(v=>mk('woman',v.x,v.y,(v.revealed&&v.real)?a.victimRealColor:a.victimFakeColor,a.victimFillAlpha));(s.digitalPolice||[]).forEach(p=>{const c=(p.revealed&&p.real)?(a.policeColors||defaultAppearance.policeColors)[Math.max(0,Math.min(4,(p.id||1)-1))]:(a.policeColors||defaultAppearance.policeColors)[5];mk('patrol',p.x,p.y,c,a.policeFillAlpha)});(s.crime||[]).forEach(v=>mk(v.current?'crime current':'crime',v.x,v.y));(s.clues||[]).forEach(v=>mk('clue',v.x,v.y,a.clueColor,a.clueFillAlpha));renderBoardMoveTrack(s);document.getElementById('game').textContent='شب '+(s.night||1)+' • '+(s.publicPhase||s.phase||'')+' • '+(['','I','II','III','IV','V'][s.time]||'');document.getElementById('event').textContent=s.publicMessage||'';document.getElementById('status').textContent='فعال'}catch(e){document.getElementById('status').textContent='قطع ارتباط'}}setInterval(refresh,500);refresh();
wrap.addEventListener('wheel',e=>{e.preventDefault();const factor=e.deltaY<0?1.15:1/1.15;zoomAt(e.clientX,e.clientY,z*factor)},{passive:false});
wrap.addEventListener('mousedown',e=>{if(e.button!==0||z<=minZ+0.0001)return;e.preventDefault();dragging=true;lastX=e.clientX;lastY=e.clientY;apply()});
window.addEventListener('mousemove',e=>{if(!dragging)return;ox+=e.clientX-lastX;oy+=e.clientY-lastY;lastX=e.clientX;lastY=e.clientY;apply()});
window.addEventListener('mouseup',()=>{dragging=false;apply()});
let lastD=0,lastMidX=0,lastMidY=0;
wrap.addEventListener('touchstart',e=>{e.preventDefault();if(e.touches.length===1){lastX=e.touches[0].clientX;lastY=e.touches[0].clientY}else if(e.touches.length===2){const a=e.touches[0],b=e.touches[1];lastD=Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY);lastMidX=(a.clientX+b.clientX)/2;lastMidY=(a.clientY+b.clientY)/2}},{passive:false});
wrap.addEventListener('touchmove',e=>{e.preventDefault();if(e.touches.length===1){const t=e.touches[0];if(z>minZ+0.0001){ox+=t.clientX-lastX;oy+=t.clientY-lastY}lastX=t.clientX;lastY=t.clientY;apply()}else if(e.touches.length===2){const a=e.touches[0],b=e.touches[1],d=Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY),midX=(a.clientX+b.clientX)/2,midY=(a.clientY+b.clientY)/2;if(lastD>0){zoomAt(midX,midY,z*(d/lastD));ox+=midX-lastMidX;oy+=midY-lastMidY;apply()}lastD=d;lastMidX=midX;lastMidY=midY}},{passive:false});
wrap.addEventListener('touchend',e=>{lastD=0;if(e.touches.length===1){lastX=e.touches[0].clientX;lastY=e.touches[0].clientY}},{passive:false});
wrap.addEventListener('dblclick',e=>{e.preventDefault();fit()});
</script></body></html>"""
}
