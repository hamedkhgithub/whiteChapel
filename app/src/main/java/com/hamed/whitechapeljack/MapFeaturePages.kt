package com.hamed.whitechapeljack

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt
import kotlin.math.roundToInt

private val FGold = Color(0xFFD6AD63)
private val FBlood = Color(0xFFB41616)
private val policeDefs = listOf("#1976D2", "#F9A825", "#6D4C41", "#D32F2F", "#2E7D32", "#111111", "#111111")
private const val FEATURE_BOARD_ASPECT = 3f / 2f
private fun hc(s:String)=Color(android.graphics.Color.parseColor(s))

data class DToken(val id:Int,val point:Int,val color:String,val real:Boolean,val revealed:Boolean=false)

object DigitalGameStore {
    private const val PREF="whitechapel_digital_final"; private const val KEY="game"
    fun exists(c:Context)=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).contains(KEY)
    fun newGame(c:Context,hideout:Int,pin:String){
        val o=JSONObject().put("hideout",hideout).put("pin",pin).put("night",1).put("phase","HELL_WOMEN").put("time",1)
            .put("women",JSONArray()).put("police",JSONArray()).put("crime",JSONArray()).put("clues",JSONArray()).put("jackPath",JSONArray()).put("policeIndex",0)
        save(c,o)
    }
    fun load(c:Context):JSONObject?=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,null)?.let{runCatching{JSONObject(it)}.getOrNull()}
    fun save(c:Context,o:JSONObject){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,o.toString()).apply();publish(c,o)}
    private fun points(c:Context,file:String)=JSONArray(c.assets.open(file).bufferedReader().use{it.readText()}).let{a->List(a.length()){i->val x=a.getJSONObject(i);BoardPoint(x.optInt("id",i+1),x.getInt("x"),x.getInt("y"),x.getDouble("norm_x").toFloat(),x.getDouble("norm_y").toFloat(),x.getInt("number"))}.associateBy{it.number}}
    fun publish(c:Context){ val o=load(c)?:return; publish(c,o) }
    private fun publish(c:Context,o:JSONObject){
        val houses=points(c,"houses.json");val pp=points(c,"polises.json");val pub=JSONObject().put("mode","digital").put("night",o.optInt("night",1)).put("phase",o.optString("phase")).put("time",o.optInt("time",1))
        val wa=JSONArray(); val women=o.optJSONArray("women")?:JSONArray();for(i in 0 until women.length()){val x=women.getJSONObject(i);houses[x.getInt("point")]?.let{p->wa.put(JSONObject().put("x",p.normX).put("y",p.normY))}}
        val pa=JSONArray();val police=o.optJSONArray("police")?:JSONArray();for(i in 0 until police.length()){val x=police.getJSONObject(i);pp[x.getInt("point")]?.let{p->val it=JSONObject().put("x",p.normX).put("y",p.normY);if(x.optBoolean("revealed"))it.put("color",x.optString("color")).put("revealed",true).put("real",x.optBoolean("real"));pa.put(it)}}
        val ca=JSONArray();val crimes=o.optJSONArray("crime")?:JSONArray();for(i in 0 until crimes.length()){houses[crimes.getInt(i)]?.let{p->ca.put(JSONObject().put("x",p.normX).put("y",p.normY))}}
        val cla=JSONArray();val clues=o.optJSONArray("clues")?:JSONArray();for(i in 0 until clues.length()){houses[clues.getInt(i)]?.let{p->cla.put(JSONObject().put("x",p.normX).put("y",p.normY))}}
        pub.put("women",wa).put("digitalPolice",pa).put("crime",ca).put("clues",cla);TvMapHub.setState(pub)
    }
}

