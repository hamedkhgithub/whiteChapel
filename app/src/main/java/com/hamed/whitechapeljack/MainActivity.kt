package com.hamed.whitechapeljack

import android.content.Context
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.concurrent.thread

private val Gold=Color(0xFFD6AD63)
private val Blood=Color(0xFFB41616)
private val Ink=Color(0xFF090807)
private val GrayCard=Color(0xFFD0D0CE)
private val GrayField=Color(0xFFE1E0DC)
private val DarkButton=Color(0xE91A1510)
private val Green=Color(0xFF075D2D)
private val Blue=Color(0xFF155B87)
private val AlleyRed=Color(0xFFD32626)

enum class MoveType { NORMAL, COACH, ALLEY }
enum class InquiryType { SEARCH, ARREST }
data class JackMove(val night:Int,val turn:Int,val first:Int,val second:Int?,val type:MoveType)
data class Inquiry(val night:Int,val index:Int,val house:Int,val type:InquiryType,val positive:Boolean)

class GameStore(ctx:Context){
 private val p=ctx.getSharedPreferences("whitechapel_near_final",Context.MODE_PRIVATE)
 fun exists()=p.contains("game")
 fun clear()=p.edit().clear().apply()
 fun save(pinHash:String,hideout:Int,night:Int,starts:Map<Int,Int>,moves:List<JackMove>,queries:List<Inquiry>,escaped:Set<Int>,gameOver:Boolean){
  val o=JSONObject().put("pin",pinHash).put("hideout",hideout).put("night",night).put("gameOver",gameOver)
  val s=JSONObject();starts.forEach{s.put(it.key.toString(),it.value)};o.put("starts",s)
  val ma=JSONArray();moves.forEach{ma.put(JSONObject().put("n",it.night).put("t",it.turn).put("a",it.first).put("b",it.second).put("y",it.type.name))};o.put("moves",ma)
  val qa=JSONArray();queries.forEach{qa.put(JSONObject().put("n",it.night).put("i",it.index).put("h",it.house).put("t",it.type.name).put("p",it.positive))};o.put("queries",qa)
  val ea=JSONArray();escaped.sorted().forEach{ea.put(it)};o.put("escaped",ea)
  p.edit().putString("game",o.toString()).apply()
 }
 fun load():JSONObject?=p.getString("game",null)?.let{runCatching{JSONObject(it)}.getOrNull()}
 companion object{fun hash(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}}
}


