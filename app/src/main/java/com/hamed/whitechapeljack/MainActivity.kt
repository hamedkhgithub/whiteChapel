package com.hamed.whitechapeljack

import androidx.compose.ui.text.style.TextDirection

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.sqrt
import kotlin.math.roundToInt

private val Gold = Color(0xFFD6AD63)
private val Blood = Color(0xFFB41616)
private val Ink = Color(0xFF090807)
private val GrayCard = Color(0xFFD0D0CE)
private val GrayField = Color(0xFFE1E0DC)
private val DarkButton = Color(0xE91A1510)
private val Green = Color(0xFF075D2D)
private val Blue = Color(0xFF155B87)
private val AlleyRed = Color(0xFFD32626)
private const val BOARD_ASPECT_RATIO = 3f / 2f

enum class MoveType { NORMAL, COACH, ALLEY }
enum class InquiryType { SEARCH, ARREST }

data class JackMove(val night: Int, val turn: Int, val first: Int, val second: Int?, val type: MoveType)
data class Inquiry(val night: Int, val index: Int, val house: Int, val type: InquiryType, val positive: Boolean)
data class BoardPoint(val id: Int, val x: Int, val y: Int, val normX: Float, val normY: Float, val number: Int)

class GameStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("whitechapel_near_final", Context.MODE_PRIVATE)
    fun exists() = p.contains("game")
    fun clear() = p.edit().clear().apply()

    fun save(
        pinHash: String,
        hideout: Int,
        night: Int,
        starts: Map<Int, Int>,
        moves: List<JackMove>,
        queries: List<Inquiry>,
        escaped: Set<Int>,
        police: Map<Int, List<Int>>,
        gameOver: Boolean
    ) {
        val o = JSONObject()
            .put("pin", pinHash)
            .put("hideout", hideout)
            .put("night", night)
            .put("gameOver", gameOver)

        val s = JSONObject()
        starts.forEach { s.put(it.key.toString(), it.value) }
        o.put("starts", s)

        val ma = JSONArray()
        moves.forEach {
            ma.put(JSONObject().put("n", it.night).put("t", it.turn).put("a", it.first).put("b", it.second).put("y", it.type.name))
        }
        o.put("moves", ma)

        val qa = JSONArray()
        queries.forEach {
            qa.put(JSONObject().put("n", it.night).put("i", it.index).put("h", it.house).put("t", it.type.name).put("p", it.positive))
        }
        o.put("queries", qa)

        val ea = JSONArray()
        escaped.sorted().forEach { ea.put(it) }
        o.put("escaped", ea)

        val policeObj = JSONObject()
        police.toSortedMap().forEach { (nightKey, values) ->
            val arr = JSONArray()
            values.distinct().sorted().forEach(arr::put)
            policeObj.put(nightKey.toString(), arr)
        }
        o.put("police", policeObj)

        p.edit().putString("game", o.toString()).apply()
    }

    fun load(): JSONObject? = p.getString("game", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    companion object {
        fun hash(v: String) = MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

class MainActivity : ComponentActivity() {
    private var tvServer: TvMapServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        tvServer = TvMapServer(this).also { it.start() }
        MapStateStore.publish(this)
setContent {
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(
            textDirection = TextDirection.Rtl,
            textAlign = TextAlign.Right
        )
    ) {
        WhitechapelApp(GameStore(this))
    }
}
    }

    override fun onDestroy() {
        tvServer?.stop()
        super.onDestroy()
    }
}

