package com.hamed.whitechapeljack

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class MoveType { NORMAL, COACH, ALLEY }
enum class QueryType { SEARCH, ARREST }
data class JackMove(val night:Int,val turn:Int,val location:Int,val type:MoveType)
data class Inquiry(val night:Int,val index:Int,val type:QueryType,val location:Int,val success:Boolean)

private val Ink=Color(0xFF100E0D)
private val Panel=Color(0xFF201B17)
private val Gold=Color(0xFFD2A65A)
private val Blood=Color(0xFF9D1717)
private val Parchment=Color(0xFFE7D1A7)
private val Green=Color(0xFF176B37)

class Store(ctx:Context){
 private val p=ctx.getSharedPreferences("whitechapel_v4",Context.MODE_PRIVATE)
 fun save(pin:String,hideout:Int,night:Int,starts:Map<Int,Int>,moves:List<JackMove>,queries:List<Inquiry>,ended:Set<Int>,gameOver:Boolean){
  val o=JSONObject().put("pin",pin).put("hideout",hideout).put("night",night).put("gameOver",gameOver)
  val s=JSONObject();starts.forEach{s.put(it.key.toString(),it.value)};o.put("starts",s)
  val ma=JSONArray();moves.forEach{ma.put(JSONObject().put("n",it.night).put("t",it.turn).put("l",it.location).put("y",it.type.name))};o.put("moves",ma)
  val qa=JSONArray();queries.forEach{qa.put(JSONObject().put("n",it.night).put("i",it.index).put("t",it.type.name).put("l",it.location).put("s",it.success))};o.put("queries",qa)
  val ea=JSONArray();ended.sorted().forEach{ea.put(it)};o.put("ended",ea)
  p.edit().putString("game",o.toString()).apply()
 }
 fun load():JSONObject?=p.getString("game",null)?.let{runCatching{JSONObject(it)}.getOrNull()}
 fun clear(){p.edit().clear().apply()}
 companion object{fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}}
}

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);setContent{App(Store(this))}}
}