class PilotMapServer(context: Context) {
 private val appContext=context.applicationContext
 private val prefs=appContext.getSharedPreferences("whitechapel_map_mode",Context.MODE_PRIVATE)
 @Volatile private var running=false
 private var socket:ServerSocket?=null
 val port=8080
 private val policePositions=java.util.concurrent.ConcurrentHashMap<String,String>()
 private val clues=java.util.concurrent.CopyOnWriteArraySet<Int>()
 @Volatile private var jackPosition:Int?=null
 @Volatile private var visited:Set<Int> = emptySet()
 @Volatile private var revealRoute:List<Int> = emptyList()
 @Volatile private var arrested=false
 init{
  listOf("red","blue","green","yellow","brown").forEach{c->prefs.getString("police_$c",null)?.let{policePositions[c]=it}}
  prefs.getStringSet("clues",emptySet())?.mapNotNull{it.toIntOrNull()}?.forEach{clues+=it}
 }
 fun setJackState(current:Int?,visitedHouses:Set<Int>,route:List<Int>){jackPosition=current;visited=visitedHouses;revealRoute=route}
 fun resetPublicState(){policePositions.clear();clues.clear();arrested=false;prefs.edit().clear().apply()}
 private fun persist(){val e=prefs.edit();policePositions.forEach{(k,v)->e.putString("police_$k",v)};e.putStringSet("clues",clues.map{it.toString()}.toSet());e.apply()}
 fun start():String { if(!running){running=true;thread(name="whitechapel-pilot-server",isDaemon=true){try{socket=ServerSocket(port);while(running){val client=socket?.accept()?:break;thread(isDaemon=true){client.use{c->runCatching{handle(c)}}}}}catch(_:Exception){}finally{running=false;runCatching{socket?.close()};socket=null}}};return "http://${localIpv4()}:$port" }
 private fun handle(c:java.net.Socket){
  val reader=c.getInputStream().bufferedReader();val requestLine=reader.readLine().orEmpty();while(true){val line=reader.readLine()?:break;if(line.isBlank())break}
  val raw=requestLine.split(" ").getOrNull(1)?:"/";val path=raw.substringBefore("?");val query=raw.substringAfter("?","")
  val params=query.split("&").mapNotNull{q->val a=q.split("=",limit=2);if(a.size==2)java.net.URLDecoder.decode(a[0],"UTF-8") to java.net.URLDecoder.decode(a[1],"UTF-8") else null}.toMap()
  val bytes:ByteArray;val contentType:String
  when(path){
   "/map.webp","/map"->{bytes=appContext.assets.open("whitechapel_map.webp").use{it.readBytes()};contentType="image/webp"}
   "/move"->{val police=params["police"];val node=params["node"];if(police in COLORS&&node!=null&&POLICE_IDS.contains(node)){policePositions[police!!]=node;persist()};bytes="{\"ok\":true}".toByteArray();contentType="application/json"}
   "/action"->{val type=params["type"];val house=params["house"]?.toIntOrNull();var positive=false;if(house!=null&&house in 1..195){positive=if(type=="arrest")jackPosition==house else visited.contains(house);if(type=="search"&&positive){clues+=house;persist()};if(type=="arrest"&&positive)arrested=true};bytes="{\"ok\":true,\"positive\":$positive,\"gameOver\":$arrested}".toByteArray();contentType="application/json"}
   "/state"->{bytes=stateJson().toByteArray();contentType="application/json"}
   "/controller"->{bytes=controllerHtml().toByteArray();contentType="text/html; charset=utf-8"}
   else->{bytes=displayHtml().toByteArray();contentType="text/html; charset=utf-8"}
  }
  val header="HTTP/1.1 200 OK\r\n" +
   "Content-Type: $contentType\r\n" +
   "Content-Length: ${bytes.size}\r\n" +
   "Cache-Control: no-cache\r\n" +
   "Connection: close\r\n\r\n"
  c.getOutputStream().apply{write(header.toByteArray());write(bytes);flush()}
 }
 private fun stateJson():String{val p=policePositions.entries.joinToString(","){"\"${it.key}\":\"${it.value}\""};val cs=clues.sorted().joinToString(",");val rr=revealRoute.joinToString(",");return "{\"police\":{$p},\"clues\":[$cs],\"gameOver\":$arrested,\"route\":[$rr]}"}
 private fun displayHtml()="""<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body{margin:0;background:#090807;width:100%;height:100%;overflow:hidden}#wrap{position:relative;width:100vw;height:100vh;display:flex;align-items:center;justify-content:center}#map{max-width:100%;max-height:100%;display:block}.p{position:absolute;width:24px;height:24px;border-radius:50%;border:3px solid white;box-shadow:0 2px 8px #000;transform:translate(-50%,-50%);z-index:5}.red{background:#d32222}.blue{background:#1976d2}.green{background:#16813b}.yellow{background:#f0c51a}.brown{background:#5b3020}.clue{position:absolute;width:14px;height:14px;border-radius:50%;background:#ffd54f;border:2px solid #5a3700;transform:translate(-50%,-50%);z-index:4}.route{position:absolute;width:13px;height:13px;border-radius:50%;background:#b41616;border:2px solid white;transform:translate(-50%,-50%);z-index:6}#over{display:none;position:fixed;inset:0;background:#090807dd;color:#e6c47b;z-index:20;text-align:center;padding-top:8vh;font:700 30px sans-serif}</style></head><body><div id="wrap"><img id="map" src="/map.webp"></div><div id="over">جک دستگیر شد<br><small>مسیر حرکت جک روی نقشه نمایش داده شده است</small></div><script>
const colors=['red','blue','green','yellow','brown'],pnodes=POLICE_JS,jnodes=JACK_JS;
function put(cls,id,n,img,wrap){let e=document.getElementById(id);if(!e){e=document.createElement('div');e.id=id;e.className=cls;wrap.appendChild(e)}let ir=img.getBoundingClientRect(),wr=wrap.getBoundingClientRect();e.style.left=(ir.left-wr.left+n.x*ir.width)+'px';e.style.top=(ir.top-wr.top+n.y*ir.height)+'px'}
function sync(){fetch('/state').then(r=>r.json()).then(s=>{let img=document.getElementById('map'),wrap=document.getElementById('wrap');colors.forEach(c=>{let n=pnodes.find(x=>x.id===s.police[c]);let e=document.getElementById('p_'+c);if(n)put('p '+c,'p_'+c,n,img,wrap);else if(e)e.style.display='none'});document.querySelectorAll('.clue,.route').forEach(e=>e.remove());s.clues.forEach(h=>{let n=jnodes.find(x=>x.id==='J'+String(h).padStart(3,'0'));if(n)put('clue','c'+h,n,img,wrap)});if(s.gameOver){document.getElementById('over').style.display='block';s.route.forEach((h,i)=>{let n=jnodes.find(x=>x.id==='J'+String(h).padStart(3,'0'));if(n)put('route','r'+i,n,img,wrap)})}}).catch(()=>{})}setInterval(sync,400);window.addEventListener('resize',sync);document.getElementById('map').onload=sync;
</script></body></html>""".replace("POLICE_JS",POLICE_JS).replace("JACK_JS",JACK_JS)
 private fun controllerHtml()="""<!doctype html><html dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=8,user-scalable=yes"><style>body{margin:0;background:#0b0907;color:#e6c47b;font-family:sans-serif}#bar,#actions{position:sticky;z-index:20;background:#17120e;padding:7px;display:flex;gap:5px;overflow:auto}#bar{top:0}#actions{top:52px}button{padding:9px 11px;border:1px solid #d6ad63;background:#292019;color:white;border-radius:8px;white-space:nowrap}button.on{outline:3px solid #d6ad63}.done{opacity:.45}#mapbox{position:relative;width:1536px;height:1024px}#map{width:1536px;height:1024px;display:block}.node{position:absolute;width:17px;height:17px;border-radius:50%;border:2px solid #00e5ff;background:#001a22aa;transform:translate(-50%,-50%);z-index:4}.jnode{position:absolute;width:15px;height:15px;border-radius:50%;border:2px solid #f0c34e;background:#2b1800aa;transform:translate(-50%,-50%);z-index:3}.p{position:absolute;width:28px;height:28px;border-radius:50%;border:3px solid white;transform:translate(-50%,-50%);z-index:8;box-shadow:0 2px 8px #000}.red{background:#d32222}.blue{background:#1976d2}.green{background:#16813b}.yellow{background:#f0c51a}.brown{background:#5b3020}.clue{position:absolute;width:16px;height:16px;border-radius:50%;background:#ffd54f;border:2px solid #5a3700;transform:translate(-50%,-50%);z-index:7}#hint{padding:7px;text-align:center}</style></head><body><div id="bar"><button data-p="red">🔴 قرمز</button><button data-p="blue">🔵 آبی</button><button data-p="green">🟢 سبز</button><button data-p="yellow">🟡 زرد</button><button data-p="brown">🟤 قهوه‌ای</button></div><div id="actions"><button data-a="move">جابجایی</button><button data-a="search">🔎 سرنخ</button><button data-a="arrest">⛓ دستگیری</button></div><div id="hint">پلیس را انتخاب کنید؛ سپس جابجایی، سرنخ یا دستگیری را انتخاب کنید.</div><div id="mapbox"><img id="map" src="/map.webp"></div><script>
let police=null,action='move';const pnodes=POLICE_JS,jnodes=JACK_JS,colors=['red','blue','green','yellow','brown'];const done={};const box=document.getElementById('mapbox'),hint=document.getElementById('hint');
document.querySelectorAll('[data-p]').forEach(b=>b.onclick=()=>{if(done[b.dataset.p])return;police=b.dataset.p;document.querySelectorAll('[data-p]').forEach(x=>x.classList.remove('on'));b.classList.add('on');hint.innerText='پلیس انتخاب شد؛ نوع عمل را انتخاب کنید.'});document.querySelectorAll('[data-a]').forEach(b=>b.onclick=()=>{action=b.dataset.a;document.querySelectorAll('[data-a]').forEach(x=>x.classList.remove('on'));b.classList.add('on');hint.innerText=action==='move'?'یک نقطه آبی را انتخاب کنید':'یک خانه زرد را انتخاب کنید'});
function marker(cls,id,n){let d=document.getElementById(id);if(!d){d=document.createElement('div');d.id=id;d.className=cls;box.appendChild(d)}d.className=cls;d.style.left=(n.x*1536)+'px';d.style.top=(n.y*1024)+'px';if(id.startsWith('pp_')){let c=id.substring(3);d.onclick=e=>{e.stopPropagation();if(done[c])return;police=c;document.querySelectorAll('[data-p]').forEach(x=>x.classList.toggle('on',x.dataset.p===c));hint.innerText='مهره '+c+' انتخاب شد؛ مقصد یا اکشن را انتخاب کنید.'}}}
pnodes.forEach(n=>{let d=document.createElement('div');d.className='node';d.title=n.id;d.style.left=(n.x*1536)+'px';d.style.top=(n.y*1024)+'px';d.onclick=e=>{e.stopPropagation();if(!police||action!=='move'){hint.innerText='برای جابجایی، پلیس و گزینه جابجایی را انتخاب کنید';return}fetch('/move?police='+police+'&node='+n.id).then(sync)};box.appendChild(d)});
jnodes.forEach(n=>{let d=document.createElement('div');d.className='jnode';d.title=n.id;d.style.left=(n.x*1536)+'px';d.style.top=(n.y*1024)+'px';d.onclick=e=>{e.stopPropagation();if(!police||action==='move'){hint.innerText='ابتدا پلیس و اکشن سرنخ/دستگیری را انتخاب کنید';return}let h=parseInt(n.id.substring(1));fetch('/action?type='+action+'&house='+h).then(r=>r.json()).then(x=>{if(action==='arrest'){done[police]=true;document.querySelector('[data-p="'+police+'"]').classList.add('done');if(x.positive){alert('جک دستگیر شد — بازی تمام شد');hint.innerText='جک دستگیر شد';}else{alert('دستگیری ناموفق بود. نوبت این مهره تمام شد.');hint.innerText='دستگیری ناموفق؛ مهره بعدی را انتخاب کنید.'}}else{if(x.positive){done[police]=true;document.querySelector('[data-p="'+police+'"]').classList.add('done');alert('سرنخ پیدا شد. نوبت این مهره تمام شد.')}else alert('سرنخی پیدا نشد؛ می‌توانید با همین مهره جستجو را ادامه دهید.')}sync()})};box.appendChild(d)});
function sync(){fetch('/state').then(r=>r.json()).then(s=>{colors.forEach(c=>{let n=pnodes.find(x=>x.id===s.police[c]);if(n)marker('p '+c,'pp_'+c,n)});document.querySelectorAll('.clue').forEach(e=>e.remove());s.clues.forEach(h=>{let n=jnodes.find(x=>x.id==='J'+String(h).padStart(3,'0'));if(n)marker('clue','cc_'+h,n)})})}setInterval(sync,700);sync();
</script></body></html>""".replace("POLICE_JS",POLICE_JS).replace("JACK_JS",JACK_JS)
 fun stop(){running=false;runCatching{socket?.close()};socket=null}
 private fun localIpv4():String=runCatching{NetworkInterface.getNetworkInterfaces().toList().flatMap{it.inetAddresses.toList()}.filterIsInstance<Inet4Address>().firstOrNull{!it.isLoopbackAddress&&it.isSiteLocalAddress}?.hostAddress?:"127.0.0.1"}.getOrDefault("127.0.0.1")
 companion object{private val COLORS=setOf("red","blue","green","yellow","brown");private val POLICE_IDS=setOf("P001","P002","P003","P004","P005","P006","P007","P008","P009","P010","P011","P012","P013","P014","P015","P016","P017","P018","P019","P020","P021","P022","P023","P024","P025","P026","P027","P028","P029","P030","P031","P032","P033","P034","P035","P036","P037","P038","P039","P040","P041","P042","P043","P044","P045","P046","P047","P048","P049","P050","P051","P052","P053","P054","P055","P056","P057","P058","P059","P060","P061","P062","P063","P064","P065","P066","P067","P068","P069","P070","P071","P072","P073","P074","P075","P076","P077","P078","P079","P080","P081","P082","P083","P084","P085","P086","P087","P088","P089","P090","P091","P092","P093","P094","P095","P096","P097","P098","P099","P100","P101","P102","P103","P104","P105","P106","P107","P108","P109","P110","P111","P112","P113","P114","P115","P116","P117","P118","P119","P120","P121","P122","P123","P124","P125","P126","P127","P128","P129","P130","P131","P132","P133","P134","P135","P136","P137","P138","P139","P140","P141","P142","P143","P144","P145","P146","P147","P148","P149","P150","P151","P152","P153","P154","P155","P156","P157","P158","P159","P160","P161","P162","P163","P164","P165","P166","P167","P168","P169","P170","P171","P172","P173","P174","P175","P176","P177","P178");private const val POLICE_JS="""[{id:'P001',x:0.376302,y:0.035156},{id:'P002',x:0.937774,y:0.037842},{id:'P003',x:0.883726,y:0.064847},{id:'P004',x:0.469623,y:0.088971},{id:'P005',x:0.378596,y:0.091376},{id:'P006',x:0.597057,y:0.091589},{id:'P007',x:0.313921,y:0.09232},{id:'P008',x:0.669303,y:0.094775},{id:'P009',x:0.137984,y:0.097068},{id:'P010',x:0.109375,y:0.097656},{id:'P011',x:0.736261,y:0.09897},{id:'P012',x:0.266392,y:0.106399},{id:'P013',x:0.806605,y:0.113941},{id:'P014',x:0.789818,y:0.126818},{id:'P015',x:0.379942,y:0.142105},{id:'P016',x:0.905459,y:0.144},{id:'P017',x:0.310547,y:0.145996},{id:'P018',x:0.819043,y:0.14751},{id:'P019',x:0.265616,y:0.151914},{id:'P020',x:0.743498,y:0.152868},{id:'P021',x:0.601604,y:0.164384},{id:'P022',x:0.86271,y:0.16631},{id:'P023',x:0.160314,y:0.169567},{id:'P024',x:0.137988,y:0.169971},{id:'P025',x:0.572132,y:0.173899},{id:'P026',x:0.433919,y:0.17688},{id:'P027',x:0.546283,y:0.183949},{id:'P028',x:0.518589,y:0.198037},{id:'P029',x:0.803662,y:0.200342},{id:'P030',x:0.26647,y:0.209982},{id:'P031',x:0.48863,y:0.2131},{id:'P032',x:0.109375,y:0.219727},{id:'P033',x:0.613777,y:0.220145},{id:'P034',x:0.185571,y:0.228577},{id:'P035',x:0.461046,y:0.229804},{id:'P036',x:0.311508,y:0.232747},{id:'P037',x:0.927618,y:0.235247},{id:'P038',x:0.153441,y:0.236742},{id:'P039',x:0.644734,y:0.242504},{id:'P040',x:0.58678,y:0.247656},{id:'P041',x:0.227772,y:0.24877},{id:'P042',x:0.190137,y:0.255217},{id:'P043',x:0.400682,y:0.258927},{id:'P044',x:0.128581,y:0.26123},{id:'P045',x:0.104167,y:0.262207},{id:'P046',x:0.500019,y:0.265262},{id:'P047',x:0.688676,y:0.269633},{id:'P048',x:0.348497,y:0.271564},{id:'P049',x:0.262594,y:0.274152},{id:'P050',x:0.471613,y:0.278952},{id:'P051',x:0.828194,y:0.279802},{id:'P052',x:0.767321,y:0.283034},{id:'P053',x:0.532812,y:0.291981},{id:'P054',x:0.411306,y:0.298738},{id:'P055',x:0.115374,y:0.29913},{id:'P056',x:0.139625,y:0.312852},{id:'P057',x:0.498708,y:0.314497},{id:'P058',x:0.538604,y:0.314508},{id:'P059',x:0.624202,y:0.315461},{id:'P060',x:0.355384,y:0.315797},{id:'P061',x:0.302207,y:0.326209},{id:'P062',x:0.272193,y:0.328224},{id:'P063',x:0.204756,y:0.330127},{id:'P064',x:0.832698,y:0.330619},{id:'P065',x:0.451799,y:0.33724},{id:'P066',x:0.230246,y:0.337331},{id:'P067',x:0.588526,y:0.339703},{id:'P068',x:0.773938,y:0.345894},{id:'P069',x:0.431808,y:0.348172},{id:'P070',x:0.190482,y:0.359318},{id:'P071',x:0.723706,y:0.359839},{id:'P072',x:0.562598,y:0.362488},{id:'P073',x:0.152235,y:0.36671},{id:'P074',x:0.369855,y:0.369331},{id:'P075',x:0.681671,y:0.37326},{id:'P076',x:0.839876,y:0.374072},{id:'P077',x:0.442254,y:0.376318},{id:'P078',x:0.642017,y:0.377979},{id:'P079',x:0.585175,y:0.385969},{id:'P080',x:0.204221,y:0.387321},{id:'P081',x:0.779122,y:0.388734},{id:'P082',x:0.907734,y:0.40043},{id:'P083',x:0.521999,y:0.408101},{id:'P084',x:0.233619,y:0.410689},{id:'P085',x:0.161857,y:0.414609},{id:'P086',x:0.84544,y:0.415504},{id:'P087',x:0.706706,y:0.42627},{id:'P088',x:0.683948,y:0.430116},{id:'P089',x:0.782584,y:0.430605},{id:'P090',x:0.337089,y:0.431836},{id:'P091',x:0.730469,y:0.435547},{id:'P092',x:0.458127,y:0.436734},{id:'P093',x:0.917074,y:0.442078},{id:'P094',x:0.286516,y:0.447365},{id:'P095',x:0.630661,y:0.451278},{id:'P096',x:0.185639,y:0.451543},{id:'P097',x:0.850221,y:0.456996},{id:'P098',x:0.313127,y:0.464759},{id:'P099',x:0.550534,y:0.475413},{id:'P100',x:0.412722,y:0.477713},{id:'P101',x:0.734528,y:0.479469},{id:'P102',x:0.687083,y:0.481821},{id:'P103',x:0.924265,y:0.483465},{id:'P104',x:0.126285,y:0.484451},{id:'P105',x:0.206822,y:0.488685},{id:'P106',x:0.527353,y:0.499051},{id:'P107',x:0.43783,y:0.499617},{id:'P108',x:0.789949,y:0.515126},{id:'P109',x:0.366234,y:0.517241},{id:'P110',x:0.223384,y:0.517694},{id:'P111',x:0.161044,y:0.526824},{id:'P112',x:0.496544,y:0.531985},{id:'P113',x:0.620674,y:0.538558},{id:'P114',x:0.714811,y:0.53999},{id:'P115',x:0.935349,y:0.542251},{id:'P116',x:0.858472,y:0.546623},{id:'P117',x:0.514301,y:0.546776},{id:'P118',x:0.169082,y:0.563151},{id:'P119',x:0.427742,y:0.571584},{id:'P120',x:0.456784,y:0.572288},{id:'P121',x:0.293041,y:0.580037},{id:'P122',x:0.262234,y:0.586823},{id:'P123',x:0.064744,y:0.589792},{id:'P124',x:0.575521,y:0.59082},{id:'P125',x:0.60319,y:0.597656},{id:'P126',x:0.660117,y:0.60055},{id:'P127',x:0.121912,y:0.600639},{id:'P128',x:0.823552,y:0.605887},{id:'P129',x:0.497489,y:0.620257},{id:'P130',x:0.445054,y:0.623058},{id:'P131',x:0.105966,y:0.631181},{id:'P132',x:0.543743,y:0.639501},{id:'P133',x:0.749349,y:0.641602},{id:'P134',x:0.597683,y:0.643127},{id:'P135',x:0.639323,y:0.644531},{id:'P136',x:0.503255,y:0.645508},{id:'P137',x:0.411079,y:0.652088},{id:'P138',x:0.726205,y:0.664007},{id:'P139',x:0.667951,y:0.664419},{id:'P140',x:0.13781,y:0.681586},{id:'P141',x:0.42597,y:0.695029},{id:'P142',x:0.061679,y:0.708163},{id:'P143',x:0.340845,y:0.709827},{id:'P144',x:0.103963,y:0.718709},{id:'P145',x:0.305933,y:0.723295},{id:'P146',x:0.642578,y:0.725098},{id:'P147',x:0.384298,y:0.725173},{id:'P148',x:0.16794,y:0.728993},{id:'P149',x:0.57944,y:0.729883},{id:'P150',x:0.571933,y:0.755753},{id:'P151',x:0.396721,y:0.76032},{id:'P152',x:0.75,y:0.760742},{id:'P153',x:0.48725,y:0.762553},{id:'P154',x:0.314482,y:0.763156},{id:'P155',x:0.678025,y:0.772281},{id:'P156',x:0.66027,y:0.775171},{id:'P157',x:0.455589,y:0.784435},{id:'P158',x:0.644196,y:0.787856},{id:'P159',x:0.344184,y:0.79116},{id:'P160',x:0.517152,y:0.796976},{id:'P161',x:0.572997,y:0.799457},{id:'P162',x:0.400933,y:0.806836},{id:'P163',x:0.672493,y:0.816455},{id:'P164',x:0.263021,y:0.821777},{id:'P165',x:0.6204,y:0.824992},{id:'P166',x:0.600951,y:0.827101},{id:'P167',x:0.44261,y:0.834847},{id:'P168',x:0.575475,y:0.849893},{id:'P169',x:0.281887,y:0.867086},{id:'P170',x:0.351328,y:0.86758},{id:'P171',x:0.51375,y:0.868105},{id:'P172',x:0.674379,y:0.868252},{id:'P173',x:0.417052,y:0.876439},{id:'P174',x:0.224609,y:0.893555},{id:'P175',x:0.674805,y:0.893555},{id:'P176',x:0.425369,y:0.917289},{id:'P177',x:0.606771,y:0.92334},{id:'P178',x:0.676163,y:0.924699}]""";private const val JACK_JS="""[{id:'J001',x:0.296129,y:0.032555},{id:'J002',x:0.447586,y:0.032996},{id:'J003',x:0.348274,y:0.03305},{id:'J004',x:0.178728,y:0.043759},{id:'J005',x:0.26917,y:0.050776},{id:'J006',x:0.378011,y:0.064549},{id:'J007',x:0.847594,y:0.084534},{id:'J008',x:0.351092,y:0.085802},{id:'J009',x:0.425189,y:0.091114},{id:'J010',x:0.561645,y:0.091654},{id:'J011',x:0.643834,y:0.094949},{id:'J012',x:0.952573,y:0.10386},{id:'J013',x:0.311415,y:0.120361},{id:'J014',x:0.567857,y:0.12206},{id:'J015',x:0.476804,y:0.123782},{id:'J016',x:0.739102,y:0.127981},{id:'J017',x:0.688592,y:0.130252},{id:'J018',x:0.688463,y:0.133388},{id:'J019',x:0.559062,y:0.136758},{id:'J020',x:0.139821,y:0.141249},{id:'J021',x:0.344958,y:0.148327},{id:'J022',x:0.534377,y:0.152003},{id:'J023',x:0.286871,y:0.153175},{id:'J024',x:0.888212,y:0.154211},{id:'J025',x:0.88599,y:0.154449},{id:'J026',x:0.508483,y:0.157929},{id:'J027',x:0.799028,y:0.165039},{id:'J028',x:0.110131,y:0.166802},{id:'J029',x:0.19042,y:0.167979},{id:'J030',x:0.583267,y:0.172259},{id:'J031',x:0.271252,y:0.182055},{id:'J032',x:0.258961,y:0.18331},{id:'J033',x:0.749352,y:0.185738},{id:'J034',x:0.827557,y:0.186759},{id:'J035',x:0.393799,y:0.186863},{id:'J036',x:0.481103,y:0.191349},{id:'J037',x:0.606735,y:0.194351},{id:'J038',x:0.532567,y:0.19519},{id:'J039',x:0.628504,y:0.207571},{id:'J040',x:0.139531,y:0.211784},{id:'J041',x:0.5497,y:0.222232},{id:'J042',x:0.227247,y:0.222933},{id:'J043',x:0.561696,y:0.226017},{id:'J044',x:0.354993,y:0.227563},{id:'J045',x:0.604625,y:0.238895},{id:'J046',x:0.82595,y:0.24696},{id:'J047',x:0.428177,y:0.247759},{id:'J048',x:0.427759,y:0.248167},{id:'J049',x:0.717598,y:0.250249},{id:'J050',x:0.720012,y:0.250361},{id:'J051',x:0.169209,y:0.252344},{id:'J052',x:0.816094,y:0.253887},{id:'J053',x:0.523693,y:0.260267},{id:'J054',x:0.160111,y:0.262881},{id:'J055',x:0.378006,y:0.264792},{id:'J056',x:0.375528,y:0.265922},{id:'J057',x:0.486493,y:0.270078},{id:'J058',x:0.486169,y:0.270308},{id:'J059',x:0.286938,y:0.282695},{id:'J060',x:0.214334,y:0.284521},{id:'J061',x:0.205525,y:0.28457},{id:'J062',x:0.102488,y:0.286279},{id:'J063',x:0.664104,y:0.286655},{id:'J064',x:0.204336,y:0.294468},{id:'J065',x:0.442173,y:0.295164},{id:'J066',x:0.578132,y:0.305904},{id:'J067',x:0.51437,y:0.306099},{id:'J068',x:0.931034,y:0.307739},{id:'J069',x:0.380695,y:0.308518},{id:'J070',x:0.7739,y:0.310004},{id:'J071',x:0.770379,y:0.311407},{id:'J072',x:0.419783,y:0.320874},{id:'J073',x:0.421862,y:0.321506},{id:'J074',x:0.854327,y:0.326564},{id:'J075',x:0.47681,y:0.326616},{id:'J076',x:0.145549,y:0.342111},{id:'J077',x:0.365305,y:0.346386},{id:'J078',x:0.552882,y:0.347801},{id:'J079',x:0.252422,y:0.351505},{id:'J080',x:0.312364,y:0.354377},{id:'J081',x:0.837088,y:0.354496},{id:'J082',x:0.741413,y:0.354741},{id:'J083',x:0.17559,y:0.358564},{id:'J084',x:0.40286,y:0.360129},{id:'J085',x:0.258128,y:0.364741},{id:'J086',x:0.654798,y:0.368709},{id:'J087',x:0.699531,y:0.37025},{id:'J088',x:0.622674,y:0.37345},{id:'J089',x:0.662777,y:0.380278},{id:'J090',x:0.90457,y:0.382324},{id:'J091',x:0.807629,y:0.382348},{id:'J092',x:0.342095,y:0.38246},{id:'J093',x:0.807561,y:0.382919},{id:'J094',x:0.223521,y:0.383021},{id:'J095',x:0.222773,y:0.385081},{id:'J096',x:0.272641,y:0.392241},{id:'J097',x:0.536287,y:0.393569},{id:'J098',x:0.726686,y:0.399168},{id:'J099',x:0.68029,y:0.408844},{id:'J100',x:0.876808,y:0.409418},{id:'J101',x:0.877022,y:0.41199},{id:'J102',x:0.453251,y:0.412043},{id:'J103',x:0.212123,y:0.421675},{id:'J104',x:0.214099,y:0.423132},{id:'J105',x:0.399158,y:0.438174},{id:'J106',x:0.398633,y:0.438213},{id:'J107',x:0.307125,y:0.438676},{id:'J108',x:0.854917,y:0.441072},{id:'J109',x:0.540373,y:0.442101},{id:'J110',x:0.886669,y:0.447241},{id:'J111',x:0.883454,y:0.448391},{id:'J112',x:0.143553,y:0.453125},{id:'J113',x:0.608818,y:0.456767},{id:'J114',x:0.582595,y:0.459597},{id:'J115',x:0.255213,y:0.462149},{id:'J116',x:0.601197,y:0.46476},{id:'J117',x:0.821735,y:0.465002},{id:'J118',x:0.196635,y:0.467448},{id:'J119',x:0.195499,y:0.467989},{id:'J120',x:0.654932,y:0.468103},{id:'J121',x:0.356047,y:0.46869},{id:'J122',x:0.642874,y:0.469756},{id:'J123',x:0.757821,y:0.479327},{id:'J124',x:0.347786,y:0.479582},{id:'J125',x:0.485559,y:0.487614},{id:'J126',x:0.782911,y:0.491346},{id:'J127',x:0.793404,y:0.49825},{id:'J128',x:0.825131,y:0.509607},{id:'J129',x:0.93451,y:0.514009},{id:'J130',x:0.855208,y:0.515513},{id:'J131',x:0.860411,y:0.530412},{id:'J132',x:0.275802,y:0.531602},{id:'J133',x:0.640247,y:0.533502},{id:'J134',x:0.742457,y:0.53375},{id:'J135',x:0.693776,y:0.534492},{id:'J136',x:0.533788,y:0.535091},{id:'J137',x:0.629927,y:0.542593},{id:'J138',x:0.734319,y:0.544169},{id:'J139',x:0.571661,y:0.54578},{id:'J140',x:0.152861,y:0.5464},{id:'J141',x:0.866536,y:0.573159},{id:'J142',x:0.062487,y:0.57512},{id:'J143',x:0.544013,y:0.581168},{id:'J144',x:0.545347,y:0.581553},{id:'J145',x:0.79382,y:0.582806},{id:'J146',x:0.859125,y:0.585921},{id:'J147',x:0.390505,y:0.592085},{id:'J148',x:0.639562,y:0.593197},{id:'J149',x:0.149658,y:0.599701},{id:'J150',x:0.678048,y:0.60057},{id:'J151',x:0.942936,y:0.600899},{id:'J152',x:0.83384,y:0.606302},{id:'J153',x:0.092216,y:0.618964},{id:'J154',x:0.521877,y:0.643731},{id:'J155',x:0.25813,y:0.64895},{id:'J156',x:0.12301,y:0.658668},{id:'J157',x:0.161114,y:0.665441},{id:'J158',x:0.421394,y:0.665483},{id:'J159',x:0.415866,y:0.676794},{id:'J160',x:0.303166,y:0.677354},{id:'J161',x:0.75481,y:0.678914},{id:'J162',x:0.296905,y:0.688512},{id:'J163',x:0.068626,y:0.692535},{id:'J164',x:0.641951,y:0.693968},{id:'J165',x:0.404622,y:0.711943},{id:'J166',x:0.723864,y:0.722249},{id:'J167',x:0.510335,y:0.749131},{id:'J168',x:0.348382,y:0.750091},{id:'J169',x:0.652772,y:0.751715},{id:'J170',x:0.152878,y:0.751735},{id:'J171',x:0.415911,y:0.754189},{id:'J172',x:0.20827,y:0.758108},{id:'J173',x:0.701737,y:0.77259},{id:'J174',x:0.469908,y:0.772865},{id:'J175',x:0.698917,y:0.774028},{id:'J176',x:0.500072,y:0.812145},{id:'J177',x:0.3177,y:0.813743},{id:'J178',x:0.372163,y:0.819787},{id:'J179',x:0.575231,y:0.826065},{id:'J180',x:0.521773,y:0.837929},{id:'J181',x:0.471234,y:0.838789},{id:'J182',x:0.412548,y:0.848548},{id:'J183',x:0.272931,y:0.849697},{id:'J184',x:0.668536,y:0.850806},{id:'J185',x:0.549426,y:0.857274},{id:'J186',x:0.650393,y:0.870138},{id:'J187',x:0.607952,y:0.877119},{id:'J188',x:0.605476,y:0.879829},{id:'J189',x:0.25633,y:0.893858},{id:'J190',x:0.629654,y:0.897741},{id:'J191',x:0.625815,y:0.898752},{id:'J192',x:0.354228,y:0.900825},{id:'J193',x:0.460145,y:0.907956},{id:'J194',x:0.643411,y:0.931152},{id:'J195',x:0.381525,y:0.933403}]"""}
}

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN)
  setContent{WhitechapelApp(GameStore(this))}
 }
}