@Composable
fun WhitechapelApp(store: GameStore) {
    val appContext = LocalContext.current
    var page by remember { mutableStateOf("splash") }
    var pinHash by remember { mutableStateOf("") }
    var hideout by remember { mutableIntStateOf(0) }
    var night by remember { mutableIntStateOf(1) }
    var gameOver by remember { mutableStateOf(false) }
    var appearanceRevision by remember { mutableIntStateOf(0) }
    val appearance = remember(appearanceRevision) { AppearanceStore.load(appContext) }

    val starts = remember { mutableStateMapOf<Int, Int>() }
    val moves = remember { mutableStateListOf<JackMove>() }
    val queries = remember { mutableStateListOf<Inquiry>() }
    val escaped = remember { mutableStateListOf<Int>() }
    val police = remember { mutableStateMapOf<Int, List<Int>>() }

    fun save() {
        store.save(pinHash, hideout, night, starts, moves, queries, escaped.toSet(), police.toMap(), gameOver)
    }

    fun load(): Boolean {
        val o = store.load() ?: return false
        pinHash = o.getString("pin")
        hideout = o.getInt("hideout")
        night = o.getInt("night")
        gameOver = o.optBoolean("gameOver", false)

        starts.clear()
        moves.clear()
        queries.clear()
        escaped.clear()
        police.clear()

        val s = o.optJSONObject("starts") ?: JSONObject()
        s.keys().forEach { starts[it.toInt()] = s.getInt(it) }

        val ma = o.optJSONArray("moves") ?: JSONArray()
        for (i in 0 until ma.length()) {
            val x = ma.getJSONObject(i)
            moves += JackMove(
                x.getInt("n"),
                x.getInt("t"),
                x.getInt("a"),
                if (x.isNull("b")) null else x.getInt("b"),
                MoveType.valueOf(x.getString("y"))
            )
        }

        val qa = o.optJSONArray("queries") ?: JSONArray()
        for (i in 0 until qa.length()) {
            val x = qa.getJSONObject(i)
            queries += Inquiry(
                x.getInt("n"),
                x.getInt("i"),
                x.getInt("h"),
                InquiryType.valueOf(x.getString("t")),
                x.getBoolean("p")
            )
        }

        val ea = o.optJSONArray("escaped") ?: JSONArray()
        for (i in 0 until ea.length()) escaped += ea.getInt(i)

        val policeObj = o.optJSONObject("police") ?: JSONObject()
        policeObj.keys().forEach { key ->
            val arr = policeObj.optJSONArray(key) ?: JSONArray()
            val values = mutableListOf<Int>()
            for (i in 0 until arr.length()) values += arr.getInt(i)
            police[key.toInt()] = values
        }
        return true
    }

    BackHandler(enabled = page != "splash") {
        when (page) {
            "newgame" -> page = "home"
            "jack" -> {
                save()
                page = "detective"
            }
            "detective" -> save()
            "unlock" -> page = "detective"
            "audit" -> page = "home"
            "newmap", "digital", "settings" -> page = "home"
            "home" -> Unit
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, surface = Ink)) {
        when (page) {
            "splash" -> Splash { page = "home" }
            "home" -> Home(
                hasGame = store.exists(),
                hasMapGame = DigitalGameStore.exists(appContext),
                onNewGame = { page = "newgame" },
                onContinue = { if (load()) page = if (gameOver) "audit" else "detective" },
                onNewMapGame = { page = "newmap" },
                onContinueMap = { page = "digital" },
                onSettings = { page = "settings" }
            )
            "settings" -> AppearanceSettingsPage(
                initial = appearance,
                onChanged = { updated -> AppearanceStore.save(appContext, updated); appearanceRevision++ },
                onBack = { page = "home" }
            )
            "digital" -> DigitalGamePage(onBack = { page = "home" })

            "newgame" -> NewGame(onBack = { page = "home" }) { h, p ->
                hideout = h
                pinHash = GameStore.hash(p)
                night = 1
                gameOver = false
                starts.clear()
                moves.clear()
                queries.clear()
                escaped.clear()
                police.clear()
                save()
                page = "jack"
            }

            "newmap" -> NewGame(onBack = { page = "home" }) { h, p ->
                DigitalGameStore.newGame(appContext, h, GameStore.hash(p))
                page = "digital"
            }

            "jack" -> JackPage(
                night = night,
                hideout = hideout,
                start = starts[night],
                nightMoves = moves.filter { it.night == night },
                onSetStart = { first, second ->
                    if (night == 3) {
                        starts[30] = first
                        starts[3] = second!!
                    } else starts[night] = first
                    save()
                },
                onMove = { m -> moves += m; save() },
                onUndoLast = { turn ->
                    val last = moves.lastOrNull { it.night == night }
                    if (last != null && last.turn == turn) moves.remove(last)
                    save()
                },
                onPass = { save(); page = "detective" },
                onGameLost = { gameOver = true; save(); page = "audit" },
                onEscape = {
                    if (!escaped.contains(night)) escaped += night
                    if (night == 4) {
                        gameOver = true; save(); page = "audit"
                    } else {
                        night++
                        save()
                        page = "detective"
                    }
                }
            )

            "detective" -> DetectivePage(
                night = night,
                allMoves = moves,
                starts = starts,
                history = queries.filter { it.night == night },
                selectedPolice = police[night].orEmpty(),
                onPoliceChange = { police[night] = it; save() },
                onInquiry = { q -> queries += q; save() },
                onGameLost = { gameOver = true; save(); page = "audit" },
                onJackTurn = { page = "unlock" },
                escapedPrevious = escaped.contains(night - 1) && starts[night] == null
            )

            "unlock" -> UnlockPage(pinHash, onSuccess = { page = "jack" }, onCancel = { page = "detective" })
            "audit" -> AuditPage(hideout, starts, moves, queries, escaped.toSet(), police.toMap()) { store.clear(); page = "home" }
        }
    }
}

@Composable
private fun Splash(onDone: () -> Unit) {
    var go by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (go) 1f else 0f, tween(5000), label = "load")
    LaunchedEffect(Unit) { go = true; delay(5100); onDone() }
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.splash), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 44.dp, vertical = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(.72f)).border(1.dp, Gold, RoundedCornerShape(20.dp)).padding(2.dp)
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF650B0B), Color.Red)))
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Initializing…", color = Gold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Home(
    hasGame: Boolean,
    hasMapGame: Boolean,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onNewMapGame: () -> Unit,
    onContinueMap: () -> Unit,
    onSettings: () -> Unit
) {
    var pendingNewGame by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.home_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(.12f)))
        Column(Modifier.fillMaxSize().padding(horizontal = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(.20f))
            if (TvServerInfo.url.isNotBlank()) {
                Text("نمایش عمومی: ${TvServerInfo.url}", color = Gold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            MenuButton("▶", "شروع بازی جدید", "کلاسیک • نیازمند صفحه بازی", true) {
                if (hasGame) pendingNewGame = "classic" else onNewGame()
            }
            Spacer(Modifier.height(10.dp))
            MenuButton("▰", "ادامه بازی کلاسیک", "ادامه بازی کلاسیک", hasGame, onContinue)
            Spacer(Modifier.height(10.dp))
            MenuButton("⌖", "شروع بازی جدید با نقشه", "نقشه دیجیتال • بدون صفحه بازی", true) {
                if (hasMapGame) pendingNewGame = "digital" else onNewMapGame()
            }
            Spacer(Modifier.height(10.dp))
            MenuButton("◉", "ادامه بازی با نقشه", "ادامه بازی با نقشه دیجیتال", hasMapGame, onContinueMap)
            Spacer(Modifier.height(10.dp))
            MenuButton("⚙", "تنظیمات", "ظاهر گوشی و نمایش عمومی", true, onSettings)
            Spacer(Modifier.weight(.12f))
        }
    }

    if (pendingNewGame != null) {
        AlertDialog(
            onDismissRequest = { pendingNewGame = null },
            title = { Text("شروع بازی جدید") },
            text = {
                Text("یک بازی ذخیره‌شده در حال حاضر وجود دارد. با شروع بازی جدید، بازی قبلی این حالت دیگر قابل ادامه دادن نخواهد بود. ادامه می‌دهید؟")
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = pendingNewGame
                    pendingNewGame = null
                    if (target == "digital") onNewMapGame() else onNewGame()
                }) { Text("قبول") }
            },
            dismissButton = {
                TextButton(onClick = { pendingNewGame = null }) { Text("لغو") }
            }
        )
    }
}