private fun JSONArray.tokens():MutableList<DToken>{val r=mutableListOf<DToken>();for(i in 0 until length()){val x=getJSONObject(i);r+=DToken(x.getInt("id"),x.getInt("point"),x.optString("color","#FFFFFF"),x.optBoolean("real",true),x.optBoolean("revealed",false))};return r}
private fun List<DToken>.json():JSONArray{val a=JSONArray();forEach{a.put(JSONObject().put("id",it.id).put("point",it.point).put("color",it.color).put("real",it.real).put("revealed",it.revealed))};return a}
private fun womenCounts(n:Int)=when(n){1->8 to 5;2->7 to 4;3->6 to 3;else->4 to 1}
private fun roman(n:Int)=listOf("","I","II","III","IV","V").getOrElse(n){n.toString()}

@Composable fun DigitalGamePage(onBack:()->Unit){
    val ctx=LocalContext.current;var rev by remember{mutableIntStateOf(0)};var selectedId by remember{mutableIntStateOf(1)};var action by remember{mutableStateOf("search")}
    val o=remember(rev){DigitalGameStore.load(ctx)}
    if(o==null){Column(Modifier.fillMaxSize().background(Color(0xFF100E0C)).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("بازی با نقشه‌ای ذخیره نشده است.",color=Color.White);Button(onClick=onBack){Text("بازگشت")}};return}
    val phase=o.optString("phase");val night=o.optInt("night",1);val time=o.optInt("time",1);val women=o.optJSONArray("women")!!.tokens();val police=o.optJSONArray("police")!!.tokens()
    var movingTokenId by remember(phase){mutableStateOf<Int?>(null)}
    var pendingPoint by remember(phase){mutableStateOf<Int?>(null)}
    LaunchedEffect(Unit) { DigitalGameStore.publish(ctx) }
    val hp=rememberBoardPointsFeature("houses.json");val pp=rememberBoardPointsFeature("polises.json")
    LaunchedEffect(phase) { selectedId = 1; action = "search" }
    fun save(){DigitalGameStore.save(ctx,o);rev++}
    fun setPhase(p:String){o.put("phase",p);save()}
    val title=when(phase){"HELL_WOMEN"->"HELL • جک: جانمایی Women";"HAND_POLICE"->"تحویل گوشی به کارآگاه";"HELL_POLICE"->"HELL • کارآگاه: جانمایی پلیس";"HAND_JACK"->"تحویل گوشی به جک";"HELL_DECISION"->"HELL • تصمیم جک";"HELL_MOVE_WOMEN"->"HELL • حرکت Women توسط کارآگاه";"HELL_REVEAL_POLICE"->"HELL • جک: افشای یک Patrol";"HELL_KILL"->"HELL • انتخاب قربانی";"HUNT_JACK"->"HUNTING • حرکت مخفی جک";"HUNT_POLICE_MOVE"->"HUNTING • حرکت پلیس";"HUNT_POLICE_ACTION"->"HUNTING • سرنخ / دستگیری";else->phase}
    Column(Modifier.fillMaxSize().background(Color(0xFF100E0C)).padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹ خانه",color=FGold)};Text(title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.weight(1f),textAlign=TextAlign.Center);Text("شب $night • ${roman(time)}",color=FGold,fontSize=12.sp)}
        TvLinkCard()
        when(phase){
            "HELL_WOMEN"->{
                val (total,real)=womenCounts(night)
                TokenSelector((1..total).map{ id->if(id<=real) Color.Red else Color.White},selectedId){selectedId=it}
                Text("مهره را از نوار بالا انتخاب کن و روی محل موردنظر قرار بده. روی TV همه Womenها یکسان دیده می‌شوند.",color=Color.LightGray,fontSize=11.sp)
                GameMap(hp,true,women.associate{it.point to hc(it.color)},emptySet(),{p->
                    val list=women.toMutableList();val isReal=selectedId<=real;val t=DToken(selectedId,p.number,if(isReal)"#D32F2F" else "#FFFFFF",isReal);val k=list.indexOfFirst{it.id==selectedId};if(k>=0)list[k]=t else list+=t;o.put("women",list.json());save()
                },Modifier.weight(1f))
                Button(onClick={setPhase("HAND_POLICE")},enabled=women.size==total,modifier=Modifier.fillMaxWidth()){Text("تحویل به کارآگاه")}
            }
            "HAND_POLICE"->Handoff("گوشی را به کارآگاه بدهید"){setPhase("HELL_POLICE")}
            "HELL_POLICE"->{
                TokenSelector(policeDefs.map(::hc),selectedId){selectedId=it}
                Text("مهره را از نوار بالا انتخاب کن و روی محل موردنظر قرار بده. روی TV هر ۷ Patrol یکسان دیده می‌شوند.",color=Color.LightGray,fontSize=11.sp)
                GameMap(pp,true,police.associate{it.point to hc(it.color)},emptySet(),{p->
                    val list=police.toMutableList();val color=policeDefs[selectedId-1];val t=DToken(selectedId,p.number,color,selectedId<=5,false);val k=list.indexOfFirst{it.id==selectedId};if(k>=0)list[k]=t else list+=t;o.put("police",list.json());save()
                },Modifier.weight(1f))
                Button(onClick={setPhase("HAND_JACK")},enabled=police.size==7,modifier=Modifier.fillMaxWidth()){Text("تحویل به جک")}
            }
            "HAND_JACK"->Handoff("گوشی را به جک بدهید") {val realWomen=women.filter{it.real};o.put("women",realWomen.json()).put("time",1);setPhase("HELL_DECISION")}
            "HELL_DECISION"->{Text("Time of Crime: ${roman(time)}",color=FGold,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center);Spacer(Modifier.weight(1f));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={setPhase("HELL_KILL")},colors=ButtonDefaults.buttonColors(containerColor=FBlood),modifier=Modifier.weight(1f)){Text("کشتن")};Button(onClick={o.put("time",(time+1).coerceAtMost(5));setPhase("HELL_MOVE_WOMEN")},enabled=time<5,modifier=Modifier.weight(1f)){Text(if(time<5)"انتظار" else "در V باید بکشد")}};Spacer(Modifier.weight(1f))}
            "HELL_MOVE_WOMEN"->{
                val preview=women.map{token->if(token.id==movingTokenId && pendingPoint!=null)token.copy(point=pendingPoint!!) else token}
                val activePoint=movingTokenId?.let{id->preview.firstOrNull{it.id==id}?.point}
                Text("روی خود مهره بزن، سپس مقصد را لمس کن. با انتخاب مهره بعدی، حرکت قبلی ذخیره می‌شود.",color=Color.White,fontSize=12.sp)
                GameMap(hp,true,preview.associate{it.point to Color.White},activePoint?.let(::setOf)?:emptySet(),{p->
                    val tokenAtPoint=preview.firstOrNull{it.point==p.number}
                    when{
                        tokenAtPoint!=null && tokenAtPoint.id!=movingTokenId->{
                            if(movingTokenId!=null && pendingPoint!=null){
                                val committed=women.toMutableList();val k=committed.indexOfFirst{it.id==movingTokenId};if(k>=0)committed[k]=committed[k].copy(point=pendingPoint!!);o.put("women",committed.json());save()
                            }
                            movingTokenId=tokenAtPoint.id;pendingPoint=null
                        }
                        movingTokenId==null && tokenAtPoint!=null->{movingTokenId=tokenAtPoint.id;pendingPoint=null}
                        movingTokenId!=null && tokenAtPoint==null->{pendingPoint=p.number}
                    }
                },Modifier.weight(1f))
                Button(onClick={
                    val committed=women.toMutableList();if(movingTokenId!=null && pendingPoint!=null){val k=committed.indexOfFirst{it.id==movingTokenId};if(k>=0)committed[k]=committed[k].copy(point=pendingPoint!!)}
                    o.put("women",committed.json()).put("phase","HELL_REVEAL_POLICE");movingTokenId=null;pendingPoint=null;save()
                },modifier=Modifier.fillMaxWidth()){Text("پایان حرکت Women • تحویل به جک")}
            }
            "HELL_REVEAL_POLICE"->{val hidden=police.filter{!it.revealed};Text("یکی از Police Patrolهای مخفی را انتخاب کن تا هویتش عمومی شود.",color=Color.White);GameMap(pp,true,police.associate{it.point to if(it.revealed)hc(it.color) else Color.Black},emptySet(),{p->val list=police.toMutableList();val k=list.indexOfFirst{it.point==p.number&&!it.revealed};if(k>=0){list[k]=list[k].copy(revealed=true);o.put("police",list.json());setPhase("HELL_DECISION")}},Modifier.weight(1f))}
            "HELL_KILL"->{Text("یکی از Womenهای باقی‌مانده را برای قتل انتخاب کن.",color=Color.White);GameMap(hp,true,women.associate{it.point to Color.Red},emptySet(),{p->val victim=women.firstOrNull{it.point==p.number}?:return@GameMap;val crimes=o.optJSONArray("crime")?:JSONArray();crimes.put(victim.point);o.put("crime",crimes).put("women",JSONArray()).put("jackPath",JSONArray().put(victim.point));val revealed=police.filter{it.real}.map{it.copy(revealed=true)};o.put("police",revealed.json()).put("policeIndex",0);setPhase("HUNT_JACK")},Modifier.weight(1f))}
            "HUNT_JACK"->{Text("مقصد حرکت مخفی جک را انتخاب کن. مقصد روی TV نمایش داده نمی‌شود. حرکت باید طبق خطوط نقطه‌چین قانونی باشد.",color=Color.White,fontSize=12.sp);GameMap(hp,true,emptyMap(),emptySet(),{p->val path=o.optJSONArray("jackPath")?:JSONArray();path.put(p.number);o.put("jackPath",path);if(p.number==o.getInt("hideout")){endNight(o,ctx);rev++}else{o.put("policeIndex",0);setPhase("HUNT_POLICE_MOVE")}},Modifier.weight(1f))}
            "HUNT_POLICE_MOVE"->{
                val real=police.filter{it.real}
                val preview=real.map{token->if(token.id==movingTokenId && pendingPoint!=null)token.copy(point=pendingPoint!!) else token}
                val activePoint=movingTokenId?.let{id->preview.firstOrNull{it.id==id}?.point}
                Text("روی پلیس موردنظر بزن، سپس مقصد را لمس کن (حداکثر ۲ Crossing). با انتخاب پلیس بعدی، حرکت قبلی ذخیره می‌شود.",color=Color.White,fontSize=12.sp)
                GameMap(pp,true,preview.associate{it.point to hc(it.color)},activePoint?.let(::setOf)?:emptySet(),{p->
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
                },Modifier.weight(1f))
                Button(onClick={
                    val committed=police.toMutableList();if(movingTokenId!=null && pendingPoint!=null){val k=committed.indexOfFirst{it.id==movingTokenId};if(k>=0)committed[k]=committed[k].copy(point=pendingPoint!!,revealed=true)}
                    o.put("police",committed.json()).put("policeIndex",0).put("phase","HUNT_POLICE_ACTION");movingTokenId=null;pendingPoint=null;save()
                },modifier=Modifier.fillMaxWidth()){Text("پایان حرکت پلیس‌ها")}
            }
            "HUNT_POLICE_ACTION"->{val idx=o.optInt("policeIndex",0);val real=police.filter{it.real};if(idx>=real.size){o.put("policeIndex",0);setPhase("HUNT_JACK")}else{val active=real[idx];Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=action=="search",onClick={action="search"},label={Text("سرنخ")},modifier=Modifier.weight(1f));FilterChip(selected=action=="arrest",onClick={action="arrest"},label={Text("دستگیری")},modifier=Modifier.weight(1f))};Text("پلیس ${idx+1}/5: یک خانه مجاور را انتخاب کن.",color=Color.White);GameMap(hp,true,emptyMap(),emptySet(),{p->val path=o.optJSONArray("jackPath")?:JSONArray();val visited=(0 until path.length()).any{path.getInt(it)==p.number};if(action=="search"){if(visited){val clues=o.optJSONArray("clues")?:JSONArray();if((0 until clues.length()).none{clues.getInt(it)==p.number})clues.put(p.number);o.put("clues",clues).put("policeIndex",idx+1)}/* negative search may continue */}else{val current=if(path.length()>0)path.getInt(path.length()-1) else -1;if(current==p.number)o.put("phase","GAME_OVER_POLICE") else o.put("policeIndex",idx+1)};save()},Modifier.weight(1f));if(action=="search")OutlinedButton(onClick={o.put("policeIndex",idx+1);save()},modifier=Modifier.fillMaxWidth()){Text("پایان جستجوی این پلیس")}}}
            "GAME_OVER_POLICE"->CenterMessage("جک دستگیر شد • کارآگاه‌ها برنده شدند",onBack)
            "GAME_OVER_JACK"->CenterMessage("جک بازی را به پایان رساند",onBack)
        }
    }
}