@Composable fun WhitechapelApp(store:GameStore){
 val context=androidx.compose.ui.platform.LocalContext.current
 val pilotServer=remember{PilotMapServer(context)}
 var pilotUrl by remember{mutableStateOf("")}
 var mapMode by remember{mutableStateOf(false)}
 var page by remember{mutableStateOf("splash")}
 var pinHash by remember{mutableStateOf("")};var hideout by remember{mutableIntStateOf(0)};var night by remember{mutableIntStateOf(1)}
 var gameOver by remember{mutableStateOf(false)}
 val starts=remember{mutableStateMapOf<Int,Int>()};val moves=remember{mutableStateListOf<JackMove>()};val queries=remember{mutableStateListOf<Inquiry>()};val escaped=remember{mutableStateListOf<Int>()}

 fun save(){store.save(pinHash,hideout,night,starts,moves,queries,escaped.toSet(),gameOver)}
 fun load():Boolean{
  val o=store.load()?:return false
  pinHash=o.getString("pin");hideout=o.getInt("hideout");night=o.getInt("night");gameOver=o.optBoolean("gameOver",false)
  starts.clear();moves.clear();queries.clear();escaped.clear()
  val s=o.optJSONObject("starts")?:JSONObject();s.keys().forEach{starts[it.toInt()]=s.getInt(it)}
  val ma=o.optJSONArray("moves")?:JSONArray()
  for(i in 0 until ma.length()){val x=ma.getJSONObject(i);moves+=JackMove(x.getInt("n"),x.getInt("t"),x.getInt("a"),if(x.isNull("b"))null else x.getInt("b"),MoveType.valueOf(x.getString("y")))}
  val qa=o.optJSONArray("queries")?:JSONArray()
  for(i in 0 until qa.length()){val x=qa.getJSONObject(i);queries+=Inquiry(x.getInt("n"),x.getInt("i"),x.getInt("h"),InquiryType.valueOf(x.getString("t")),x.getBoolean("p"))}
  val ea=o.optJSONArray("escaped")?:JSONArray();for(i in 0 until ea.length())escaped+=ea.getInt(i)
  return true
 }

 LaunchedEffect(moves.size,night,starts.size){
  val nm=moves.filter{it.night==night};val st=starts[night];val route=mutableListOf<Int>();if(st!=null)route+=st;nm.forEach{route+=it.first;if(it.second!=null)route+=it.second!!};pilotServer.setJackState(route.lastOrNull(),route.toSet(),route)
 }

 BackHandler(enabled=page!="splash"){
  when(page){
   "newgame"->page="home"
   "mapserver"->page="home"
   "mapdetective"->page="mapserver"
   "jack"-> { save(); page="detective" } // never expose Jack screen after leaving it
   "detective"-> save() // consume system Back: stay in game
   "unlock"->page=if(mapMode)"mapdetective" else "detective"
   "audit"->page="home"
   "home"->Unit // consume Back so app does not accidentally exit
  }
 }

 MaterialTheme(colorScheme=darkColorScheme(primary=Gold,surface=Ink)){
  when(page){
   "splash"->Splash{page="home"}
   "home"->Home(store.exists(),onNewGame={mapMode=false;page="newgame"},onMapGame={mapMode=true;pilotUrl=pilotServer.start();page="mapserver"},onContinue={if(load())page=if(gameOver)"audit" else "detective"})
   "mapserver"->MapServerPage(pilotUrl,onBack={page="home"},onTest={if(pinHash.isBlank())page="newgame" else page="mapdetective"},onCopy={
    val cb=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("Whitechapel server",pilotUrl))
   })
   "mapdetective"->MapDetectivePage(onBack={page="mapserver"},onPass={page="unlock"})
   "newgame"->NewGame(onBack={page="home"}){h,p->hideout=h;pinHash=GameStore.hash(p);night=1;gameOver=false;starts.clear();moves.clear();queries.clear();escaped.clear();if(mapMode)pilotServer.resetPublicState();save();page="jack"}
   "jack"->JackPage(night,hideout,starts[night],moves.filter{it.night==night},
    onSetStart={first,second->
     if(night==3){
      starts[30]=first
      starts[3]=second!!
      // The second Crime Scene is Jack's actual starting position for Hunting.
     } else starts[night]=first
     save()
    },
    onMove={m->moves+=m;save()},
    onUndoLast={turn->
     val last=moves.lastOrNull{it.night==night}
     if(last!=null && last.turn==turn) moves.remove(last)
     save()
    },
    onPass={save();page=if(mapMode)"mapdetective" else "detective"},
    onGameLost={gameOver=true;save();page="audit"},
    onEscape={
     if(!escaped.contains(night))escaped+=night
     if(night==4){gameOver=true;save();page="audit"} else {night++;save();page="detective"}
    })
   "detective"->DetectivePage(night,moves,starts,queries.filter{it.night==night},
    onInquiry={q->queries+=q;save()},
    onGameLost={gameOver=true;save();page="audit"},
    onJackTurn={page="unlock"},
    escapedPrevious=escaped.contains(night-1) && starts[night]==null)
   "unlock"->UnlockPage(pinHash,onSuccess={page="jack"},onCancel={page=if(mapMode)"mapdetective" else "detective"})
   "audit"->AuditPage(hideout,starts,moves,queries,escaped.toSet()){store.clear();page="home"}
  }
 }
}

