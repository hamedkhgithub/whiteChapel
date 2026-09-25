package com.hamed.whitechapeljack
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

data class Move(val night:Int,val turn:Int,val location:Int,val type:String)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE)
  setContent{App()}
 }
}
@Composable fun App(){
 var screen by remember{mutableStateOf("setup")}
 var pin by remember{mutableStateOf("")}; var secretPin by remember{mutableStateOf("")}
 var hideout by remember{mutableStateOf("")}; var night by remember{mutableIntStateOf(1)}
 var location by remember{mutableStateOf("")}; var type by remember{mutableStateOf("Normal")}
 var result by remember{mutableStateOf("")}; val moves=remember{mutableStateListOf<Move>()}
 MaterialTheme(colorScheme=darkColorScheme()){Surface(Modifier.fillMaxSize()){
  Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("Whitechapel Jack",style=MaterialTheme.typography.headlineMedium)
   when(screen){
    "setup"->{ Text("بازی جدید")
     OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(6)},label={Text("PIN جک")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
     OutlinedTextField(hideout,{hideout=it.filter(Char::isDigit).take(3)},label={Text("مخفیگاه 1–199")})
     Button({if(pin.length>=4&&hideout.toIntOrNull() in 1..199){secretPin=pin;pin="";screen="jack"}}){Text("شروع")}
    }
    "jack"->{ Text("Jack • شب $night • مخفیگاه $hideout")
     OutlinedTextField(location,{location=it.filter(Char::isDigit).take(3)},label={Text("خانه مقصد")})
     Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Normal","Coach","Alley").forEach{t->FilterChip(type==t,{type=t},label={Text(t)})}}
     Text("اعتبارسنجی حرکت: غیرفعال")
     Button({val n=location.toIntOrNull();if(n in 1..199){moves+=Move(night,moves.count{it.night==night}+1,n!!,type);location=""}}){Text("ثبت حرکت")}
     LazyColumn(Modifier.weight(1f)){items(moves.filter{it.night==night}.reversed()){Text("Turn ${it.turn}: ${it.location} • ${it.type}")}}
     Button({screen="detective"}){Text("قفل و تحویل به کارآگاه‌ها")}
     if(night<4) OutlinedButton({night++}){Text("شب بعد")}
    }
    "detective"->{ Text("Detective Mode • شب $night")
     OutlinedTextField(location,{location=it.filter(Char::isDigit).take(3)},label={Text("شماره خانه")})
     Button({val n=location.toIntOrNull();result=if(n!=null&&moves.any{it.night==night&&it.location==n})"سرنخ پیدا شد: $n" else "سرنخی نیست"}){Text("Search for Clues")}
     Button({val n=location.toIntOrNull();result=if(n!=null&&moves.lastOrNull{it.night==night}?.location==n)"دستگیری موفق" else "دستگیری ناموفق"}){Text("Arrest")}
     Text(result); Spacer(Modifier.weight(1f)); OutlinedButton({screen="unlock"}){Text("ورود جک")}
    }
    "unlock"->{ OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(6)},label={Text("PIN")},visualTransformation=PasswordVisualTransformation())
     Button({if(pin==secretPin){pin="";screen="jack"}else result="PIN اشتباه است"}){Text("باز کردن")}
     Text(result); OutlinedButton({screen="detective"}){Text("بازگشت")}
    }
   }
  }
 }}
}