@Composable
private fun NewGame(onBack: () -> Unit, onStart: (Int, String) -> Unit) {
    var h by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showHideoutPicker by remember { mutableStateOf(false) }
    val hideoutNumber = h.toIntOrNull()
    val redHouseIds = setOf(3, 21, 27, 65, 84, 147, 149, 158)
    val valid = hideoutNumber != null && hideoutNumber in 1..195 && hideoutNumber !in redHouseIds && pin.length in 4..6 && pin == confirm

    Background {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Header("شروع بازی جدید", "تنظیمات بازی جدید", onBack, true)
            GrayCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⌂", fontSize = 43.sp, color = Color.Black)
                    Spacer(Modifier.width(12.dp))
                    Text("انتخاب مخفیگاه", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        DarkField(h, { h = it.filter(Char::isDigit).take(3) }, "شماره مخفیگاه", "⌂", KeyboardType.Number)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { showHideoutPicker = true },
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBB), contentColor = Color.Black)
                    ) { Text("نقشه", fontWeight = FontWeight.Bold) }
                }
                Text("🔒  مخفیگاه برای کل بازی ثابت می‌ماند و نمی‌تواند یکی از خانه‌های قرمز باشد.", color = Color.Black, fontSize = 13.sp)
                if (hideoutNumber != null && hideoutNumber in redHouseIds) Text("خانه قرمز برای مخفیگاه مجاز نیست.", color = Color(0xFF8D0000), fontSize = 12.sp)
            }
            GrayCard {
                Text("🔐  تعیین PIN جک", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                PinField(pin, { pin = it.filter(Char::isDigit).take(6) }, "PIN")
                PinField(confirm, { confirm = it.filter(Char::isDigit).take(6) }, "تکرار PIN")
                if (confirm.isNotEmpty() && pin != confirm) Text("PINها یکسان نیستند.", color = Color(0xFF8D0000), fontSize = 12.sp)
            }
            RedButton("▶   شروع بازی", valid) { onStart(h.toInt(), pin) }
        }
    }

    if (showHideoutPicker) {
        HousePickerDialog(
            title = "انتخاب مخفیگاه روی نقشه",
            currentSelection = h.toIntOrNull(),
            onDismiss = { showHideoutPicker = false },
            onSelect = { selected -> if(selected !in redHouseIds){h = selected.toString(); showHideoutPicker = false} }
        )
    }
}