@Composable private fun Splash(onDone:()->Unit){
 var go by remember{mutableStateOf(false)}
 val progress by animateFloatAsState(if(go)1f else 0f,tween(5000),label="load")
 LaunchedEffect(Unit){go=true;delay(5100);onDone()}
 Box(Modifier.fillMaxSize()){
  Image(painterResource(R.drawable.splash),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  Column(Modifier.align(Alignment.BottomCenter).padding(horizontal=44.dp,vertical=46.dp),horizontalAlignment=Alignment.CenterHorizontally){
   Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(.72f)).border(1.dp,Gold,RoundedCornerShape(20.dp)).padding(2.dp)){
    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF650B0B),Color.Red))))
   }
   Spacer(Modifier.height(12.dp));Text("Preparing the streets of Whitechapel...",color=Gold,fontSize=12.sp)
  }
 }
}

@Composable private fun Home(hasGame:Boolean,onNewGame:()->Unit,onMapGame:()->Unit,onContinue:()->Unit){
 Box(Modifier.fillMaxSize()){
  Image(painterResource(R.drawable.home_background),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  Box(Modifier.fillMaxSize().background(Color.Black.copy(.12f)))
  Column(Modifier.fillMaxSize().padding(horizontal=34.dp),horizontalAlignment=Alignment.CenterHorizontally){
   Spacer(Modifier.weight(.40f))
   MenuButton("▶","شروع بازی جدید","New Game",true,onNewGame);Spacer(Modifier.height(13.dp))
   MenuButton("⌘","شروع بازی جدید با نقشه","Map Mode • Pilot",true,onMapGame);Spacer(Modifier.height(13.dp))
   MenuButton("▰","ادامه بازی","Continue",hasGame,onContinue);Spacer(Modifier.height(13.dp))
   MenuButton("▤","راهنما","How to Play",false){};Spacer(Modifier.height(13.dp))
   MenuButton("⚙","تنظیمات","Settings",false){};Spacer(Modifier.weight(.18f))
  }
 }
}

@Composable private fun MapServerPage(url:String,onBack:()->Unit,onTest:()->Unit,onCopy:()->Unit){
 Background{
  Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   Header("بازی با نقشه","Map Mode • Pilot",onBack,true)
   GrayCard{
    Text("سرور آزمایشی فعال است",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Color.Black)
    Spacer(Modifier.height(8.dp))
    Text("دستگاه دیگر را به همان Wi-Fi یا Hotspot وصل کنید و این آدرس را در مرورگر وارد کنید:",color=Color.Black,fontSize=14.sp)
    Spacer(Modifier.height(12.dp))
    Text(url,color=Color(0xFF5E130D),fontSize=18.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Button(onClick=onCopy,modifier=Modifier.fillMaxWidth()){Text("کپی لینک")}
   }
   GrayCard{
    Text("در این پایلوت، نقشه بازی به‌صورت تمام‌صفحه روی مرورگر دستگاه متصل نمایش داده می‌شود.",color=Color.Black,fontSize=14.sp)
    Button(onClick=onTest,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Blood)){Text("آزمایش جابه‌جایی پلیس‌ها")}
   }
  }
 }
}

