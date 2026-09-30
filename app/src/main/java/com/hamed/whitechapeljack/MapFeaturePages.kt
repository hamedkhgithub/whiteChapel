package com.hamed.whitechapeljack

import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt
import kotlin.math.roundToInt

private val FGold = Color(0xFFD6AD63)
private val FBlood = Color(0xFFB41616)
private val YELLOW_CROSSING_IDS = setOf(34, 62, 72, 132, 137, 160, 174)
private val RED_HOUSE_IDS = setOf(3, 21, 27, 65, 84, 147, 149, 158)
private const val FEATURE_BOARD_ASPECT = 3f / 2f
private fun hc(s:String)=Color(android.graphics.Color.parseColor(s))

data class DToken(val id:Int,val point:Int,val color:String,val real:Boolean,val revealed:Boolean=false)

private enum class VictimPlacementType { REAL, FAKE }
private enum class MapMarkerShape { CIRCLE, HEART }

private fun startDigitalIntro(o: JSONObject, type: String, houses: List<Int> = emptyList()) {
    o.put("introType", type)
    o.put("introId", System.currentTimeMillis())
    if (type == "hunting") {
        val publicHouses = JSONArray()
        houses.distinct().sorted().forEach { publicHouses.put(it) }
        o.put("introHouses", publicHouses)
    } else {
        o.remove("introHouses")
    }
}

