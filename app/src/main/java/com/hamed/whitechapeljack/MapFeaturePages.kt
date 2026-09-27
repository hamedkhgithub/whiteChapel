package com.hamed.whitechapeljack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

private val FeatureGold = Color(0xFFD6AD63)
private val FeatureBlue = Color(0xFF155B87)
private val FeatureBlood = Color(0xFFB41616)

private val policeColors = listOf(
    "آبی" to "#1976D2",
    "قرمز" to "#D32F2F",
    "سبز" to "#2E7D32",
    "زرد" to "#F9A825",
    "مشکی" to "#212121"
)

private val victimColors = listOf(
    "قرمز" to "#C62828",
    "بنفش" to "#7B1FA2",
    "نارنجی" to "#EF6C00",
    "سبز" to "#388E3C",
    "آبی" to "#1976D2",
    "صورتی" to "#AD1457",
    "قهوه‌ای" to "#6D4C41",
    "خاکستری" to "#616161"
)

private fun hexColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

@Composable
fun MapTestPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val housePoints = rememberBoardPointsFeature("houses.json")
    val policePoints = rememberBoardPointsFeature("polises.json")
    var revision by remember { mutableIntStateOf(0) }
    var action by remember { mutableStateOf("move") }
    var selectedColor by remember { mutableStateOf(policeColors.first().second) }
    val police = remember(revision) { MapStateStore.testPolice(ctx) }
    val events = remember(revision) { MapStateStore.events(ctx) }
    val actionPoints = if (action == "move") policePoints else housePoints

    Column(Modifier.fillMaxSize().background(Color(0xFF100E0C)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ بازگشت", color = FeatureGold) }
            Text("صفحه تست نقشه و پلیس", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
        TvLinkCard()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionChip("جابجایی پلیس", action == "move") { action = "move" }
            ActionChip("سرنخ", action == "search") { action = "search" }
            ActionChip("دستگیری", action == "arrest") { action = "arrest" }
        }
        if (action == "move") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                policeColors.forEach { (name, hex) ->
                    FilterChip(
                        selected = selectedColor == hex,
                        onClick = { selectedColor = hex },
                        label = { Text(name, fontSize = 11.sp) },
                        leadingIcon = { Box(Modifier.size(13.dp).background(hexColor(hex), RoundedCornerShape(50))) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        ZoomableFeatureMap(
            points = actionPoints,
            showHouseNumbers = true,
            selected = if (action == "move") police.map { it.point }.toSet() else emptySet(),
            selectedColor = FeatureBlue,
            onTap = { p ->
                when (action) {
                    "move" -> MapStateStore.upsertTestPolice(ctx, p.number, selectedColor)
                    "search" -> MapStateStore.addEvent(ctx, p.number, "search")
                    "arrest" -> MapStateStore.addEvent(ctx, p.number, "arrest")
                }
                revision++
            },
            modifier = Modifier.weight(1f)
        )

        if (police.isNotEmpty()) Text("پلیس‌ها: " + police.joinToString("، ") { it.point.toString() }, color = Color.White, fontSize = 12.sp)
        if (events.isNotEmpty()) Text("فعالیت‌ها: " + events.takeLast(8).joinToString(" | ") { "${if (it.type == "search") "S" else "A"}:${it.house}" }, color = Color.LightGray, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { MapStateStore.removeTestPolice(ctx, selectedColor); revision++ }, modifier = Modifier.weight(1f)) { Text("حذف پلیس انتخابی") }
            OutlinedButton(onClick = { MapStateStore.clearEvents(ctx); revision++ }, modifier = Modifier.weight(1f)) { Text("پاک کردن فعالیت‌ها") }
        }
    }
}

@Composable
private fun RowScope.ActionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text, fontWeight = FontWeight.Bold) }, modifier = Modifier.weight(1f))
}