@Composable private fun MapDetectivePage(onBack:()->Unit,onPass:()->Unit){
 Background{
  Column(Modifier.fillMaxSize()){
   Header("نوبت کارآگاه‌ها","Hunting • Map Mode",onBack,true)
   Text("مهره پلیس را روی نقشه انتخاب کنید، جابه‌جا کنید و سپس Search یا Arrest انجام دهید. نقشه با دو انگشت قابل زوم است.",color=Gold,fontSize=12.sp,modifier=Modifier.padding(8.dp))
   AndroidView(factory={ctx->WebView(ctx).apply{webViewClient=WebViewClient();settings.javaScriptEnabled=true;settings.builtInZoomControls=true;settings.displayZoomControls=false;settings.setSupportZoom(true);loadUrl("http://127.0.0.1:8080/controller")}},modifier=Modifier.weight(1f).fillMaxWidth())
   Button(onClick=onPass,modifier=Modifier.fillMaxWidth().padding(10.dp),colors=ButtonDefaults.buttonColors(containerColor=Blood)){Text("پایان نوبت کارآگاه‌ها / تحویل به جک")}
  }
 }
}

@Composable private fun NewGame(onBack:()->Unit,onStart:(Int,String)->Unit){
 var h by remember{mutableStateOf("")};var pin by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")}
 val hideoutNumber=h.toIntOrNull()
 val valid=hideoutNumber != null && hideoutNumber in 1..195 && pin.length in 4..6 && pin==confirm
 Background{
  Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Header("شروع بازی جدید","New Game Setup",onBack,true)
   GrayCard{
    Row(verticalAlignment=Alignment.CenterVertically){Text("⌂",fontSize=43.sp,color=Color.Black);Spacer(Modifier.width(12.dp));Text("انتخاب مخفیگاه",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.Black)}
    DarkField(h,{h=it.filter(Char::isDigit).take(3)},"شماره مخفیگاه","⌂",KeyboardType.Number)
    Text("🔒  مخفیگاه برای کل بازی ثابت می‌ماند و قابل تغییر نیست.",color=Color.Black,fontSize=13.sp)
   }
   GrayCard{
    Text("🔐  تعیین PIN جک",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.Black)
    PinField(pin,{pin=it.filter(Char::isDigit).take(6)},"PIN")
    PinField(confirm,{confirm=it.filter(Char::isDigit).take(6)},"تکرار PIN")
    if(confirm.isNotEmpty()&&pin!=confirm)Text("PINها یکسان نیستند.",color=Color(0xFF8D0000),fontSize=12.sp)
   }
   RedButton("▶   شروع بازی",valid){onStart(h.toInt(),pin)}
  }
 }
}