@Composable
private fun DigitalIntroPage(type: String, night: Int, houses: List<Int> = emptyList()) {
    val context = LocalContext.current
    val assetName = if (type == "hunting") "intro-hunting.html" else "intro-hell.html"
    val html = remember(type, night, houses) {
        val source = context.assets.open(assetName).bufferedReader().use { it.readText() }
        if (type == "hunting") {
            source.replace("var currentHouses = [];", "var currentHouses = [${houses.distinct().sorted().joinToString(",")}];")
        } else {
            source.replace("var currentNight = 1;", "var currentNight = $night;")
        }
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

object DigitalGameStore {
    private const val PREF="whitechapel_digital_final"
    private const val KEY="game"
    private const val KEY_TWO="game_two_phone"

    private fun key(twoPhone:Boolean)=if(twoPhone) KEY_TWO else KEY
    fun exists(c:Context,twoPhone:Boolean=false)=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).contains(key(twoPhone))
    fun isTwoPhone(o:JSONObject)=o.optString("gameMode")=="two_phone"
    fun detectiveToken(c:Context):String=load(c,true)?.optString("detectiveToken","").orEmpty()

    fun newGame(c:Context,hideout:Int,pin:String,twoPhone:Boolean=false){
        val token=if(twoPhone) java.security.SecureRandom().let { r -> ByteArray(12).also(r::nextBytes).joinToString(""){"%02x".format(it.toInt() and 0xff)} } else ""
        val o=JSONObject().put("hideout",hideout).put("pin",if(twoPhone) "" else pin).put("gameMode",if(twoPhone)"two_phone" else "single_phone")
            .put("detectiveToken",token).put("night",1).put("phase","HELL_WOMEN").put("time",1)
            .put("women",JSONArray()).put("police",JSONArray()).put("crime",JSONArray()).put("crimeNight",JSONObject()).put("clues",JSONArray()).put("jackPath",JSONArray()).put("jackMoves",JSONArray()).put("moveTrack",0).put("policeDone",JSONArray()).put("policeSearched",JSONArray()).put("policeIndex",0).put("previousPolicePoints",JSONArray()).put("publicMessage","شب ۱ آغاز شد.")
        startDigitalIntro(o, "hell")
        save(c,o)
    }
    fun load(c:Context,twoPhone:Boolean=false):JSONObject?=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(key(twoPhone),null)?.let{runCatching{JSONObject(it)}.getOrNull()}
    fun save(c:Context,o:JSONObject){
        o.put("updatedAt",System.currentTimeMillis())
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(key(isTwoPhone(o)),o.toString()).apply();publish(c,o)
    }
    private fun points(c:Context,file:String)=JSONArray(c.assets.open(file).bufferedReader().use{it.readText()}).let{a->List(a.length()){i->val x=a.getJSONObject(i);BoardPoint(x.optInt("id",i+1),x.getInt("x"),x.getInt("y"),x.getDouble("norm_x").toFloat(),x.getDouble("norm_y").toFloat(),x.getInt("number"))}.associateBy{it.number}}
    fun publish(c:Context){ val o=load(c)?:load(c,true)?:return; publish(c,o) }
    fun publish(c:Context,o:JSONObject){
        val houses=points(c,"houses.json");val pp=points(c,"polises.json");val pub=JSONObject().put("mode","digital").put("night",o.optInt("night",1)).put("phase",o.optString("phase")).put("time",o.optInt("time",1))
        val publicAppearance=AppearanceStore.load(c).publicDisplay
        val phase=o.optString("phase")
        val victimsRevealed=phase !in setOf("HELL_WOMEN","HAND_POLICE","HELL_POLICE","HAND_JACK")
        val wa=JSONArray(); val women=o.optJSONArray("women")?:JSONArray();if(phase!="HELL_WOMEN")for(i in 0 until women.length()){val x=women.getJSONObject(i);houses[x.getInt("point")]?.let{p->wa.put(JSONObject().put("x",p.normX).put("y",p.normY).put("revealed",victimsRevealed).put("real",x.optBoolean("real",true)))}}
        val pa=JSONArray();val police=o.optJSONArray("police")?:JSONArray();if(phase!="HELL_POLICE")for(i in 0 until police.length()){val x=police.getJSONObject(i);pp[x.getInt("point")]?.let{p->val item=JSONObject().put("x",p.normX).put("y",p.normY).put("revealed",x.optBoolean("revealed",false)).put("real",x.optBoolean("real",true)).put("id",x.optInt("id",1));pa.put(item)}}
        val ca=JSONArray();val crimes=o.optJSONArray("crime")?:JSONArray();val currentCrimes=currentCrimePoints(o);for(i in 0 until crimes.length()){val crimeId=crimes.getInt(i);houses[crimeId]?.let{p->ca.put(JSONObject().put("x",p.normX).put("y",p.normY).put("current",crimeId in currentCrimes))}}
        val cla=JSONArray();val clues=o.optJSONArray("clues")?:JSONArray();for(i in 0 until clues.length()){houses[clues.getInt(i)]?.let{p->cla.put(JSONObject().put("x",p.normX).put("y",p.normY))}}
        val moveSpecials=JSONArray();var usedTrack=0;val jackMoves=o.optJSONArray("jackMoves")?:JSONArray()
        for(i in 0 until jackMoves.length()){
            val type=jackMoves.optJSONObject(i)?.optString("type").orEmpty();val cost=if(type=="COACH")2 else 1
            if(type=="COACH" || type=="ALLEY") moveSpecials.put(JSONObject().put("type",type).put("from",usedTrack+1).put("to",usedTrack+cost));usedTrack+=cost
        }
        val setupCrossings=JSONArray();if(phase=="HELL_POLICE") YELLOW_CROSSING_IDS.forEach { id -> pp[id]?.let { p -> setupCrossings.put(JSONObject().put("x",p.normX).put("y",p.normY)) } }
        pub.put("women",wa).put("digitalPolice",pa).put("crime",ca).put("clues",cla).put("setupCrossings",setupCrossings)
            .put("moveTrack",o.optInt("moveTrack",0)).put("moveSpecials",moveSpecials).put("appearance",publicAppearance.toJson()).put("publicMessage",o.optString("publicMessage","")).put("publicPhase",publicPhaseName(o.optString("phase")))
            .put("introType",o.optString("introType","")).put("introId",o.optLong("introId",0)).put("introHouses",o.optJSONArray("introHouses")?:JSONArray());TvMapHub.setState(pub)
    }

    fun detectiveState(c:Context,token:String):JSONObject{
        val o=load(c,true)?:return JSONObject().put("ok",false).put("error","No two-phone game")
        if(token.isBlank() || token!=o.optString("detectiveToken")) return JSONObject().put("ok",false).put("error","Invalid detective link")
        val phase=o.optString("phase");val detectivePhases=setOf("HELL_POLICE","HELL_MOVE_WOMEN","HUNT_POLICE_MOVE","HUNT_POLICE_ACTION")
        val introRunning=o.optString("introType","").isNotBlank();val out=JSONObject().put("ok",true).put("night",o.optInt("night",1)).put("time",o.optInt("time",1)).put("phase",phase).put("active",phase in detectivePhases && !introRunning).put("introRunning",introRunning).put("updatedAt",o.optLong("updatedAt",0)).put("message",o.optString("publicMessage",""))
        val pa=JSONArray();(o.optJSONArray("police")?:JSONArray()).let{a->for(i in 0 until a.length()){val x=a.getJSONObject(i);pa.put(JSONObject().put("id",x.optInt("id")).put("point",x.optInt("point")).put("real",x.optBoolean("real",true)).put("revealed",x.optBoolean("revealed",false)).put("color",x.optString("color","#777777")))}};out.put("police",pa)
        val wa=JSONArray();(o.optJSONArray("women")?:JSONArray()).let{a->for(i in 0 until a.length()){val x=a.getJSONObject(i);wa.put(JSONObject().put("id",x.optInt("id")).put("point",x.optInt("point")))}};out.put("women",wa)
        out.put("crime",o.optJSONArray("crime")?:JSONArray()).put("clues",o.optJSONArray("clues")?:JSONArray()).put("previousPolicePoints",o.optJSONArray("previousPolicePoints")?:JSONArray()).put("policeDone",o.optJSONArray("policeDone")?:JSONArray()).put("policeSearched",o.optJSONArray("policeSearched")?:JSONArray())
        return out
    }

    fun detectiveAction(c:Context,token:String,action:String,args:Map<String,String>):JSONObject{
        val o=load(c,true)?:return JSONObject().put("ok",false).put("error","No two-phone game")
        if(token.isBlank() || token!=o.optString("detectiveToken")) return JSONObject().put("ok",false).put("error","Invalid detective link")
        val phase=o.optString("phase");fun bad(m:String)=JSONObject().put("ok",false).put("error",m)
        if(o.optString("introType","").isNotBlank()) return bad("Intro is still running")
        val id=args["id"]?.toIntOrNull();val point=args["point"]?.toIntOrNull();val police=o.optJSONArray("police")?.tokens().orEmpty();val women=o.optJSONArray("women")?.tokens().orEmpty()
        when(action){
            "placePolice"->{
                if(phase!="HELL_POLICE"||id==null||point==null||id !in 1..7)return bad("Invalid police placement")
                val prev=(o.optJSONArray("previousPolicePoints")?:JSONArray()).intSet();val extra=YELLOW_CROSSING_IDS-prev;val allowed=if(o.optInt("night",1)==1)YELLOW_CROSSING_IDS else prev+extra
                if(point !in allowed)return bad("This crossing is not allowed")
                val list=police.toMutableList();if(list.any{it.id!=id&&it.point==point})return bad("Crossing already occupied")
                if(o.optInt("night",1)>1 && point in extra){val projected=(list.filter{it.id!=id}.map{it.point}+point).count{it in extra};if(projected>2)return bad("Only two new yellow crossings are allowed")}
                val colors=AppearanceStore.load(c).phone.policeColors;val t=DToken(id,point,colors[if(id<=5)id-1 else 5],id<=5,false);val k=list.indexOfFirst{it.id==id};if(k>=0)list[k]=t else list+=t;o.put("police",list.json())
            }
            "finishPoliceSetup"->{
                if(phase!="HELL_POLICE")return bad("Wrong phase")
                val pts=police.map{it.point}.toSet();val prev=(o.optJSONArray("previousPolicePoints")?:JSONArray()).intSet();val extra=YELLOW_CROSSING_IDS-prev;val valid=police.size==7&&pts.size==7&&if(o.optInt("night",1)==1)pts==YELLOW_CROSSING_IDS else prev.size==5&&prev.all{it in pts}&&pts.count{it in extra}==2
                if(!valid)return bad("Police setup is incomplete")
                o.put("women",women.filter{it.real}.json()).put("time",1).put("phase","HELL_DECISION");setPublicMessage(o,"قربانی‌های جعلی حذف شدند و قربانی‌های واقعی روی نقشه باقی ماندند.")
            }
            "moveWoman"->{if(phase!="HELL_MOVE_WOMEN"||id==null||point==null)return bad("Wrong phase");if(point in (o.optJSONArray("crime")?:JSONArray()).intSet())return bad("Crime scene is blocked");val list=women.toMutableList();val k=list.indexOfFirst{it.id==id};if(k<0)return bad("Victim not found");if(list.any{it.id!=id&&it.point==point})return bad("House already occupied");list[k]=list[k].copy(point=point);o.put("women",list.json())}
            "finishWomenMove"->{if(phase!="HELL_MOVE_WOMEN")return bad("Wrong phase");o.put("phase","HELL_REVEAL_POLICE")}
            "movePolice"->{if(phase!="HUNT_POLICE_MOVE"||id==null||point==null)return bad("Wrong phase");val list=police.toMutableList();val k=list.indexOfFirst{it.id==id&&it.real};if(k<0)return bad("Police not found");if(list.any{it.id!=id&&it.point==point})return bad("Crossing occupied");list[k]=list[k].copy(point=point,revealed=true);o.put("police",list.json())}
            "finishPoliceMove"->{if(phase!="HUNT_POLICE_MOVE")return bad("Wrong phase");o.put("policeDone",JSONArray()).put("policeSearched",JSONArray()).put("policeIndex",0).put("phase","HUNT_POLICE_ACTION");setPublicMessage(o,"حرکت پلیس‌ها پایان یافت؛ مرحله سرنخ و دستگیری آغاز شد.")}
            "search"->{if(phase!="HUNT_POLICE_ACTION"||id==null||point==null)return bad("Wrong phase");if(police.none{it.id==id&&it.real})return bad("Police not found");val done=(o.optJSONArray("policeDone")?:JSONArray()).intSet();if(id in done)return bad("Police action already finished");val path=o.optJSONArray("jackPath")?:JSONArray();val visited=(0 until path.length()).any{path.optInt(it)==point};markPoliceSearched(o,id);if(visited){val ca=o.optJSONArray("clues")?:JSONArray();if((0 until ca.length()).none{ca.optInt(it)==point})ca.put(point);o.put("clues",ca);markPoliceDone(o,id);setPublicMessage(o,"سرنخ پیدا شد — جک از خانه $point عبور کرده است.")}else setPublicMessage(o,"جک از خانه $point عبور نکرده است.")}
            "endSearch"->{if(phase!="HUNT_POLICE_ACTION"||id==null)return bad("Wrong phase");markPoliceDone(o,id);setPublicMessage(o,"استعلام‌های سرنخ یک پلیس پایان یافت.")}
            "arrest"->{if(phase!="HUNT_POLICE_ACTION"||id==null||point==null)return bad("Wrong phase");if(police.none{it.id==id&&it.real})return bad("Police not found");if(id in (o.optJSONArray("policeDone")?:JSONArray()).intSet())return bad("Police action already finished");if(id in (o.optJSONArray("policeSearched")?:JSONArray()).intSet())return bad("This police already searched");val path=o.optJSONArray("jackPath")?:JSONArray();val current=if(path.length()>0)path.optInt(path.length()-1) else -1;if(current==point){o.put("phase","GAME_OVER_POLICE");setPublicMessage(o,"دستگیری موفق بود — جک در خانه $point دستگیر شد.")}else{markPoliceDone(o,id);setPublicMessage(o,"دستگیری ناموفق بود — جک در خانه $point نیست.")}}
            "handoffJack"->{if(phase!="HUNT_POLICE_ACTION")return bad("Wrong phase");setPublicMessage(o,"نوبت کارآگاه‌ها پایان یافت؛ نوبت جک آغاز شد.");o.put("phase","HUNT_JACK")}
            else->return bad("Unknown action")
        }
        save(c,o);return JSONObject().put("ok",true).put("state",detectiveState(c,token))
    }
}
private fun JSONArray.tokens():MutableList<DToken>{val r=mutableListOf<DToken>();for(i in 0 until length()){val x=getJSONObject(i);r+=DToken(x.getInt("id"),x.getInt("point"),x.optString("color","#FFFFFF"),x.optBoolean("real",true),x.optBoolean("revealed",false))};return r}
private fun List<DToken>.json():JSONArray{val a=JSONArray();forEach{a.put(JSONObject().put("id",it.id).put("point",it.point).put("color",it.color).put("real",it.real).put("revealed",it.revealed))};return a}
private fun womenCounts(n:Int)=when(n){1->8 to 5;2->7 to 4;3->6 to 3;else->4 to 1}
private fun roman(n:Int)=listOf("","I","II","III","IV","V").getOrElse(n){n.toString()}
private val MOVE_TRACK_LABELS = listOf("V","IV","III","II","I") + (1..15).map(Int::toString)
private fun moveTrackStartIndex(time:Int)=(5-time.coerceIn(1,5)).coerceIn(0,4)
private fun isHuntingPhase(phase:String)=phase.startsWith("HUNT_") || phase.startsWith("GAME_OVER")

private data class MoveTrackVisual(
    val currentPointId:Int,
    val alleyPointIds:Set<Int>,
    val coachPointPairs:List<Pair<Int,Int>>
)

private fun moveTrackVisual(o:JSONObject):MoveTrackVisual{
    val time=o.optInt("time",1).coerceIn(1,5)
    val phase=o.optString("phase")
    val hunting=isHuntingPhase(phase)
    val start=moveTrackStartIndex(time)
    val used=if(hunting)o.optInt("moveTrack",0).coerceAtLeast(0) else 0
    val currentIndex=(start+used).coerceIn(0,MOVE_TRACK_LABELS.lastIndex)
    val alley=mutableSetOf<Int>()
    val coach=mutableListOf<Pair<Int,Int>>()
    if(hunting){
        var cursor=0
        val moves=o.optJSONArray("jackMoves")?:JSONArray()
        for(i in 0 until moves.length()){
            when(moves.optJSONObject(i)?.optString("type")){
                "COACH"->{
                    val a=start+cursor+1
                    val b=start+cursor+2
                    if(a in MOVE_TRACK_LABELS.indices && b in MOVE_TRACK_LABELS.indices) coach += (a+1) to (b+1)
                    cursor+=2
                }
                "ALLEY"->{
                    val a=start+cursor+1
                    if(a in MOVE_TRACK_LABELS.indices) alley += a+1
                    cursor+=1
                }
                else->cursor+=1
            }
        }
    }
    // move_track_points.json uses IDs 1..20 from left to right:
    // V, IV, III, II, I, 1 ... 15.
    return MoveTrackVisual(currentIndex+1,alley,coach)
}

private fun publicPhaseName(phase:String)=when(phase){
    "HELL_WOMEN"->"آماده‌سازی • جانمایی قربانی‌ها"
    "HELL_POLICE"->"آماده‌سازی • جانمایی پلیس"
    "HELL_DECISION"->"آماده‌سازی • تصمیم جک"
    "HELL_WAIT_HANDOFF"->"آماده‌سازی • تحویل به کارآگاه"
    "HELL_MOVE_WOMEN"->"آماده‌سازی • حرکت قربانی‌ها"
    "HELL_REVEAL_POLICE"->"آماده‌سازی • افشای گشت پلیس"
    "HELL_KILL"->"آماده‌سازی • قتل"
    "HUNT_JACK_UNLOCK","HUNT_JACK"->"تعقیب • نوبت جک"
    "HUNT_POLICE_HANDOFF","HUNT_POLICE_MOVE"->"تعقیب • نوبت پلیس"
    "HUNT_POLICE_ACTION"->"تعقیب • سرنخ / دستگیری"
    "GAME_OVER_POLICE"->"پایان بازی • پیروزی کارآگاه‌ها"
    "GAME_OVER_JACK"->"پایان بازی • پیروزی جک"
    else->""
}
private fun setPublicMessage(o:JSONObject,message:String){o.put("publicMessage",message)}

@Composable fun DigitalGamePage(onBack:()->Unit, twoPhone:Boolean=false){
    val ctx=LocalContext.current;var rev by remember{mutableIntStateOf(0)};
    var lastSeenUpdate by remember { mutableLongStateOf(0L) }
    LaunchedEffect(twoPhone) {
        if(twoPhone) while(true){
            val stamp=DigitalGameStore.load(ctx,true)?.optLong("updatedAt",0L)?:0L
            if(stamp!=0L && stamp!=lastSeenUpdate){ lastSeenUpdate=stamp; rev++ }
            delay(700)
        }
    }
    var selectedId by remember{mutableIntStateOf(1)};var action by remember{mutableStateOf("search")}
    var victimPlacementType by remember { mutableStateOf(VictimPlacementType.REAL) }
    val o=remember(rev,twoPhone){DigitalGameStore.load(ctx,twoPhone)}
    if(o==null){Column(Modifier.fillMaxSize().background(Color(0xFF100E0C)).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("بازی با نقشه‌ای ذخیره نشده است.",color=Color.White);Button(onClick=onBack){Text("بازگشت")}};return}
    val phase=o.optString("phase");val night=o.optInt("night",1);val time=o.optInt("time",1);val women=o.optJSONArray("women")!!.tokens();val police=o.optJSONArray("police")!!.tokens()
    val introType=o.optString("introType","")
    val introId=o.optLong("introId",0L)
    val introHouses=(o.optJSONArray("introHouses")?:JSONArray()).let { a -> List(a.length()){ i -> a.optInt(i) }.filter{it>0}.sorted() }
    if(introType=="hell" || introType=="hunting"){
        LaunchedEffect(introId){
            delay(if(introType=="hunting")4700 else 3900)
            val latest=DigitalGameStore.load(ctx,twoPhone)
            if(latest!=null && latest.optLong("introId",0L)==introId){
                latest.remove("introType")
                latest.remove("introId")
                latest.remove("introHouses")
                DigitalGameStore.save(ctx,latest)
                rev++
            }
        }
        DigitalIntroPage(introType,night,introHouses)
        return
    }
    val detectiveOwned = phase in setOf("HELL_POLICE","HELL_MOVE_WOMEN","HUNT_POLICE_MOVE","HUNT_POLICE_ACTION")
    if(twoPhone && detectiveOwned){
        Column(Modifier.fillMaxSize().background(Color(0xFF100E0C)).padding(20.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
            Text("منتظر حرکت کارآگاه",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text("کنترل بازی اکنون روی گوشی کارآگاه است.",color=FGold,fontSize=14.sp,textAlign=TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            TextButton(onClick=onBack){Text("‹ خانه",color=FGold)}
        }
        return
    }
    val trackVisual=moveTrackVisual(o)
    val phoneAppearance=remember(rev){AppearanceStore.load(ctx).phone}
    fun policeColor(token:DToken, identityVisible:Boolean=true)=hc(phoneAppearance.policeColor(token.id,token.real,identityVisible))
    fun victimColor(token:DToken, identityVisible:Boolean=true)=hc(if(identityVisible && token.real) phoneAppearance.victimRealColor else phoneAppearance.victimFakeColor)
    var movingTokenId by remember(phase){mutableStateOf<Int?>(null)}
    var pendingPoint by remember(phase){mutableStateOf<Int?>(null)}
    var hideoutVisible by remember(phase){mutableStateOf(false)}
    val jackPrivatePhase = phase in setOf("HELL_WOMEN","HELL_DECISION","HELL_REVEAL_POLICE","HELL_KILL","HUNT_JACK")
    LaunchedEffect(Unit) { DigitalGameStore.publish(ctx,o) }
    val hp=rememberBoardPointsFeature("houses.json");val pp=rememberBoardPointsFeature("polises.json")
    LaunchedEffect(phase) {
        selectedId = 1
        action = "search"
        if (phase == "HELL_WOMEN") victimPlacementType = VictimPlacementType.REAL
    }
    fun save(){DigitalGameStore.save(ctx,o);rev++}
    fun setPhase(p:String){o.put("phase",p);save()}
    val title=when(phase){"HELL_WOMEN"->"آماده‌سازی • جک: جانمایی قربانی‌ها";"HAND_POLICE"->"تحویل گوشی به کارآگاه";"HELL_POLICE"->"آماده‌سازی • کارآگاه: جانمایی پلیس";"HAND_JACK"->"تحویل گوشی به جک";"HELL_DECISION"->"آماده‌سازی • تصمیم جک";"HELL_WAIT_HANDOFF"->"تحویل گوشی به کارآگاه";"HELL_MOVE_WOMEN"->"آماده‌سازی • حرکت قربانی‌ها توسط کارآگاه";"HELL_REVEAL_POLICE"->"آماده‌سازی • جک: افشای یک گشت پلیس";"HELL_KILL"->"آماده‌سازی • انتخاب قربانی";"HUNT_JACK_UNLOCK"->"تعقیب • ورود محرمانه جک";"HUNT_JACK"->"تعقیب • ثبت حرکت جک";"HUNT_POLICE_HANDOFF"->"تعقیب • تحویل به کارآگاه";"HUNT_POLICE_MOVE"->"تعقیب • حرکت پلیس";"HUNT_POLICE_ACTION"->"تعقیب • سرنخ / دستگیری";else->phase}
    Column(Modifier.fillMaxSize().background(Color(0xFF100E0C)).padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onBack){Text("‹ خانه",color=FGold)}
            Text(title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.weight(1f),textAlign=TextAlign.Center)
            Column(horizontalAlignment=Alignment.End){
                Text("شب $night • ${roman(time)}",color=FGold,fontSize=12.sp)
                if(jackPrivatePhase){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){
                        Text("مخفیگاه: ${if(hideoutVisible)o.optInt("hideout").toString() else "•••"}",color=FGold,fontSize=11.sp,fontWeight=FontWeight.Bold)
                        IconButton(onClick={hideoutVisible=!hideoutVisible},modifier=Modifier.size(28.dp)){Text("👁",fontSize=16.sp)}
                    }
                }
            }
        }
        TvLinkCard()
        o.optString("publicMessage","").takeIf{it.isNotBlank()}?.let{message->
            Surface(color=Color(0xFF241E18),shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth()){
                Text(message,color=Color(0xFFFFF3C4),fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp))
            }
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
        when(phase){
            "HELL_WOMEN"->{
                val (total,realTarget)=womenCounts(night)
                val fakeTarget=total-realTarget
                val realCount=women.count{it.real};val fakeCount=women.count{!it.real}
                VictimTypeSelector(
                    selectedType = victimPlacementType,
                    appearance = phoneAppearance,
                    onSelect = { victimPlacementType = it }
                )
                Text(
                    "قربانی واقعی: $realCount/$realTarget   •   قربانی جعلی: $fakeCount/$fakeTarget",
                    color=Color.LightGray,fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth()
                )
                Text(
                    "نوع قربانی را از بالا انتخاب کن. هر بار روی یک خانه خالی بزنی، قربانی از همان نوع اضافه می‌شود. با لمس دوباره قربانی موجود، همان قربانی حذف می‌شود.",
                    color=Color.LightGray,fontSize=11.sp
                )
                val previousCrimeScenes=(o.optJSONArray("crime")?:JSONArray()).intSet()
                GameMap(hp,true,women.associate{it.point to victimColor(it,true)},emptySet(),{p->
                    // Victims can only be placed on the eight red numbered houses, and a
                    // Crime Scene from a previous night can never be used again.
                    val list = women.toMutableList()
                    val existingIndex = list.indexOfFirst { it.point == p.number }
                    if (existingIndex >= 0) {
                        list.removeAt(existingIndex)
                    } else if (p.number in RED_HOUSE_IDS && p.number !in previousCrimeScenes) {
                        val placingReal = victimPlacementType == VictimPlacementType.REAL
                        val typeCount = list.count { it.real == placingReal }
                        val typeLimit = if (placingReal) realTarget else fakeTarget
                        if (typeCount < typeLimit) {
                            val nextId = (list.maxOfOrNull { it.id } ?: 0) + 1
                            list += DToken(
                                id = nextId,
                                point = p.number,
                                color = if (placingReal) "#D32F2F" else "#FFFFFF",
                                real = placingReal
                            )
                        }
                    }
                    o.put("women", list.json())
                    save()
                },Modifier.weight(1f),crimePoints=previousCrimeScenes,currentCrimePoints=currentCrimePoints(o),primaryShape=MapMarkerShape.HEART,moveTrackVisual=trackVisual)
                Button(onClick={setPhase(if(twoPhone) "HELL_POLICE" else "HAND_POLICE")},enabled=realCount==realTarget && fakeCount==fakeTarget,modifier=Modifier.fillMaxWidth()){Text("تحویل به کارآگاه")}
            }
            "HAND_POLICE"->Handoff("گوشی را به کارآگاه بدهید"){setPhase("HELL_POLICE")}
            "HELL_POLICE"->{
                var placementMessage by remember(phase){mutableStateOf("")}
                val previousPolicePoints=(o.optJSONArray("previousPolicePoints")?:JSONArray()).intSet()
                val extraYellow=YELLOW_CROSSING_IDS - previousPolicePoints
                val allowedPoints=if(night==1)YELLOW_CROSSING_IDS else previousPolicePoints + extraYellow
                val occupiedPoints=police.map{it.point}.toSet()
                val setupValid=police.size==7 && occupiedPoints.size==7 && if(night==1){
                    occupiedPoints==YELLOW_CROSSING_IDS
                }else{
                    previousPolicePoints.size==5 && previousPolicePoints.all{it in occupiedPoints} && occupiedPoints.count{it in extraYellow}==2
                }
                TokenSelector(List(7){i->hc(phoneAppearance.policeColors[if(i<5)i else 5])},phoneAppearance.policeFillAlpha,selectedId){selectedId=it;placementMessage=""}
                Text(
                    if(night==1) "هر ۷ گشت را روی هفت تقاطع زرد قرار بده. هویت ۵ گشت واقعی و ۲ گشت جعلی فقط برای کارآگاه معلوم است."
                    else "پنج گشت باید روی پنج موقعیت پایان شب قبل و دو گشت روی دو تقاطع زرد دیگر قرار بگیرند؛ هویت‌ها را می‌توانی دوباره مخلوط کنی.",
                    color=Color.LightGray,fontSize=11.sp
                )
                if(placementMessage.isNotBlank())Text(placementMessage,color=Color(0xFFFF8A80),fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                GameMap(pp,true,police.associate{it.point to policeColor(it,true)},emptySet(),{p->
                    val list=police.toMutableList()
                    val occupiedByOther=list.any{it.id!=selectedId && it.point==p.number}
                    when{
                        p.number !in allowedPoints -> placementMessage=if(night==1) "این نقطه تقاطع زرد نیست." else "این نقطه جزو پنج موقعیت پایان شب قبل یا تقاطع‌های زرد مجاز نیست."
                        occupiedByOther -> placementMessage="این تقاطع قبلاً توسط یک گشت دیگر اشغال شده است."
                        night>1 && p.number in extraYellow -> {
                            val projectedExtraCount=(list.filter{it.id!=selectedId}.map{it.point}+p.number).count{it in extraYellow}
                            if(projectedExtraCount>2){
                                placementMessage="فقط دو گشت می‌توانند روی دو تقاطع زرد جدید قرار بگیرند. پنج گشت دیگر باید روی پنج محل مشخص‌شده با حلقه طلایی باشند."
                            }else{
                                placementMessage=""
                                val color=phoneAppearance.policeColors[if(selectedId<=5) selectedId-1 else 5]
                                val t=DToken(selectedId,p.number,color,selectedId<=5,false)
                                val k=list.indexOfFirst{it.id==selectedId}
                                if(k>=0)list[k]=t else list+=t
                                o.put("police",list.json());save()
                            }
                        }
                        else -> {
                            placementMessage=""
                            val color=phoneAppearance.policeColors[if(selectedId<=5) selectedId-1 else 5]
                            val t=DToken(selectedId,p.number,color,selectedId<=5,false)
                            val k=list.indexOfFirst{it.id==selectedId}
                            if(k>=0)list[k]=t else list+=t
                            o.put("police",list.json());save()
                        }
                    }
                },Modifier.weight(1f),secondaryPoints=hp,secondaryMarkers=women.associate{it.point to victimColor(it,false)},secondaryShape=MapMarkerShape.HEART,overlayPoints=hp,requiredPoints=if(night>1)previousPolicePoints else emptySet(),yellowSetupPoints=YELLOW_CROSSING_IDS,moveTrackVisual=trackVisual)
                Button(onClick={setPhase("HAND_JACK")},enabled=setupValid,modifier=Modifier.fillMaxWidth()){Text("تحویل به جک")}
            }
            "HAND_JACK"->Handoff("گوشی را به جک بدهید") {val realWomen=women.filter{it.real};o.put("women",realWomen.json()).put("time",1);setPublicMessage(o,"قربانی‌های جعلی حذف شدند و قربانی‌های واقعی روی نقشه باقی ماندند.");setPhase("HELL_DECISION")}
            "HELL_DECISION"->{Text("زمان ارتکاب جرم: ${roman(time)}",color=FGold,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center);Spacer(Modifier.weight(1f));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={setPhase("HELL_KILL")},colors=ButtonDefaults.buttonColors(containerColor=FBlood),modifier=Modifier.weight(1f)){Text("کشتن")};Button(onClick={val next=(time+1).coerceAtMost(5);o.put("time",next);setPublicMessage(o,"جک منتظر ماند؛ زمان ارتکاب جرم به ${roman(next)} منتقل شد.");setPhase(if(twoPhone) "HELL_MOVE_WOMEN" else "HELL_WAIT_HANDOFF")},enabled=time<5,modifier=Modifier.weight(1f)){Text(if(time<5)"انتظار" else "در V باید بکشد")}};Spacer(Modifier.weight(1f))}
            "HELL_WAIT_HANDOFF"->Handoff("گوشی را به کارآگاه بدهید\n"){setPhase("HELL_MOVE_WOMEN")}
            "HELL_MOVE_WOMEN"->{
                val preview=women.map{token->if(token.id==movingTokenId && pendingPoint!=null)token.copy(point=pendingPoint!!) else token}
                val activePoint=movingTokenId?.let{id->preview.firstOrNull{it.id==id}?.point}
                Text("روی خود مهره بزن، سپس مقصد را لمس کن. با انتخاب مهره بعدی، حرکت قبلی ذخیره می‌شود.",color=Color.White,fontSize=12.sp)
                GameMap(hp,true,preview.associate{it.point to victimColor(it,true)},activePoint?.let(::setOf)?:emptySet(),{p->
                    val tokenAtPoint=preview.firstOrNull{it.point==p.number}
                    when{
                        tokenAtPoint!=null && tokenAtPoint.id!=movingTokenId->{
                            if(movingTokenId!=null && pendingPoint!=null){
                                val committed=women.toMutableList();val k=committed.indexOfFirst{it.id==movingTokenId};if(k>=0)committed[k]=committed[k].copy(point=pendingPoint!!);o.put("women",committed.json());save()
                            }
                            movingTokenId=tokenAtPoint.id;pendingPoint=null
                        }
                        movingTokenId==null && tokenAtPoint!=null->{movingTokenId=tokenAtPoint.id;pendingPoint=null}
                        movingTokenId!=null && tokenAtPoint==null && p.number !in (o.optJSONArray("crime")?:JSONArray()).intSet()->{pendingPoint=p.number}
                    }
                },Modifier.weight(1f),secondaryPoints=pp,secondaryMarkers=police.associate{it.point to policeColor(it,true)},crimePoints=(o.optJSONArray("crime")?:JSONArray()).intSet(),currentCrimePoints=currentCrimePoints(o),primaryShape=MapMarkerShape.HEART,moveTrackVisual=trackVisual)
                Button(onClick={
                    val committed=women.toMutableList();if(movingTokenId!=null && pendingPoint!=null){val k=committed.indexOfFirst{it.id==movingTokenId};if(k>=0)committed[k]=committed[k].copy(point=pendingPoint!!)}
                    o.put("women",committed.json()).put("phase","HELL_REVEAL_POLICE");movingTokenId=null;pendingPoint=null;save()
                },modifier=Modifier.fillMaxWidth()){Text("پایان حرکت قربانی‌ها • تحویل به جک")}
            }
            "HELL_REVEAL_POLICE"->{
                Text("یکی از گشت‌های پلیس مخفی را انتخاب کن تا هویتش آشکار شود.",color=Color.White)
                GameMap(pp,true,police.associate{it.point to policeColor(it,it.revealed)},emptySet(),{p->
                    val list=police.toMutableList()
                    val k=list.indexOfFirst{it.point==p.number&&!it.revealed}
                    if(k>=0){
                        val token=list[k]
                        if(token.real){
                            list[k]=token.copy(revealed=true)
                            setPublicMessage(o,"یک گشت پلیس واقعی آشکار شد و روی نقشه باقی ماند.")
                        }else{
                            list.removeAt(k)
                            setPublicMessage(o,"یک گشت پلیس جعلی آشکار و از نقشه حذف شد.")
                        }
                        o.put("police",list.json())
                        setPhase("HELL_DECISION")
                    }
                },Modifier.weight(1f),secondaryPoints=hp,secondaryMarkers=women.associate{it.point to victimColor(it,true)},secondaryShape=MapMarkerShape.HEART,overlayPoints=hp,crimePoints=(o.optJSONArray("crime")?:JSONArray()).intSet(),currentCrimePoints=currentCrimePoints(o),moveTrackVisual=trackVisual)
            }
            "HELL_KILL"->{
                val firstDoubleKill=o.optInt("doubleKillFirst",-1).takeIf{it>0}
                Text(
                    if(night==3 && firstDoubleKill==null) "شب سوم: قربانی اول را انتخاب کن."
                    else if(night==3) "قربانی اول: $firstDoubleKill • حالا قربانی دوم را انتخاب کن. لمس دوباره قربانی اول، انتخاب را لغو می‌کند."
                    else "یکی از قربانی‌های باقی‌مانده را برای قتل انتخاب کن.",
                    color=Color.White
                )
                GameMap(hp,true,women.associate{it.point to victimColor(it,true)},firstDoubleKill?.let(::setOf)?:emptySet(),{p->
                    val victim=women.firstOrNull{it.point==p.number}?:return@GameMap
                    if(night==3){
                        if(firstDoubleKill==null){
                            o.put("doubleKillFirst",victim.point);save()
                        }else if(victim.point==firstDoubleKill){
                            o.remove("doubleKillFirst");save()
                        }else{
                            val crimes=o.optJSONArray("crime")?:JSONArray()
                            if((0 until crimes.length()).none{crimes.getInt(it)==firstDoubleKill})crimes.put(firstDoubleKill)
                            if((0 until crimes.length()).none{crimes.getInt(it)==victim.point})crimes.put(victim.point)
                            recordCrimeNight(o,firstDoubleKill,night)
                            recordCrimeNight(o,victim.point,night)
                            val path=JSONArray().put(firstDoubleKill).put(victim.point)
                            val firstMove=JSONArray().put(JSONObject().put("type","DOUBLE_EVENT").put("first",victim.point))
                            o.put("crime",crimes).put("women",JSONArray()).put("jackPath",path).put("jackMoves",firstMove).put("moveTrack",1).put("policeDone",JSONArray()).put("policeSearched",JSONArray()).remove("doubleKillFirst")
                            val revealed=police.filter{it.real}.map{it.copy(revealed=true)}
                            o.put("police",revealed.json()).put("policeIndex",0)
                            setPublicMessage(o,"دو قتل شب سوم رخ داد؛ هر دو محل قتل ثبت شدند و تعقیب با نوبت پلیس آغاز می‌شود.")
                            startDigitalIntro(o,"hunting",listOf(firstDoubleKill,victim.point))
                            setPhase(if(twoPhone) "HUNT_POLICE_MOVE" else "HUNT_POLICE_HANDOFF")
                        }
                    }else{
                        val crimes=o.optJSONArray("crime")?:JSONArray(); if((0 until crimes.length()).none{crimes.getInt(it)==victim.point}) crimes.put(victim.point)
                        recordCrimeNight(o,victim.point,night)
                        o.put("crime",crimes).put("women",JSONArray()).put("jackPath",JSONArray().put(victim.point)).put("jackMoves",JSONArray()).put("moveTrack",0).put("policeDone",JSONArray()).put("policeSearched",JSONArray())
                        val revealed=police.filter{it.real}.map{it.copy(revealed=true)}
                        o.put("police",revealed.json()).put("policeIndex",0)
                        setPublicMessage(o,"قتل در خانه ${victim.point} رخ داد؛ محل قتل ثبت شد و مرحله تعقیب آغاز شد.")
                        startDigitalIntro(o,"hunting",listOf(victim.point))
                        setPhase(if(twoPhone) "HUNT_JACK" else "HUNT_JACK_UNLOCK")
                    }
                },Modifier.weight(1f),secondaryPoints=pp,secondaryMarkers=police.associate{it.point to policeColor(it,it.revealed)},crimePoints=(o.optJSONArray("crime")?:JSONArray()).intSet(),currentCrimePoints=currentCrimePoints(o),primaryShape=MapMarkerShape.HEART,moveTrackVisual=trackVisual)
            }
            "HUNT_POLICE_HANDOFF"->Handoff("گوشی را به کارآگاه بدهید\nتعقیب شب سوم با پلیس آغاز می‌شود.\n"){setPhase("HUNT_POLICE_MOVE")}
            "HUNT_JACK_UNLOCK"->{
                DigitalJackUnlock(o.optString("pin"), onSuccess={setPhase("HUNT_JACK")})
            }
            "HUNT_JACK"->{
                DigitalJackMove(o,hp,pp,police.filter{it.real && it.revealed},onCommitted={escaped->
                    val moveLimit=(14+o.optInt("time",1)).coerceIn(15,19)
                    if(escaped){endNight(o,ctx);rev++}
                    else if(o.optInt("moveTrack",0)>=moveLimit){
                        o.put("phase","GAME_OVER_POLICE");setPublicMessage(o,"شمارنده حرکت جک تمام شد و او به مخفیگاه نرسید؛ کارآگاه‌ها برنده شدند.");save()
                    }else{o.put("policeDone",JSONArray()).put("policeSearched",JSONArray()).put("policeIndex",0);setPhase("HUNT_POLICE_MOVE")}
                }, onSave={save()})
            }
            "HUNT_POLICE_MOVE"->{
                val real=police.filter{it.real}
                val preview=real.map{token->if(token.id==movingTokenId && pendingPoint!=null)token.copy(point=pendingPoint!!) else token}
                val activePoint=movingTokenId?.let{id->preview.firstOrNull{it.id==id}?.point}
                PoliceActionSelector(real,movingTokenId?:0,emptySet()){id->
                    if(movingTokenId!=null && pendingPoint!=null){
                        val committed=police.toMutableList();val k=committed.indexOfFirst{it.id==movingTokenId};if(k>=0)committed[k]=committed[k].copy(point=pendingPoint!!,revealed=true);o.put("police",committed.json());save()
                    }
                    movingTokenId=id;pendingPoint=null
                }
                Text("روی پلیس یا نوار رنگی بالای صفحه بزن، سپس مقصد را لمس کن (حداکثر ۲ تقاطع). با انتخاب پلیس بعدی، حرکت قبلی ذخیره می‌شود.",color=Color.White,fontSize=12.sp)
                GameMap(pp,true,preview.associate{it.point to policeColor(it,true)},activePoint?.let(::setOf)?:emptySet(),{p->
                    val tokenAtPoint=preview.firstOrNull{it.point==p.number}
                    when{
                        tokenAtPoint!=null && tokenAtPoint.id!=movingTokenId->{
                            if(movingTokenId!=null && pendingPoint!=null){
                                val committed=police.toMutableList();val k=committed.indexOfFirst{it.id==movingTokenId};if(k>=0)committed[k]=committed[k].copy(point=pendingPoint!!,revealed=true);o.put("police",committed.json());save()
                            }
                            movingTokenId=tokenAtPoint.id;pendingPoint=null
                        }
                        movingTokenId==null && tokenAtPoint!=null->{movingTokenId=tokenAtPoint.id;pendingPoint=null}
                        movingTokenId!=null && tokenAtPoint==null->{pendingPoint=p.number}
                    }
                },Modifier.weight(1f),overlayPoints=hp,crimePoints=(o.optJSONArray("crime")?:JSONArray()).intSet(),currentCrimePoints=currentCrimePoints(o),cluePoints=(o.optJSONArray("clues")?:JSONArray()).intSet(),moveTrackVisual=trackVisual)
                Button(onClick={
                    val committed=police.toMutableList();if(movingTokenId!=null && pendingPoint!=null){val k=committed.indexOfFirst{it.id==movingTokenId};if(k>=0)committed[k]=committed[k].copy(point=pendingPoint!!,revealed=true)}
                    o.put("police",committed.json()).put("policeDone",JSONArray()).put("policeSearched",JSONArray()).put("policeIndex",0).put("phase","HUNT_POLICE_ACTION");setPublicMessage(o,"حرکت پلیس‌ها پایان یافت؛ مرحله سرنخ و دستگیری آغاز شد.");movingTokenId=null;pendingPoint=null;save()
                },modifier=Modifier.fillMaxWidth()){Text("پایان حرکت پلیس‌ها")}
            }
            "HUNT_POLICE_ACTION"->{
                val real=police.filter{it.real}.sortedBy{it.id}
                val doneIds=(o.optJSONArray("policeDone")?:JSONArray()).intSet()
                val searchedIds=(o.optJSONArray("policeSearched")?:JSONArray()).intSet()
                var activeId by remember(phase){mutableIntStateOf(real.firstOrNull{it.id !in doneIds}?.id ?: 0)}
                var resultMessage by remember(phase){mutableStateOf("")}
                if(real.isNotEmpty()){
                    PoliceActionSelector(real,activeId,doneIds){id->activeId=id;if(id in searchedIds)action="search";resultMessage=""}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        FilterChip(selected=action=="search",onClick={action="search"},enabled=activeId!=0 && activeId !in doneIds,label={Text("سرنخ")},modifier=Modifier.weight(1f))
                        FilterChip(selected=action=="arrest",onClick={action="arrest"},enabled=activeId!=0 && activeId !in doneIds && activeId !in searchedIds,label={Text("دستگیری")},modifier=Modifier.weight(1f))
                    }
                    // Reserve a stable message area so search results do not resize the map and make zoom/pan jump.
                    Box(Modifier.fillMaxWidth().height(54.dp),contentAlignment=Alignment.Center){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            if(resultMessage.isNotBlank()) Text(resultMessage,color=if(resultMessage.contains("پیدا شد")||resultMessage.contains("موفق")) Color(0xFFFFD54F) else Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontSize=11.sp)
                            if(activeId in searchedIds && activeId !in doneIds) Text("این پلیس استعلام سرنخ انجام داده است؛ دستگیری برای او تا نوبت بعد غیرفعال است.",color=FGold,fontSize=10.sp,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                        }
                    }
                    Text(if(activeId==0)"فعالیت هر پنج پلیس تمام شده است." else "پلیس انتخاب‌شده: یک خانه مجاور را برای ${if(action=="search") "جستجوی سرنخ" else "دستگیری"} انتخاب کن.",color=Color.White,fontSize=12.sp)
                    val crimes=(o.optJSONArray("crime")?:JSONArray()).intSet();val clues=(o.optJSONArray("clues")?:JSONArray()).intSet()
                    GameMap(hp,true,emptyMap(),emptySet(),{p->
                        if(activeId==0 || activeId in doneIds)return@GameMap
                        val path=o.optJSONArray("jackPath")?:JSONArray();val visited=(0 until path.length()).any{path.getInt(it)==p.number}
                        if(action=="search"){
                            markPoliceSearched(o,activeId)
                            if(visited){
                                val ca=o.optJSONArray("clues")?:JSONArray();if((0 until ca.length()).none{ca.getInt(it)==p.number})ca.put(p.number)
                                o.put("clues",ca);markPoliceDone(o,activeId);resultMessage="سرنخ پیدا شد — جک از خانه ${p.number} عبور کرده است.";setPublicMessage(o,resultMessage)
                            }else{resultMessage="جک از خانه ${p.number} عبور نکرده است.";setPublicMessage(o,resultMessage)}
                        }else{
                            if(activeId in searchedIds){
                                action="search"
                                resultMessage="این پلیس در این نوبت استعلام سرنخ انجام داده و دیگر نمی‌تواند دستگیری انجام دهد."
                            }else{
                                val current=if(path.length()>0)path.getInt(path.length()-1) else -1
                                if(current==p.number){resultMessage="دستگیری موفق بود — جک در خانه ${p.number} دستگیر شد.";setPublicMessage(o,resultMessage);o.put("phase","GAME_OVER_POLICE")}
                                else{resultMessage="دستگیری ناموفق بود — جک در خانه ${p.number} نیست.";setPublicMessage(o,resultMessage);markPoliceDone(o,activeId)}
                            }
                        }
                        save()
                    },Modifier.weight(1f),secondaryPoints=pp,secondaryMarkers=real.associate{it.point to policeColor(it,true)},secondaryActive=if(activeId==0) emptySet() else setOf(real.firstOrNull{it.id==activeId}?.point?:-1),crimePoints=crimes,currentCrimePoints=currentCrimePoints(o),cluePoints=clues,moveTrackVisual=trackVisual)
                    if(action=="search" && activeId!=0 && activeId !in doneIds) OutlinedButton(onClick={markPoliceDone(o,activeId);resultMessage="استعلام‌های سرنخ این پلیس تمام شد؛ این پلیس برای این نوبت غیرفعال شد.";setPublicMessage(o,"استعلام‌های سرنخ یک پلیس پایان یافت.");save()},modifier=Modifier.fillMaxWidth()){Text("پایان استعلام‌های این پلیس")}
                    Button(onClick={setPublicMessage(o,"نوبت کارآگاه‌ها پایان یافت؛ نوبت جک آغاز شد.");setPhase("HUNT_JACK_UNLOCK")},modifier=Modifier.fillMaxWidth()){Text("تحویل به جک")}
                }
            }
            "GAME_OVER_POLICE"->CenterMessage("جک دستگیر شد • کارآگاه‌ها برنده شدند",onBack)
            "GAME_OVER_JACK"->CenterMessage("جک بازی را به پایان رساند",onBack)
        }
        }
    }
}

