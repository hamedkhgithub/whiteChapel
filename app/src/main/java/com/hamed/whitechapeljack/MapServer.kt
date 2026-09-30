package com.hamed.whitechapeljack

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
                "/qr" -> sendQr(s)
                "/intro-hell" -> sendAsset(s, "intro-hell.html", "text/html; charset=utf-8")
                "/intro-hunting" -> sendAsset(s, "intro-hunting.html", "text/html; charset=utf-8")
                else -> sendText(s, "text/plain; charset=utf-8", "Not found", status = "404 Not Found")
            }
        }
    }

    private fun sendQr(socket: Socket) {
        val value = TvServerInfo.url.ifBlank { "http://${localIp()}:$port" }
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 320, 320)
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        for (y in 0 until 320) {
            for (x in 0 until 320) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()
        val header = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store, no-cache, must-revalidate, max-age=0\r\nPragma: no-cache\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
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
#qrbox{position:absolute;right:10px;top:10px;z-index:1000;background:#fff;padding:6px;border:2px solid #111;box-shadow:0 2px 8px #0009;box-sizing:border-box}
#qrimg{display:block;width:120px;height:120px}
#introOverlay{position:fixed;left:0;top:0;width:100%;height:100%;background:#000;z-index:5000;display:none}
#introFrame{display:block;width:100%;height:100%;border:0;background:#000}
.m{position:absolute;transform:translate(-50%,-50%);box-sizing:border-box}
.number-badge{position:absolute;transform:translate(-50%,-50%);width:23px;height:23px;border-radius:50%;background:rgba(255,255,255,.92);border:1px solid rgba(0,0,0,.45);color:#000;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;line-height:1;box-sizing:border-box}.number-badge.red-house{background:rgba(211,47,47,.94);border-color:rgba(122,12,12,.85);color:#fff}
.woman{width:42px;height:42px;border:0;background:transparent}
.patrol{width:40px;height:40px;border:0;background:transparent}
.crime{width:35px;height:35px;border:0;background:transparent}
.clue{width:32px;height:32px;border:0;background:transparent}
.setup-crossing{position:absolute;transform:translate(-50%,-50%);border:3px solid #ffd600;background:transparent;box-sizing:border-box}

/* Move Track markers are rendered directly on the printed board track. */
.track-jack-map{position:absolute;transform:translate(-50%,-50%);width:36px;height:36px;border-radius:50%;background-image:url('/jack-token');background-size:cover;background-position:center;border:2px solid #2b2b2b;box-shadow:0 1px 4px #000b;z-index:15;box-sizing:border-box}
.track-alley-map{position:absolute;transform:translate(-50%,-50%);width:56px;height:47px;background:url('/alley-token') center/contain no-repeat;z-index:13;box-sizing:border-box;filter:drop-shadow(0 1px 2px #0009)}
.track-coach-map{position:absolute;transform:translate(-50%,-50%);background:url('/coach-token') center/contain no-repeat;z-index:12;box-sizing:border-box;filter:drop-shadow(0 1px 3px #0009)}
</style></head><body>
<div id="top"><div id="headline"><span>WhiteChapel Map • Legacy TV</span><span id="game"></span><span id="status">در حال اتصال…</span></div><div id="event"></div></div>
<div id="wrap"><div id="stage"><img id="base" src="/map"><div id="numbers" class="layer"></div><div id="marks" class="layer"></div></div><div id="qrbox"><img id="qrimg" src="/qr" alt="Server QR"></div></div>
<div id="introOverlay"><iframe id="introFrame" frameborder="0"></iframe></div>
<script>
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
  clueColor:'#F0C52B',clueSize:1,clueFillAlpha:.32,
  crimeSceneColor:'#D01818',crimeSceneSize:1,yellowCrossingSize:1,houseNumberScale:1,
  overlayOffsetX:0,overlayOffsetY:0,overlayScale:1,publicUiTextScale:1,publicQrScale:1
};
var currentAppearance=defaultAppearance;
var lastIntroId=0;
var introHideTimer=null;
function handleIntro(s){
  var id=Number(s.introId||0),type=String(s.introType||''),overlay=document.getElementById('introOverlay'),frame=document.getElementById('introFrame'),url,houses,i,houseParts=[];
  if(!id||!type||id===lastIntroId)return;
  lastIntroId=id;
  if(type==='hunting'){
    houses=s.introHouses||[];
    for(i=0;i<houses.length;i++)houseParts.push(Number(houses[i]));
    houseParts.sort(function(a,b){return a-b;});
    url='/intro-hunting?houses='+encodeURIComponent(houseParts.join(','))+'&id='+id;
  }else{
    url='/intro-hell?night='+Number(s.night||1)+'&id='+id;
  }
  frame.src=url;
  overlay.style.display='block';
  if(introHideTimer)clearTimeout(introHideTimer);
  introHideTimer=setTimeout(function(){overlay.style.display='none';frame.src='about:blank';},type==='hunting'?4900:4100);
}

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
function calibratedX(x){
  var scale=Number(currentAppearance.overlayScale||1);
  var offset=Number(currentAppearance.overlayOffsetX||0);
  return Number(x)*1536*scale+offset;
}
function calibratedY(y){
  var scale=Number(currentAppearance.overlayScale||1);
  var offset=Number(currentAppearance.overlayOffsetY||0);
  return Number(y)*1024*scale+offset;
}
function placeCalibrated(e,x,y){
  e.style.left=calibratedX(x)+'px';
  e.style.top=calibratedY(y)+'px';
}
function applyAppearance(a){
  var uiScale,qrScale,top,event,qr;
  currentAppearance=a||defaultAppearance;
  uiScale=Math.max(.7,Math.min(2,Number(currentAppearance.publicUiTextScale||1)));
  qrScale=Math.max(.5,Math.min(2,Number(currentAppearance.publicQrScale||1)));
  top=document.getElementById('top');
  event=document.getElementById('event');
  qr=document.getElementById('qrimg');
  if(top)top.style.fontSize=(16*uiScale)+'px';
  if(event)event.style.fontSize=(13*uiScale)+'px';
  if(qr){qr.style.width=(120*qrScale)+'px';qr.style.height=(120*qrScale)+'px';}
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
function addSearchSvg(e,color,fillAlpha){
  var svg=svgEl('svg');svg.setAttribute('viewBox','0 0 512.001 512.001');svg.setAttribute('width','100%');svg.setAttribute('height','100%');
  addSvgPath(svg,'M283.097,0C156.88,0,54.194,102.686,54.194,228.904c0,37.27,8.959,72.485,24.827,103.613l-58.247,58.247 c-27.699,27.697-27.699,72.766,0,100.463C34.622,505.077,52.814,512,71.005,512c18.192,0,36.385-6.924,50.232-20.773 l58.246-58.247c31.131,15.869,66.346,24.827,103.614,24.827c126.217,0,228.903-102.686,228.903-228.903S409.315,0,283.097,0z M87.751,457.739c-9.235,9.235-24.256,9.232-33.489,0c-9.234-9.233-9.234-24.256,0-33.488l51.18-51.178 c9.983,12.279,21.209,23.504,33.488,33.488L87.751,457.739z M283.097,410.448c-100.103,0-181.544-81.441-181.544-181.544 S182.994,47.36,283.097,47.36S464.641,128.8,464.641,228.904S383.201,410.448,283.097,410.448z',color,fillAlpha,color,10);
  e.appendChild(svg);
}
function addTargetSvg(e,color){
  var svg=svgEl('svg');svg.setAttribute('viewBox','0 0 512 512');svg.setAttribute('width','100%');svg.setAttribute('height','100%');
  addSvgPath(svg,'M256,0C114.84,0,0,114.842,0,256s114.84,256,256,256s256-114.842,256-256S397.16,0,256,0z M280.774,460.96v-56.315 h-49.548v56.315C137.152,449.658,62.342,374.848,51.04,280.774h56.315v-49.548H51.04C62.342,137.152,137.152,62.342,231.226,51.04 v56.315h49.548V51.04c94.073,11.302,168.884,86.112,180.186,180.186h-56.315v49.548h56.315 C449.658,374.848,374.847,449.658,280.774,460.96z',color,1,null,0);
  e.appendChild(svg);
}
function mk(cls,x,y,color,fillAlpha){
  var e=document.createElement('div');
  var size;
  e.className='m '+cls;
  placeCalibrated(e,x,y);
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
    addSearchSvg(e,color,fillAlpha);
  }else if(cls==='crime'){
    size=35*(currentAppearance.crimeSceneSize||1);
    e.style.width=size+'px';e.style.height=size+'px';
    addTargetSvg(e,color);
  }else if(cls==='setup-crossing'){
    size=38*(currentAppearance.yellowCrossingSize||1);
    e.style.width=size+'px';e.style.height=size+'px';
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
    placeCalibrated(e,Number(p.norm_x||0),Number(p.norm_y||0));
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
  placeCalibrated(e,x,y);
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
  var a,women,police,crimes,clues,setupCrossings,i,v,p,c,colors,timeLabel;
  a=appearanceOf(s);
  applyAppearance(a);
  marks.innerHTML='';
  setupCrossings=s.setupCrossings||[];
  for(i=0;i<setupCrossings.length;i++){v=setupCrossings[i];mk('setup-crossing',v.x,v.y,'#FFD600',1);}
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
  for(i=0;i<crimes.length;i++){v=crimes[i];mk('crime',v.x,v.y,v.current?a.crimeSceneColor:'#8B8B8B',1);}
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
    try{handleIntro(s);renderState(s);}catch(e){document.getElementById('status').innerHTML='خطای نمایش';}
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