@Composable private fun JackPage(
 night:Int,hideout:Int,start:Int?,nightMoves:List<JackMove>,
 onSetStart:(Int,Int?)->Unit,onMove:(JackMove)->Unit,onUndoLast:(Int)->Unit,onPass:()->Unit,onGameLost:()->Unit,onEscape:()->Unit
){
 var startText by remember(night){mutableStateOf(if(night==3)(start?.let{""}?:"") else (start?.toString()?:""))}
 var secondCrime by remember(night){mutableStateOf("")}
 var type by remember{mutableStateOf(MoveType.NORMAL)};var d1 by remember{mutableStateOf("")};var d2 by remember{mutableStateOf("")}
 var hideoutPopup by remember{mutableStateOf(false)};var hideoutVisible by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
 var trackLossPopup by remember{mutableStateOf(false)};var jackWinPopup by remember{mutableStateOf(false)}
 var undoTarget by remember{mutableStateOf<JackMove?>(null)}
 var moveMadeThisTurn by remember(night){mutableStateOf(false)}
 val coachMax=listOf(0,3,2,2,1)[night];val alleyMax=listOf(0,2,2,1,1)[night]
 val coachUsed=nightMoves.count{it.type==MoveType.COACH};val alleyUsed=nightMoves.count{it.type==MoveType.ALLEY}
 val coachLeft=coachMax-coachUsed;val alleyLeft=alleyMax-alleyUsed
 val doubleEventCost=if(night==3)1 else 0
 val trackUsed: Int = nightMoves.fold(doubleEventCost) { total, move -> total + if (move.type == MoveType.COACH) 2 else 1 }
 Background{
  Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
   Box(Modifier.fillMaxWidth()){
    SimpleTitle("حرکت‌های جک","حرکت‌های جک • شب $night")
    Row(
     Modifier.align(Alignment.TopEnd).background(Color(0xCC11100E),RoundedCornerShape(8.dp)).border(1.dp,Gold.copy(alpha=.65f),RoundedCornerShape(8.dp)).padding(start=9.dp,end=4.dp),
     verticalAlignment=Alignment.CenterVertically
    ){
     Text(if(hideoutVisible)hideout.toString() else "•••",color=Gold,fontWeight=FontWeight.Bold,fontSize=15.sp)
     IconButton(onClick={hideoutVisible=!hideoutVisible},modifier=Modifier.size(34.dp)){
      Text(if(hideoutVisible)"◉" else "◎",color=Gold,fontSize=20.sp)
     }
    }
   }
   GrayCard{
    Text(if(night==3)"محل‌های ارتکاب قتل (Double Event)" else "محل ارتکاب قتل",color=Color.Black,fontWeight=FontWeight.Bold,fontSize=18.sp)
    if(night==3 && start==null){
     Text("ترتیب دو قتل محرمانه است؛ محل دوم، موقعیت شروع فرار جک است.",color=Color.Black,fontSize=12.sp)
     DarkField(startText,{startText=it.filter(Char::isDigit).take(3)},"قتل اول","⌖",KeyboardType.Number)
     DarkField(secondCrime,{secondCrime=it.filter(Char::isDigit).take(3)},"قتل دوم / شروع فرار","⌖",KeyboardType.Number)
     Button(
      onClick={
       val a=startText.toIntOrNull();val b=secondCrime.toIntOrNull()
       if(a != null && b != null && a in 1..195 && b in 1..195 && a!=b) onSetStart(a,b)
      },
      colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFBDBDBB),contentColor=Color.Black),
      modifier=Modifier.fillMaxWidth().height(56.dp)
     ){Text("ثبت دو محل قتل",color=Color.Black,fontWeight=FontWeight.Bold)}
    } else {
     Row(verticalAlignment=Alignment.CenterVertically){
      Box(Modifier.weight(1f)){DarkField(if(night==3 && start!=null)start.toString() else startText,{if(start==null)startText=it.filter(Char::isDigit).take(3)},"شماره خانه","⌖",KeyboardType.Number)}
      Spacer(Modifier.width(8.dp))
      Button(
       onClick={startText.toIntOrNull()?.takeIf{it in 1..195}?.let{onSetStart(it,null)}},
       enabled=start==null,
       colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFBDBDBB),contentColor=Color.Black,disabledContainerColor=Color(0xFFBDBDBB),disabledContentColor=Color.Black),
       modifier=Modifier.height(56.dp)
      ){Text(if(start==null)"ثبت" else "ثبت شد",color=Color.Black,fontWeight=FontWeight.Bold)}
     }
    }
   }
   if(start!=null){
    GrayCard{
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
      Text("نوع حرکت",color=Color.Black,fontWeight=FontWeight.Bold,fontSize=18.sp)
      Text("Move Track: $trackUsed / 15",color=Color.Black,fontWeight=FontWeight.Bold)
     }
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
      MoveChip("🚶","عادی","",type==MoveType.NORMAL,!moveMadeThisTurn,Color.Black){type=MoveType.NORMAL}
      MoveChip("♞","درشکه","$coachLeft",type==MoveType.COACH,coachLeft>0 && !moveMadeThisTurn,Color.Black){type=MoveType.COACH}
      MoveChip("↯","کوچه","$alleyLeft",type==MoveType.ALLEY,alleyLeft>0 && !moveMadeThisTurn,AlleyRed){type=MoveType.ALLEY}
     }
     DarkField(d1,{if(!moveMadeThisTurn)d1=it.filter(Char::isDigit).take(3)},if(type==MoveType.COACH)"مقصد اول درشکه" else "خانه مقصد","⌖",KeyboardType.Number)
     if(type==MoveType.COACH)DarkField(d2,{if(!moveMadeThisTurn)d2=it.filter(Char::isDigit).take(3)},"مقصد دوم درشکه","⌖",KeyboardType.Number)
     if(error.isNotEmpty())Text(error,color=Color(0xFF8D0000),fontSize=12.sp)
     Button(onClick={
      val a=d1.toIntOrNull();val b=d2.toIntOrNull()
      val cost=if(type==MoveType.COACH)2 else 1
      when{
       moveMadeThisTurn->error="حرکت این نوبت ثبت شده است. ابتدا آن را اصلاح کنید یا گوشی را به کارآگاه‌ها تحویل دهید."
       trackUsed+cost>15->error="ظرفیت Move Track این شب تمام شده است."
       a == null || a !in 1..195 || (type==MoveType.COACH && (b == null || b !in 1..195))->error="شماره مقصد معتبر نیست."
       type==MoveType.COACH&&coachLeft<=0->error="درشکه‌های این شب تمام شده‌اند."
       type==MoveType.ALLEY&&alleyLeft<=0->error="حرکت کوچه این شب تمام شده است."
       type==MoveType.COACH&&(a==b || a==currentLocation(start,nightMoves) || b==currentLocation(start,nightMoves))->error="در حرکت درشکه، دو مقصد و مبدأ باید متفاوت باشند."
       else->{
        val firstDestination=a!!
        val secondDestination=if(type==MoveType.COACH)b!! else null
        val m=JackMove(night,nightMoves.size+1+if(night==3)1 else 0,firstDestination,secondDestination,type)
        onMove(m)
        moveMadeThisTurn=true
        val end=secondDestination ?: firstDestination
        hideoutPopup=(type==MoveType.NORMAL&&end==hideout)
        if(!hideoutPopup && trackUsed+cost==15) trackLossPopup=true
        error=""
       }
      }
     },enabled=!moveMadeThisTurn,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Blood,disabledContainerColor=Color(0xFF5C5550),disabledContentColor=Color(0xFFBDB7AF))){Text(if(moveMadeThisTurn)"حرکت این نوبت ثبت شد" else "ثبت حرکت",fontWeight=FontWeight.Bold)}
    }
    Text("مسیر حرکت‌های شب $night",color=Gold,fontSize=18.sp,fontWeight=FontWeight.Bold)
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){
     items(nightMoves){m->
      val editable=moveMadeThisTurn && m==nightMoves.lastOrNull()
      MoveHistoryCard(m,editable=editable,onCorrect={if(editable)undoTarget=m})
     }
    }
    Button(
     onClick=onPass,
     enabled=moveMadeThisTurn,
     modifier=Modifier.fillMaxWidth().height(58.dp).border(1.dp,if(moveMadeThisTurn)Gold else Color.Gray,RoundedCornerShape(9.dp)),
     shape=RoundedCornerShape(9.dp),
     colors=ButtonDefaults.buttonColors(containerColor=DarkButton,contentColor=Gold,disabledContainerColor=Color(0xFF555555),disabledContentColor=Color(0xFFB8B8B8))
    ){
     Text(if(moveMadeThisTurn)"🔒   تحویل به کارآگاه‌ها" else "ابتدا حرکت جک را ثبت کنید",fontSize=17.sp,fontWeight=FontWeight.Bold)
    }
   }
  }
 }
 undoTarget?.let{target->
  AlertDialog(
   onDismissRequest={undoTarget=null},
   containerColor=Color(0xFF11100E),
   title={Text("اصلاح حرکت",color=Color.White,fontWeight=FontWeight.Bold)},
   text={Text("فقط آخرین حرکتِ همین نوبت حذف می‌شود و جک می‌تواند همان نوبت را دوباره ثبت کند. حرکت‌های قبلی شب قابل ویرایش نیستند.",color=Color(0xFFF2DFC0))},
   confirmButton={
    Button(onClick={
     onUndoLast(target.turn)
     moveMadeThisTurn=false
     d1=""
     d2=""
     error=""
     hideoutPopup=false
     undoTarget=null
    },colors=ButtonDefaults.buttonColors(containerColor=Blood)){
     Text("حذف و اصلاح",fontWeight=FontWeight.Bold)
    }
   },
   dismissButton={TextButton(onClick={undoTarget=null}){Text("انصراف",color=Gold)}}
  )
 }
 if(hideoutPopup)HideoutDialog(
  onEscape={hideoutPopup=false;if(night==4)jackWinPopup=true else onEscape()},
  onContinue={hideoutPopup=false;onPass()}
 )
 if(trackLossPopup)GameResultDialog(
  title="جک به مخفیگاه نرسید",
  message="تعداد حرکت‌های مجاز این شب تمام شد و جک به مخفیگاه نرسید. کارآگاه‌ها برنده شدند.",
  buttonText="مشاهده نتیجه بازی",
  onConfirm={trackLossPopup=false;onGameLost()}
 )
 if(jackWinPopup)GameResultDialog(
  title="جک برنده شد",
  message="جک در شب چهارم به مخفیگاه رسید. جک برنده بازی شد.",
  buttonText="مشاهده نتیجه بازی",
  onConfirm={jackWinPopup=false;onEscape()}
 )
}