private fun endNight(o:JSONObject,ctx:Context){
    val n=o.optInt("night",1)
    if(n>=4){o.put("phase","GAME_OVER_JACK");setPublicMessage(o,"شب چهارم پایان یافت؛ جک فرار کرد.");DigitalGameStore.save(ctx,o);return}
    val finalPolice=o.optJSONArray("police")?.tokens().orEmpty().filter{it.real}
    val previousPoints=JSONArray();finalPolice.forEach{previousPoints.put(it.point)}
    o.put("previousPolicePoints",previousPoints).put("night",n+1).put("phase","HELL_WOMEN").put("time",1).put("women",JSONArray()).put("police",JSONArray()).put("clues",JSONArray()).put("jackPath",JSONArray()).put("jackMoves",JSONArray()).put("moveTrack",0).put("policeDone",JSONArray()).put("policeSearched",JSONArray()).put("policeIndex",0).remove("doubleKillFirst")
    setPublicMessage(o,"شب $n پایان یافت؛ شب ${n+1} آغاز شد.")
    startDigitalIntro(o,"hell")
    DigitalGameStore.save(ctx,o)
}


private fun recordCrimeNight(o:JSONObject, house:Int, night:Int){
    val byNight=o.optJSONObject("crimeNight")?:JSONObject()
    byNight.put(house.toString(),night)
    o.put("crimeNight",byNight)
}

