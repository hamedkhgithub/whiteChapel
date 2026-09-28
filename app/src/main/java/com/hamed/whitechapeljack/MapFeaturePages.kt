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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
private val YELLOW_CROSSING_IDS = setOf(34, 62, 72, 132, 137, 160, 174)
private val RED_HOUSE_IDS = setOf(3, 21, 27, 65, 84, 147, 149, 158)
private const val FEATURE_BOARD_ASPECT = 3f / 2f
private fun hc(s:String)=Color(android.graphics.Color.parseColor(s))

data class DToken(val id:Int,val point:Int,val color:String,val real:Boolean,val revealed:Boolean=false)

private enum class VictimPlacementType { REAL, FAKE }
private enum class MapMarkerShape { CIRCLE, HEART }

object DigitalGameStore {
    private const val PREF="whitechapel_digital_final"; private const val KEY="game"
    fun exists(c:Context)=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).contains(KEY)
    fun newGame(c:Context,hideout:Int,pin:String){
        val o=JSONObject().put("hideout",hideout).put("pin",pin).put("night",1).put("phase","HELL_WOMEN").put("time",1)
            .put("women",JSONArray()).put("police",JSONArray()).put("crime",JSONArray()).put("clues",JSONArray()).put("jackPath",JSONArray()).put("jackMoves",JSONArray()).put("moveTrack",0).put("policeDone",JSONArray()).put("policeSearched",JSONArray()).put("policeIndex",0).put("previousPolicePoints",JSONArray()).put("publicMessage","شب ۱ آغاز شد.")
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
        pub.put("women",wa).put("digitalPolice",pa).put("crime",ca).put("clues",cla).put("publicMessage",o.optString("publicMessage","")).put("publicPhase",publicPhaseName(o.optString("phase")));TvMapHub.setState(pub)
    }
}

private fun JSONArray.tokens():MutableList<DToken>{val r=mutableListOf<DToken>();for(i in 0 until length()){val x=getJSONObject(i);r+=DToken(x.getInt("id"),x.getInt("point"),x.optString("color","#FFFFFF"),x.optBoolean("real",true),x.optBoolean("revealed",false))};return r}
private fun List<DToken>.json():JSONArray{val a=JSONArray();forEach{a.put(JSONObject().put("id",it.id).put("point",it.point).put("color",it.color).put("real",it.real).put("revealed",it.revealed))};return a}
private fun womenCounts(n:Int)=when(n){1->8 to 5;2->7 to 4;3->6 to 3;else->4 to 1}
private fun roman(n:Int)=listOf("","I","II","III","IV","V").getOrElse(n){n.toString()}
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