@Composable
private fun JackPage(
    night: Int,
    hideout: Int,
    start: Int?,
    nightMoves: List<JackMove>,
    onSetStart: (Int, Int?) -> Unit,
    onMove: (JackMove) -> Unit,
    onUndoLast: (Int) -> Unit,
    onPass: () -> Unit,
    onGameLost: () -> Unit,
    onEscape: () -> Unit
) {
    var startText by remember(night) { mutableStateOf(if (night == 3) "" else (start?.toString() ?: "")) }
    var secondCrime by remember(night) { mutableStateOf("") }
    var type by remember { mutableStateOf(MoveType.NORMAL) }
    var d1 by remember { mutableStateOf("") }
    var d2 by remember { mutableStateOf("") }
    var hideoutPopup by remember { mutableStateOf(false) }
    var hideoutVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var trackLossPopup by remember { mutableStateOf(false) }
    var jackWinPopup by remember { mutableStateOf(false) }
    var undoTarget by remember { mutableStateOf<JackMove?>(null) }
    var moveMadeThisTurn by remember(night) { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showSecondCrimePicker by remember { mutableStateOf(false) }
    var showFirstMovePicker by remember { mutableStateOf(false) }
    var showSecondMovePicker by remember { mutableStateOf(false) }

    val coachMax = listOf(0, 3, 2, 2, 1)[night]
    val alleyMax = listOf(0, 2, 2, 1, 1)[night]
    val coachUsed = nightMoves.count { it.type == MoveType.COACH }
    val alleyUsed = nightMoves.count { it.type == MoveType.ALLEY }
    val coachLeft = coachMax - coachUsed
    val alleyLeft = alleyMax - alleyUsed
    val doubleEventCost = if (night == 3) 1 else 0
    val trackUsed: Int = nightMoves.fold(doubleEventCost) { total, move -> total + if (move.type == MoveType.COACH) 2 else 1 }

    Background {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.fillMaxWidth()) {
                SimpleTitle("حرکت‌های جک", "حرکت‌های جک • شب $night")
                Row(
                    Modifier.align(Alignment.TopEnd).background(Color(0xCC11100E), RoundedCornerShape(8.dp))
                        .border(1.dp, Gold.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(start = 9.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (hideoutVisible) hideout.toString() else "•••", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = { hideoutVisible = !hideoutVisible }, modifier = Modifier.size(34.dp)) {
                        Text(if (hideoutVisible) "◉" else "◎", color = Gold, fontSize = 20.sp)
                    }
                }
            }

            GrayCard {
                Text(if (night == 3) "محل‌های ارتکاب قتل (قتل دوگانه)" else "محل ارتکاب قتل", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (night == 3 && start == null) {
                    Text("ترتیب دو قتل محرمانه است؛ محل دوم، موقعیت شروع فرار جک است.", color = Color.Black, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { DarkField(startText, { startText = it.filter(Char::isDigit).take(3) }, "قتل اول", "⌖", KeyboardType.Number) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showStartPicker = true }, modifier = Modifier.height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBB), contentColor = Color.Black)) { Text("نقشه") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { DarkField(secondCrime, { secondCrime = it.filter(Char::isDigit).take(3) }, "قتل دوم / شروع فرار", "⌖", KeyboardType.Number) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showSecondCrimePicker = true }, modifier = Modifier.height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBB), contentColor = Color.Black)) { Text("نقشه") }
                    }
                    Button(
                        onClick = {
                            val a = startText.toIntOrNull(); val b = secondCrime.toIntOrNull()
                            if (a != null && b != null && a in 1..195 && b in 1..195 && a != b) onSetStart(a, b)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBB), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("ثبت دو محل قتل", color = Color.Black, fontWeight = FontWeight.Bold) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            DarkField(
                                if (night == 3 && start != null) start.toString() else startText,
                                { if (start == null) startText = it.filter(Char::isDigit).take(3) },
                                "شماره خانه",
                                "⌖",
                                KeyboardType.Number
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showStartPicker = true }, modifier = Modifier.height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBB), contentColor = Color.Black)) { Text("نقشه") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { startText.toIntOrNull()?.takeIf { it in 1..195 }?.let { onSetStart(it, null) } },
                            enabled = start == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFBDBDBB),
                                contentColor = Color.Black,
                                disabledContainerColor = Color(0xFFBDBDBB),
                                disabledContentColor = Color.Black
                            ),
                            modifier = Modifier.height(56.dp)
                        ) { Text(if (start == null) "ثبت" else "ثبت شد", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (start != null) {
                GrayCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("نوع حرکت", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("شمارنده حرکت: $trackUsed / 15", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        MoveChip("🚶", "عادی", "", type == MoveType.NORMAL, !moveMadeThisTurn, Color.Black) { type = MoveType.NORMAL }
                        MoveChip("♞", "درشکه", "$coachLeft", type == MoveType.COACH, coachLeft > 0 && !moveMadeThisTurn, Color.Black) { type = MoveType.COACH }
                        MoveChip("↯", "کوچه", "$alleyLeft", type == MoveType.ALLEY, alleyLeft > 0 && !moveMadeThisTurn, AlleyRed) { type = MoveType.ALLEY }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            DarkField(d1, { if (!moveMadeThisTurn) d1 = it.filter(Char::isDigit).take(3) }, if (type == MoveType.COACH) "مقصد اول درشکه" else "خانه مقصد", "⌖", KeyboardType.Number)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showFirstMovePicker = true }, enabled = !moveMadeThisTurn, modifier = Modifier.height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBB), contentColor = Color.Black)) { Text("نقشه") }
                    }
                    if (type == MoveType.COACH) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { DarkField(d2, { if (!moveMadeThisTurn) d2 = it.filter(Char::isDigit).take(3) }, "مقصد دوم درشکه", "⌖", KeyboardType.Number) }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { showSecondMovePicker = true }, enabled = !moveMadeThisTurn, modifier = Modifier.height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBB), contentColor = Color.Black)) { Text("نقشه") }
                        }
                    }
                    if (error.isNotEmpty()) Text(error, color = Color(0xFF8D0000), fontSize = 12.sp)
                    Button(onClick = {
                        val a = d1.toIntOrNull(); val b = d2.toIntOrNull(); val cost = if (type == MoveType.COACH) 2 else 1
                        when {
                            moveMadeThisTurn -> error = "حرکت این نوبت ثبت شده است. ابتدا آن را اصلاح کنید یا گوشی را به کارآگاه‌ها تحویل دهید."
                            trackUsed + cost > 15 -> error = "ظرفیت شمارنده حرکت این شب تمام شده است."
                            a == null || a !in 1..195 || (type == MoveType.COACH && (b == null || b !in 1..195)) -> error = "شماره مقصد معتبر نیست."
                            type == MoveType.COACH && coachLeft <= 0 -> error = "درشکه‌های این شب تمام شده‌اند."
                            type == MoveType.ALLEY && alleyLeft <= 0 -> error = "حرکت کوچه این شب تمام شده است."
                            type == MoveType.COACH && (a == b || a == currentLocation(start, nightMoves) || b == currentLocation(start, nightMoves)) -> error = "در حرکت درشکه، دو مقصد و مبدأ باید متفاوت باشند."
                            else -> {
                                val firstDestination = a!!
                                val secondDestination = if (type == MoveType.COACH) b!! else null
                                val m = JackMove(night, nightMoves.size + 1 + if (night == 3) 1 else 0, firstDestination, secondDestination, type)
                                onMove(m)
                                moveMadeThisTurn = true
                                val end = secondDestination ?: firstDestination
                                hideoutPopup = (type == MoveType.NORMAL && end == hideout)
                                if (!hideoutPopup && trackUsed + cost == 15) trackLossPopup = true
                                error = ""
                            }
                        }
                    }, enabled = !moveMadeThisTurn, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Blood, disabledContainerColor = Color(0xFF5C5550), disabledContentColor = Color(0xFFBDB7AF))) {
                        Text(if (moveMadeThisTurn) "حرکت این نوبت ثبت شد" else "ثبت حرکت", fontWeight = FontWeight.Bold)
                    }
                }

                Text("مسیر حرکت‌های شب $night", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(nightMoves) { m ->
                        val editable = moveMadeThisTurn && m == nightMoves.lastOrNull()
                        MoveHistoryCard(m, editable = editable, onCorrect = { if (editable) undoTarget = m })
                    }
                }
                Button(
                    onClick = onPass,
                    enabled = moveMadeThisTurn,
                    modifier = Modifier.fillMaxWidth().height(58.dp).border(1.dp, if (moveMadeThisTurn) Gold else Color.Gray, RoundedCornerShape(9.dp)),
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkButton, contentColor = Gold, disabledContainerColor = Color(0xFF555555), disabledContentColor = Color(0xFFB8B8B8))
                ) {
                    Text(if (moveMadeThisTurn) "🔒   تحویل به کارآگاه‌ها" else "ابتدا حرکت جک را ثبت کنید", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showStartPicker) HousePickerDialog("انتخاب خانه روی نقشه", startText.toIntOrNull(), { showStartPicker = false }) { startText = it.toString(); showStartPicker = false }
    if (showSecondCrimePicker) HousePickerDialog("انتخاب قتل دوم / شروع فرار", secondCrime.toIntOrNull(), { showSecondCrimePicker = false }) { secondCrime = it.toString(); showSecondCrimePicker = false }
    if (showFirstMovePicker) HousePickerDialog("انتخاب مقصد روی نقشه", d1.toIntOrNull(), { showFirstMovePicker = false }) { d1 = it.toString(); showFirstMovePicker = false }
    if (showSecondMovePicker) HousePickerDialog("انتخاب مقصد دوم روی نقشه", d2.toIntOrNull(), { showSecondMovePicker = false }) { d2 = it.toString(); showSecondMovePicker = false }

    undoTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            containerColor = Color(0xFF11100E),
            title = { Text("اصلاح حرکت", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("فقط آخرین حرکتِ همین نوبت حذف می‌شود و جک می‌تواند همان نوبت را دوباره ثبت کند. حرکت‌های قبلی شب قابل ویرایش نیستند.", color = Color(0xFFF2DFC0)) },
            confirmButton = {
                Button(onClick = {
                    onUndoLast(target.turn)
                    moveMadeThisTurn = false
                    d1 = ""
                    d2 = ""
                    error = ""
                    hideoutPopup = false
                    undoTarget = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Blood)) {
                    Text("حذف و اصلاح", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { undoTarget = null }) { Text("انصراف", color = Gold) } }
        )
    }

    if (hideoutPopup) HideoutDialog(
        onEscape = { hideoutPopup = false; if (night == 4) jackWinPopup = true else onEscape() },
        onContinue = { hideoutPopup = false; onPass() }
    )
    if (trackLossPopup) GameResultDialog(
        title = "جک به مخفیگاه نرسید",
        message = "تعداد حرکت‌های مجاز این شب تمام شد و جک به مخفیگاه نرسید. کارآگاه‌ها برنده شدند.",
        buttonText = "مشاهده نتیجه بازی",
        onConfirm = { trackLossPopup = false; onGameLost() }
    )
    if (jackWinPopup) GameResultDialog(
        title = "جک برنده شد",
        message = "جک در شب چهارم به مخفیگاه رسید. جک برنده بازی شد.",
        buttonText = "مشاهده نتیجه بازی",
        onConfirm = { jackWinPopup = false; onEscape() }
    )
}