@Composable fun App(store:Store){
 var screen by remember{mutableStateOf("splash")}
 var pinHash by remember{mutableStateOf("")};var pin by remember{mutableStateOf("")}
 var hideout by remember{mutableIntStateOf(0)};var night by remember{mutableIntStateOf(1)};var gameOver by remember{mutableStateOf(false)}
 val starts=remember{mutableStateMapOf<Int,Int>()};val moves=remember{mutableStateListOf<JackMove>()};val queries=remember{mutableStateListOf<Inquiry>()};val ended=remember{mutableStateListOf<Int>()}
 fun persist(){store.save(pinHash,hideout,night,starts,moves,queries,ended.toSet(),gameOver)}
 fun restore():Boolean{val o=store.load()?:return false;pinHash=o.getString("pin");hideout=o.getInt("hideout");night=o.getInt("night");gameOver=o.optBoolean("gameOver",false)
  starts.clear();moves.clear();queries.clear();ended.clear()
  val s=o.optJSONObject("starts")?:JSONObject();s.keys().forEach{starts[it.toInt()]=s.getInt(it)}
  val ma=o.optJSONArray("moves")?:JSONArray();for(i in 0 until ma.length()){val x=ma.getJSONObject(i);moves+=JackMove(x.getInt("n"),x.getInt("t"),x.getInt("l"),MoveType.valueOf(x.getString("y")))}
  val qa=o.optJSONArray("queries")?:JSONArray();for(i in 0 until qa.length()){val x=qa.getJSONObject(i);queries+=Inquiry(x.getInt("n"),x.getInt("i"),QueryType.valueOf(x.getString("t")),x.getInt("l"),x.getBoolean("s"))}
  val ea=o.optJSONArray("ended")?:JSONArray();for(i in 0 until ea.length())ended+=ea.getInt(i);return true}
 val colors=darkColorScheme(primary=Gold,secondary=Blood,surface=Ink,surfaceVariant=Panel,onPrimary=Color.Black)
 MaterialTheme(colorScheme=colors){
  if(screen=="splash"){LaunchedEffect(Unit){delay(1600);screen="home"};Splash()}
  else Surface(Modifier.fillMaxSize(),color=Ink){Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF090909),Color(0xFF1B1410)))).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text("WHITECHAPEL",color=Color(0xFFCF2929),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
   when(screen){
    "home"->{Text("Game Companion",color=Gold);VictorianButton("▶  شروع بازی جدید"){screen="setup"}
     if(store.load()!=null)VictorianButton("▣  ادامه بازی"){if(restore())screen=if(gameOver)"audit" else "detective"}}
    "setup"->{var h by remember{mutableStateOf("")};var err by remember{mutableStateOf("")}
     ParchmentCard{Text("⌂  انتخاب مخفیگاه",fontWeight=FontWeight.Bold);Text("این شماره برای تمام بازی ثابت و محرمانه می‌ماند.")
      OutlinedTextField(h,{h=it.filter(Char::isDigit).take(3)},leadingIcon={Text("⌂",style=MaterialTheme.typography.headlineSmall)},label={Text("Hideout 1–195")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())}
     OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(6)},leadingIcon={Text("🔒")},label={Text("PIN جک")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
     if(err.isNotEmpty())Text(err,color=Color.Red)
     RedButton("شروع بازی"){val x=h.toIntOrNull();if(pin.length in 4..6&&x in 1..195){pinHash=Store.hash(pin);hideout=x!!;night=1;gameOver=false;starts.clear();moves.clear();queries.clear();ended.clear();pin="";persist();screen="jack"}else err="PIN یا مخفیگاه معتبر نیست."}}
    "jack"->{Text("حالت جک • Night $night",color=Gold,style=MaterialTheme.typography.titleLarge)
     if(starts[night]==null){var st by remember(night){mutableStateOf("")};ParchmentCard{Text("Crime Scene / موقعیت شروع")
      OutlinedTextField(st,{st=it.filter(Char::isDigit).take(3)},label={Text("Start 1–195")},modifier=Modifier.fillMaxWidth())
      RedButton("ثبت شروع شب"){val x=st.toIntOrNull();if(x in 1..195){starts[night]=x!!;persist()}}}}
     else{
      var type by remember{mutableStateOf(MoveType.NORMAL)};var a by remember{mutableStateOf("")};var b by remember{mutableStateOf("")};var msg by remember{mutableStateOf("")};var atHideout by remember{mutableStateOf(false)}
      Text("موقعیت شروع: ${starts[night]}   •   مخفیگاه: $hideout")
      Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){MoveType.entries.forEach{t->FilterChip(type==t,{type=t},label={Text(when(t){MoveType.NORMAL->"🚶 عادی";MoveType.ALLEY->"↯ کوچه";MoveType.COACH->"▣ درشکه"})})}}
      OutlinedTextField(a,{a=it.filter(Char::isDigit).take(3)},label={Text(if(type==MoveType.COACH)"مقصد اول Coach" else "مقصد")},modifier=Modifier.fillMaxWidth())
      if(type==MoveType.COACH)OutlinedTextField(b,{b=it.filter(Char::isDigit).take(3)},label={Text("مقصد دوم Coach")},modifier=Modifier.fillMaxWidth())
      Text("اعتبارسنجی اتصال نقشه: خاموش",color=Gold)
      RedButton("ثبت حرکت"){val x=a.toIntOrNull();val y=b.toIntOrNull()
       if(x !in 1..195||(type==MoveType.COACH&&y !in 1..195))msg="شماره مقصد معتبر نیست."
       else{var t=moves.count{it.night==night}+1;moves+=JackMove(night,t++,x!!,type);if(type==MoveType.COACH)moves+=JackMove(night,t,y!!,type);persist();atHideout=type==MoveType.NORMAL&&x==hideout;msg=if(atHideout)"شما در مخفیگاه هستید." else "حرکت ثبت شد.";a="";b=""}}
      if(msg.isNotEmpty())Text(msg,color=if(atHideout)Gold else Color.White)
      if(atHideout){ParchmentCard{Text("⌂ شما با حرکت عادی به مخفیگاه رسیده‌اید.",fontWeight=FontWeight.Bold)
       Button({if(!ended.contains(night))ended+=night;if(night==4){gameOver=true;persist();screen="audit"}else{persist();screen="escaped"}},colors=ButtonDefaults.buttonColors(containerColor=Green),modifier=Modifier.fillMaxWidth()){Text("⚑ اعلام فرار و پایان شب")}
       OutlinedButton({atHideout=false;screen="detective"},Modifier.fillMaxWidth()){Text("اعلام نکن — ادامه بازی")}}}
      Text("تاریخچه مخفی حرکت‌ها",color=Gold)
      LazyColumn(Modifier.weight(1f)){items(moves.filter{it.night==night}.reversed()){Text("#${it.turn} • ${it.location} • ${it.type.name}")}}
      if(!atHideout)VictorianButton("🔒 پایان نوبت و تحویل به کارآگاه‌ها"){screen="detective"}}
    }
    "escaped"->{Text("پایان Night $night",color=Gold,style=MaterialTheme.typography.headlineSmall);Card(colors=CardDefaults.cardColors(containerColor=Green),modifier=Modifier.fillMaxWidth()){Text("✓ فرار Jack توسط برنامه تأیید شد.",Modifier.padding(18.dp),fontWeight=FontWeight.Bold)}
     Text("مسیر و محل مخفیگاه همچنان محرمانه‌اند.");VictorianButton("شروع شب بعد"){night++;persist();screen="unlock"}}
    "detective"->{var q by remember{mutableStateOf("")};var result by remember{mutableStateOf("اطلاعات محرمانه Jack نمایش داده نمی‌شود.")}
     Text("حالت کارآگاه‌ها • Night $night",color=Gold,style=MaterialTheme.typography.titleLarge)
     OutlinedTextField(q,{q=it.filter(Char::isDigit).take(3)},leadingIcon={Text("⌕")},label={Text("شماره خانه")},modifier=Modifier.fillMaxWidth())
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({val x=q.toIntOrNull();if(x in 1..195){val ok=starts[night]==x||moves.any{it.night==night&&it.location==x};queries+=Inquiry(night,queries.count{it.night==night}+1,QueryType.SEARCH,x!!,ok);persist();result=if(ok)"✓ سرنخ پیدا شد: $x" else "✗ سرنخی نیست: $x"}},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF135A91))){Text("⌕ جستجو")}
      Button({val x=q.toIntOrNull();if(x in 1..195){val cur=moves.lastOrNull{it.night==night}?.location?:starts[night];val ok=cur==x;queries+=Inquiry(night,queries.count{it.night==night}+1,QueryType.ARREST,x!!,ok);persist();result=if(ok)"✓ دستگیری موفق: $x" else "✗ دستگیری ناموفق: $x"}},colors=ButtonDefaults.buttonColors(containerColor=Blood)){Text("⛓ دستگیری")}}
     ParchmentCard{Text(result,fontWeight=FontWeight.Bold)}
     Text("تاریخچه استعلام‌های این شب",color=Gold)
     LazyColumn(Modifier.weight(1f)){items(queries.filter{it.night==night}.reversed()){i->Text("#${i.index} • ${i.type.name} • ${i.location} • ${if(i.success)"✓ مثبت" else "✗ منفی"}")}}
     VictorianButton("🔐 نوبت Jack"){screen="unlock"}}
    "unlock"->{var err by remember{mutableStateOf("")};Text("ورود محرمانه Jack",color=Gold)
     OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(6)},leadingIcon={Text("🔒")},label={Text("PIN")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
     if(err.isNotEmpty())Text(err,color=Color.Red);RedButton("باز کردن دفترچه"){if(Store.hash(pin)==pinHash){pin="";screen="jack"}else err="PIN اشتباه است."};VictorianButton("بازگشت"){screen="detective"}}
    "audit"->{Text("GAME AUDIT / REVEAL",color=Gold,style=MaterialTheme.typography.headlineSmall);ParchmentCard{Text("⌂ Hideout: $hideout",fontWeight=FontWeight.Bold)}
     LazyColumn(Modifier.weight(1f)){for(n in 1..4){item{Text("Night $n • Start ${starts[n]?:"-"} • ${if(ended.contains(n))"✓ Escape verified" else "—"}",color=Gold,fontWeight=FontWeight.Bold)}
      items(moves.filter{it.night==n}){Text("  Move ${it.turn}: ${it.location} • ${it.type.name}")};items(queries.filter{it.night==n}){Text("  ${it.type.name} ${it.location}: ${if(it.success)"✓" else "✗"}")}}}
     RedButton("پایان و پاک کردن بازی"){store.clear();screen="home"}}
   }
  }}
 }
}

@Composable fun Splash(){Box(Modifier.fillMaxSize()){Image(painterResource(R.drawable.whitechapel_splash),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.85f)))));Column(Modifier.align(Alignment.BottomCenter).padding(30.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("LETTERS FROM",color=Parchment);Text("WHITECHAPEL",color=Color(0xFFCE2020),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Text("GAME COMPANION",color=Gold);Spacer(Modifier.height(28.dp));LinearProgressIndicator(Modifier.fillMaxWidth(),color=Blood)}}}
@Composable fun VictorianButton(text:String,onClick:()->Unit){Button(onClick,Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=Panel,contentColor=Gold)){Text(text,fontWeight=FontWeight.Bold)}}
@Composable fun RedButton(text:String,onClick:()->Unit){Button(onClick,Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=Blood)){Text(text,fontWeight=FontWeight.Bold)}}
@Composable fun ParchmentCard(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=Parchment,contentColor=Color(0xFF21170F))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
