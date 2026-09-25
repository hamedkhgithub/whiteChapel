package com.hamed.whitechapeljack

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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

private val Gold=Color(0xFFD6AD63)
private val Blood=Color(0xFFB41616)
private val Ink=Color(0xFF090807)
private val GrayCard=Color(0xFFD0D0CE)
private val GrayField=Color(0xFFE1E0DC)
private val DarkButton=Color(0xE91A1510)
private val Green=Color(0xFF075D2D)

enum class MoveType { NORMAL, COACH, ALLEY }
data class UiMove(val turn:Int,val destination:String,val secondDestination:String?=null,val type:MoveType)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN)
  setContent{WhitechapelUi()}
 }
}

@Composable fun WhitechapelUi(){
 var page by remember{mutableStateOf("splash")}
 var hideout by remember{mutableStateOf("")}
 var jackPin by remember{mutableStateOf("")}
 MaterialTheme(colorScheme=darkColorScheme(primary=Gold,surface=Ink)){
  when(page){
   "splash"->Splash{page="home"}
   "home"->Home{page="newgame"}
   "newgame"->NewGame(
    onBack={page="home"},
    onStart={h,p->hideout=h;jackPin=p;page="jack"}
   )
   "jack"->JackMoves(hideout=hideout,onBack={page="newgame"})
  }
 }
}