private fun currentLocation(start: Int, moves: List<JackMove>): Int = moves.lastOrNull()?.let { it.second ?: it.first } ?: start

@Composable
private fun DetectivePage(
    night: Int,
    allMoves: List<JackMove>,
    starts: Map<Int, Int>,
    history: List<Inquiry>,
    selectedPolice: List<Int>,
    onPoliceChange: (List<Int>) -> Unit,
    onInquiry: (Inquiry) -> Unit,
    onGameLost: () -> Unit,
    onJackTurn: () -> Unit,
    escapedPrevious: Boolean
) {
    var house by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("اطلاعات محرمانه جک نمایش داده نمی‌شود.") }
    var arrestWinPopup by remember { mutableStateOf(false) }
    var showHousePicker by remember { mutableStateOf(false) }
    var showPolicePicker by remember { mutableStateOf(false) }

    val current = allMoves.lastOrNull { it.night == night }?.let { it.second ?: it.first } ?: starts[night]
    fun visited(h: Int): Boolean = starts[night] == h || allMoves.any { it.night == night && (it.first == h || it.second == h) }

    Background {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SimpleTitle("کارآگاه‌ها", "حالت کارآگاه • شب $night")
            if (escapedPrevious) Card(colors = CardDefaults.cardColors(containerColor = Green), modifier = Modifier.fillMaxWidth()) {
                Text("✓ فرار شب قبل توسط برنامه تأیید شد.", Modifier.padding(14.dp), color = Color.White, fontWeight = FontWeight.Bold)
            }

            GrayCard {
                Text("محل‌های قابل انتخاب پلیس", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("شماره این محل‌ها روی نقشه نمایش داده نمی‌شود و فقط برای انتخاب موقعیت پلیس استفاده می‌گردد.", color = Color.Black, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { showPolicePicker = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBB), contentColor = Color.Black), modifier = Modifier.height(52.dp)) {
                        Text("انتخاب از نقشه", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${selectedPolice.size} موقعیت انتخاب شده", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                if (selectedPolice.isNotEmpty()) {
                    Text("شماره‌های انتخاب‌شده: ${selectedPolice.sorted().joinToString("، ")}", color = Color.Black, fontSize = 13.sp)
                }
            }

            GrayCard {
                Text("استعلام خانه", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        DarkField(house, { house = it.filter(Char::isDigit).take(3) }, "شماره خانه", "⌕", KeyboardType.Number)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { showHousePicker = true }, modifier = Modifier.height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBB), contentColor = Color.Black)) {
                        Text("نقشه", fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val h = house.toIntOrNull()
                        if (h != null && h in 1..195) {
                            val ok = visited(h)
                            val q = Inquiry(night, history.size + 1, h, InquiryType.SEARCH, ok)
                            onInquiry(q)
                            result = if (ok) "✓ سرنخ در خانه $h پیدا شد." else "✗ در خانه $h سرنخی نیست."
                            house = ""
                        }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White)) {
                        Text("⌕ جستجوی سرنخ", fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = {
                        val h = house.toIntOrNull()
                        if (h != null && h in 1..195) {
                            val ok = current == h
                            val q = Inquiry(night, history.size + 1, h, InquiryType.ARREST, ok)
                            onInquiry(q)
                            result = if (ok) "✓ جک در خانه $h دستگیر شد." else "✗ دستگیری در خانه $h ناموفق بود."
                            if (ok) arrestWinPopup = true
                            house = ""
                        }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Blood, contentColor = Color.White)) {
                        Text("⛓ دستگیری", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF22201D)), modifier = Modifier.fillMaxWidth()) {
                Text(result, Modifier.padding(15.dp), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text("تاریخچه استعلام‌های شب $night", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(history) { q ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC17130F)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp)) {
                            Text(if (q.type == InquiryType.SEARCH) "⌕ جستجو" else "⛓ دستگیری", color = if (q.type == InquiryType.SEARCH) Color(0xFF65B6E8) else Color(0xFFFF7777))
                            Spacer(Modifier.weight(1f))
                            Text("خانه ${q.house} • ${if (q.positive) "✓ مثبت" else "✗ منفی"}", color = Color.White)
                        }
                    }
                }
            }
            Button(onClick = onJackTurn, modifier = Modifier.fillMaxWidth().height(58.dp).border(1.dp, Gold, RoundedCornerShape(9.dp)), shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.buttonColors(containerColor = DarkButton, contentColor = Gold)) {
                Text("🔐   نوبت جک", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }

    if (showHousePicker) HousePickerDialog("انتخاب خانه روی نقشه", house.toIntOrNull(), { showHousePicker = false }) { house = it.toString(); showHousePicker = false }
    if (showPolicePicker) PolicePickerDialog(selectedPolice, onDismiss = { showPolicePicker = false }, onConfirm = {
        onPoliceChange(it)
        showPolicePicker = false
    })

    if (arrestWinPopup) GameResultDialog(
        title = "جک دستگیر شد",
        message = "جک دستگیر شد و کارآگاه‌ها بازی را بردند.",
        buttonText = "مشاهده نتیجه بازی",
        onConfirm = { arrestWinPopup = false; onGameLost() }
    )
}