@Composable fun DigitalGamePage(onBack:()->Unit){
    val ctx=LocalContext.current;var rev by remember{mutableIntStateOf(0)};var selectedId by remember{mutableIntStateOf(1)};var action by remember{mutableStateOf("search")}
    var victimPlacementType by remember { mutableStateOf(VictimPlacementType.REAL) }
    val o=remember(rev){DigitalGameStore.load(ctx)}
    if(o==null){Column(Modifier.fillMaxSize().background(Color(0xFF100E0C)).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("بازی با نقشه‌ای ذخیره نشده است.",color=Color.White);Button(onClick=onBack){Text("بازگشت")}};return}
    val phase=o.optString("phase");val night=o.optInt("night",1);val time=o.optInt("time",1);val women=o.optJSONArray("women")!!.tokens();val police=o.optJSONArray("police")!!.tokens()
    var movingTokenId by remember(phase){mutableStateOf<Int?>(null)}
    var pendingPoint by remember(phase){mutableStateOf<Int?>(null)}
    var hideoutVisible by remember(phase){mutableStateOf(false)}
    val jackPrivatePhase = phase in setOf("HELL_WOMEN","HELL_DECISION","HELL_REVEAL_POLICE","HELL_KILL","HUNT_JACK")
    LaunchedEffect(Unit) { DigitalGameStore.publish(ctx) }
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
        when(phase){
            "HELL_WOMEN"->{
                val (total,realTarget)=womenCounts(night)
                val fakeTarget=total-realTarget
                val realCount=women.count{it.real};val fakeCount=women.count{!it.real}
                VictimTypeSelector(
                    selectedType = victimPlacementType,
                    onSelect = { victimPlacementType = it }
                )
                Text(
                    "قرمز: قربانی واقعی ($realCount/$realTarget)   •   سفید: قربانی جعلی ($fakeCount/$fakeTarget)",
                    color=Color.LightGray,fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth()
                )
                Text(
                    "حلقه قرمز یا سفید را انتخاب کن. هر بار روی یک خانه خالی بزنی، قربانی از همان نوع اضافه می‌شود. با لمس دوباره قربانی موجود، همان قربانی حذف می‌شود.",
                    color=Color.LightGray,fontSize=11.sp
                )
                val previousCrimeScenes=(o.optJSONArray("crime")?:JSONArray()).intSet()
                GameMap(hp,true,women.associate{it.point to hc(it.color)},emptySet(),{p->
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
                },Modifier.weight(1f),crimePoints=previousCrimeScenes,primaryShape=MapMarkerShape.HEART)
                Button(onClick={setPhase("HAND_POLICE")},enabled=realCount==realTarget && fakeCount==fakeTarget,modifier=Modifier.fillMaxWidth()){Text("تحویل به کارآگاه")}
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
                TokenSelector(policeDefs.map(::hc),selectedId){selectedId=it;placementMessage=""}
                Text(
                    if(night==1) "هر ۷ گشت را روی هفت تقاطع زرد قرار بده. هویت ۵ گشت واقعی و ۲ گشت جعلی فقط برای کارآگاه معلوم است."
                    else "پنج گشت باید روی پنج موقعیت پایان شب قبل و دو گشت روی دو تقاطع زرد دیگر قرار بگیرند؛ هویت‌ها را می‌توانی دوباره مخلوط کنی.",
                    color=Color.LightGray,fontSize=11.sp
                )
                if(placementMessage.isNotBlank())Text(placementMessage,color=Color(0xFFFF8A80),fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                GameMap(pp,true,police.associate{it.point to hc(it.color)},emptySet(),{p->
                    val list=police.toMutableList()
                    val occupiedByOther=list.any{it.id!=selectedId && it.point==p.number}
                    when{
                        p.number !in allowedPoints -> placementMessage=if(night==1) "این نقطه تقاطع زرد نیست." else "این نقطه جزو پنج موقعیت پایان شب قبل یا تقاطع‌های زرد مجاز نیست."
                        occupiedByOther -> placementMessage="این تقاطع قبلاً توسط یک گشت دیگر اشغال شده است."
                        else -> {
                            placementMessage=""
                            val color=policeDefs[selectedId-1]
                            val t=DToken(selectedId,p.number,color,selectedId<=5,false)
                            val k=list.indexOfFirst{it.id==selectedId}
                            if(k>=0)list[k]=t else list+=t
                            o.put("police",list.json());save()
                        }
                    }
                },Modifier.weight(1f))
                Button(onClick={setPhase("HAND_JACK")},enabled=setupValid,modifier=Modifier.fillMaxWidth()){Text("تحویل به جک")}
            }
            "HAND_JACK"->Handoff("گوشی را به جک بدهید") {val realWomen=women.filter{it.real};o.put("women",realWomen.json()).put("time",1);setPublicMessage(o,"قربانی‌های جعلی حذف شدند و قربانی‌های واقعی روی نقشه باقی ماندند.");setPhase("HELL_DECISION")}
            "HELL_DECISION"->{Text("زمان ارتکاب جرم: ${roman(time)}",color=FGold,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center);Spacer(Modifier.weight(1f));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={setPhase("HELL_KILL")},colors=ButtonDefaults.buttonColors(containerColor=FBlood),modifier=Modifier.weight(1f)){Text("کشتن")};Button(onClick={val next=(time+1).coerceAtMost(5);o.put("time",next);setPublicMessage(o,"جک منتظر ماند؛ زمان ارتکاب جرم به ${roman(next)} منتقل شد.");setPhase("HELL_WAIT_HANDOFF")},enabled=time<5,modifier=Modifier.weight(1f)){Text(if(time<5)"انتظار" else "در V باید بکشد")}};Spacer(Modifier.weight(1f))}
            "HELL_WAIT_HANDOFF"->Handoff("گوشی را به کارآگاه بدهید\n"){setPhase("HELL_MOVE_WOMEN")}
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
                        movingTokenId!=null && tokenAtPoint==null && p.number !in (o.optJSONArray("crime")?:JSONArray()).intSet()->{pendingPoint=p.number}
                    }
                },Modifier.weight(1f),secondaryPoints=pp,secondaryMarkers=police.associate{it.point to hc(it.color)},crimePoints=(o.optJSONArray("crime")?:JSONArray()).intSet(),primaryShape=MapMarkerShape.HEART)
                Button(onClick={
                    val committed=women.toMutableList();if(movingTokenId!=null && pendingPoint!=null){val k=committed.indexOfFirst{it.id==movingTokenId};if(k>=0)committed[k]=committed[k].copy(point=pendingPoint!!)}
                    o.put("women",committed.json()).put("phase","HELL_REVEAL_POLICE");movingTokenId=null;pendingPoint=null;save()
                },modifier=Modifier.fillMaxWidth()){Text("پایان حرکت قربانی‌ها • تحویل به جک")}
            }
            "HELL_REVEAL_POLICE"->{
                Text("یکی از گشت‌های پلیس مخفی را انتخاب کن تا هویتش آشکار شود.",color=Color.White)
                GameMap(pp,true,police.associate{it.point to if(it.revealed)hc(it.color) else Color.Black},emptySet(),{p->
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
                },Modifier.weight(1f),secondaryPoints=hp,secondaryMarkers=women.associate{it.point to Color.Red},secondaryShape=MapMarkerShape.HEART,overlayPoints=hp,crimePoints=(o.optJSONArray("crime")?:JSONArray()).intSet())
            }
            "HELL_KILL"->{
                val firstDoubleKill=o.optInt("doubleKillFirst",-1).takeIf{it>0}
                Text(
                    if(night==3 && firstDoubleKill==null) "شب سوم: قربانی اول را انتخاب کن."
                    else if(night==3) "قربانی اول: $firstDoubleKill • حالا قربانی دوم را انتخاب کن. لمس دوباره قربانی اول، انتخاب را لغو می‌کند."
                    else "یکی از قربانی‌های باقی‌مانده را برای قتل انتخاب کن.",
                    color=Color.White
                )
                GameMap(hp,true,women.associate{it.point to Color.Red},firstDoubleKill?.let(::setOf)?:emptySet(),{p->
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
                            val path=JSONArray().put(firstDoubleKill).put(victim.point)
                            val firstMove=JSONArray().put(JSONObject().put("type","DOUBLE_EVENT").put("first",victim.point))
                            o.put("crime",crimes).put("women",JSONArray()).put("jackPath",path).put("jackMoves",firstMove).put("moveTrack",1).put("policeDone",JSONArray()).put("policeSearched",JSONArray()).remove("doubleKillFirst")
                            val revealed=police.filter{it.real}.map{it.copy(revealed=true)}
                            o.put("police",revealed.json()).put("policeIndex",0)
                            setPublicMessage(o,"دو قتل شب سوم رخ داد؛ هر دو محل قتل ثبت شدند و تعقیب با نوبت پلیس آغاز می‌شود.")
                            setPhase("HUNT_POLICE_HANDOFF")
                        }
                    }else{
                        val crimes=o.optJSONArray("crime")?:JSONArray(); if((0 until crimes.length()).none{crimes.getInt(it)==victim.point}) crimes.put(victim.point)
                        o.put("crime",crimes).put("women",JSONArray()).put("jackPath",JSONArray().put(victim.point)).put("jackMoves",JSONArray()).put("moveTrack",0).put("policeDone",JSONArray()).put("policeSearched",JSONArray())
                        val revealed=police.filter{it.real}.map{it.copy(revealed=true)}
                        o.put("police",revealed.json()).put("policeIndex",0)
                        setPublicMessage(o,"قتل در خانه ${victim.point} رخ داد؛ محل قتل ثبت شد و مرحله تعقیب آغاز شد.")
                        setPhase("HUNT_JACK_UNLOCK")
                    }
                },Modifier.weight(1f),crimePoints=(o.optJSONArray("crime")?:JSONArray()).intSet(),primaryShape=MapMarkerShape.HEART)
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
                },Modifier.weight(1f),overlayPoints=hp,crimePoints=(o.optJSONArray("crime")?:JSONArray()).intSet(),cluePoints=(o.optJSONArray("clues")?:JSONArray()).intSet())
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
                    },Modifier.weight(1f),secondaryPoints=pp,secondaryMarkers=real.associate{it.point to hc(it.color)},secondaryActive=if(activeId==0) emptySet() else setOf(real.firstOrNull{it.id==activeId}?.point?:-1),crimePoints=crimes,cluePoints=clues)
                    if(action=="search" && activeId!=0 && activeId !in doneIds) OutlinedButton(onClick={markPoliceDone(o,activeId);resultMessage="استعلام‌های سرنخ این پلیس تمام شد؛ این پلیس برای این نوبت غیرفعال شد.";setPublicMessage(o,"استعلام‌های سرنخ یک پلیس پایان یافت.");save()},modifier=Modifier.fillMaxWidth()){Text("پایان استعلام‌های این پلیس")}
                    Button(onClick={setPublicMessage(o,"نوبت کارآگاه‌ها پایان یافت؛ نوبت جک آغاز شد.");setPhase("HUNT_JACK_UNLOCK")},modifier=Modifier.fillMaxWidth()){Text("تحویل به جک")}
                }
            }
            "GAME_OVER_POLICE"->CenterMessage("جک دستگیر شد • کارآگاه‌ها برنده شدند",onBack)
            "GAME_OVER_JACK"->CenterMessage("جک بازی را به پایان رساند",onBack)
        }
    }
}