@Composable private fun Splash(onDone:()->Unit){
 var started by remember{mutableStateOf(false)}
 val progress by animateFloatAsState(if(started)1f else 0f,tween(5000),label="loading")
 LaunchedEffect(Unit){started=true;delay(5100);onDone()}
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

@Composable private fun Home(onNewGame:()->Unit){
 Box(Modifier.fillMaxSize()){
  Image(painterResource(R.drawable.home_background),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  Box(Modifier.fillMaxSize().background(Color.Black.copy(.12f)))
  Column(Modifier.fillMaxSize().padding(horizontal=34.dp),horizontalAlignment=Alignment.CenterHorizontally){
   Spacer(Modifier.weight(.40f))
   MenuButton("▶","شروع بازی جدید","New Game",true,onNewGame);Spacer(Modifier.height(13.dp))
   MenuButton("▰","ادامه بازی","Continue",false){};Spacer(Modifier.height(13.dp))
   MenuButton("▤","راهنما","How to Play",false){};Spacer(Modifier.height(13.dp))
   MenuButton("⚙","تنظیمات","Settings",false){};Spacer(Modifier.weight(.18f))
  }
 }
}

@Composable private fun MenuButton(icon:String,fa:String,en:String,enabled:Boolean,onClick:()->Unit){
 Button(onClick,enabled,Modifier.fillMaxWidth().height(70.dp).border(1.dp,Gold,RoundedCornerShape(9.dp)),shape=RoundedCornerShape(9.dp),
  colors=ButtonDefaults.buttonColors(containerColor=DarkButton,contentColor=Gold,disabledContainerColor=Color(0xD915120F),disabledContentColor=Gold.copy(.7f)),
  contentPadding=PaddingValues(horizontal=18.dp)){
  Text(icon,fontSize=29.sp);Spacer(Modifier.width(18.dp))
  Column(Modifier.weight(1f),horizontalAlignment=Alignment.End){
   Text(fa,color=Color(0xFFF2DFC0),fontWeight=FontWeight.Bold,fontSize=18.sp);Text(en,color=Gold,fontSize=11.sp)
  }
 }
}

@Composable private fun NewGame(onBack:()->Unit,onStart:(String,String)->Unit){
 var hideout by remember{mutableStateOf("")};var pin by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")}
 val valid=hideout.toIntOrNull() in 1..195 && pin.length in 4..6 && pin==confirm
 Background{
  Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Header("شروع بازی جدید","New Game Setup",onBack)
   GrayCard{
    Row(verticalAlignment=Alignment.CenterVertically){Text("⌂",fontSize=43.sp,color=Color.Black);Spacer(Modifier.width(12.dp));Text("انتخاب مخفیگاه",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.Black)}
    DarkOutlinedField(hideout,{hideout=it.filter(Char::isDigit).take(3)},"شماره مخفیگاه","⌂",KeyboardType.Number)
    Row(verticalAlignment=Alignment.CenterVertically){Text("🔒",fontSize=22.sp);Spacer(Modifier.width(8.dp));Text("مخفیگاه برای کل بازی ثابت می‌ماند و قابل تغییر نیست.",color=Color.Black,fontSize=13.sp)}
   }
   GrayCard{
    Row(verticalAlignment=Alignment.CenterVertically){Text("🔐",fontSize=34.sp);Spacer(Modifier.width(10.dp));Column{Text("تعیین PIN جک",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.Black);Text("یک رمز ۴ تا ۶ رقمی تعیین کنید",color=Color.Black,fontSize=13.sp)}}
    PinField(pin,{pin=it.filter(Char::isDigit).take(6)},"PIN")
    PinField(confirm,{confirm=it.filter(Char::isDigit).take(6)},"تکرار PIN")
    if(confirm.isNotEmpty()&&pin!=confirm)Text("PINها یکسان نیستند.",color=Color(0xFF8D0000),fontSize=12.sp)
   }
   RedButton("▶   شروع بازی",valid){onStart(hideout,pin)}
  }
 }
}

@Composable private fun JackMoves(hideout:String,onBack:()->Unit){
 var start by remember{mutableStateOf("")}
 var startLocked by remember{mutableStateOf(false)}
 var type by remember{mutableStateOf(MoveType.NORMAL)}
 var d1 by remember{mutableStateOf("")};var d2 by remember{mutableStateOf("")}
 val moves=remember{mutableStateListOf<UiMove>()}
 var showHideout by remember{mutableStateOf(false)}

 Background{
  Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Header("حرکت‌های جک","Jack's Movement",onBack)

   GrayCard{
    Text("محل ارتکاب / شروع حرکت",color=Color.Black,fontWeight=FontWeight.Bold,fontSize=18.sp)
    Row(verticalAlignment=Alignment.CenterVertically){
     Box(Modifier.weight(1f)){DarkOutlinedField(start,{if(!startLocked)start=it.filter(Char::isDigit).take(3)},"شماره خانه","⌖",KeyboardType.Number)}
     Spacer(Modifier.width(8.dp))
     Button(onClick={if(start.toIntOrNull() in 1..195)startLocked=true},enabled=!startLocked,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF333333))){Text(if(startLocked)"✓" else "ثبت")}
    }
   }

   if(startLocked){
    GrayCard{
     Text("نوع حرکت",color=Color.Black,fontWeight=FontWeight.Bold,fontSize=18.sp)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
      MoveChip("🚶","عادی",type==MoveType.NORMAL){type=MoveType.NORMAL}
      MoveChip("♞","درشکه",type==MoveType.COACH){type=MoveType.COACH}
      MoveChip("↯","کوچه",type==MoveType.ALLEY){type=MoveType.ALLEY}
     }
     DarkOutlinedField(d1,{d1=it.filter(Char::isDigit).take(3)},if(type==MoveType.COACH)"مقصد اول" else "خانه مقصد","⌖",KeyboardType.Number)
     if(type==MoveType.COACH)DarkOutlinedField(d2,{d2=it.filter(Char::isDigit).take(3)},"مقصد دوم درشکه","⌖",KeyboardType.Number)
     Button(onClick={
      val a=d1.toIntOrNull();val b=d2.toIntOrNull()
      if(a in 1..195 && (type!=MoveType.COACH || b in 1..195)){
       moves+=UiMove(moves.size+1,d1,if(type==MoveType.COACH)d2 else null,type)
       val finalDest=if(type==MoveType.COACH)d2 else d1
       if(type==MoveType.NORMAL && finalDest==hideout)showHideout=true
       d1="";d2=""
      }
     },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Blood)){Text("ثبت حرکت",fontWeight=FontWeight.Bold)}
    }

    Text("مسیر حرکت‌ها",color=Gold,fontSize=18.sp,fontWeight=FontWeight.Bold)
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){
     items(moves){m->
      Card(colors=CardDefaults.cardColors(containerColor=Color(0xCC17130F)),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)){
       Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
        Text(when(m.type){MoveType.NORMAL->"🚶";MoveType.COACH->"♞";MoveType.ALLEY->"↯"},fontSize=23.sp)
        Spacer(Modifier.width(12.dp))
        Text("حرکت ${m.turn}",color=Gold,fontWeight=FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(if(m.secondDestination!=null)"${m.destination}  →  ${m.secondDestination}" else m.destination,color=Color.White,fontSize=17.sp)
       }
      }
     }
    }
    Button(onClick={},modifier=Modifier.fillMaxWidth().height(58.dp).border(1.dp,Gold,RoundedCornerShape(9.dp)),shape=RoundedCornerShape(9.dp),colors=ButtonDefaults.buttonColors(containerColor=DarkButton,contentColor=Gold)){
     Text("🔒   تحویل به کارآگاه‌ها",fontSize=18.sp,fontWeight=FontWeight.Bold)
    }
   }
  }
 }

 if(showHideout){
  AlertDialog(
   onDismissRequest={},
   containerColor=Color(0xFF12100E),
   title={Text("شما در مخفیگاه هستید",color=Color.White,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth(),fontWeight=FontWeight.Bold)},
   text={Column(horizontalAlignment=Alignment.CenterHorizontally){
    Text("⌂",fontSize=62.sp);Spacer(Modifier.height(8.dp))
    Text("شما با حرکت عادی به مخفیگاه رسیده‌اید.",color=Color(0xFFF2DFC0),textAlign=TextAlign.Center)
    Spacer(Modifier.height(18.dp))
    Button(onClick={showHideout=false},modifier=Modifier.fillMaxWidth().height(56.dp),colors=ButtonDefaults.buttonColors(containerColor=Green),shape=RoundedCornerShape(8.dp)){Text("⚑  اعلام فرار و پایان شب",fontWeight=FontWeight.Bold)}
    Spacer(Modifier.height(10.dp))
    Button(onClick={showHideout=false},modifier=Modifier.fillMaxWidth().height(56.dp).border(1.dp,Gold,RoundedCornerShape(8.dp)),colors=ButtonDefaults.buttonColors(containerColor=DarkButton),shape=RoundedCornerShape(8.dp)){Text("→  اعلام نکن — ادامه بازی",color=Gold,fontWeight=FontWeight.Bold)}
    Spacer(Modifier.height(12.dp))
    Text("در صورت اعلام نکردن، باید در نوبت بعد از مخفیگاه خارج شوید.",color=Color(0xFFE07B68),fontSize=12.sp,textAlign=TextAlign.Center)
   }}
   ,confirmButton={}
  )
 }
}