private fun currentCrimePoints(o:JSONObject):Set<Int>{
    val night=o.optInt("night",1)
    val byNight=o.optJSONObject("crimeNight")
    if(byNight!=null){
        val result=mutableSetOf<Int>()
        val keys=byNight.keys()
        while(keys.hasNext()){
            val key=keys.next()
            if(byNight.optInt(key,-1)==night)key.toIntOrNull()?.let(result::add)
        }
        if(result.isNotEmpty())return result
    }
    // Backward compatibility for saves created before crimeNight existed.
    val phase=o.optString("phase")
    if(phase.startsWith("HUNT_") || phase.startsWith("GAME_OVER_")){
        val path=o.optJSONArray("jackPath")?:JSONArray()
        if(path.length()>0){
            val moves=o.optJSONArray("jackMoves")?:JSONArray()
            val doubleEvent=night==3 && moves.optJSONObject(0)?.optString("type")=="DOUBLE_EVENT" && path.length()>=2
            return if(doubleEvent)setOf(path.optInt(0),path.optInt(1)) else setOf(path.optInt(0))
        }
    }
    return emptySet()
}

private fun JSONArray.intSet():Set<Int> = buildSet { for(i in 0 until length()) add(optInt(i)) }
private fun markPoliceDone(o:JSONObject,id:Int){
    val a=o.optJSONArray("policeDone")?:JSONArray()
    if((0 until a.length()).none{a.optInt(it)==id})a.put(id)
    o.put("policeDone",a)
}
private fun markPoliceSearched(o:JSONObject,id:Int){
    val a=o.optJSONArray("policeSearched")?:JSONArray()
    if((0 until a.length()).none{a.optInt(it)==id})a.put(id)
    o.put("policeSearched",a)
}