@Composable
private fun UnlockPage(pinHash: String, onSuccess: () -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    BackHandler { onCancel() }
    Background {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            GrayCard {
                Text("🔐 ورود محرمانه جک", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                PinField(pin, { pin = it.filter(Char::isDigit).take(6) }, "PIN")
                if (error.isNotEmpty()) Text(error, color = Color(0xFF8D0000))
                Button(onClick = { if (GameStore.hash(pin) == pinHash) onSuccess() else error = "PIN اشتباه است." }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Blood)) {
                    Text("باز کردن دفترچه", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AuditPage(
    hideout: Int,
    starts: Map<Int, Int>,
    moves: List<JackMove>,
    queries: List<Inquiry>,
    escaped: Set<Int>,
    police: Map<Int, List<Int>>,
    onClear: () -> Unit
) {
    Background {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SimpleTitle("بررسی نهایی بازی", "بررسی و آشکارسازی نهایی")
            GrayCard { Text("مخفیگاه: $hideout", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
            LazyColumn(Modifier.weight(1f)) {
                for (n in 1..4) {
                    item {
                        val startLabel = if (n == 3) "قتل‌ها: ${starts[30] ?: "-"} → ${starts[3] ?: "-"}" else "شروع: ${starts[n] ?: "-"}"
                        Text("شب $n • $startLabel • ${if (escaped.contains(n)) "✓ فرار تأیید شده" else "—"}", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        val policeLabel = police[n].orEmpty().sorted().joinToString("، ").ifEmpty { "—" }
                        Text("موقعیت پلیس‌ها: $policeLabel", color = Color.LightGray, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    items(moves.filter { it.night == n }) { m -> Text("حرکت ${m.turn}: ${m.first}${m.second?.let { " → $it" } ?: ""} • ${when(m.type){MoveType.NORMAL->"عادی";MoveType.COACH->"درشکه";MoveType.ALLEY->"کوچه"}}", color = Color.White) }
                    items(queries.filter { it.night == n }) { q -> Text("${if(q.type==InquiryType.SEARCH) "جستجو" else "دستگیری"} ${q.house}: ${if (q.positive) "✓" else "✗"}", color = Color.LightGray) }
                }
            }
            RedButton("پایان و پاک کردن بازی", true, onClear)
        }
    }
}

@Composable
private fun GameResultDialog(title: String, message: String, buttonText: String, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF11100E),
        title = { Text(title, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 22.sp) },
        text = { Text(message, color = Color(0xFFF2DFC0), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 16.sp) },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Blood)) {
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun HideoutDialog(onEscape: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF11100E),
        title = { Text("شما در مخفیگاه هستید", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⌂", fontSize = 66.sp, color = Gold)
                Text("شما با حرکت عادی به مخفیگاه رسیده‌اید.", color = Color(0xFFF2DFC0), textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onEscape, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(8.dp)) {
                    Text("⚑  اعلام فرار و پایان شب", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp).border(1.dp, Gold, RoundedCornerShape(8.dp)), colors = ButtonDefaults.buttonColors(containerColor = DarkButton), shape = RoundedCornerShape(8.dp)) {
                    Text("→  اعلام نکن — ادامه بازی", color = Gold, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text("اگر اعلام نکنید، در نوبت بعد باید از مخفیگاه خارج شوید.", color = Color(0xFFE07B68), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun MoveHistoryCard(m: JackMove, editable: Boolean, onCorrect: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC17130F)), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when (m.type) { MoveType.NORMAL -> "🚶"; MoveType.COACH -> "♞"; MoveType.ALLEY -> "↯" }, fontSize = 22.sp, color = if (m.type == MoveType.ALLEY) AlleyRed else Color.White)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("حرکت ${m.turn}", color = Gold, fontWeight = FontWeight.Bold)
                Text(if (m.second != null) "${m.first} → ${m.second}" else "${m.first}", color = Color.White, fontSize = 16.sp)
            }
            if (editable) {
                TextButton(onClick = onCorrect, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFC85A))) { Text("✎ اصلاح", fontWeight = FontWeight.Bold) }
            } else {
                Text("🔒", color = Color.Gray, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RowScope.MoveChip(icon: String, label: String, count: String, selected: Boolean, enabled: Boolean, iconColor: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, color = iconColor, fontSize = 18.sp)
                Spacer(Modifier.width(3.dp))
                Text(label, fontWeight = FontWeight.Bold)
                if (count.isNotEmpty()) Text(" ($count)", fontSize = 11.sp)
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Blood,
            selectedLabelColor = Color.White,
            containerColor = Color(0xFFE2E0DB),
            labelColor = Color.Black,
            disabledContainerColor = Color(0xFF999999),
            disabledLabelColor = Color.DarkGray
        ),
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun Background(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.home_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(.58f)))
        content()
    }
}

@Composable
private fun Header(title: String, sub: String, onBack: () -> Unit, showBack: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹", color = Gold, fontSize = 42.sp) } else Spacer(Modifier.width(42.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Gold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(42.dp))
    }
}

@Composable private fun SimpleTitle(title: String, sub: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Gold, fontSize = 13.sp)
    }
}

@Composable private fun GrayCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = GrayCard, contentColor = Color.Black)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun DarkField(value: String, onValue: (String) -> Unit, label: String, icon: String, type: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        leadingIcon = { Text(icon, color = Color.Black) },
        label = { Text(label, color = Color.Black) },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        textStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedContainerColor = GrayField,
            unfocusedContainerColor = GrayField,
            focusedBorderColor = Color.DarkGray,
            unfocusedBorderColor = Color.Gray,
            cursorColor = Color.Black
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PinField(value: String, onValue: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, color = Color.Black) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedContainerColor = GrayField,
            unfocusedContainerColor = GrayField,
            focusedBorderColor = Color.DarkGray,
            unfocusedBorderColor = Color.Gray,
            cursorColor = Color.Black
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MenuButton(icon: String, fa: String, en: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(70.dp).border(1.dp, Gold, RoundedCornerShape(9.dp)),
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DarkButton, contentColor = Gold, disabledContainerColor = Color(0xD915120F), disabledContentColor = Gold.copy(.7f)),
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
private fun RedButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(62.dp).border(1.dp, Gold, RoundedCornerShape(9.dp)),
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Blood, disabledContainerColor = Color(0xFF551515), contentColor = Color.White, disabledContentColor = Color.Gray)
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

private fun loadBoardPoints(ctx: Context, fileName: String): List<BoardPoint> {
    val text = ctx.assets.open(fileName).bufferedReader().use { it.readText() }
    val arr = JSONArray(text)
    val result = mutableListOf<BoardPoint>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        result += BoardPoint(
            id = o.optInt("id", i + 1),
            x = o.getInt("x"),
            y = o.getInt("y"),
            normX = o.getDouble("norm_x").toFloat(),
            normY = o.getDouble("norm_y").toFloat(),
            number = o.getInt("number")
        )
    }
    return result
}

@Composable
private fun rememberBoardPoints(fileName: String): List<BoardPoint> {
    val context = LocalContext.current
    return remember(fileName) { loadBoardPoints(context, fileName) }
}

private fun nearestPoint(points: List<BoardPoint>, tap: Offset, width: Float, height: Float, thresholdPx: Float): BoardPoint? {
    var best: BoardPoint? = null
    var bestDistance = Float.MAX_VALUE
    for (p in points) {
        val px = p.normX * width
        val py = p.normY * height
        val dx = tap.x - px
        val dy = tap.y - py
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < bestDistance) {
            bestDistance = dist
            best = p
        }
    }
    return if (bestDistance <= thresholdPx) best else null
}