private fun endNight(o:JSONObject,ctx:Context){
    val n=o.optInt("night",1)
    if(n>=4){o.put("phase","GAME_OVER_JACK");setPublicMessage(o,"شب چهارم پایان یافت؛ جک فرار کرد.");DigitalGameStore.save(ctx,o);return}
    val finalPolice=o.optJSONArray("police")?.tokens().orEmpty().filter{it.real}
    val previousPoints=JSONArray();finalPolice.forEach{previousPoints.put(it.point)}
    o.put("previousPolicePoints",previousPoints).put("night",n+1).put("phase","HELL_WOMEN").put("time",1).put("women",JSONArray()).put("police",JSONArray()).put("clues",JSONArray()).put("jackPath",JSONArray()).put("jackMoves",JSONArray()).put("moveTrack",0).put("policeDone",JSONArray()).put("policeSearched",JSONArray()).put("policeIndex",0).remove("doubleKillFirst")
    setPublicMessage(o,"شب $n پایان یافت؛ شب ${n+1} آغاز شد.");DigitalGameStore.save(ctx,o)
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
                OutlinedTextField(value=pin,onValueChange={pin=it.filter(Char::isDigit).take(6);error=""},label={Text("PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth())
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
    var type by remember{mutableStateOf(MoveType.NORMAL)};var first by remember{mutableStateOf<Int?>(null)};var second by remember{mutableStateOf<Int?>(null)};var registered by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    val crime=(o.optJSONArray("crime")?:JSONArray()).intSet();val clues=(o.optJSONArray("clues")?:JSONArray()).intSet()
    Text("محل شروع فرار به‌صورت خودکار از محل قتل ثبت شده است. موقعیت فعلی جک: $current",color=Color.White,fontSize=12.sp)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
        FilterChip(selected=type==MoveType.NORMAL,onClick={if(!registered){type=MoveType.NORMAL;first=null;second=null}},enabled=!registered,label={Text("عادی")},modifier=Modifier.weight(1f))
        FilterChip(selected=type==MoveType.COACH,onClick={if(!registered){type=MoveType.COACH;first=null;second=null}},enabled=!registered&&coachUsed<coachMax,label={Text("درشکه ${coachMax-coachUsed}")},modifier=Modifier.weight(1f))
        FilterChip(selected=type==MoveType.ALLEY,onClick={if(!registered){type=MoveType.ALLEY;first=null;second=null}},enabled=!registered&&alleyUsed<alleyMax,label={Text("کوچه ${alleyMax-alleyUsed}")},modifier=Modifier.weight(1f))
    }
    Text("شمارنده حرکت: $track / $moveLimit  •  ${if(type==MoveType.COACH) if(first==null) "مقصد اول درشکه را انتخاب کن" else "مقصد دوم درشکه را انتخاب کن" else "خانه مقصد را انتخاب کن"}",color=FGold,fontWeight=FontWeight.Bold)
    if(first!=null)Text(if(type==MoveType.COACH)"انتخاب: ${first}${second?.let{" → $it"}?:" → …"}" else "انتخاب: $first",color=Color.White)
    if(error.isNotBlank())Text(error,color=Color(0xFFFF8A80),fontSize=12.sp)
    GameMap(houses,true,emptyMap(),emptySet(),{p->
        if(!registered){if(type==MoveType.COACH){if(first==null)first=p.number else second=p.number}else first=p.number;error=""}
    },Modifier.weight(1f),secondaryPoints=policePoints,secondaryMarkers=publicPolice.associate{it.point to hc(it.color)},crimePoints=crime,cluePoints=clues,jackPoint=current)
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
    Button(onClick={val end=if(type==MoveType.COACH)second?:first else first;onCommitted(type==MoveType.NORMAL && end==o.optInt("hideout"))},enabled=registered,modifier=Modifier.fillMaxWidth()){Text("تحویل به کارآگاه‌ها")}
}

@Composable private fun PoliceActionSelector(real:List<DToken>,selected:Int,done:Set<Int>,onSelect:(Int)->Unit){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
        real.forEach{token->
            val disabled=token.id in done
            Surface(onClick={if(!disabled)onSelect(token.id)},enabled=!disabled,color=if(selected==token.id)Color(0xFF3A332B) else Color(0xFF1D1A17),shape=RoundedCornerShape(8.dp),border=androidx.compose.foundation.BorderStroke(if(selected==token.id)2.dp else 1.dp,if(selected==token.id)FGold else Color.DarkGray),modifier=Modifier.weight(1f).height(48.dp)){
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                    Box(Modifier.width(34.dp).height(10.dp).background(hc(token.color).copy(alpha=if(disabled).28f else 1f),RoundedCornerShape(8.dp)))
                }
            }
        }
    }
}