@Composable private fun DigitalJackUnlock(pinHash:String,onSuccess:()->Unit){
    var pin by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        Surface(color=Color(0xFFD0D0CE),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("🔐 ورود محرمانه جک",color=Color.Black,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=pin,onValueChange={pin=it.filter(Char::isDigit).take(6);error=""},label={Text("PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),textStyle=LocalTextStyle.current.copy(color=Color.Black),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.Black,unfocusedTextColor=Color.Black,cursorColor=Color.Black),modifier=Modifier.fillMaxWidth())
                if(error.isNotBlank())Text(error,color=Color(0xFF8D0000),fontSize=12.sp)
                Button(onClick={if(GameStore.hash(pin)==pinHash)onSuccess() else error="PIN اشتباه است."},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FBlood)){Text("ورود به دفترچه جک",fontWeight=FontWeight.Bold)}
            }
        }
    }
}

@Composable private fun ColumnScope.DigitalJackMove(o:JSONObject,houses:List<BoardPoint>,policePoints:List<BoardPoint>,publicPolice:List<DToken>,onCommitted:(Boolean)->Unit,onSave:()->Unit){
    val night=o.optInt("night",1);val path=o.optJSONArray("jackPath")?:JSONArray();val current=if(path.length()>0)path.optInt(path.length()-1) else -1
    val moves=o.optJSONArray("jackMoves")?:JSONArray();val track=o.optInt("moveTrack",0);val moveLimit=(14+o.optInt("time",1)).coerceIn(15,19)
    val coachMax=listOf(0,3,2,2,1).getOrElse(night){0};val alleyMax=listOf(0,2,2,1,1).getOrElse(night){0}
    var coachUsed=0;var alleyUsed=0;for(i in 0 until moves.length()){when(moves.optJSONObject(i)?.optString("type")){"COACH"->coachUsed++;"ALLEY"->alleyUsed++}}
    var type by remember{mutableStateOf(MoveType.NORMAL)};var first by remember{mutableStateOf<Int?>(null)};var second by remember{mutableStateOf<Int?>(null)};var registered by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var hideoutPopup by remember{mutableStateOf(false)}
    val crime=(o.optJSONArray("crime")?:JSONArray()).intSet();val clues=(o.optJSONArray("clues")?:JSONArray()).intSet()
    Text("محل شروع فرار به‌صورت خودکار از محل قتل ثبت شده است. موقعیت فعلی جک: $current",color=Color.White,fontSize=12.sp)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
        FilterChip(selected=type==MoveType.NORMAL,onClick={if(!registered){type=MoveType.NORMAL;first=null;second=null}},enabled=!registered,label={Text("عادی")},modifier=Modifier.weight(1f))
        FilterChip(selected=type==MoveType.COACH,onClick={if(!registered){type=MoveType.COACH;first=null;second=null}},enabled=!registered&&coachUsed<coachMax,label={Text("درشکه ${coachMax-coachUsed}")},modifier=Modifier.weight(1f))
        FilterChip(selected=type==MoveType.ALLEY,onClick={if(!registered){type=MoveType.ALLEY;first=null;second=null}},enabled=!registered&&alleyUsed<alleyMax,label={Text("کوچه ${alleyMax-alleyUsed}")},modifier=Modifier.weight(1f))
    }
    Text("شمارنده حرکت: $track / $moveLimit  •  ${if(type==MoveType.COACH) if(first==null) "مقصد اول درشکه را انتخاب کن" else "مقصد دوم درشکه را انتخاب کن" else "خانه مقصد را انتخاب کن"}",color=FGold,fontWeight=FontWeight.Bold)
    if(first!=null)Text(if(type==MoveType.COACH)"انتخاب: ${first}${second?.let{" ← $it"}?:" ← …"}" else "انتخاب: $first",color=Color.White)
    if(error.isNotBlank())Text(error,color=Color(0xFFFF8A80),fontSize=12.sp)
    GameMap(houses,true,emptyMap(),emptySet(),{p->
        if(!registered){if(type==MoveType.COACH){if(first==null)first=p.number else second=p.number}else first=p.number;error=""}
    },Modifier.weight(1f),secondaryPoints=policePoints,secondaryMarkers=publicPolice.associate{it.point to hc(AppearanceStore.load(LocalContext.current).phone.policeColor(it.id,it.real,true))},crimePoints=crime,currentCrimePoints=currentCrimePoints(o),cluePoints=clues,jackPoint=current,moveTrackVisual=moveTrackVisual(o))
    Button(onClick={
        val a=first;val b=second;val cost=if(type==MoveType.COACH)2 else 1
        when{
            a==null || (type==MoveType.COACH && b==null)->error="مقصد حرکت را کامل انتخاب کنید."
            track+cost>moveLimit->error="ظرفیت شمارنده حرکت این شب تمام شده است."
            type==MoveType.COACH && (a==current || b==current || a==b)->error="در حرکت درشکه، مبدأ و دو مقصد باید متفاوت باشند."
            else->{
                val m=JSONObject().put("type",type.name).put("first",a);if(type==MoveType.COACH)m.put("second",b)
                moves.put(m);path.put(a);if(type==MoveType.COACH)path.put(b)
                o.put("jackMoves",moves).put("jackPath",path).put("moveTrack",track+cost);registered=true;error="";onSave()
            }
        }
    },enabled=!registered,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FBlood)){Text(if(registered)"حرکت ثبت شد" else "ثبت حرکت",fontWeight=FontWeight.Bold)}
    Button(onClick={
        val end=if(type==MoveType.COACH)second?:first else first
        if(type==MoveType.NORMAL && end==o.optInt("hideout")) hideoutPopup=true else onCommitted(false)
    },enabled=registered,modifier=Modifier.fillMaxWidth()){Text("تحویل به کارآگاه‌ها")}
    if(hideoutPopup) DigitalHideoutDialog(
        onEscape={hideoutPopup=false;onCommitted(true)},
        onContinue={hideoutPopup=false;onCommitted(false)}
    )
}