@Composable
private fun HousePickerDialog(
    title: String,
    currentSelection: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val points = rememberBoardPoints("houses.json")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color(0xFF11100E), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("با لمس خانه موردنظر روی نقشه، شماره همان خانه انتخاب می‌شود.", color = Color(0xFFF2DFC0), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                BoardMap(
                    showNumberOverlay = true,
                    selectedNumbers = currentSelection?.let { setOf(it) } ?: emptySet(),
                    onTap = { point -> onSelect(point.number) },
                    points = points,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = DarkButton, contentColor = Gold)) {
                    Text("بستن", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PolicePickerDialog(
    selectedPolice: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit
) {
    val points = rememberBoardPoints("polises.json")
    val localSelection = remember(selectedPolice) { selectedPolice.toMutableStateList() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color(0xFF11100E), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("انتخاب موقعیت پلیس‌ها", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("شماره‌های پلیس روی نقشه نمایش داده نمی‌شود. با لمس موقعیت‌ها آن‌ها را انتخاب یا لغو انتخاب کنید.", color = Color(0xFFF2DFC0), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                BoardMap(
                    showNumberOverlay = false,
                    selectedNumbers = localSelection.toSet(),
                    onTap = { point ->
                        if (localSelection.contains(point.number)) localSelection.remove(point.number) else localSelection.add(point.number)
                    },
                    points = points,
                    selectionColor = Blue,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (localSelection.isEmpty()) "هیچ موقعیتی انتخاب نشده است."
                    else "انتخاب شده: ${localSelection.sorted().joinToString("، ")}",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { localSelection.clear() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF666666))) {
                        Text("پاک کردن")
                    }
                    Button(onClick = { onConfirm(localSelection.distinct().sorted()) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                        Text("تأیید", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("انصراف") }
            }
        }
    }
}

@Composable
private fun BoardMap(
    points: List<BoardPoint>,
    selectedNumbers: Set<Int>,
    showNumberOverlay: Boolean,
    onTap: (BoardPoint) -> Unit,
    selectionColor: Color = Gold,
    modifier: Modifier = Modifier
) {
    ZoomableFeatureMap(
        points = points,
        showHouseNumbers = showNumberOverlay,
        selected = selectedNumbers,
        selectedColor = selectionColor,
        onTap = onTap,
        modifier = modifier
    )
}

/**
 * Full-area, aspect-correct map. The original PNG is decoded at its native resolution and
 * redrawn from that source for every zoom level with high-quality filtering; it is never
 * stretched to the phone's aspect ratio and is not raster-scaled inside a graphicsLayer.
 */
@Composable
private fun ZoomableFeatureMap(
    points: List<BoardPoint>,
    showHouseNumbers: Boolean,
    selected: Set<Int>,
    selectedColor: Color,
    onTap: (BoardPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baseBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.whitechapel_board_base).asImageBitmap()
    }
    val houseNumberPoints = rememberHouseNumberPoints()
    val phoneAppearance = remember { AppearanceStore.load(context).phone }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, 6f)
                    val ratio = newScale / oldScale
                    // Keep the point under the fingers stable while zooming and also apply pan.
                    offset = centroid + pan - (centroid - offset) * ratio
                    scale = newScale
                }
            }
            .pointerInput(points, scale, offset) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    onTap = { raw ->
                        val viewportW = size.width.toFloat()
                        val viewportH = size.height.toFloat()
                        val boardAspect = BOARD_ASPECT_RATIO
                        val boardW: Float
                        val boardH: Float
                        val boardLeft: Float
                        val boardTop: Float
                        if (viewportW / viewportH > boardAspect) {
                            boardH = viewportH
                            boardW = boardH * boardAspect
                            boardLeft = (viewportW - boardW) / 2f
                            boardTop = 0f
                        } else {
                            boardW = viewportW
                            boardH = boardW / boardAspect
                            boardLeft = 0f
                            boardTop = (viewportH - boardH) / 2f
                        }

                        val unscaled = (raw - offset) / scale
                        val mapX = unscaled.x - boardLeft
                        val mapY = unscaled.y - boardTop
                        if (mapX !in 0f..boardW || mapY !in 0f..boardH) return@detectTapGestures

                        var nearest: BoardPoint? = null
                        var bestDistance = Float.MAX_VALUE
                        points.forEach { point ->
                            val dx = mapX - point.normX * boardW
                            val dy = mapY - point.normY * boardH
                            val distance = sqrt(dx * dx + dy * dy)
                            if (distance < bestDistance) {
                                bestDistance = distance
                                nearest = point
                            }
                        }
                        if (bestDistance <= 40f / scale) nearest?.let(onTap)
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val viewportW = size.width
            val viewportH = size.height
            val boardAspect = BOARD_ASPECT_RATIO
            val boardW: Float
            val boardH: Float
            val boardLeft: Float
            val boardTop: Float
            if (viewportW / viewportH > boardAspect) {
                boardH = viewportH
                boardW = boardH * boardAspect
                boardLeft = (viewportW - boardW) / 2f
                boardTop = 0f
            } else {
                boardW = viewportW
                boardH = boardW / boardAspect
                boardLeft = 0f
                boardTop = (viewportH - boardH) / 2f
            }

            val dstLeft = boardLeft * scale + offset.x
            val dstTop = boardTop * scale + offset.y
            val dstW = boardW * scale
            val dstH = boardH * scale
            val dstOffset = IntOffset(dstLeft.roundToInt(), dstTop.roundToInt())
            val dstSize = IntSize(dstW.roundToInt().coerceAtLeast(1), dstH.roundToInt().coerceAtLeast(1))

            drawImage(
                image = baseBitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(baseBitmap.width, baseBitmap.height),
                dstOffset = dstOffset,
                dstSize = dstSize,
                filterQuality = FilterQuality.High
            )
            if (showHouseNumbers) {
                drawHouseNumberBadges(
                    houses = houseNumberPoints,
                    boardLeft = boardLeft,
                    boardTop = boardTop,
                    boardWidth = boardW,
                    boardHeight = boardH,
                    scale = scale,
                    offset = offset,
                    numberScale = phoneAppearance.houseNumberScale
                )
            }

            // Larger hollow marker so the underlying house number stays readable.
            val markerRadius = boardH * 0.030f * scale
            val markerStroke = (boardH * 0.0065f * scale).coerceAtLeast(2f)
            selected.forEach { number ->
                points.firstOrNull { it.number == number }?.let { point ->
                    val center = Offset(
                        (boardLeft + point.normX * boardW) * scale + offset.x,
                        (boardTop + point.normY * boardH) * scale + offset.y
                    )
                    drawCircle(
                        color = selectedColor,
                        radius = markerRadius,
                        center = center,
                        style = Stroke(width = markerStroke)
                    )
                }
            }
        }
    }
}

