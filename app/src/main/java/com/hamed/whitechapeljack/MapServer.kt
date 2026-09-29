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

class TvMapServer(private val context: Context, private val port: Int = 8766) {
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
                "/coach-token" -> sendDrawable(s, R.drawable.coach_track_token_legacy, "image/png")
                "/alley-token" -> sendDrawable(s, R.drawable.alley_track_token_legacy, "image/png")
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
        val cache = if (noCache) "Cache-Control: no-store, no-cache, must-revalidate, max-age=0\r\nPragma: no-cache\r\nExpires: 0\r\n" else ""
        val header = "HTTP/1.1 $status\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\n${cache}Connection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
    }

    private fun html(): String = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=yes">
<title>WhiteChapel Map - Legacy TV</title><style>
html,body{margin:0;background:#111;color:#eee;font-family:Arial,sans-serif;height:100%;overflow:hidden}
#top{height:66px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;background:#16120e;color:#d6ad63;font-weight:700;padding:0 10px;box-sizing:border-box;font-size:16px}
#headline{display:flex;align-items:center;justify-content:center;gap:24px;width:100%}
#event{min-height:18px;color:#fff3c4;font-size:13px;font-weight:600;text-align:center;direction:rtl;unicode-bidi:plaintext;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:96vw}
#wrap{position:relative;overflow:hidden;touch-action:none}
#stage{position:absolute;left:0;top:0;width:1536px;height:1024px;transform-origin:0 0}
.layer{position:absolute;left:0;top:0;width:1536px;height:1024px;pointer-events:none}
#base{position:absolute;left:0;top:0;width:1536px;height:1024px;display:block}
.m{position:absolute;transform:translate(-50%,-50%);box-sizing:border-box}
.number-badge{position:absolute;transform:translate(-50%,-50%);width:23px;height:23px;border-radius:50%;background:rgba(255,255,255,.92);border:1px solid rgba(0,0,0,.45);color:#000;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;line-height:1;box-sizing:border-box}.number-badge.red-house{background:rgba(211,47,47,.94);border-color:rgba(122,12,12,.85);color:#fff}
.woman{width:42px;height:42px;border:0;background:transparent}
.patrol{width:40px;height:40px;border:0;background:transparent}
.crime{width:35px;height:35px;border:4px solid #8b8b8b;border-radius:0;background:transparent}.crime.current{border-color:#d01818}
.clue{width:32px;height:32px;border:3px solid #f0c52b;border-radius:0;background:rgba(240,197,43,.32);transform:translate(-50%,-50%) rotate(45deg)}
#legend{position:fixed;right:10px;bottom:62px;background:#111d;padding:7px 10px;border:1px solid #d6ad63;border-radius:8px;font-size:12px;z-index:50;direction:rtl;unicode-bidi:plaintext}

/* Move Track markers are rendered directly on the printed board track. */
.track-jack-map{position:absolute;transform:translate(-50%,-50%);width:36px;height:36px;border-radius:50%;background-image:url('/jack-token');background-size:cover;background-position:center;border:2px solid #2b2b2b;box-shadow:0 1px 4px #000b;z-index:15;box-sizing:border-box}
.track-alley-map{position:absolute;transform:translate(-50%,-50%);width:56px;height:47px;background:url('/alley-token') center/contain no-repeat;z-index:13;box-sizing:border-box;filter:drop-shadow(0 1px 2px #0009)}
.track-coach-map{position:absolute;transform:translate(-50%,-50%);background:url('/coach-token') center/contain no-repeat;z-index:12;box-sizing:border-box;filter:drop-shadow(0 1px 3px #0009)}
</style></head><body>
<div id="top"><div id="headline"><span>WhiteChapel Map • Legacy TV</span><span id="game"></span><span id="status">در حال اتصال…</span></div><div id="event"></div></div>
<div id="wrap"><div id="stage"><img id="base" src="/map"><div id="numbers" class="layer"></div><div id="marks" class="layer"></div></div></div>
<div id="legend">اطلاعات محرمانه جک روی این صفحه ارسال نمی‌شود.</div><script>
var stage=document.getElementById('stage');
var base=document.getElementById('base');
var marks=document.getElementById('marks');
var numbers=document.getElementById('numbers');
var wrap=document.getElementById('wrap');
var z=1,minZ=1,ox=0,oy=0,dragging=false,lastX=0,lastY=0;
var housePoints=[];
var trackPoints=[];
var redHouseIds=[3,21,27,65,84,147,149,158];
var defaultAppearance={
  policeColors:['#1976D2','#F9A825','#6D4C41','#D32F2F','#2E7D32','#111111'],
  policeSize:1,policeFillAlpha:.42,
  victimRealColor:'#D32F2F',victimFakeColor:'#FFFFFF',victimSize:1,victimFillAlpha:.42,
  clueColor:'#F0C52B',clueSize:1,clueFillAlpha:.32,houseNumberScale:1
};
var currentAppearance=defaultAppearance;

function resizeViewport(){
  var top=document.getElementById('top');
  var topH=top?top.offsetHeight:66;
  var vw=window.innerWidth||document.documentElement.clientWidth||1536;
  var vh=window.innerHeight||document.documentElement.clientHeight||1024;
  wrap.style.width=vw+'px';
  wrap.style.height=Math.max(100,vh-topH)+'px';
}
function fit(){
  resizeViewport();
  var w=1536;
  var h=1024;
  minZ=Math.min(wrap.clientWidth/w,wrap.clientHeight/h);
  z=minZ;
  ox=(wrap.clientWidth-w*z)/2;
  oy=(wrap.clientHeight-h*z)/2;
  apply();
}
function clampPan(){
  var w=1536*z;
  var h=1024*z;
  var vw=wrap.clientWidth;
  var vh=wrap.clientHeight;
  if(z<=minZ+0.0001){ox=(vw-w)/2;oy=(vh-h)/2;return;}
  ox=w<=vw?(vw-w)/2:Math.max(vw-w,Math.min(0,ox));
  oy=h<=vh?(vh-h)/2:Math.max(vh-h,Math.min(0,oy));
}
function apply(){
  clampPan();
  stage.style.transform='translate('+ox+'px,'+oy+'px) scale('+z+')';
  wrap.style.cursor=z<=minZ+0.0001?'default':(dragging?'grabbing':'grab');
}
function zoomAt(clientX,clientY,newZ){
  var r=wrap.getBoundingClientRect();
  var px=clientX-r.left;
  var py=clientY-r.top;
  var worldX=(px-ox)/z;
  var worldY=(py-oy)/z;
  newZ=Math.max(minZ,Math.min(minZ*6,newZ));
  ox=px-worldX*newZ;
  oy=py-worldY*newZ;
  z=newZ;
  apply();
}
base.onload=fit;
base.draggable=false;
window.onresize=function(){fit();};
if(base.complete){fit();}

function rgba(hex,a){
  var h=(hex||'#000000').replace('#','');
  if(h.length===3){h=h.charAt(0)+h.charAt(0)+h.charAt(1)+h.charAt(1)+h.charAt(2)+h.charAt(2);}
  var n=parseInt(h,16);
  return 'rgba('+((n>>16)&255)+','+((n>>8)&255)+','+(n&255)+','+a+')';
}
function containsNumber(arr,n){
  var i;
  for(i=0;i<arr.length;i++){if(Number(arr[i])===Number(n))return true;}
  return false;
}
function appearanceOf(s){
  var a={};
  var k;
  for(k in defaultAppearance){if(defaultAppearance.hasOwnProperty(k))a[k]=defaultAppearance[k];}
  if(s&&s.appearance){for(k in s.appearance){if(s.appearance.hasOwnProperty(k))a[k]=s.appearance[k];}}
  return a;
}
function applyAppearance(a){
  currentAppearance=a||defaultAppearance;
  renderNumbers();
}
function svgEl(name){return document.createElementNS('http://www.w3.org/2000/svg',name);}
function addSvgPath(svg,d,fill,fillOpacity,stroke,strokeWidth){
  var p=svgEl('path');
  p.setAttribute('d',d);
  p.setAttribute('fill',fill||'none');
  if(fillOpacity!=null)p.setAttribute('fill-opacity',String(fillOpacity));
  if(stroke){p.setAttribute('stroke',stroke);p.setAttribute('stroke-width',String(strokeWidth||1));p.setAttribute('stroke-linejoin','round');p.setAttribute('stroke-linecap','round');}
  svg.appendChild(p);return p;
}
function addHeartSvg(e,color,fillAlpha){
  var svg=svgEl('svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('width','100%');svg.setAttribute('height','100%');
  var g=svgEl('g');g.setAttribute('transform','translate(1 2)');svg.appendChild(g);
  var inner=svgEl('path');inner.setAttribute('d','M18.6707335,10.0469949 C20.4444204,8.20475335 20.4428931,5.22154308 18.6673208,3.38125356 C16.8917484,1.54096405 14.0134482,1.53938105 12.2359925,3.37771648 L10.9702069,4.68963823 L9.74663024,3.42106257 C7.97421677,1.58443498 5.10087015,1.58474944 3.32883095,3.42176494 C1.55679174,5.25878043 1.55709514,8.23685657 3.32950861,10.0734842 L10.9750473,18 L18.6707335,10.0469949 Z');inner.setAttribute('fill',color);inner.setAttribute('fill-opacity',String(fillAlpha==null?0:fillAlpha));g.appendChild(inner);
  var outline=svgEl('path');outline.setAttribute('d','M9.53555048,19.3884699 L1.89036034,11.4623154 C-0.629744037,8.85090825 -0.630172509,4.64518565 1.88938959,2.03323745 C4.37655172,-0.545122756 8.39543397,-0.61732966 10.9687169,1.81730162 C13.5445576,-0.66312694 17.60123,-0.604129239 20.1066156,1.99257419 C22.6292352,4.60713978 22.6313904,8.81686087 20.1115002,11.434147 L12.4427074,19.3824584 C12.1544685,19.6812032 11.7964701,19.8704534 11.4198481,19.9502088 C10.7609371,20.0997637 10.0408904,19.9123813 9.53555048,19.3884699 Z');outline.setAttribute('fill','none');outline.setAttribute('stroke',color);outline.setAttribute('stroke-width','1.45');outline.setAttribute('stroke-linejoin','round');g.appendChild(outline);
  e.appendChild(svg);
}
function addPoliceSvg(e,color,fillAlpha){
  var svg=svgEl('svg');svg.setAttribute('viewBox','0 0 512 512');svg.setAttribute('width','100%');svg.setAttribute('height','100%');
  addSvgPath(svg,'M503.407,432.422c-10.635,-0.836 -21.386,-1.266 -32.23,-1.266c-78.996,0 -152.709,22.582 -215.068,61.627c-34.773,-21.769 -73.075,-38.43 -113.862,-48.891c-32.346,-8.325 -66.26,-12.736 -101.206,-12.736c-10.844,0 -21.583,0.43 -32.218,1.266c12.191,-45.28 59.212,-59.212 59.212,-59.212l4.853,-37.756l18.077,-140.855c5.329,-41.53 25.682,-77.858 55.16,-103.807c29.49,-25.937 68.106,-41.495 109.995,-41.495c83.756,0 154.474,62.231 165.144,145.302l22.93,178.612C444.195,373.21 491.216,387.143 503.407,432.422z',color,fillAlpha,color,18);
  var badge=svgEl('polygon');badge.setAttribute('points','256.117,194.281 279.646,217.811 312.921,217.811 312.921,251.086 336.449,274.614 312.921,298.143 312.921,331.418 279.646,331.418 256.117,354.947 232.588,331.418 199.313,331.418 199.313,298.143 175.784,274.614 199.313,251.086 199.313,217.811 232.588,217.811');badge.setAttribute('fill','none');badge.setAttribute('stroke',color);badge.setAttribute('stroke-width','14');badge.setAttribute('stroke-linejoin','round');svg.appendChild(badge);
  e.appendChild(svg);
}
function mk(cls,x,y,color,fillAlpha){
  var e=document.createElement('div');
  var size;
  e.className='m '+cls;
  e.style.left=(Number(x)*100)+'%';
  e.style.top=(Number(y)*100)+'%';
  if(cls==='woman'){
    size=42*(currentAppearance.victimSize||1);
    e.style.width=size+'px';e.style.height=size+'px';
    addHeartSvg(e,color,fillAlpha);
  }else if(cls==='patrol'){
    size=40*(currentAppearance.policeSize||1);
    e.style.width=size+'px';e.style.height=size+'px';
    addPoliceSvg(e,color,fillAlpha);
  }else if(cls==='clue'){
    size=32*(currentAppearance.clueSize||1);
    e.style.width=size+'px';e.style.height=size+'px';
    e.style.borderColor=color;
    e.style.backgroundColor=rgba(color,fillAlpha==null?0:fillAlpha);
  }
  marks.appendChild(e);
}
function renderNumbers(){
  var i,p,e,scale,diam,font;
  if(!numbers)return;
  numbers.innerHTML='';
  scale=(currentAppearance&&currentAppearance.houseNumberScale)||1;
  diam=23*scale;
  font=11*scale;
  for(i=0;i<housePoints.length;i++){
    p=housePoints[i];
    e=document.createElement('div');
    e.className='number-badge'+(containsNumber(redHouseIds,p.number)?' red-house':'');
    e.style.left=(Number(p.norm_x||0)*100)+'%';
    e.style.top=(Number(p.norm_y||0)*100)+'%';
    e.style.width=diam+'px';e.style.height=diam+'px';e.style.fontSize=font+'px';
    e.appendChild(document.createTextNode(String(p.number)));
    numbers.appendChild(e);
  }
}
function xhrJson(url,onOk,onFail){
  var xhr=new XMLHttpRequest();
  xhr.open('GET',url,true);
  try{xhr.setRequestHeader('Cache-Control','no-cache');}catch(ignore){}
  xhr.onreadystatechange=function(){
    if(xhr.readyState!==4)return;
    if(xhr.status>=200&&xhr.status<300){
      try{onOk(JSON.parse(xhr.responseText));}
      catch(e){if(onFail)onFail(e);}
    }else{if(onFail)onFail(new Error('HTTP '+xhr.status));}
  };
  xhr.onerror=function(){if(onFail)onFail(new Error('network'));};
  xhr.send(null);
}
function loadStaticData(){
  xhrJson('/houses?t='+(new Date().getTime()),function(v){housePoints=v||[];renderNumbers();},function(){});
  xhrJson('/move-track?t='+(new Date().getTime()),function(v){trackPoints=v||[];},function(){});
}
function trackPointById(id){
  var i;
  for(i=0;i<trackPoints.length;i++){
    if(Number(trackPoints[i].id)===Number(id))return trackPoints[i];
  }
  return null;
}
function addTrackDiv(cls,x,y,w,h){
  var e=document.createElement('div');
  e.className=cls;
  e.style.left=(Number(x)*100)+'%';
  e.style.top=(Number(y)*100)+'%';
  if(w!=null)e.style.width=w+'px';
  if(h!=null)e.style.height=h+'px';
  marks.appendChild(e);
  return e;
}
function renderBoardMoveTrack(s){
  var time,start,hunting,used,currentIndex,currentPoint,specials,i,m,idx,p,ia,ib,a,b,mx,my,gap,nativeWidth;
  if(!trackPoints.length)return;
  time=Math.max(1,Math.min(5,Number(s.time||1)));
  start=5-time;
  hunting=(String(s.phase||'').indexOf('HUNT_')===0)||(String(s.phase||'').indexOf('GAME_OVER')===0);
  used=hunting?Math.max(0,Number(s.moveTrack||0)):0;
  currentIndex=Math.max(0,Math.min(19,start+used));
  currentPoint=trackPointById(currentIndex+1);
  if(hunting){
    specials=s.moveSpecials||[];
    for(i=0;i<specials.length;i++){
      m=specials[i];
      if(m.type==='ALLEY'){
        idx=start+Number(m.from||0);p=trackPointById(idx+1);
        if(p)addTrackDiv('track-alley-map',Number(p.norm_x),Number(p.norm_y),56,47);
      }else if(m.type==='COACH'){
        ia=start+Number(m.from||0);ib=start+Number(m.to||0);
        a=trackPointById(ia+1);b=trackPointById(ib+1);
        if(a&&b){
          mx=(Number(a.norm_x)+Number(b.norm_x))/2;
          my=(Number(a.norm_y)+Number(b.norm_y))/2;
          gap=Math.max(1,Math.abs(Number(b.x)-Number(a.x)));
          nativeWidth=gap*1.55;
          addTrackDiv('track-coach-map',mx,my,nativeWidth,nativeWidth*(320/407));
        }
      }
    }
  }
  if(currentPoint)addTrackDiv('track-jack-map',Number(currentPoint.norm_x),Number(currentPoint.norm_y),36,36);
}
function renderState(s){
  var a,women,police,crimes,clues,i,v,p,c,colors,timeLabel;
  a=appearanceOf(s);
  applyAppearance(a);
  marks.innerHTML='';
  women=s.women||[];
  for(i=0;i<women.length;i++){
    v=women[i];
    mk('woman',v.x,v.y,(v.revealed&&v.real)?a.victimRealColor:a.victimFakeColor,a.victimFillAlpha);
  }
  police=s.digitalPolice||[];
  colors=a.policeColors||defaultAppearance.policeColors;
  for(i=0;i<police.length;i++){
    p=police[i];
    c=(p.revealed&&p.real)?colors[Math.max(0,Math.min(4,(p.id||1)-1))]:colors[5];
    mk('patrol',p.x,p.y,c,a.policeFillAlpha);
  }
  crimes=s.crime||[];
  for(i=0;i<crimes.length;i++){v=crimes[i];mk(v.current?'crime current':'crime',v.x,v.y);}
  clues=s.clues||[];
  for(i=0;i<clues.length;i++){v=clues[i];mk('clue',v.x,v.y,a.clueColor,a.clueFillAlpha);}
  renderBoardMoveTrack(s);
  timeLabel=['','I','II','III','IV','V'][Number(s.time||0)]||'';
  document.getElementById('game').innerHTML='شب '+(s.night||1)+' &bull; '+(s.publicPhase||s.phase||'')+' &bull; '+timeLabel;
  document.getElementById('event').textContent=s.publicMessage||'';
  document.getElementById('status').innerHTML='فعال';
}
var refreshBusy=false;
function refresh(){
  if(refreshBusy)return;
  refreshBusy=true;
  xhrJson('/state?t='+(new Date().getTime()),function(s){
    refreshBusy=false;
    try{renderState(s);}catch(e){document.getElementById('status').innerHTML='خطای نمایش';}
  },function(){
    refreshBusy=false;
    document.getElementById('status').innerHTML='قطع ارتباط';
  });
}
loadStaticData();
setInterval(refresh,700);
refresh();

wrap.addEventListener('wheel',function(e){
  e=e||window.event;
  if(e.preventDefault)e.preventDefault();
  var dy=(typeof e.deltaY==='number')?e.deltaY:(e.wheelDelta?-e.wheelDelta:0);
  var factor=dy<0?1.15:1/1.15;
  zoomAt(e.clientX||0,e.clientY||0,z*factor);
  return false;
},false);
wrap.addEventListener('mousedown',function(e){
  e=e||window.event;
  if((e.button!=null&&e.button!==0)||z<=minZ+0.0001)return;
  if(e.preventDefault)e.preventDefault();
  dragging=true;lastX=e.clientX;lastY=e.clientY;apply();
},false);
window.addEventListener('mousemove',function(e){
  if(!dragging)return;
  ox+=e.clientX-lastX;oy+=e.clientY-lastY;lastX=e.clientX;lastY=e.clientY;apply();
},false);
window.addEventListener('mouseup',function(){dragging=false;apply();},false);
var lastD=0,lastMidX=0,lastMidY=0;
function touchDistance(a,b){var dx=a.clientX-b.clientX,dy=a.clientY-b.clientY;return Math.sqrt(dx*dx+dy*dy);}
wrap.addEventListener('touchstart',function(e){
  if(e.preventDefault)e.preventDefault();
  if(e.touches.length===1){lastX=e.touches[0].clientX;lastY=e.touches[0].clientY;}
  else if(e.touches.length===2){
    var a=e.touches[0],b=e.touches[1];lastD=touchDistance(a,b);lastMidX=(a.clientX+b.clientX)/2;lastMidY=(a.clientY+b.clientY)/2;
  }
},false);
wrap.addEventListener('touchmove',function(e){
  if(e.preventDefault)e.preventDefault();
  if(e.touches.length===1){
    var t=e.touches[0];if(z>minZ+0.0001){ox+=t.clientX-lastX;oy+=t.clientY-lastY;}lastX=t.clientX;lastY=t.clientY;apply();
  }else if(e.touches.length===2){
    var a=e.touches[0],b=e.touches[1],d=touchDistance(a,b),midX=(a.clientX+b.clientX)/2,midY=(a.clientY+b.clientY)/2;
    if(lastD>0){zoomAt(midX,midY,z*(d/lastD));ox+=midX-lastMidX;oy+=midY-lastMidY;apply();}
    lastD=d;lastMidX=midX;lastMidY=midY;
  }
},false);
wrap.addEventListener('touchend',function(e){
  lastD=0;if(e.touches&&e.touches.length===1){lastX=e.touches[0].clientX;lastY=e.touches[0].clientY;}
},false);
wrap.addEventListener('dblclick',function(e){if(e.preventDefault)e.preventDefault();fit();},false);
</script></body></html>"""
}