@Composable private fun DigitalHideoutDialog(onEscape:()->Unit,onContinue:()->Unit){
    AlertDialog(
        onDismissRequest={},
        containerColor=Color(0xFF11100E),
        title={Text("شما در مخفیگاه هستید",color=Color.White,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth(),fontWeight=FontWeight.Bold)},
        text={
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                Text("⌂",fontSize=66.sp,color=FGold)
                Text("شما با حرکت عادی به مخفیگاه رسیده‌اید.",color=Color(0xFFF2DFC0),textAlign=TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Button(onClick=onEscape,modifier=Modifier.fillMaxWidth().height(56.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF075D2D)),shape=RoundedCornerShape(8.dp)){
                    Text("⚑  اعلام فرار و پایان شب",fontWeight=FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick=onContinue,modifier=Modifier.fillMaxWidth().height(56.dp).border(1.dp,FGold,RoundedCornerShape(8.dp)),colors=ButtonDefaults.buttonColors(containerColor=Color(0xE91A1510)),shape=RoundedCornerShape(8.dp)){
                    Text("→  اعلام نکن — ادامه بازی",color=FGold,fontWeight=FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text("اگر اعلام نکنید، در نوبت بعد باید از مخفیگاه خارج شوید.",color=Color(0xFFE07B68),fontSize=12.sp,textAlign=TextAlign.Center)
            }
        },
        confirmButton={}
    )
}

@Composable private fun PoliceActionSelector(real:List<DToken>,selected:Int,done:Set<Int>,onSelect:(Int)->Unit){
    val appearance=AppearanceStore.load(LocalContext.current).phone
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
        real.forEach{token->
            val disabled=token.id in done
            Surface(onClick={if(!disabled)onSelect(token.id)},enabled=!disabled,color=if(selected==token.id)Color(0xFF3A332B) else Color(0xFF1D1A17),shape=RoundedCornerShape(8.dp),border=androidx.compose.foundation.BorderStroke(if(selected==token.id)2.dp else 1.dp,if(selected==token.id)FGold else Color.DarkGray),modifier=Modifier.weight(1f).height(48.dp)){
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                    Box(Modifier.width(34.dp).height(10.dp).background(hc(appearance.policeColor(token.id,token.real,true)).copy(alpha=if(disabled).28f else 1f),RoundedCornerShape(8.dp)))
                }
            }
        }
    }
}

@Composable private fun CenterMessage(t:String,onBack:()->Unit){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(t,color=FGold,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center);Button(onClick=onBack){Text("صفحه اصلی")}}}
@Composable private fun Handoff(t:String,onReady:()->Unit){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(t,color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp));Button(onClick=onReady){Text("آماده‌ام")}}}

@Composable private fun VictimTypeSelector(
    selectedType: VictimPlacementType,
    appearance: DisplayAppearance,
    onSelect: (VictimPlacementType) -> Unit
){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
        listOf(
            VictimPlacementType.REAL to hc(appearance.victimRealColor),
            VictimPlacementType.FAKE to hc(appearance.victimFakeColor)
        ).forEach { (type,color) ->
            val selected = selectedType == type
            Surface(
                onClick = { onSelect(type) },
                color = if(selected) Color(0xFF3A332B) else Color(0xFF1D1A17),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    if(selected) 3.dp else 1.dp,
                    if(selected) FGold else Color.DarkGray
                ),
                modifier = Modifier.weight(1f).height(56.dp)
            ){
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                    Box(Modifier.size(32.dp).background(color.copy(alpha=appearance.victimFillAlpha),RoundedCornerShape(50)).border(5.dp,color,RoundedCornerShape(50)))
                }
            }
        }
    }
}

@Composable private fun TokenSelector(colors:List<Color>,fillAlpha:Float,selected:Int,onSelect:(Int)->Unit){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
        colors.forEachIndexed{i,color->
            Surface(
                onClick={onSelect(i+1)},
                color=if(selected==i+1)Color(0xFF3A332B) else Color(0xFF1D1A17),
                shape=RoundedCornerShape(8.dp),
                border=androidx.compose.foundation.BorderStroke(if(selected==i+1)2.dp else 1.dp,if(selected==i+1)FGold else Color.DarkGray),
                modifier=Modifier.weight(1f).height(44.dp)
            ){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Box(Modifier.size(25.dp).background(color.copy(alpha=fillAlpha),RoundedCornerShape(50)).border(4.dp,color,RoundedCornerShape(50)))}}
        }
    }
}
@Composable
private fun TvLinkCard() {
    val u = TvServerInfo.url.ifBlank { "در حال ساخت لینک…" }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        color = Color(0xFF241E18),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "نمایش عمومی: $u",
            color = FGold,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(5.dp)
                .clickable(enabled = TvServerInfo.url.isNotBlank()) {
                    clipboard.setText(
                        AnnotatedString(TvServerInfo.url)
                    )
                    Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
                }
        )
    }
}
@Composable private fun rememberBoardPointsFeature(file:String):List<BoardPoint>{val c=LocalContext.current;return remember(file){val a=JSONArray(c.assets.open(file).bufferedReader().use{it.readText()});List(a.length()){i->val x=a.getJSONObject(i);BoardPoint(x.optInt("id",i+1),x.getInt("x"),x.getInt("y"),x.getDouble("norm_x").toFloat(),x.getDouble("norm_y").toFloat(),x.getInt("number"))}}}


private data class MoveTrackPoint(val id:Int,val normX:Float,val normY:Float)

@Composable private fun rememberMoveTrackPoints():List<MoveTrackPoint>{
    val c=LocalContext.current
    return remember{
        val a=JSONArray(c.assets.open("move_track_points.json").bufferedReader().use{it.readText()})
        List(a.length()){i->
            val x=a.getJSONObject(i)
            MoveTrackPoint(
                id=x.optInt("id",i+1),
                normX=x.getDouble("norm_x").toFloat(),
                normY=x.getDouble("norm_y").toFloat()
            )
        }
    }
}

