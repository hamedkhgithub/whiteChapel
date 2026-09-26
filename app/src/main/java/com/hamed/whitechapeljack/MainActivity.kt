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
 @Volatile private var running=false
 private var socket:ServerSocket?=null
 val port=8080
 private val policePositions=java.util.concurrent.ConcurrentHashMap<String,String>()

 fun start():String {
  if(!running){
   running=true
   thread(name="whitechapel-pilot-server",isDaemon=true){
    try{
     socket=ServerSocket(port)
     while(running){
      val client=socket?.accept()?:break
      thread(isDaemon=true){client.use { c -> runCatching{handle(c)} }}
     }
    }catch(_:Exception){} finally{running=false;runCatching{socket?.close()};socket=null}
   }
  }
  return "http://${localIpv4()}:$port"
 }
 private fun handle(c:java.net.Socket){
  val reader=c.getInputStream().bufferedReader()
  val requestLine=reader.readLine().orEmpty()
  while(true){val line=reader.readLine()?:break;if(line.isBlank())break}
  val raw=requestLine.split(" ").getOrNull(1) ?: "/"
  val path=raw.substringBefore("?");val query=raw.substringAfter("?","")
  val params=query.split("&").mapNotNull{p->val a=p.split("=",limit=2);if(a.size==2)java.net.URLDecoder.decode(a[0],"UTF-8") to java.net.URLDecoder.decode(a[1],"UTF-8") else null}.toMap()
  val bytes:ByteArray;val contentType:String
  when(path){
   "/map.webp","/map"->{bytes=appContext.assets.open("whitechapel_map.webp").use{it.readBytes()};contentType="image/webp"}
   "/move"->{val police=params["police"];val node=params["node"];if(police!=null&&node!=null&&POLICE_IDS.contains(node))policePositions[police]=node;bytes="""{"ok":true}""".toByteArray();contentType="application/json"}
   "/state"->{val entries=policePositions.entries.joinToString(","){e->"\"${e.key}\":\"${e.value}\""};bytes="{$entries}".toByteArray();contentType="application/json"}
   "/controller"->{bytes=controllerHtml().toByteArray();contentType="text/html; charset=utf-8"}
   else->{bytes=displayHtml().toByteArray();contentType="text/html; charset=utf-8"}
  }
  val header="HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n"
  c.getOutputStream().apply{write(header.toByteArray());write(bytes);flush()}
 }
 private fun displayHtml()="""<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body{margin:0;background:#090807;width:100%;height:100%;overflow:hidden}#wrap{position:relative;width:100vw;height:100vh;display:flex;align-items:center;justify-content:center}#map{max-width:100%;max-height:100%;display:block}.p{position:absolute;width:24px;height:24px;border-radius:50%;border:3px solid white;box-shadow:0 2px 8px #000;transform:translate(-50%,-50%);z-index:5}.red{background:#d32222}.blue{background:#1976d2}.green{background:#16813b}.yellow{background:#f0c51a}.brown{background:#5b3020}</style></head><body><div id="wrap"><img id="map" src="/map.webp"></div><script>
const colors=['red','blue','green','yellow','brown'];const nodes=POLICE_JS;
function sync(){fetch('/state').then(r=>r.json()).then(s=>{const img=document.getElementById('map'),wrap=document.getElementById('wrap'),ir=img.getBoundingClientRect(),wr=wrap.getBoundingClientRect();colors.forEach(c=>{let el=document.getElementById('p_'+c);if(!el){el=document.createElement('div');el.id='p_'+c;el.className='p '+c;wrap.appendChild(el)}const n=nodes.find(x=>x.id===s[c]);if(n){el.style.display='block';el.style.left=(ir.left-wr.left+n.x*ir.width)+'px';el.style.top=(ir.top-wr.top+n.y*ir.height)+'px'}else el.style.display='none'})}).catch(()=>{})}setInterval(sync,400);window.addEventListener('resize',sync);document.getElementById('map').onload=sync;
</script></body></html>""".replace("POLICE_JS",POLICE_JS)
 private fun controllerHtml()="""<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=8,user-scalable=yes"><style>body{margin:0;background:#0b0907;color:#e6c47b;font-family:sans-serif}#bar{position:sticky;top:0;z-index:20;background:#17120e;padding:8px;display:flex;gap:6px;overflow:auto}button{padding:10px 13px;border:1px solid #d6ad63;background:#292019;color:white;border-radius:8px;white-space:nowrap}button.on{outline:3px solid #d6ad63}#mapbox{position:relative;width:1536px;height:1024px}#map{width:1536px;height:1024px;display:block}.node{position:absolute;width:18px;height:18px;border-radius:50%;border:2px solid #00e5ff;background:#001a22aa;transform:translate(-50%,-50%)}#hint{padding:8px;text-align:center}</style></head><body><div id="bar"><button data-p="red">🔴 قرمز</button><button data-p="blue">🔵 آبی</button><button data-p="green">🟢 سبز</button><button data-p="yellow">🟡 زرد</button><button data-p="brown">🟤 قهوه‌ای</button></div><div id="hint">یک پلیس را انتخاب کنید، سپس نقطه مقصد را لمس کنید. برای زوم از دو انگشت استفاده کنید.</div><div id="mapbox"><img id="map" src="/map.webp"></div><script>
let police=null;const nodes=POLICE_JS;document.querySelectorAll('button').forEach(b=>b.onclick=()=>{police=b.dataset.p;document.querySelectorAll('button').forEach(x=>x.classList.remove('on'));b.classList.add('on')});const box=document.getElementById('mapbox');nodes.forEach(n=>{let d=document.createElement('div');d.className='node';d.title=n.id;d.style.left=(n.x*1536)+'px';d.style.top=(n.y*1024)+'px';d.onclick=e=>{e.stopPropagation();if(!police){alert('ابتدا پلیس را انتخاب کنید');return}fetch('/move?police='+police+'&node='+n.id).then(()=>{document.getElementById('hint').innerText='✓ '+police+' → '+n.id})};box.appendChild(d)});
</script></body></html>""".replace("POLICE_JS",POLICE_JS)
 fun stop(){running=false;runCatching{socket?.close()};socket=null}
 private fun localIpv4():String=runCatching{NetworkInterface.getNetworkInterfaces().toList().flatMap{it.inetAddresses.toList()}.filterIsInstance<Inet4Address>().firstOrNull{!it.isLoopbackAddress&&it.isSiteLocalAddress}?.hostAddress?:"127.0.0.1"}.getOrDefault("127.0.0.1")
 companion object{
  private val POLICE_IDS=setOf("P001","P002","P003","P004","P005","P006","P007","P008","P009","P010","P011","P012","P013","P014","P015","P016","P017","P018","P019","P020","P021","P022","P023","P024","P025","P026","P027","P028","P029","P030","P031","P032","P033","P034","P035","P036","P037","P038","P039","P040","P041","P042","P043","P044","P045","P046","P047","P048","P049","P050","P051","P052","P053","P054","P055","P056","P057","P058","P059","P060","P061","P062","P063","P064","P065","P066","P067","P068","P069","P070","P071","P072","P073","P074","P075","P076","P077","P078","P079","P080","P081","P082","P083","P084","P085","P086","P087","P088","P089","P090","P091","P092","P093","P094","P095","P096","P097","P098","P099","P100","P101","P102","P103","P104","P105","P106","P107","P108","P109","P110","P111","P112","P113","P114","P115","P116","P117","P118","P119","P120","P121","P122","P123","P124","P125","P126","P127","P128","P129","P130","P131","P132","P133","P134","P135","P136","P137","P138","P139","P140","P141","P142","P143","P144","P145","P146","P147","P148","P149","P150","P151","P152","P153","P154","P155","P156","P157","P158","P159","P160","P161","P162","P163","P164","P165","P166","P167","P168","P169","P170","P171","P172","P173","P174","P175","P176","P177","P178")
  private const val POLICE_JS="""[{id:'P001',x:0.376302,y:0.035156},{id:'P002',x:0.937774,y:0.037842},{id:'P003',x:0.883726,y:0.064847},{id:'P004',x:0.469623,y:0.088971},{id:'P005',x:0.378596,y:0.091376},{id:'P006',x:0.597057,y:0.091589},{id:'P007',x:0.313921,y:0.092320},{id:'P008',x:0.669303,y:0.094775},{id:'P009',x:0.137984,y:0.097068},{id:'P010',x:0.109375,y:0.097656},{id:'P011',x:0.736261,y:0.098970},{id:'P012',x:0.266392,y:0.106399},{id:'P013',x:0.806605,y:0.113941},{id:'P014',x:0.789818,y:0.126818},{id:'P015',x:0.379942,y:0.142105},{id:'P016',x:0.905459,y:0.144000},{id:'P017',x:0.310547,y:0.145996},{id:'P018',x:0.819043,y:0.147510},{id:'P019',x:0.265616,y:0.151914},{id:'P020',x:0.743498,y:0.152868},{id:'P021',x:0.601604,y:0.164384},{id:'P022',x:0.862710,y:0.166310},{id:'P023',x:0.160314,y:0.169567},{id:'P024',x:0.137988,y:0.169971},{id:'P025',x:0.572132,y:0.173899},{id:'P026',x:0.433919,y:0.176880},{id:'P027',x:0.546283,y:0.183949},{id:'P028',x:0.518589,y:0.198037},{id:'P029',x:0.803662,y:0.200342},{id:'P030',x:0.266470,y:0.209982},{id:'P031',x:0.488630,y:0.213100},{id:'P032',x:0.109375,y:0.219727},{id:'P033',x:0.613777,y:0.220145},{id:'P034',x:0.185571,y:0.228577},{id:'P035',x:0.461046,y:0.229804},{id:'P036',x:0.311508,y:0.232747},{id:'P037',x:0.927618,y:0.235247},{id:'P038',x:0.153441,y:0.236742},{id:'P039',x:0.644734,y:0.242504},{id:'P040',x:0.586780,y:0.247656},{id:'P041',x:0.227772,y:0.248770},{id:'P042',x:0.190137,y:0.255217},{id:'P043',x:0.400682,y:0.258927},{id:'P044',x:0.128581,y:0.261230},{id:'P045',x:0.104167,y:0.262207},{id:'P046',x:0.500019,y:0.265262},{id:'P047',x:0.688676,y:0.269633},{id:'P048',x:0.348497,y:0.271564},{id:'P049',x:0.262594,y:0.274152},{id:'P050',x:0.471613,y:0.278952},{id:'P051',x:0.828194,y:0.279802},{id:'P052',x:0.767321,y:0.283034},{id:'P053',x:0.532813,y:0.291981},{id:'P054',x:0.411306,y:0.298738},{id:'P055',x:0.115374,y:0.299130},{id:'P056',x:0.139625,y:0.312852},{id:'P057',x:0.498708,y:0.314497},{id:'P058',x:0.538604,y:0.314508},{id:'P059',x:0.624202,y:0.315461},{id:'P060',x:0.355384,y:0.315797},{id:'P061',x:0.302207,y:0.326209},{id:'P062',x:0.272193,y:0.328224},{id:'P063',x:0.204756,y:0.330127},{id:'P064',x:0.832698,y:0.330619},{id:'P065',x:0.451799,y:0.337240},{id:'P066',x:0.230246,y:0.337331},{id:'P067',x:0.588526,y:0.339703},{id:'P068',x:0.773938,y:0.345894},{id:'P069',x:0.431808,y:0.348172},{id:'P070',x:0.190482,y:0.359318},{id:'P071',x:0.723706,y:0.359839},{id:'P072',x:0.562598,y:0.362488},{id:'P073',x:0.152235,y:0.366710},{id:'P074',x:0.369855,y:0.369331},{id:'P075',x:0.681671,y:0.373260},{id:'P076',x:0.839876,y:0.374072},{id:'P077',x:0.442254,y:0.376318},{id:'P078',x:0.642017,y:0.377979},{id:'P079',x:0.585175,y:0.385969},{id:'P080',x:0.204221,y:0.387321},{id:'P081',x:0.779122,y:0.388734},{id:'P082',x:0.907734,y:0.400430},{id:'P083',x:0.521999,y:0.408101},{id:'P084',x:0.233619,y:0.410689},{id:'P085',x:0.161857,y:0.414609},{id:'P086',x:0.845440,y:0.415504},{id:'P087',x:0.706706,y:0.426270},{id:'P088',x:0.683948,y:0.430116},{id:'P089',x:0.782584,y:0.430605},{id:'P090',x:0.337089,y:0.431836},{id:'P091',x:0.730469,y:0.435547},{id:'P092',x:0.458127,y:0.436734},{id:'P093',x:0.917074,y:0.442078},{id:'P094',x:0.286516,y:0.447365},{id:'P095',x:0.630661,y:0.451278},{id:'P096',x:0.185639,y:0.451543},{id:'P097',x:0.850221,y:0.456996},{id:'P098',x:0.313127,y:0.464759},{id:'P099',x:0.550534,y:0.475413},{id:'P100',x:0.412722,y:0.477713},{id:'P101',x:0.734528,y:0.479469},{id:'P102',x:0.687083,y:0.481821},{id:'P103',x:0.924265,y:0.483465},{id:'P104',x:0.126285,y:0.484451},{id:'P105',x:0.206822,y:0.488685},{id:'P106',x:0.527353,y:0.499051},{id:'P107',x:0.437830,y:0.499617},{id:'P108',x:0.789949,y:0.515126},{id:'P109',x:0.366234,y:0.517241},{id:'P110',x:0.223384,y:0.517694},{id:'P111',x:0.161044,y:0.526824},{id:'P112',x:0.496544,y:0.531985},{id:'P113',x:0.620674,y:0.538558},{id:'P114',x:0.714811,y:0.539990},{id:'P115',x:0.935349,y:0.542251},{id:'P116',x:0.858472,y:0.546623},{id:'P117',x:0.514301,y:0.546776},{id:'P118',x:0.169082,y:0.563151},{id:'P119',x:0.427742,y:0.571584},{id:'P120',x:0.456784,y:0.572288},{id:'P121',x:0.293041,y:0.580037},{id:'P122',x:0.262234,y:0.586823},{id:'P123',x:0.064744,y:0.589792},{id:'P124',x:0.575521,y:0.590820},{id:'P125',x:0.603190,y:0.597656},{id:'P126',x:0.660117,y:0.600550},{id:'P127',x:0.121912,y:0.600639},{id:'P128',x:0.823552,y:0.605887},{id:'P129',x:0.497489,y:0.620257},{id:'P130',x:0.445054,y:0.623058},{id:'P131',x:0.105966,y:0.631181},{id:'P132',x:0.543743,y:0.639501},{id:'P133',x:0.749349,y:0.641602},{id:'P134',x:0.597683,y:0.643127},{id:'P135',x:0.639323,y:0.644531},{id:'P136',x:0.503255,y:0.645508},{id:'P137',x:0.411079,y:0.652088},{id:'P138',x:0.726205,y:0.664007},{id:'P139',x:0.667951,y:0.664419},{id:'P140',x:0.137810,y:0.681586},{id:'P141',x:0.425970,y:0.695029},{id:'P142',x:0.061679,y:0.708163},{id:'P143',x:0.340845,y:0.709827},{id:'P144',x:0.103963,y:0.718709},{id:'P145',x:0.305933,y:0.723295},{id:'P146',x:0.642578,y:0.725098},{id:'P147',x:0.384298,y:0.725173},{id:'P148',x:0.167940,y:0.728993},{id:'P149',x:0.579440,y:0.729883},{id:'P150',x:0.571933,y:0.755753},{id:'P151',x:0.396721,y:0.760320},{id:'P152',x:0.750000,y:0.760742},{id:'P153',x:0.487250,y:0.762553},{id:'P154',x:0.314482,y:0.763156},{id:'P155',x:0.678025,y:0.772281},{id:'P156',x:0.660270,y:0.775171},{id:'P157',x:0.455589,y:0.784435},{id:'P158',x:0.644196,y:0.787856},{id:'P159',x:0.344184,y:0.791160},{id:'P160',x:0.517152,y:0.796976},{id:'P161',x:0.572997,y:0.799457},{id:'P162',x:0.400933,y:0.806836},{id:'P163',x:0.672493,y:0.816455},{id:'P164',x:0.263021,y:0.821777},{id:'P165',x:0.620400,y:0.824992},{id:'P166',x:0.600951,y:0.827101},{id:'P167',x:0.442610,y:0.834847},{id:'P168',x:0.575475,y:0.849893},{id:'P169',x:0.281887,y:0.867086},{id:'P170',x:0.351328,y:0.867580},{id:'P171',x:0.513750,y:0.868105},{id:'P172',x:0.674379,y:0.868252},{id:'P173',x:0.417052,y:0.876439},{id:'P174',x:0.224609,y:0.893555},{id:'P175',x:0.674805,y:0.893555},{id:'P176',x:0.425369,y:0.917289},{id:'P177',x:0.606771,y:0.923340},{id:'P178',x:0.676163,y:0.924699}]"""
 }
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

 BackHandler(enabled=page!="splash"){
  when(page){
   "newgame"->page="home"
   "mapserver"->page="home"
   "mapdetective"->page="mapserver"
   "jack"-> { save(); page="detective" } // never expose Jack screen after leaving it
   "detective"-> save() // consume system Back: stay in game
   "unlock"->page="detective"
   "audit"->page="home"
   "home"->Unit // consume Back so app does not accidentally exit
  }
 }

 MaterialTheme(colorScheme=darkColorScheme(primary=Gold,surface=Ink)){
  when(page){
   "splash"->Splash{page="home"}
   "home"->Home(store.exists(),onNewGame={page="newgame"},onMapGame={pilotUrl=pilotServer.start();page="mapserver"},onContinue={if(load())page=if(gameOver)"audit" else "detective"})
   "mapserver"->MapServerPage(pilotUrl,onBack={page="home"},onTest={page="mapdetective"},onCopy={
    val cb=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("Whitechapel server",pilotUrl))
   })
   "mapdetective"->MapDetectivePage(onBack={page="mapserver"})
   "newgame"->NewGame(onBack={page="home"}){h,p->hideout=h;pinHash=GameStore.hash(p);night=1;gameOver=false;starts.clear();moves.clear();queries.clear();escaped.clear();save();page="jack"}
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
    onPass={save();page="detective"},
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
   "unlock"->UnlockPage(pinHash,onSuccess={page="jack"},onCancel={page="detective"})
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

@Composable private fun MapDetectivePage(onBack:()->Unit){
 Background{
  Column(Modifier.fillMaxSize()){
   Header("جابجایی پلیس‌ها","Map Mode • Pilot",onBack,true)
   Text("پلیس را انتخاب کنید و نقطه آبی مقصد را لمس کنید. نقشه با دو انگشت قابل زوم است.",color=Gold,fontSize=12.sp,modifier=Modifier.padding(10.dp))
   AndroidView(factory={ctx->WebView(ctx).apply{
    webViewClient=WebViewClient();settings.javaScriptEnabled=true;settings.builtInZoomControls=true;settings.displayZoomControls=false;settings.setSupportZoom(true);loadUrl("http://127.0.0.1:8080/controller")
   }},modifier=Modifier.fillMaxSize())
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