private fun currentLocation(start:Int,moves:List<JackMove>):Int =
 moves.lastOrNull()?.let{it.second?:it.first}?:start

@Composable private fun DetectivePage(
 night:Int,allMoves:List<JackMove>,starts:Map<Int,Int>,history:List<Inquiry>,
 onInquiry:(Inquiry)->Unit,onGameLost:()->Unit,onJackTurn:()->Unit,escapedPrevious:Boolean
){
 var house by remember{mutableStateOf("")}
 var result by remember{mutableStateOf("اطلاعات محرمانه جک نمایش داده نمی‌شود.")}
 var arrestWinPopup by remember{mutableStateOf(false)}
 val current=allMoves.lastOrNull{it.night==night}?.let{it.second?:it.first}?:starts[night]
 fun visited(h:Int):Boolean = starts[night]==h || allMoves.any{it.night==night && (it.first==h || it.second==h)}
 Background{
  Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   SimpleTitle("کارآگاه‌ها","Detective Mode • شب $night")
   if(escapedPrevious)Card(colors=CardDefaults.cardColors(containerColor=Green),modifier=Modifier.fillMaxWidth()){Text("✓ فرار شب قبل توسط برنامه تأیید شد.",Modifier.padding(14.dp),color=Color.White,fontWeight=FontWeight.Bold)}
   GrayCard{
    Text("استعلام خانه",color=Color.Black,fontWeight=FontWeight.Bold,fontSize=18.sp)
    DarkField(house,{house=it.filter(Char::isDigit).take(3)},"شماره خانه","⌕",KeyboardType.Number)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(onClick={
      val h=house.toIntOrNull()
      if(h != null && h in 1..195){val ok=visited(h);val q=Inquiry(night,history.size+1,h,InquiryType.SEARCH,ok);onInquiry(q);result=if(ok)"✓ سرنخ در خانه $h پیدا شد." else "✗ در خانه $h سرنخی نیست.";house=""}
     },modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Blue,contentColor=Color.White)){Text("⌕ جستجوی سرنخ",fontWeight=FontWeight.Bold)}
     Button(onClick={
      val h=house.toIntOrNull()
      if(h != null && h in 1..195){val ok=current==h;val q=Inquiry(night,history.size+1,h,InquiryType.ARREST,ok);onInquiry(q);result=if(ok)"✓ جک در خانه $h دستگیر شد." else "✗ دستگیری در خانه $h ناموفق بود.";if(ok)arrestWinPopup=true;house=""}
     },modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Blood,contentColor=Color.White)){Text("⛓ دستگیری",fontWeight=FontWeight.Bold)}
    }
   }
   Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF22201D)),modifier=Modifier.fillMaxWidth()){Text(result,Modifier.padding(15.dp),color=Color.White,fontWeight=FontWeight.Bold)}
   Text("تاریخچه استعلام‌های شب $night",color=Gold,fontWeight=FontWeight.Bold,fontSize=18.sp)
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){
    items(history){q->
     Card(colors=CardDefaults.cardColors(containerColor=Color(0xCC17130F)),modifier=Modifier.fillMaxWidth()){
      Row(Modifier.padding(12.dp)){Text(if(q.type==InquiryType.SEARCH)"⌕ Search" else "⛓ Arrest",color=if(q.type==InquiryType.SEARCH)Color(0xFF65B6E8) else Color(0xFFFF7777));Spacer(Modifier.weight(1f));Text("خانه ${q.house} • ${if(q.positive)"✓ مثبت" else "✗ منفی"}",color=Color.White)}
     }
    }
   }
   Button(onClick=onJackTurn,modifier=Modifier.fillMaxWidth().height(58.dp).border(1.dp,Gold,RoundedCornerShape(9.dp)),shape=RoundedCornerShape(9.dp),colors=ButtonDefaults.buttonColors(containerColor=DarkButton,contentColor=Gold)){Text("🔐   نوبت جک",fontWeight=FontWeight.Bold,fontSize=18.sp)}
  }
 }
 if(arrestWinPopup)GameResultDialog(
  title="جک دستگیر شد",
  message="جک دستگیر شد و کارآگاه‌ها بازی را بردند.",
  buttonText="مشاهده نتیجه بازی",
  onConfirm={arrestWinPopup=false;onGameLost()}
 )
}

@Composable private fun UnlockPage(pinHash:String,onSuccess:()->Unit,onCancel:()->Unit){
 var pin by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
 BackHandler{onCancel()}
 Background{
  Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
   GrayCard{
    Text("🔐 ورود محرمانه جک",color=Color.Black,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    PinField(pin,{pin=it.filter(Char::isDigit).take(6)},"PIN")
    if(error.isNotEmpty())Text(error,color=Color(0xFF8D0000))
    Button(onClick={if(GameStore.hash(pin)==pinHash)onSuccess() else error="PIN اشتباه است."},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Blood)){Text("باز کردن دفترچه",fontWeight=FontWeight.Bold)}
   }
  }
 }
}