@Composable private fun GameMap(
    points:List<BoardPoint>,
    numbers:Boolean,
    markers:Map<Int,Color>,
    active:Set<Int>,
    onTap:(BoardPoint)->Unit,
    modifier:Modifier=Modifier,
    secondaryPoints:List<BoardPoint> = emptyList(),
    secondaryMarkers:Map<Int,Color> = emptyMap(),
    secondaryActive:Set<Int> = emptySet(),
    overlayPoints:List<BoardPoint> = points,
    crimePoints:Set<Int> = emptySet(),
    currentCrimePoints:Set<Int> = emptySet(),
    cluePoints:Set<Int> = emptySet(),
    primaryShape:MapMarkerShape = MapMarkerShape.CIRCLE,
    secondaryShape:MapMarkerShape = MapMarkerShape.CIRCLE,
    jackPoint:Int? = null,
    requiredPoints:Set<Int> = emptySet(),
    yellowSetupPoints:Set<Int> = emptySet(),
    moveTrackVisual:MoveTrackVisual? = null
){
    val context=LocalContext.current
    val appearance=remember{AppearanceStore.load(context).phone}
    val baseBitmap=remember{BitmapFactory.decodeResource(context.resources,R.drawable.whitechapel_board_base).asImageBitmap()}
    val jackTrackBitmap=remember{BitmapFactory.decodeResource(context.resources,R.drawable.jack_track_token).asImageBitmap()}
    val alleyTrackBitmap=remember{BitmapFactory.decodeResource(context.resources,R.drawable.alley_track_token).asImageBitmap()}
    val coachTrackBitmap=remember{BitmapFactory.decodeResource(context.resources,R.drawable.coach_track_token).asImageBitmap()}
    val houseNumberPoints=rememberHouseNumberPoints()
    val moveTrackPoints=rememberMoveTrackPoints()
    var scale by remember{mutableFloatStateOf(1f)}
    var offset by remember{mutableStateOf(Offset.Zero)}
    // The pointerInput coroutine can outlive a recomposition. Keep the latest tap
    // callback so placement always sees the current victim list and selected type.
    val currentOnTap by rememberUpdatedState(onTap)

    BoxWithConstraints(
        modifier.fillMaxSize()
            .padding(horizontal=4.dp,vertical=2.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp,Color(0xFF5B5147),RoundedCornerShape(10.dp))
            .background(Color.Black)
            .pointerInput(Unit){
                detectTransformGestures{centroid,pan,zoom,_ ->
                    val vw=size.width.toFloat();val vh=size.height.toFloat();val bw:Float;val bh:Float;val bl:Float;val bt:Float
                    if(vw/vh>FEATURE_BOARD_ASPECT){bh=vh;bw=bh*FEATURE_BOARD_ASPECT;bl=(vw-bw)/2f;bt=0f}else{bw=vw;bh=bw/FEATURE_BOARD_ASPECT;bl=0f;bt=(vh-bh)/2f}
                    val oldScale=scale
                    val newScale=(oldScale*zoom).coerceIn(1f,5f)
                    val ratio=newScale/oldScale
                    val candidate=centroid-(centroid-offset)*ratio+pan
                    fun clampAxis(value:Float,start:Float,length:Float):Float{
                        if(newScale<=1.0001f)return 0f
                        val min=(start+length)*(1f-newScale)
                        val max=start*(1f-newScale)
                        return value.coerceIn(min,max)
                    }
                    offset=Offset(clampAxis(candidate.x,bl,bw),clampAxis(candidate.y,bt,bh))
                    scale=newScale
                }
            }
            .pointerInput(points,scale,offset){
                detectTapGestures(onDoubleTap={scale=1f;offset=Offset.Zero},onTap={raw->
                    val vw=size.width.toFloat();val vh=size.height.toFloat();val bw:Float;val bh:Float;val bl:Float;val bt:Float
                    if(vw/vh>FEATURE_BOARD_ASPECT){bh=vh;bw=bh*FEATURE_BOARD_ASPECT;bl=(vw-bw)/2f;bt=0f}else{bw=vw;bh=bw/FEATURE_BOARD_ASPECT;bl=0f;bt=(vh-bh)/2f}
                    val unscaled=(raw-offset)/scale;val mx=unscaled.x-bl;val my=unscaled.y-bt
                    if(mx !in 0f..bw || my !in 0f..bh)return@detectTapGestures
                    var best:BoardPoint?=null;var distance=Float.MAX_VALUE
                    points.forEach{p->val dx=mx-p.normX*bw;val dy=my-p.normY*bh;val d=sqrt(dx*dx+dy*dy);if(d<distance){distance=d;best=p}}
                    if(distance<=42f/scale)best?.let { currentOnTap(it) }
                })
            }
    ){
        Canvas(Modifier.fillMaxSize()){
            val vw=size.width;val vh=size.height;val bw:Float;val bh:Float;val bl:Float;val bt:Float
            if(vw/vh>FEATURE_BOARD_ASPECT){bh=vh;bw=bh*FEATURE_BOARD_ASPECT;bl=(vw-bw)/2f;bt=0f}else{bw=vw;bh=bw/FEATURE_BOARD_ASPECT;bl=0f;bt=(vh-bh)/2f}
            val dl=bl*scale+offset.x;val dt=bt*scale+offset.y;val dw=bw*scale;val dh=bh*scale
            val dstOffset=IntOffset(dl.roundToInt(),dt.roundToInt());val dstSize=IntSize(dw.roundToInt().coerceAtLeast(1),dh.roundToInt().coerceAtLeast(1))
            drawImage(baseBitmap,IntOffset.Zero,IntSize(baseBitmap.width,baseBitmap.height),dstOffset,dstSize,filterQuality=FilterQuality.High)
            if(numbers)drawHouseNumberBadges(houseNumberPoints,bl,bt,bw,bh,scale,offset,appearance.houseNumberScale)

            fun center(p:BoardPoint)=Offset((bl+p.normX*bw)*scale+offset.x,(bt+p.normY*bh)*scale+offset.y)
            // Police circles are about 10% larger on phone for better contrast over red crossings.
            val policeRadius=12.dp.toPx()*appearance.policeSize;val stroke=3.dp.toPx()
            // During police setup on nights 2–4, mark the five required locations from
            // the previous night's final police positions. This overlay is supplied only
            // by HELL_POLICE, so it disappears automatically after leaving that phase.
            requiredPoints.forEach{n->points.firstOrNull{it.number==n}?.let{p->
                drawCircle(FGold,policeRadius*1.55f,center(p),style=Stroke(2.5.dp.toPx()))
            }}
            // During HELL_POLICE, outline the seven printed yellow setup crossings.
            // This is visual guidance only; placement validation above remains unchanged.
            val yellowGuideSide=30.dp.toPx()*appearance.yellowCrossingSize
            yellowSetupPoints.forEach{n->points.firstOrNull{it.number==n}?.let{p->
                val c=center(p)
                drawRect(
                    Color(0xFFFFD600),
                    topLeft=Offset(c.x-yellowGuideSide/2f,c.y-yellowGuideSide/2f),
                    size=Size(yellowGuideSide,yellowGuideSide),
                    style=Stroke(2.5.dp.toPx())
                )
            }}
            // Marker geometry below is derived from the two SVG files supplied for
            // victims and police. Fill alpha is configurable; the outline stays opaque.
            fun svgPoint(c:Offset,targetSize:Float,viewBox:Float,x:Float,y:Float)=Offset(
                c.x-targetSize/2f+(x/viewBox)*targetSize,
                c.y-targetSize/2f+(y/viewBox)*targetSize
            )
            fun heartFillPath(c:Offset,targetSize:Float)=Path().apply{
                fun q(x:Float,y:Float)=svgPoint(c,targetSize,24f,x+1f,y+2f)
                q(18.6707335f,10.0469949f).let{moveTo(it.x,it.y)}
                q(20.4444204f,8.20475335f).let{a->q(20.4428931f,5.22154308f).let{b->q(18.6673208f,3.38125356f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(16.8917484f,1.54096405f).let{a->q(14.0134482f,1.53938105f).let{b->q(12.2359925f,3.37771648f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(10.9702069f,4.68963823f).let{lineTo(it.x,it.y)}
                q(9.74663024f,3.42106257f).let{lineTo(it.x,it.y)}
                q(7.97421677f,1.58443498f).let{a->q(5.10087015f,1.58474944f).let{b->q(3.32883095f,3.42176494f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(1.55679174f,5.25878043f).let{a->q(1.55709514f,8.23685657f).let{b->q(3.32950861f,10.0734842f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(10.9750473f,18f).let{lineTo(it.x,it.y)}
                q(18.6707335f,10.0469949f).let{lineTo(it.x,it.y)}
                close()
            }
            fun heartOutlinePath(c:Offset,targetSize:Float)=Path().apply{
                fun q(x:Float,y:Float)=svgPoint(c,targetSize,24f,x+1f,y+2f)
                q(9.53555048f,19.3884699f).let{moveTo(it.x,it.y)}
                q(1.89036034f,11.4623154f).let{lineTo(it.x,it.y)}
                q(-0.629744037f,8.85090825f).let{a->q(-0.630172509f,4.64518565f).let{b->q(1.88938959f,2.03323745f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(4.37655172f,-0.545122756f).let{a->q(8.39543397f,-0.61732966f).let{b->q(10.9687169f,1.81730162f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(13.5445576f,-0.66312694f).let{a->q(17.60123f,-0.604129239f).let{b->q(20.1066156f,1.99257419f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(22.6292352f,4.60713978f).let{a->q(22.6313904f,8.81686087f).let{b->q(20.1115002f,11.434147f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(12.4427074f,19.3824584f).let{lineTo(it.x,it.y)}
                q(12.1544685f,19.6812032f).let{a->q(11.7964701f,19.8704534f).let{b->q(11.4198481f,19.9502088f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(10.7609371f,20.0997637f).let{a->q(10.0408904f,19.9123813f).let{b->q(9.53555048f,19.3884699f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                close()
            }
            fun policeHatPath(c:Offset,targetSize:Float)=Path().apply{
                fun q(x:Float,y:Float)=svgPoint(c,targetSize,512f,x,y)
                q(503.407f,432.422f).let{moveTo(it.x,it.y)}
                q(492.772f,431.586f).let{a->q(482.021f,431.156f).let{b->q(471.177f,431.156f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(392.181f,431.156f).let{a->q(318.468f,453.738f).let{b->q(256.109f,492.783f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(221.336f,471.014f).let{a->q(183.034f,454.353f).let{b->q(142.247f,443.892f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(109.901f,435.567f).let{a->q(75.987f,431.156f).let{b->q(41.041f,431.156f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(30.197f,431.156f).let{a->q(19.458f,431.586f).let{b->q(8.823f,432.422f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(21.014f,387.142f).let{a->q(68.035f,373.210f).let{b->q(68.035f,373.210f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(72.888f,335.454f).let{lineTo(it.x,it.y)}
                q(90.965f,194.599f).let{lineTo(it.x,it.y)}
                q(96.294f,153.069f).let{a->q(116.647f,116.741f).let{b->q(146.125f,90.792f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(175.615f,64.855f).let{a->q(214.231f,49.297f).let{b->q(256.120f,49.297f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(339.876f,49.297f).let{a->q(410.594f,111.528f).let{b->q(421.264f,194.599f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(444.194f,373.211f).let{lineTo(it.x,it.y)}
                q(444.195f,373.210f).let{a->q(491.216f,387.143f).let{b->q(503.407f,432.422f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                close()
            }
            fun policeBadgePath(c:Offset,targetSize:Float)=Path().apply{
                fun q(x:Float,y:Float)=svgPoint(c,targetSize,512f,x,y)
                val pts=listOf(
                    256.117f to 194.281f,279.646f to 217.811f,312.921f to 217.811f,312.921f to 251.086f,
                    336.449f to 274.614f,312.921f to 298.143f,312.921f to 331.418f,279.646f to 331.418f,
                    256.117f to 354.947f,232.588f to 331.418f,199.313f to 331.418f,199.313f to 298.143f,
                    175.784f to 274.614f,199.313f to 251.086f,199.313f to 217.811f,232.588f to 217.811f
                )
                pts.forEachIndexed{i,(x,y)->q(x,y).let{if(i==0)moveTo(it.x,it.y) else lineTo(it.x,it.y)}}
                close()
            }
            markers.forEach{(n,col)->points.firstOrNull{it.number==n}?.let{p->
                val c=center(p)
                if(primaryShape==MapMarkerShape.HEART){
                    val markerSize=30.dp.toPx()*appearance.victimSize
                    drawPath(heartFillPath(c,markerSize),col.copy(alpha=appearance.victimFillAlpha))
                    drawPath(heartOutlinePath(c,markerSize),col,style=Stroke(2.2.dp.toPx()))
                } else {
                    val markerSize=34.dp.toPx()*appearance.policeSize
                    val hat=policeHatPath(c,markerSize)
                    drawPath(hat,col.copy(alpha=appearance.policeFillAlpha))
                    drawPath(hat,col,style=Stroke(2.2.dp.toPx()))
                    drawPath(policeBadgePath(c,markerSize),col,style=Stroke(1.5.dp.toPx()))
                }
                if(n in active)drawCircle(FGold,policeRadius*1.30f,c,style=Stroke(2.dp.toPx()))
            }}
            secondaryMarkers.forEach{(n,col)->secondaryPoints.firstOrNull{it.number==n}?.let{p->
                val c=center(p)
                if(secondaryShape==MapMarkerShape.HEART){
                    val markerSize=30.dp.toPx()*appearance.victimSize
                    drawPath(heartFillPath(c,markerSize),col.copy(alpha=appearance.victimFillAlpha))
                    drawPath(heartOutlinePath(c,markerSize),col,style=Stroke(2.2.dp.toPx()))
                } else {
                    val markerSize=34.dp.toPx()*appearance.policeSize
                    val hat=policeHatPath(c,markerSize)
                    drawPath(hat,col.copy(alpha=appearance.policeFillAlpha))
                    drawPath(hat,col,style=Stroke(2.2.dp.toPx()))
                    drawPath(policeBadgePath(c,markerSize),col,style=Stroke(1.5.dp.toPx()))
                }
                if(n in secondaryActive)drawCircle(FGold,policeRadius*1.30f,c,style=Stroke(2.dp.toPx()))
            }}

            // Crime Scene and clues always use house coordinates, never police/Crossing IDs.
            // Geometry below comes from the supplied target.svg and search.svg files.
            fun targetSvgPath(c:Offset,targetSize:Float)=Path().apply{
                fun q(x:Float,y:Float)=svgPoint(c,targetSize,512f,x,y)
                q(256f,0f).let{moveTo(it.x,it.y)}
                q(114.84f,0f).let{a->q(0f,114.842f).let{b->q(0f,256f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(0f,397.158f).let{a->q(114.84f,512f).let{b->q(256f,512f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(397.16f,512f).let{a->q(512f,397.158f).let{b->q(512f,256f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(512f,114.842f).let{a->q(397.16f,0f).let{b->q(256f,0f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                close()
                q(280.774f,460.96f).let{moveTo(it.x,it.y)}
                q(280.774f,404.645f).let{lineTo(it.x,it.y)};q(231.226f,404.645f).let{lineTo(it.x,it.y)};q(231.226f,460.96f).let{lineTo(it.x,it.y)}
                q(137.152f,449.658f).let{a->q(62.342f,374.848f).let{b->q(51.04f,280.774f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(107.355f,280.774f).let{lineTo(it.x,it.y)};q(107.355f,231.226f).let{lineTo(it.x,it.y)};q(51.04f,231.226f).let{lineTo(it.x,it.y)}
                q(62.342f,137.152f).let{a->q(137.152f,62.342f).let{b->q(231.226f,51.04f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(231.226f,107.355f).let{lineTo(it.x,it.y)};q(280.774f,107.355f).let{lineTo(it.x,it.y)};q(280.774f,51.04f).let{lineTo(it.x,it.y)}
                q(374.847f,62.342f).let{a->q(449.658f,137.152f).let{b->q(460.96f,231.226f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(404.645f,231.226f).let{lineTo(it.x,it.y)};q(404.645f,280.774f).let{lineTo(it.x,it.y)};q(460.96f,280.774f).let{lineTo(it.x,it.y)}
                q(449.658f,374.848f).let{a->q(374.847f,449.658f).let{b->q(280.774f,460.96f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                close()
            }
            fun searchSvgPath(c:Offset,targetSize:Float)=Path().apply{
                fun q(x:Float,y:Float)=svgPoint(c,targetSize,512f,x,y)
                q(283.097f,0f).let{moveTo(it.x,it.y)}
                q(156.88f,0f).let{a->q(54.194f,102.686f).let{b->q(54.194f,228.904f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(54.194f,266.174f).let{a->q(63.153f,301.389f).let{b->q(79.021f,332.517f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(20.774f,390.764f).let{lineTo(it.x,it.y)}
                q(-6.925f,418.461f).let{a->q(-6.925f,463.53f).let{b->q(20.774f,491.227f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(34.622f,505.077f).let{a->q(52.814f,512f).let{b->q(71.005f,512f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(89.197f,512f).let{a->q(107.39f,505.076f).let{b->q(121.237f,491.227f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(179.483f,432.98f).let{lineTo(it.x,it.y)}
                q(210.614f,448.849f).let{a->q(245.829f,457.807f).let{b->q(283.097f,457.807f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(409.314f,457.807f).let{a->q(512f,355.121f).let{b->q(512f,228.904f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(512f,102.687f).let{a->q(409.315f,0f).let{b->q(283.097f,0f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}};close()
                q(87.751f,457.739f).let{moveTo(it.x,it.y)}
                q(78.516f,466.974f).let{a->q(63.495f,466.971f).let{b->q(54.262f,457.739f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(45.028f,448.506f).let{a->q(45.028f,433.483f).let{b->q(54.262f,424.251f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(105.442f,373.073f).let{lineTo(it.x,it.y)}
                q(115.425f,385.352f).let{a->q(126.651f,396.577f).let{b->q(138.93f,406.561f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(87.751f,457.739f).let{lineTo(it.x,it.y)};close()
                q(283.097f,410.448f).let{moveTo(it.x,it.y)}
                q(182.994f,410.448f).let{a->q(101.553f,329.007f).let{b->q(101.553f,228.904f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(101.553f,128.801f).let{a->q(182.994f,47.36f).let{b->q(283.097f,47.36f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(383.2f,47.36f).let{a->q(464.641f,128.8f).let{b->q(464.641f,228.904f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}}
                q(464.641f,329.008f).let{a->q(383.201f,410.448f).let{b->q(283.097f,410.448f).let{d->cubicTo(a.x,a.y,b.x,b.y,d.x,d.y)}}};close()
            }
            val oldCrimeColor=Color(0xFF8B8B8B)
            crimePoints.forEach{n->overlayPoints.firstOrNull{it.number==n}?.let{p->
                val c=center(p)
                val color=if(n in currentCrimePoints) hc(appearance.crimeSceneColor) else oldCrimeColor
                val markerSize=34.dp.toPx()*appearance.crimeSceneSize
                drawPath(targetSvgPath(c,markerSize),color)
            }}
            val clueColor=hc(appearance.clueColor)
            cluePoints.forEach{n->overlayPoints.firstOrNull{it.number==n}?.let{p->
                val c=center(p);val markerSize=32.dp.toPx()*appearance.clueSize
                val icon=searchSvgPath(c,markerSize)
                drawPath(icon,clueColor.copy(alpha=appearance.clueFillAlpha))
                drawPath(icon,clueColor,style=Stroke(1.5.dp.toPx()))
            }}
            // Jack's current position is private: this X is only requested by the Jack phone screen.
            jackPoint?.let{n->overlayPoints.firstOrNull{it.number==n}?.let{p->
                val c=center(p);val r=10.dp.toPx();val sw=3.dp.toPx()
                drawLine(Color(0xFFE53935),Offset(c.x-r,c.y-r),Offset(c.x+r,c.y+r),strokeWidth=sw)
                drawLine(Color(0xFFE53935),Offset(c.x+r,c.y-r),Offset(c.x-r,c.y+r),strokeWidth=sw)
            }}

            // Public Move Track markers are drawn directly over the printed track on the board.
            // Point IDs 1..20 map to V, IV, III, II, I, 1..15.
            moveTrackVisual?.let{tv->
                fun trackCenter(id:Int):Offset?=moveTrackPoints.firstOrNull{it.id==id}?.let{p->
                    Offset((bl+p.normX*bw)*scale+offset.x,(bt+p.normY*bh)*scale+offset.y)
                }
                val boardPxScale=(dw/1536f).coerceAtLeast(0.01f)

                // Alley token: the supplied lantern artwork sits on one Move Track space.
                // Alley token enlarged to 56 native board pixels as requested.
                tv.alleyPointIds.forEach{id->trackCenter(id)?.let{c->
                    val width=56f*boardPxScale
                    val height=width*alleyTrackBitmap.height.toFloat()/alleyTrackBitmap.width.toFloat()
                    drawImage(
                        image=alleyTrackBitmap,
                        srcOffset=IntOffset.Zero,
                        srcSize=IntSize(alleyTrackBitmap.width,alleyTrackBitmap.height),
                        dstOffset=IntOffset((c.x-width/2f).roundToInt(),(c.y-height/2f).roundToInt()),
                        dstSize=IntSize(width.roundToInt().coerceAtLeast(1),height.roundToInt().coerceAtLeast(1)),
                        filterQuality=FilterQuality.High
                    )
                }}

                // Coach token: centered between its two consumed spaces and wide enough to read
                // as a two-space token, while still leaving clear room before the neighbouring space.
                tv.coachPointPairs.forEach{(a,b)->
                    val c1=trackCenter(a);val c2=trackCenter(b)
                    if(c1!=null && c2!=null){
                        val mid=Offset((c1.x+c2.x)/2f,(c1.y+c2.y)/2f)
                        val gap=kotlin.math.abs(c2.x-c1.x).coerceAtLeast(1f)
                        val width=gap*1.55f
                        val height=width*coachTrackBitmap.height.toFloat()/coachTrackBitmap.width.toFloat()
                        drawImage(
                            image=coachTrackBitmap,
                            srcOffset=IntOffset.Zero,
                            srcSize=IntSize(coachTrackBitmap.width,coachTrackBitmap.height),
                            dstOffset=IntOffset((mid.x-width/2f).roundToInt(),(mid.y-height/2f).roundToInt()),
                            dstSize=IntSize(width.roundToInt().coerceAtLeast(1),height.roundToInt().coerceAtLeast(1)),
                            filterQuality=FilterQuality.High
                        )
                    }
                }

                // Jack token stays above special-move tokens so the current position remains obvious.
                trackCenter(tv.currentPointId)?.let{c->
                    val sizePx=36f*boardPxScale
                    drawCircle(Color(0xFFD7D7D7),sizePx*.54f,c)
                    drawImage(
                        image=jackTrackBitmap,
                        srcOffset=IntOffset.Zero,
                        srcSize=IntSize(jackTrackBitmap.width,jackTrackBitmap.height),
                        dstOffset=IntOffset((c.x-sizePx/2f).roundToInt(),(c.y-sizePx/2f).roundToInt()),
                        dstSize=IntSize(sizePx.roundToInt().coerceAtLeast(1),sizePx.roundToInt().coerceAtLeast(1)),
                        filterQuality=FilterQuality.High
                    )
                    drawCircle(Color(0xFF2B2B2B),sizePx*.52f,c,style=Stroke((2f*boardPxScale).coerceAtLeast(1f)))
                }
            }
        }
    }
}