private fun endNight(o:JSONObject,ctx:Context){val n=o.optInt("night",1);if(n>=4){o.put("phase","GAME_OVER_JACK");DigitalGameStore.save(ctx,o);return};o.put("night",n+1).put("phase","HELL_WOMEN").put("time",1).put("women",JSONArray()).put("police",JSONArray()).put("clues",JSONArray()).put("jackPath",JSONArray()).put("policeIndex",0);DigitalGameStore.save(ctx,o)}

@Composable private fun CenterMessage(t:String,onBack:()->Unit){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(t,color=FGold,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center);Button(onClick=onBack){Text("صفحه اصلی")}}}
@Composable private fun Handoff(t:String,onReady:()->Unit){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(t,color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp));Button(onClick=onReady){Text("آماده‌ام")}}}
@Composable private fun TokenSelector(colors:List<Color>,selected:Int,onSelect:(Int)->Unit){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
        colors.forEachIndexed{i,color->
            Surface(
                onClick={onSelect(i+1)},
                color=if(selected==i+1)Color(0xFF3A332B) else Color(0xFF1D1A17),
                shape=RoundedCornerShape(8.dp),
                border=androidx.compose.foundation.BorderStroke(if(selected==i+1)2.dp else 1.dp,if(selected==i+1)FGold else Color.DarkGray),
                modifier=Modifier.weight(1f).height(44.dp)
            ){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Box(Modifier.size(25.dp).border(4.dp,color,RoundedCornerShape(50)))}}
        }
    }
}
@Composable private fun TvLinkCard(){val u=TvServerInfo.url.ifBlank{"در حال ساخت لینک…"};Surface(color=Color(0xFF241E18),shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth()){Text("TV / PC: $u",color=FGold,fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(5.dp))}}
@Composable private fun rememberBoardPointsFeature(file:String):List<BoardPoint>{val c=LocalContext.current;return remember(file){val a=JSONArray(c.assets.open(file).bufferedReader().use{it.readText()});List(a.length()){i->val x=a.getJSONObject(i);BoardPoint(x.optInt("id",i+1),x.getInt("x"),x.getInt("y"),x.getDouble("norm_x").toFloat(),x.getDouble("norm_y").toFloat(),x.getInt("number"))}}}

