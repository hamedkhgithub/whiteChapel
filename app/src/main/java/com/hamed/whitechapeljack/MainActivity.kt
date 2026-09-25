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

private val Gold = Color(0xFFD6AD63)
private val GoldDark = Color(0xFF7A5725)
private val Blood = Color(0xFFB41616)
private val BloodDark = Color(0xFF650B0B)
private val Ink = Color(0xFF090807)
private val Parchment = Color(0xFFE7D1A8)
private val Brown = Color(0xFF352515)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContent { WhitechapelUi() }
    }
}

@Composable
fun WhitechapelUi() {
    var page by remember { mutableStateOf("splash") }
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, surface = Ink)) {
        when (page) {
            "splash" -> Splash { page = "home" }
            "home" -> Home(onNewGame = { page = "newgame" })
            "newgame" -> NewGame(onBack = { page = "home" })
        }
    }
}

@Composable
private fun Splash(onDone: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(5000),
        label = "loading"
    )
    LaunchedEffect(Unit) {
        started = true
        delay(5100)
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 44.dp, vertical = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.fillMaxWidth().height(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha=.72f))
                    .border(1.dp, Gold, RoundedCornerShape(20.dp))
                    .padding(2.dp)
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(progress)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(BloodDark, Color.Red)))
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Preparing the streets of Whitechapel...", color = Gold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Home(onNewGame: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.home_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.12f)))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(.40f))
            MenuButton("▶", "شروع بازی جدید", "New Game", true, onNewGame)
            Spacer(Modifier.height(13.dp))
            MenuButton("▰", "ادامه بازی", "Continue", false) {}
            Spacer(Modifier.height(13.dp))
            MenuButton("▤", "راهنما", "How to Play", false) {}
            Spacer(Modifier.height(13.dp))
            MenuButton("⚙", "تنظیمات", "Settings", false) {}
            Spacer(Modifier.weight(.18f))
        }
    }
}

@Composable
private fun MenuButton(icon:String, fa:String, en:String, enabled:Boolean, onClick:()->Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(70.dp).border(1.dp, Gold, RoundedCornerShape(9.dp)),
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xE91A1510),
            contentColor = Gold,
            disabledContainerColor = Color(0xD915120F),
            disabledContentColor = Gold.copy(alpha=.70f)
        ),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Text(icon, fontSize = 29.sp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(fa, color = Color(0xFFF2DFC0), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(en, color = Gold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NewGame(onBack:()->Unit) {
    var hideout by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = hideout.toIntOrNull() in 1..195 && pin.length in 4..6 && pin == confirm

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071016), Color(0xFF11100D))))) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack, contentPadding = PaddingValues(0.dp)) { Text("‹", color=Color.White, fontSize=42.sp) }
                Column(Modifier.weight(1f), horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("شروع بازی جدید", color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold)
                    Text("New Game Setup", color=Color(0xFFB7A58D), fontSize=14.sp)
                }
                Spacer(Modifier.width(42.dp))
            }

            PaperCard {
                Row(verticalAlignment=Alignment.Top) {
                    Text("⌂", fontSize=46.sp, color=Brown)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("انتخاب مخفیگاه", fontSize=20.sp, fontWeight=FontWeight.Bold, color=Ink)
                        Text("شماره مخفیگاه را وارد کنید\n(۱ تا ۱۹۵)", color=Ink, textAlign=TextAlign.Center)
                    }
                }
                OutlinedTextField(
                    hideout, { hideout=it.filter(Char::isDigit).take(3) },
                    leadingIcon={Text("⌂",fontSize=24.sp)},
                    label={Text("شماره مخفیگاه")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                    modifier=Modifier.fillMaxWidth(),
                    singleLine=true
                )
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("🔒", fontSize=23.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("مخفیگاه برای کل بازی ثابت می‌ماند\nو قابل تغییر نیست.", color=Ink, fontSize=13.sp)
                }
            }

            PaperCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("🔐", fontSize=36.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("تعیین PIN جک", fontSize=20.sp, fontWeight=FontWeight.Bold, color=Ink)
                        Text("یک رمز ۴ تا ۶ رقمی تعیین کنید", color=Ink, fontSize=13.sp)
                    }
                }
                OutlinedTextField(
                    pin,{pin=it.filter(Char::isDigit).take(6)},
                    label={Text("PIN")},
                    visualTransformation=PasswordVisualTransformation(),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),
                    modifier=Modifier.fillMaxWidth(),singleLine=true
                )
                OutlinedTextField(
                    confirm,{confirm=it.filter(Char::isDigit).take(6)},
                    label={Text("تکرار PIN")},
                    visualTransformation=PasswordVisualTransformation(),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),
                    modifier=Modifier.fillMaxWidth(),singleLine=true
                )
                if(confirm.isNotEmpty() && pin != confirm) Text("PINها یکسان نیستند.", color=Blood, fontSize=12.sp)
            }

            Button(
                onClick = {},
                enabled = valid,
                modifier=Modifier.fillMaxWidth().height(62.dp).border(1.dp,Gold,RoundedCornerShape(9.dp)),
                shape=RoundedCornerShape(9.dp),
                colors=ButtonDefaults.buttonColors(
                    containerColor=Blood,
                    disabledContainerColor=Color(0xFF551515),
                    contentColor=Color.White,
                    disabledContentColor=Color.Gray
                )
            ) { Text("▶   شروع بازی", fontSize=20.sp, fontWeight=FontWeight.Bold) }

            Text(
                "در این مرحله فقط رابط کاربری آماده شده و دکمه شروع بازی هنوز وارد منطق بازی نمی‌شود.",
                color=Color(0xFF8E8375),fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PaperCard(content:@Composable ColumnScope.()->Unit) {
    Card(
        modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(12.dp),
        colors=CardDefaults.cardColors(containerColor=Parchment,contentColor=Ink)
    ) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