@Composable private fun AuditPage(hideout:Int,starts:Map<Int,Int>,moves:List<JackMove>,queries:List<Inquiry>,escaped:Set<Int>,onClear:()->Unit){
 Background{
  Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   SimpleTitle("بررسی نهایی بازی","Game Audit / Reveal")
   GrayCard{Text("مخفیگاه: $hideout",color=Color.Black,fontWeight=FontWeight.Bold,fontSize=22.sp)}
   LazyColumn(Modifier.weight(1f)){
    for(n in 1..4){
     item{
      val startLabel=if(n==3)"قتل‌ها: ${starts[30]?:"-"} → ${starts[3]?:"-"}" else "شروع: ${starts[n]?:"-"}"
      Text("شب $n • $startLabel • ${if(escaped.contains(n))"✓ فرار تأیید شده" else "—"}",color=Gold,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=8.dp))
     }
     items(moves.filter{it.night==n}){m->Text("حرکت ${m.turn}: ${m.first}${m.second?.let{" → $it"}?:""} • ${m.type.name}",color=Color.White)}
     items(queries.filter{it.night==n}){q->Text("${q.type.name} ${q.house}: ${if(q.positive)"✓" else "✗"}",color=Color.LightGray)}
    }
   }
   RedButton("پایان و پاک کردن بازی",true,onClear)
  }
 }
}

@Composable private fun GameResultDialog(title:String,message:String,buttonText:String,onConfirm:()->Unit){
 AlertDialog(
  onDismissRequest={},
  containerColor=Color(0xFF11100E),
  title={Text(title,color=Gold,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth(),fontWeight=FontWeight.Bold,fontSize=22.sp)},
  text={Text(message,color=Color(0xFFF2DFC0),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth(),fontSize=16.sp)},
  confirmButton={
   Button(onClick=onConfirm,modifier=Modifier.fillMaxWidth().height(54.dp),colors=ButtonDefaults.buttonColors(containerColor=Blood)){
    Text(buttonText,fontWeight=FontWeight.Bold)
   }
  }
 )
}

@Composable private fun HideoutDialog(onEscape:()->Unit,onContinue:()->Unit){
 AlertDialog(onDismissRequest={},containerColor=Color(0xFF11100E),
  title={Text("شما در مخفیگاه هستید",color=Color.White,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth(),fontWeight=FontWeight.Bold)},
  text={Column(horizontalAlignment=Alignment.CenterHorizontally){
   Text("⌂",fontSize=66.sp,color=Gold);Text("شما با حرکت عادی به مخفیگاه رسیده‌اید.",color=Color(0xFFF2DFC0),textAlign=TextAlign.Center)
   Spacer(Modifier.height(18.dp))
   Button(onClick=onEscape,modifier=Modifier.fillMaxWidth().height(56.dp),colors=ButtonDefaults.buttonColors(containerColor=Green),shape=RoundedCornerShape(8.dp)){Text("⚑  اعلام فرار و پایان شب",fontWeight=FontWeight.Bold)}
   Spacer(Modifier.height(10.dp))
   Button(onClick=onContinue,modifier=Modifier.fillMaxWidth().height(56.dp).border(1.dp,Gold,RoundedCornerShape(8.dp)),colors=ButtonDefaults.buttonColors(containerColor=DarkButton),shape=RoundedCornerShape(8.dp)){Text("→  اعلام نکن — ادامه بازی",color=Gold,fontWeight=FontWeight.Bold)}
   Spacer(Modifier.height(12.dp));Text("اگر اعلام نکنید، در نوبت بعد باید از مخفیگاه خارج شوید.",color=Color(0xFFE07B68),fontSize=12.sp,textAlign=TextAlign.Center)
  }},confirmButton={})
}

@Composable private fun MoveHistoryCard(m:JackMove,editable:Boolean,onCorrect:()->Unit){
 Card(colors=CardDefaults.cardColors(containerColor=Color(0xCC17130F)),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)){
  Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
   Text(when(m.type){MoveType.NORMAL->"🚶";MoveType.COACH->"♞";MoveType.ALLEY->"↯"},fontSize=22.sp,color=if(m.type==MoveType.ALLEY)AlleyRed else Color.White)
   Spacer(Modifier.width(8.dp))
   Column(Modifier.weight(1f)){
    Text("حرکت ${m.turn}",color=Gold,fontWeight=FontWeight.Bold)
    Text(if(m.second!=null)"${m.first} → ${m.second}" else "${m.first}",color=Color.White,fontSize=16.sp)
   }
   if(editable){
    TextButton(onClick=onCorrect,colors=ButtonDefaults.textButtonColors(contentColor=Color(0xFFFFC85A))){
     Text("✎ اصلاح",fontWeight=FontWeight.Bold)
    }
   } else {
    Text("🔒",color=Color.Gray,fontSize=16.sp)
   }
  }
 }
}
@Composable private fun RowScope.MoveChip(icon:String,label:String,count:String,selected:Boolean,enabled:Boolean,iconColor:Color,onClick:()->Unit){
 FilterChip(selected=selected,onClick=onClick,enabled=enabled,label={
  Row(verticalAlignment=Alignment.CenterVertically){Text(icon,color=iconColor,fontSize=18.sp);Spacer(Modifier.width(3.dp));Text(label,fontWeight=FontWeight.Bold);if(count.isNotEmpty())Text(" ($count)",fontSize=11.sp)}
 },colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Blood,selectedLabelColor=Color.White,containerColor=Color(0xFFE2E0DB),labelColor=Color.Black,disabledContainerColor=Color(0xFF999999),disabledLabelColor=Color.DarkGray),modifier=Modifier.weight(1f))
}
@Composable private fun Background(content:@Composable BoxScope.()->Unit){
 Box(Modifier.fillMaxSize()){Image(painterResource(R.drawable.home_background),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(Color.Black.copy(.58f)));content()}
}
@Composable private fun Header(title:String,sub:String,onBack:()->Unit,showBack:Boolean){
 Row(verticalAlignment=Alignment.CenterVertically){if(showBack)TextButton(onBack,contentPadding=PaddingValues(0.dp)){Text("‹",color=Gold,fontSize=42.sp)} else Spacer(Modifier.width(42.dp))
  Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(sub,color=Gold,fontSize=13.sp)};Spacer(Modifier.width(42.dp))}
}
@Composable private fun SimpleTitle(title:String,sub:String){Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(sub,color=Gold,fontSize=13.sp)}}
@Composable private fun GrayCard(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=GrayCard,contentColor=Color.Black)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}}
@Composable private fun DarkField(value:String,onValue:(String)->Unit,label:String,icon:String,type:KeyboardType){
 OutlinedTextField(value,onValue,leadingIcon={Text(icon,color=Color.Black)},label={Text(label,color=Color.Black)},keyboardOptions=KeyboardOptions(keyboardType=type),textStyle=LocalTextStyle.current.copy(color=Color.Black,fontSize=20.sp,fontWeight=FontWeight.Bold),singleLine=true,
  colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.Black,unfocusedTextColor=Color.Black,focusedContainerColor=GrayField,unfocusedContainerColor=GrayField,focusedBorderColor=Color.DarkGray,unfocusedBorderColor=Color.Gray,cursorColor=Color.Black),modifier=Modifier.fillMaxWidth())
}
@Composable private fun PinField(value:String,onValue:(String)->Unit,label:String){OutlinedTextField(value,onValue,label={Text(label,color=Color.Black)},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),textStyle=LocalTextStyle.current.copy(color=Color.Black,fontSize=20.sp,fontWeight=FontWeight.Bold),singleLine=true,colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.Black,unfocusedTextColor=Color.Black,focusedContainerColor=GrayField,unfocusedContainerColor=GrayField,focusedBorderColor=Color.DarkGray,unfocusedBorderColor=Color.Gray,cursorColor=Color.Black),modifier=Modifier.fillMaxWidth())}
@Composable private fun MenuButton(icon:String,fa:String,en:String,enabled:Boolean,onClick:()->Unit){Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().height(70.dp).border(1.dp,Gold,RoundedCornerShape(9.dp)),shape=RoundedCornerShape(9.dp),colors=ButtonDefaults.buttonColors(containerColor=DarkButton,contentColor=Gold,disabledContainerColor=Color(0xD915120F),disabledContentColor=Gold.copy(.7f)),contentPadding=PaddingValues(horizontal=18.dp)){Text(icon,fontSize=29.sp);Spacer(Modifier.width(18.dp));Column(Modifier.weight(1f),horizontalAlignment=Alignment.End){Text(fa,color=Color(0xFFF2DFC0),fontWeight=FontWeight.Bold,fontSize=18.sp);Text(en,color=Gold,fontSize=11.sp)}}}
@Composable private fun RedButton(text:String,enabled:Boolean,onClick:()->Unit){Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().height(62.dp).border(1.dp,Gold,RoundedCornerShape(9.dp)),shape=RoundedCornerShape(9.dp),colors=ButtonDefaults.buttonColors(containerColor=Blood,disabledContainerColor=Color(0xFF551515),contentColor=Color.White,disabledContentColor=Color.Gray)){Text(text,fontSize=20.sp,fontWeight=FontWeight.Bold)}}