@Composable private fun CenterMessage(t:String,onBack:()->Unit){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(t,color=FGold,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center);Button(onClick=onBack){Text("صفحه اصلی")}}}
@Composable private fun Handoff(t:String,onReady:()->Unit){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(t,color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp));Button(onClick=onReady){Text("آماده‌ام")}}}

@Composable private fun VictimTypeSelector(
    selectedType: VictimPlacementType,
    onSelect: (VictimPlacementType) -> Unit
){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
        listOf(
            VictimPlacementType.REAL to Color.Red,
            VictimPlacementType.FAKE to Color.White
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
                    Box(Modifier.size(32.dp).border(5.dp,color,RoundedCornerShape(50)))
                }
            }
        }
    }
}

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
@Composable private fun TvLinkCard(){val u=TvServerInfo.url.ifBlank{"در حال ساخت لینک…"};Surface(color=Color(0xFF241E18),shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth()){Text("نمایش عمومی: $u",color=FGold,fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(5.dp))}}
@Composable private fun rememberBoardPointsFeature(file:String):List<BoardPoint>{val c=LocalContext.current;return remember(file){val a=JSONArray(c.assets.open(file).bufferedReader().use{it.readText()});List(a.length()){i->val x=a.getJSONObject(i);BoardPoint(x.optInt("id",i+1),x.getInt("x"),x.getInt("y"),x.getDouble("norm_x").toFloat(),x.getDouble("norm_y").toFloat(),x.getInt("number"))}}}

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
    cluePoints:Set<Int> = emptySet(),
    primaryShape:MapMarkerShape = MapMarkerShape.CIRCLE,
    secondaryShape:MapMarkerShape = MapMarkerShape.CIRCLE,
    jackPoint:Int? = null
){
    val context=LocalContext.current
    val baseBitmap=remember{BitmapFactory.decodeResource(context.resources,R.drawable.whitechapel_board_base).asImageBitmap()}
    val numberBitmap=remember{BitmapFactory.decodeResource(context.resources,R.drawable.whitechapel_house_numbers_overlay).asImageBitmap()}
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
            if(numbers)drawImage(numberBitmap,IntOffset.Zero,IntSize(numberBitmap.width,numberBitmap.height),dstOffset,dstSize,filterQuality=FilterQuality.High)

            fun center(p:BoardPoint)=Offset((bl+p.normX*bw)*scale+offset.x,(bt+p.normY*bh)*scale+offset.y)
            // Police circles are about 10% larger on phone for better contrast over red crossings.
            val policeRadius=12.dp.toPx();val stroke=3.dp.toPx()
            fun heartPath(c:Offset,size:Float)=Path().apply{
                moveTo(c.x,c.y+size*0.88f)
                cubicTo(c.x-size*1.18f,c.y+size*0.12f,c.x-size*1.02f,c.y-size*0.82f,c.x-size*0.45f,c.y-size*0.82f)
                cubicTo(c.x-size*0.16f,c.y-size*0.82f,c.x,c.y-size*0.60f,c.x,c.y-size*0.36f)
                cubicTo(c.x,c.y-size*0.60f,c.x+size*0.16f,c.y-size*0.82f,c.x+size*0.45f,c.y-size*0.82f)
                cubicTo(c.x+size*1.02f,c.y-size*0.82f,c.x+size*1.18f,c.y+size*0.12f,c.x,c.y+size*0.88f)
                close()
            }
            markers.forEach{(n,col)->points.firstOrNull{it.number==n}?.let{p->
                val c=center(p)
                if(primaryShape==MapMarkerShape.HEART)drawPath(heartPath(c,12.dp.toPx()),col,style=Stroke(stroke)) else drawCircle(col,policeRadius,c,style=Stroke(stroke))
                if(n in active)drawCircle(FGold,policeRadius*1.30f,c,style=Stroke(2.dp.toPx()))
            }}
            secondaryMarkers.forEach{(n,col)->secondaryPoints.firstOrNull{it.number==n}?.let{p->
                val c=center(p)
                if(secondaryShape==MapMarkerShape.HEART)drawPath(heartPath(c,12.dp.toPx()),col,style=Stroke(stroke)) else drawCircle(col,policeRadius,c,style=Stroke(stroke))
                if(n in secondaryActive)drawCircle(FGold,policeRadius*1.30f,c,style=Stroke(2.dp.toPx()))
            }}

            // Crime Scene and clues always use house coordinates, never police/Crossing IDs.
            val crimeHalf=12.dp.toPx()
            crimePoints.forEach{n->overlayPoints.firstOrNull{it.number==n}?.let{p->val c=center(p);drawRect(FBlood,topLeft=Offset(c.x-crimeHalf,c.y-crimeHalf),size=Size(crimeHalf*2,crimeHalf*2),style=Stroke(3.dp.toPx()))}}
            // Clues are hollow yellow diamonds, visually distinct from police circles and victim hearts.
            val clueHalf=14.dp.toPx()
            cluePoints.forEach{n->overlayPoints.firstOrNull{it.number==n}?.let{p->
                val c=center(p);val diamond=Path().apply{moveTo(c.x,c.y-clueHalf);lineTo(c.x+clueHalf,c.y);lineTo(c.x,c.y+clueHalf);lineTo(c.x-clueHalf,c.y);close()}
                drawPath(diamond,Color(0x33F0C52B));drawPath(diamond,Color(0xFFF0C52B),style=Stroke(2.5.dp.toPx()))
            }}
            // Jack's current position is private: this X is only requested by the Jack phone screen.
            jackPoint?.let{n->overlayPoints.firstOrNull{it.number==n}?.let{p->
                val c=center(p);val r=10.dp.toPx();val sw=3.dp.toPx()
                drawLine(Color(0xFFE53935),Offset(c.x-r,c.y-r),Offset(c.x+r,c.y+r),strokeWidth=sw)
                drawLine(Color(0xFFE53935),Offset(c.x+r,c.y-r),Offset(c.x-r,c.y+r),strokeWidth=sw)
            }}
        }
    }
}