@Composable private fun GameMap(
    points:List<BoardPoint>,
    numbers:Boolean,
    markers:Map<Int,Color>,
    active:Set<Int>,
    onTap:(BoardPoint)->Unit,
    modifier:Modifier=Modifier
){
    val context=LocalContext.current
    val baseBitmap=remember{BitmapFactory.decodeResource(context.resources,R.drawable.whitechapel_board_base).asImageBitmap()}
    val numberBitmap=remember{BitmapFactory.decodeResource(context.resources,R.drawable.whitechapel_house_numbers_overlay).asImageBitmap()}
    var scale by remember{mutableFloatStateOf(1f)}
    var offset by remember{mutableStateOf(Offset.Zero)}

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit){
                detectTransformGestures{centroid,pan,zoom,_->
                    val oldScale=scale
                    val newScale=(oldScale*zoom).coerceIn(1f,6f)
                    val ratio=newScale/oldScale
                    offset=centroid+pan-(centroid-offset)*ratio
                    scale=newScale
                }
            }
            .pointerInput(points,scale,offset){
                detectTapGestures(
                    onDoubleTap={scale=1f;offset=Offset.Zero},
                    onTap={raw->
                        val vw=size.width.toFloat();val vh=size.height.toFloat()
                        val bw:Float;val bh:Float;val bl:Float;val bt:Float
                        if(vw/vh>FEATURE_BOARD_ASPECT){bh=vh;bw=bh*FEATURE_BOARD_ASPECT;bl=(vw-bw)/2f;bt=0f}else{bw=vw;bh=bw/FEATURE_BOARD_ASPECT;bl=0f;bt=(vh-bh)/2f}
                        val unscaled=(raw-offset)/scale
                        val mx=unscaled.x-bl;val my=unscaled.y-bt
                        if(mx !in 0f..bw || my !in 0f..bh)return@detectTapGestures
                        var best:BoardPoint?=null;var distance=Float.MAX_VALUE
                        points.forEach{p->val dx=mx-p.normX*bw;val dy=my-p.normY*bh;val d=sqrt(dx*dx+dy*dy);if(d<distance){distance=d;best=p}}
                        if(distance<=42f/scale)best?.let(onTap)
                    }
                )
            }
    ){
        Canvas(Modifier.fillMaxSize()){
            val vw=size.width;val vh=size.height
            val bw:Float;val bh:Float;val bl:Float;val bt:Float
            if(vw/vh>FEATURE_BOARD_ASPECT){bh=vh;bw=bh*FEATURE_BOARD_ASPECT;bl=(vw-bw)/2f;bt=0f}else{bw=vw;bh=bw/FEATURE_BOARD_ASPECT;bl=0f;bt=(vh-bh)/2f}
            val dl=bl*scale+offset.x;val dt=bt*scale+offset.y;val dw=bw*scale;val dh=bh*scale
            val dstOffset=IntOffset(dl.roundToInt(),dt.roundToInt())
            val dstSize=IntSize(dw.roundToInt().coerceAtLeast(1),dh.roundToInt().coerceAtLeast(1))
            drawImage(baseBitmap,IntOffset.Zero,IntSize(baseBitmap.width,baseBitmap.height),dstOffset,dstSize,filterQuality=FilterQuality.High)
            if(numbers)drawImage(numberBitmap,IntOffset.Zero,IntSize(numberBitmap.width,numberBitmap.height),dstOffset,dstSize,filterQuality=FilterQuality.High)

            // Hollow pieces are deliberately large enough to remain visible while keeping the number underneath readable.
            val radius=bh*.031f*scale
            val stroke=(bh*.0065f*scale).coerceAtLeast(2.2f)
            markers.forEach{(n,col)->
                points.firstOrNull{it.number==n}?.let{p->
                    val center=Offset((bl+p.normX*bw)*scale+offset.x,(bt+p.normY*bh)*scale+offset.y)
                    drawCircle(col,radius,center,style=Stroke(stroke))
                    if(n in active)drawCircle(FGold,radius*1.32f,center,style=Stroke((stroke*.72f).coerceAtLeast(2f)))
                }
            }
        }
    }
}

