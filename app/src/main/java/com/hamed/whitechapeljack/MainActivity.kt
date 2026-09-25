package com.hamed.whitechapeljack

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class MoveType { NORMAL, COACH, ALLEY }
enum class QueryType { SEARCH, ARREST }
data class JackMove(val night:Int,val turn:Int,val location:Int,val type:MoveType)
data class Inquiry(val night:Int,val index:Int,val type:QueryType,val location:Int,val success:Boolean)

class Store(ctx:Context){
 private val p=ctx.getSharedPreferences("whitechapel_v3",Context.MODE_PRIVATE)
 fun save(pin:String,hideout:Int,night:Int,starts:Map<Int,Int>,moves:List<JackMove>,queries:List<Inquiry>,ended:Set<Int>,gameOver:Boolean){
  val o=JSONObject().put("pin",pin).put("hideout",hideout).put("night",night).put("gameOver",gameOver)
  val s=JSONObject(); starts.forEach{s.put(it.key.toString(),it.value)};o.put("starts",s)
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
 var screen by remember{mutableStateOf("home")};var pinHash by remember{mutableStateOf("")};var pin by remember{mutableStateOf("")}
 var hideout by remember{mutableIntStateOf(0)};var night by remember{mutableIntStateOf(1)};var gameOver by remember{mutableStateOf(false)}
 val starts=remember{mutableStateMapOf<Int,Int>()};val moves=remember{mutableStateListOf<JackMove>()};val queries=remember{mutableStateListOf<Inquiry>()};val ended=remember{mutableStateListOf<Int>()}
 fun persist(){store.save(pinHash,hideout,night,starts,moves,queries,ended.toSet(),gameOver)}
 fun restore():Boolean{val o=store.load()?:return false;pinHash=o.getString("pin");hideout=o.getInt("hideout");night=o.getInt("night");gameOver=o.optBoolean("gameOver",false)
  starts.clear();moves.clear();queries.clear();ended.clear()
  val s=o.optJSONObject("starts")?:JSONObject();s.keys().forEach{starts[it.toInt()]=s.getInt(it)}
  val ma=o.optJSONArray("moves")?:JSONArray();for(i in 0 until ma.length()){val x=ma.getJSONObject(i);moves+=JackMove(x.getInt("n"),x.getInt("t"),x.getInt("l"),MoveType.valueOf(x.getString("y")))}
  val qa=o.optJSONArray("queries")?:JSONArray();for(i in 0 until qa.length()){val x=qa.getJSONObject(i);queries+=Inquiry(x.getInt("n"),x.getInt("i"),QueryType.valueOf(x.getString("t")),x.getInt("l"),x.getBoolean("s"))}
  val ea=o.optJSONArray("ended")?:JSONArray();for(i in 0 until ea.length())ended+=ea.getInt(i);return true}
 MaterialTheme(colorScheme=darkColorScheme()){Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text("Whitechapel Jack",style=MaterialTheme.typography.headlineMedium)
  when(screen){
   "home"->{Text("دفترچه محرمانه و داور دیجیتال Jack");Button({screen="setup"},Modifier.fillMaxWidth()){Text("بازی جدید")}
    if(store.load()!=null)Button({if(restore())screen=if(gameOver)"audit" else "detective"},Modifier.fillMaxWidth()){Text("ادامه بازی")}}
   "setup"->{var h by remember{mutableStateOf("")};var err by remember{mutableStateOf("")}
    OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(6)},label={Text("PIN (۴ تا ۶ رقم)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
    OutlinedTextField(h,{h=it.filter(Char::isDigit).take(3)},label={Text("Hideout (1–195)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
    if(err.isNotEmpty())Text(err,color=MaterialTheme.colorScheme.error)
    Button({val x=h.toIntOrNull();if(pin.length in 4..6&&x in 1..195){pinHash=Store.hash(pin);hideout=x!!;night=1;gameOver=false;starts.clear();moves.clear();queries.clear();ended.clear();pin="";persist();screen="jack"}else err="PIN یا Hideout معتبر نیست."},Modifier.fillMaxWidth()){Text("شروع بازی")}}
   "jack"->{
    Text("Jack • Night $night")
    if(starts[night]==null){var st by remember(night){mutableStateOf("")}
     Text("Crime Scene / موقعیت شروع این شب را ثبت کن.")
     OutlinedTextField(st,{st=it.filter(Char::isDigit).take(3)},label={Text("Start location 1–195")},modifier=Modifier.fillMaxWidth())
     Button({val x=st.toIntOrNull();if(x in 1..195){starts[night]=x!!;persist()}},Modifier.fillMaxWidth()){Text("ثبت شروع شب")}
    }else{
     var type by remember{mutableStateOf(MoveType.NORMAL)};var a by remember{mutableStateOf("")};var b by remember{mutableStateOf("")};var msg by remember{mutableStateOf("")};var escaped by remember{mutableStateOf(false)}
     Text("Start: ${starts[night]} • Hideout: $hideout")
     Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){MoveType.entries.forEach{t->FilterChip(type==t,{type=t},label={Text(t.name)})}}
     OutlinedTextField(a,{a=it.filter(Char::isDigit).take(3)},label={Text(if(type==MoveType.COACH)"Coach مقصد اول" else "مقصد")},modifier=Modifier.fillMaxWidth())
     if(type==MoveType.COACH)OutlinedTextField(b,{b=it.filter(Char::isDigit).take(3)},label={Text("Coach مقصد دوم")},modifier=Modifier.fillMaxWidth())
     Text("اعتبارسنجی اتصال نقشه: غیرفعال")
     Button({
      val x=a.toIntOrNull();val y=b.toIntOrNull()
      if(x !in 1..195||(type==MoveType.COACH&&y !in 1..195))msg="شماره مقصد معتبر نیست."
      else{var t=moves.count{it.night==night}+1;moves+=JackMove(night,t++,x!!,type);if(type==MoveType.COACH)moves+=JackMove(night,t,y!!,type);persist();escaped=(type==MoveType.NORMAL&&x==hideout);msg=if(escaped)"✓ برنامه رسیدن به Hideout را تأیید کرد." else "حرکت ثبت شد.";a="";b=""}
     },Modifier.fillMaxWidth()){Text("ثبت حرکت")}
     if(msg.isNotEmpty())Text(msg)
     if(escaped)Button({
       if(!ended.contains(night))ended+=night
       if(night==4){gameOver=true;persist();screen="audit"}else{persist();screen="escaped"}
     },Modifier.fillMaxWidth()){Text("تأیید فرار و پایان Night $night")}
     Text("تاریخچه مخفی حرکت‌ها")
     LazyColumn(Modifier.weight(1f)){items(moves.filter{it.night==night}.reversed()){Text("#${it.turn} • ${it.location} • ${it.type.name}")}}
     if(!escaped)Button({screen="detective"},Modifier.fillMaxWidth()){Text("🔒 پایان نوبت و تحویل به کارآگاه‌ها")}
    }}
   "escaped"->{Text("✓ فرار Jack توسط برنامه تأیید شد",style=MaterialTheme.typography.headlineSmall);Text("Night $night پایان یافت.");Text("مسیر و محل Hideout همچنان محرمانه هستند.")
    Button({night++;persist();screen="unlock"},Modifier.fillMaxWidth()){Text("آماده‌سازی Night $night")}}
   "detective"->{var q by remember{mutableStateOf("")};var result by remember{mutableStateOf("اطلاعات محرمانه Jack نمایش داده نمی‌شود.")}
    Text("Detective Mode • Night $night")
    OutlinedTextField(q,{q=it.filter(Char::isDigit).take(3)},label={Text("شماره خانه 1–195")},modifier=Modifier.fillMaxWidth())
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button({val x=q.toIntOrNull();if(x in 1..195){val ok=starts[night]==x||moves.any{it.night==night&&it.location==x};queries+=Inquiry(night,queries.count{it.night==night}+1,QueryType.SEARCH,x!!,ok);persist();result=if(ok)"✓ سرنخ پیدا شد: $x" else "✗ در $x سرنخی نیست."}}){Text("Search")}
     Button({val x=q.toIntOrNull();if(x in 1..195){val cur=moves.lastOrNull{it.night==night}?.location?:starts[night];val ok=cur==x;queries+=Inquiry(night,queries.count{it.night==night}+1,QueryType.ARREST,x!!,ok);persist();result=if(ok)"✓ دستگیری موفق در $x" else "✗ دستگیری ناموفق در $x"}}){Text("Arrest")}}
    Card(Modifier.fillMaxWidth()){Text(result,Modifier.padding(14.dp))}
    Text("تاریخچه استعلام‌های Night $night")
    LazyColumn(Modifier.weight(1f)){items(queries.filter{it.night==night}.reversed()){i->Text("#${i.index} • ${i.type.name} • ${i.location} • ${if(i.success)"✓ مثبت" else "✗ منفی"}")}}
    OutlinedButton({screen="unlock"},Modifier.fillMaxWidth()){Text("🔐 نوبت Jack")}}
   "unlock"->{var err by remember{mutableStateOf("")}
    OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(6)},label={Text("PIN Jack")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
    if(err.isNotEmpty())Text(err,color=MaterialTheme.colorScheme.error)
    Button({if(Store.hash(pin)==pinHash){pin="";screen="jack"}else err="PIN اشتباه است."},Modifier.fillMaxWidth()){Text("باز کردن دفترچه")}
    OutlinedButton({screen="detective"},Modifier.fillMaxWidth()){Text("بازگشت")}}
   "audit"->{Text("GAME AUDIT / REVEAL",style=MaterialTheme.typography.headlineSmall);Text("Hideout: $hideout")
    LazyColumn(Modifier.weight(1f)){for(n in 1..4){item{Text("Night $n • Start: ${starts[n]?:"-"} • Escape: ${if(ended.contains(n))"✓ Verified" else "—"}",style=MaterialTheme.typography.titleMedium)}
      items(moves.filter{it.night==n}){Text("  Move ${it.turn}: ${it.location} • ${it.type.name}")}
      items(queries.filter{it.night==n}){Text("  ${it.type.name} ${it.location}: ${if(it.success)"✓" else "✗"}")}}}
    Button({store.clear();screen="home"},Modifier.fillMaxWidth()){Text("پایان و پاک کردن بازی")}}
  }
 }}}}