@Composable
fun HellSetupPage(onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val housePoints = rememberBoardPointsFeature("houses.json")
    val policePoints = rememberBoardPointsFeature("polises.json")
    var revision by remember { mutableIntStateOf(0) }
    var side by remember { mutableStateOf("police") }
    var selectedColor by remember { mutableStateOf(policeColors.first().second) }
    var isReal by remember { mutableStateOf(true) }
    val hellPolice = remember(revision) { MapStateStore.hellPolice(ctx) }
    val hellVictims = remember(revision) { MapStateStore.hellVictims(ctx) }
    val reveal = remember(revision) { MapStateStore.hellReveal(ctx) }
    val colors = if (side == "police") policeColors else victimColors
    val current = if (side == "police") hellPolice else hellVictims

    LaunchedEffect(side) {
        selectedColor = if (side == "police") policeColors.first().second else victimColors.first().second
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF100E0C)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ بازگشت", color = FeatureGold) }
            Text("Hell Phase", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
        TvLinkCard()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = side == "police", onClick = { side = "police" }, label = { Text("کارآگاه: پلیس‌ها") }, modifier = Modifier.weight(1f))
            FilterChip(selected = side == "victim", onClick = { side = "victim" }, label = { Text("جک: قربانی‌ها") }, modifier = Modifier.weight(1f))
        }
        Text(
            if (side == "police") "کارآگاه رنگ واقعی پلیس‌ها و Fake بودن را روی گوشی می‌بیند؛ TV فقط جای پلیس را نشان می‌دهد."
            else "جک قربانی‌های واقعی و جعلی را روی گوشی می‌بیند؛ TV تا Reveal فقط جای قربانی‌ها را نشان می‌دهد.",
            color = Color(0xFFF2DFC0), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            colors.take(5).forEach { (name, hex) ->
                FilterChip(
                    selected = selectedColor == hex,
                    onClick = { selectedColor = hex },
                    label = { Text(name, fontSize = 10.sp) },
                    leadingIcon = { Box(Modifier.size(12.dp).background(hexColor(hex), RoundedCornerShape(50))) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = isReal, onClick = { isReal = true }, label = { Text("واقعی") }, modifier = Modifier.weight(1f))
            FilterChip(selected = !isReal, onClick = { isReal = false }, label = { Text("Fake") }, modifier = Modifier.weight(1f))
        }

        ZoomableFeatureMap(
            points = if (side == "police") policePoints else housePoints,
            showHouseNumbers = true,
            selected = current.map { it.point }.toSet(),
            selectedColor = if (side == "police") FeatureBlue else FeatureBlood,
            markerColors = current.associate { it.point to hexColor(it.color) },
            fakePoints = current.filter { !it.real }.map { it.point }.toSet(),
            onTap = { p ->
                val base = current.toMutableList()
                val sameColorIndex = base.indexOfFirst { it.color == selectedColor }
                val token = HellToken(p.number, selectedColor, isReal)
                if (sameColorIndex >= 0) base[sameColorIndex] = token else base.add(token)
                if (side == "police") MapStateStore.setHellPolice(ctx, base) else MapStateStore.setHellVictims(ctx, base)
                revision++
            },
            modifier = Modifier.weight(1f)
        )

        val privateSummary = current.joinToString(" | ") { "${it.point}:${if (it.real) "R" else "F"}" }
        Text(if (privateSummary.isEmpty()) "هنوز چیزی قرار داده نشده است." else "نمای خصوصی گوشی: $privateSummary", color = Color.White, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { MapStateStore.setHellReveal(ctx, !reveal); revision++ }, colors = ButtonDefaults.buttonColors(containerColor = if (reveal) FeatureBlood else Color(0xFF2E7D32)), modifier = Modifier.weight(1f)) {
                Text(if (reveal) "مخفی کردن نتیجه" else "Reveal روی TV", fontWeight = FontWeight.Bold)
            }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("پایان Hell", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TvLinkCard() {
    val url = TvServerInfo.url.ifBlank { "در حال ساخت لینک…" }
    Surface(color = Color(0xFF241E18), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TV / Browser Link", color = FeatureGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(url, color = Color.White, fontSize = 13.sp)
            Text("گوشی و TV باید روی یک شبکه Wi‑Fi باشند.", color = Color.LightGray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun rememberBoardPointsFeature(fileName: String): List<BoardPoint> {
    val ctx = LocalContext.current
    return remember(fileName) {
        val arr = org.json.JSONArray(ctx.assets.open(fileName).bufferedReader().use { it.readText() })
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            BoardPoint(o.optInt("id", i + 1), o.getInt("x"), o.getInt("y"), o.getDouble("norm_x").toFloat(), o.getDouble("norm_y").toFloat(), o.getInt("number"))
        }
    }
}

@Composable
fun ZoomableFeatureMap(
    points: List<BoardPoint>,
    showHouseNumbers: Boolean,
    selected: Set<Int>,
    selectedColor: Color,
    markerColors: Map<Int, Color> = emptyMap(),
    fakePoints: Set<Int> = emptySet(),
    onTap: (BoardPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        offset += panChange
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().aspectRatio(3f / 2f).clip(RoundedCornerShape(10.dp)).border(1.dp, FeatureGold, RoundedCornerShape(10.dp))
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Box(
            Modifier.fillMaxSize()
                .transformable(state)
                .pointerInput(points, scale, offset) {
                    detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }, onTap = { raw ->
                        val local = Offset((raw.x - offset.x) / scale, (raw.y - offset.y) / scale)
                        var best: BoardPoint? = null
                        var dist = Float.MAX_VALUE
                        points.forEach { p ->
                            val dx = local.x - p.normX * w
                            val dy = local.y - p.normY * h
                            val d = sqrt(dx * dx + dy * dy)
                            if (d < dist) { dist = d; best = p }
                        }
                        if (dist <= 30f / scale) best?.let(onTap)
                    })
                }
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
            ) {
                Image(painterResource(R.drawable.whitechapel_board_base), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                if (showHouseNumbers) Image(painterResource(R.drawable.whitechapel_house_numbers_overlay), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Canvas(Modifier.matchParentSize()) {
                    val r = size.minDimension * 0.014f
                    selected.forEach { n ->
                        val p = points.firstOrNull { it.number == n } ?: return@forEach
                        val c = Offset(p.normX * size.width, p.normY * size.height)
                        val tokenColor = markerColors[n] ?: selectedColor
                        drawCircle(tokenColor.copy(alpha = .30f), r * 1.7f, c)
                        drawCircle(tokenColor, r * 1.5f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * .42f))
                        if (n in fakePoints) {
                            drawLine(Color.White, Offset(c.x-r,c.y-r), Offset(c.x+r,c.y+r), strokeWidth=r*.30f)
                            drawLine(Color.White, Offset(c.x+r,c.y-r), Offset(c.x-r,c.y+r), strokeWidth=r*.30f)
                        }
                    }
                }
            }
        }
        Text("دو انگشت: زوم/جابجایی • دابل‌تپ: بازنشانی", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(.65f)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