@Composable private fun Background(content:@Composable BoxScope.()->Unit){
 Box(Modifier.fillMaxSize()){
  Image(painterResource(R.drawable.home_background),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  Box(Modifier.fillMaxSize().background(Color.Black.copy(.56f)))
  content()
 }
}
@Composable private fun Header(title:String,sub:String,onBack:()->Unit){
 Row(verticalAlignment=Alignment.CenterVertically){
  TextButton(onBack,contentPadding=PaddingValues(0.dp)){Text("‹",color=Gold,fontSize=42.sp)}
  Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(sub,color=Gold,fontSize=13.sp)}
  Spacer(Modifier.width(42.dp))
 }
}
@Composable private fun GrayCard(content:@Composable ColumnScope.()->Unit){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=GrayCard,contentColor=Color.Black)){
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
 }
}
@Composable private fun DarkOutlinedField(value:String,onValue:(String)->Unit,label:String,icon:String,type:KeyboardType){
 OutlinedTextField(value,onValue,leadingIcon={Text(icon,color=Color.Black)},label={Text(label,color=Color.Black)},keyboardOptions=KeyboardOptions(keyboardType=type),
  textStyle=LocalTextStyle.current.copy(color=Color.Black,fontSize=20.sp,fontWeight=FontWeight.Bold),singleLine=true,
  colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.Black,unfocusedTextColor=Color.Black,focusedContainerColor=GrayField,unfocusedContainerColor=GrayField,focusedBorderColor=Color.DarkGray,unfocusedBorderColor=Color.Gray,cursorColor=Color.Black),
  modifier=Modifier.fillMaxWidth())
}
@Composable private fun PinField(value:String,onValue:(String)->Unit,label:String){
 OutlinedTextField(value,onValue,label={Text(label,color=Color.Black)},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),
  textStyle=LocalTextStyle.current.copy(color=Color.Black,fontSize=20.sp,fontWeight=FontWeight.Bold),singleLine=true,
  colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.Black,unfocusedTextColor=Color.Black,focusedContainerColor=GrayField,unfocusedContainerColor=GrayField,focusedBorderColor=Color.DarkGray,unfocusedBorderColor=Color.Gray,cursorColor=Color.Black),
  modifier=Modifier.fillMaxWidth())
}
@Composable private fun MoveChip(icon:String,label:String,selected:Boolean,onClick:()->Unit){
 FilterChip(selected,onClick,label={Text("$icon  $label",fontWeight=FontWeight.Bold)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Blood,selectedLabelColor=Color.White,containerColor=Color(0xFFE2E0DB),labelColor=Color.Black),modifier=Modifier.weight(1f))
}
@Composable private fun RedButton(text:String,enabled:Boolean,onClick:()->Unit){
 Button(onClick,enabled,Modifier.fillMaxWidth().height(62.dp).border(1.dp,Gold,RoundedCornerShape(9.dp)),shape=RoundedCornerShape(9.dp),
  colors=ButtonDefaults.buttonColors(containerColor=Blood,disabledContainerColor=Color(0xFF551515),contentColor=Color.White,disabledContentColor=Color.Gray)){Text(text,fontSize=20.sp,fontWeight=FontWeight.Bold)}
}
