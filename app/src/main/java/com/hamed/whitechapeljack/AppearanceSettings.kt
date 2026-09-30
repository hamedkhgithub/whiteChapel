package com.hamed.whitechapeljack

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject

private val DEFAULT_POLICE_COLORS = listOf(
    "#1976D2", // real 1
    "#F9A825", // real 2
    "#6D4C41", // real 3
    "#D32F2F", // real 4
    "#2E7D32", // real 5
    "#111111"  // fake / hidden
)

private const val DEFAULT_REAL_VICTIM = "#D32F2F"
private const val DEFAULT_FAKE_VICTIM = "#FFFFFF"
private const val DEFAULT_CLUE = "#F0C52B"
private const val DEFAULT_CRIME_SCENE = "#D01818"

data class DisplayAppearance(
    val policeColors: List<String> = DEFAULT_POLICE_COLORS,
    val policeSize: Float = 1f,
    val policeFillAlpha: Float = 0.42f,
    val victimRealColor: String = DEFAULT_REAL_VICTIM,
    val victimFakeColor: String = DEFAULT_FAKE_VICTIM,
    val victimSize: Float = 1f,
    val victimFillAlpha: Float = 0.42f,
    val clueColor: String = DEFAULT_CLUE,
    val clueSize: Float = 1f,
    val clueFillAlpha: Float = 0.32f,
    val crimeSceneColor: String = DEFAULT_CRIME_SCENE,
    val crimeSceneSize: Float = 1f,
    val yellowCrossingSize: Float = 1f,
    val houseNumberScale: Float = 1f,
    val overlayOffsetX: Float = 0f,
    val overlayOffsetY: Float = 0f,
    val overlayScale: Float = 1f,
    val publicUiTextScale: Float = 1f,
    val publicQrScale: Float = 1f
) {
    fun policeColor(tokenId: Int, real: Boolean, identityVisible: Boolean): String {
        if (!identityVisible || !real) return policeColors.getOrElse(5) { "#111111" }
        return policeColors.getOrElse((tokenId - 1).coerceIn(0, 4)) {
            DEFAULT_POLICE_COLORS[(tokenId - 1).coerceIn(0, 4)]
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("policeColors", JSONArray().apply { policeColors.take(6).forEach(::put) })
        .put("policeSize", policeSize)
        .put("policeFillAlpha", policeFillAlpha)
        .put("victimRealColor", victimRealColor)
        .put("victimFakeColor", victimFakeColor)
        .put("victimSize", victimSize)
        .put("victimFillAlpha", victimFillAlpha)
        .put("clueColor", clueColor)
        .put("clueSize", clueSize)
        .put("clueFillAlpha", clueFillAlpha)
        .put("crimeSceneColor", crimeSceneColor)
        .put("crimeSceneSize", crimeSceneSize)
        .put("yellowCrossingSize", yellowCrossingSize)
        .put("houseNumberScale", houseNumberScale)
        .put("overlayOffsetX", overlayOffsetX)
        .put("overlayOffsetY", overlayOffsetY)
        .put("overlayScale", overlayScale)
        .put("publicUiTextScale", publicUiTextScale)
        .put("publicQrScale", publicQrScale)

    companion object {
        fun fromJson(o: JSONObject?): DisplayAppearance {
            if (o == null) return DisplayAppearance()
            val arr = o.optJSONArray("policeColors")
            val colors = MutableList(6) { DEFAULT_POLICE_COLORS[it] }
            if (arr != null) for (i in 0 until minOf(6, arr.length())) colors[i] = arr.optString(i, colors[i])
            return DisplayAppearance(
                policeColors = colors,
                policeSize = o.optDouble("policeSize", 1.0).toFloat().coerceIn(.5f, 2f),
                policeFillAlpha = o.optDouble("policeFillAlpha", o.optDouble("policeAlpha", .42)).toFloat().coerceIn(.05f, 1f),
                victimRealColor = o.optString("victimRealColor", DEFAULT_REAL_VICTIM),
                victimFakeColor = o.optString("victimFakeColor", DEFAULT_FAKE_VICTIM),
                victimSize = o.optDouble("victimSize", 1.0).toFloat().coerceIn(.5f, 2f),
                victimFillAlpha = o.optDouble("victimFillAlpha", o.optDouble("victimAlpha", .42)).toFloat().coerceIn(.05f, 1f),
                clueColor = o.optString("clueColor", DEFAULT_CLUE),
                clueSize = o.optDouble("clueSize", 1.0).toFloat().coerceIn(.5f, 2f),
                clueFillAlpha = o.optDouble("clueFillAlpha", o.optDouble("clueAlpha", .32)).toFloat().coerceIn(.05f, 1f),
                crimeSceneColor = o.optString("crimeSceneColor", DEFAULT_CRIME_SCENE),
                crimeSceneSize = o.optDouble("crimeSceneSize", 1.0).toFloat().coerceIn(.5f, 2f),
                yellowCrossingSize = o.optDouble("yellowCrossingSize", 1.0).toFloat().coerceIn(.5f, 2f),
                // V3.1 used textScale for UI text. Migrate that value to house numbers instead.
                houseNumberScale = o.optDouble("houseNumberScale", o.optDouble("textScale", 1.0)).toFloat().coerceIn(.1f, 1.8f),
                overlayOffsetX = o.optDouble("overlayOffsetX", 0.0).toFloat().coerceIn(-400f, 400f),
                overlayOffsetY = o.optDouble("overlayOffsetY", 0.0).toFloat().coerceIn(-400f, 400f),
                overlayScale = o.optDouble("overlayScale", 1.0).toFloat().coerceIn(.75f, 1.25f),
                publicUiTextScale = o.optDouble("publicUiTextScale", 1.0).toFloat().coerceIn(.7f, 2f),
                publicQrScale = o.optDouble("publicQrScale", 1.0).toFloat().coerceIn(.5f, 2f)
            )
        }
    }
}

data class AppearanceSettings(
    val phone: DisplayAppearance = DisplayAppearance(),
    val publicDisplay: DisplayAppearance = DisplayAppearance(
        policeSize = 1.15f,
        victimSize = 1.15f,
        clueSize = 1.15f,
        crimeSceneSize = 1.15f,
        yellowCrossingSize = 1.15f,
        houseNumberScale = 1.05f
    )
)

object AppearanceStore {
    private const val PREF = "whitechapel_appearance_settings"
    private const val KEY = "appearance"

    fun load(ctx: Context): AppearanceSettings {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: return AppearanceSettings()
        return runCatching {
            val o = JSONObject(raw)
            AppearanceSettings(
                phone = DisplayAppearance.fromJson(o.optJSONObject("phone")),
                publicDisplay = DisplayAppearance.fromJson(o.optJSONObject("publicDisplay"))
            )
        }.getOrDefault(AppearanceSettings())
    }

    fun save(ctx: Context, settings: AppearanceSettings) {
        val o = JSONObject()
            .put("phone", settings.phone.toJson())
            .put("publicDisplay", settings.publicDisplay.toJson())
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
        if (DigitalGameStore.exists(ctx)) DigitalGameStore.publish(ctx) else MapStateStore.publish(ctx)
    }

    fun reset(ctx: Context): AppearanceSettings {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        val defaults = AppearanceSettings()
        if (DigitalGameStore.exists(ctx)) DigitalGameStore.publish(ctx) else MapStateStore.publish(ctx)
        return defaults
    }

    fun publicJson(ctx: Context): JSONObject = load(ctx).publicDisplay.toJson()
}

private fun parseColor(hex: String): Color = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color.White)

private fun colorToHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

private val COLOR_PRESETS = listOf(
    "#FFFFFF", "#111111", "#D32F2F", "#F9A825",
    "#F0C52B", "#2E7D32", "#1976D2", "#00ACC1",
    "#7B1FA2", "#EC407A", "#FB8C00", "#6D4C41"
)

@Composable
fun AppearanceSettingsPage(
    initial: AppearanceSettings,
    onChanged: (AppearanceSettings) -> Unit,
    onBack: () -> Unit
) {
    var settings by remember(initial) { mutableStateOf(initial) }
    var publicTab by remember { mutableStateOf(false) }
    val current = if (publicTab) settings.publicDisplay else settings.phone

    fun updateDisplay(newValue: DisplayAppearance) {
        settings = if (publicTab) settings.copy(publicDisplay = newValue) else settings.copy(phone = newValue)
        onChanged(settings)
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF100E0C)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ بازگشت") }
            Text(
                "تنظیمات ظاهر",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(64.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !publicTab, onClick = { publicTab = false }, label = { Text("گوشی") }, modifier = Modifier.weight(1f))
            FilterChip(selected = publicTab, onClick = { publicTab = true }, label = { Text("نمایش عمومی") }, modifier = Modifier.weight(1f))
        }

        Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsCard("پلیس") {
                Text("۵ رنگ پلیس واقعی + یک رنگ مشترک برای پلیس جعلی/مخفی", color = Color.LightGray, fontSize = 11.sp)
                current.policeColors.take(6).forEachIndexed { index, value ->
                    ColorSettingRow(
                        title = if (index < 5) "پلیس واقعی ${index + 1}" else "پلیس جعلی / مخفی",
                        value = value,
                        onChange = { color ->
                            val next = current.policeColors.toMutableList().also { while (it.size < 6) it += "#111111" }
                            next[index] = color
                            updateDisplay(current.copy(policeColors = next))
                        }
                    )
                }
                ScaleRow("اندازه پلیس", current.policeSize, .5f, 2f) { updateDisplay(current.copy(policeSize = it)) }
                ScaleRow("شفافیت داخل", current.policeFillAlpha, .05f, 1f) { updateDisplay(current.copy(policeFillAlpha = it)) }
            }

            SettingsCard("قربانی") {
                ColorSettingRow("قربانی واقعی", current.victimRealColor) { updateDisplay(current.copy(victimRealColor = it)) }
                ColorSettingRow("قربانی جعلی / مخفی", current.victimFakeColor) { updateDisplay(current.copy(victimFakeColor = it)) }
                ScaleRow("اندازه قربانی", current.victimSize, .5f, 2f) { updateDisplay(current.copy(victimSize = it)) }
                ScaleRow("شفافیت داخل", current.victimFillAlpha, .05f, 1f) { updateDisplay(current.copy(victimFillAlpha = it)) }
            }

            SettingsCard("سرنخ") {
                Text("آیکون ذره‌بین search.svg", color = Color.LightGray, fontSize = 11.sp)
                ColorSettingRow("رنگ سرنخ", current.clueColor) { updateDisplay(current.copy(clueColor = it)) }
                ScaleRow("اندازه سرنخ", current.clueSize, .5f, 2f) { updateDisplay(current.copy(clueSize = it)) }
                ScaleRow("شفافیت داخل", current.clueFillAlpha, .05f, 1f) { updateDisplay(current.copy(clueFillAlpha = it)) }
            }

            SettingsCard("محل قتل") {
                Text("آیکون هدف target.svg؛ محل قتل شب جاری با رنگ انتخابی و محل‌های قتل قبلی خاکستری نمایش داده می‌شوند.", color = Color.LightGray, fontSize = 11.sp)
                ColorSettingRow("رنگ محل قتل", current.crimeSceneColor) { updateDisplay(current.copy(crimeSceneColor = it)) }
                ScaleRow("اندازه محل قتل", current.crimeSceneSize, .5f, 2f) { updateDisplay(current.copy(crimeSceneSize = it)) }
            }

            SettingsCard("تقاطع‌های زرد") {
                Text("در فاز جانمایی پلیس، دور هفت تقاطع زرد یک مربع راهنما نمایش داده می‌شود.", color = Color.LightGray, fontSize = 11.sp)
                ScaleRow("اندازه مربع زرد", current.yellowCrossingSize, .5f, 2f) { updateDisplay(current.copy(yellowCrossingSize = it)) }
            }

            SettingsCard("شماره خانه‌ها") {
                Text(
                    "شماره‌ها روی دایره‌های هم‌اندازه نمایش داده می‌شوند؛ خانه‌های قرمز پس‌زمینه قرمز دارند.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                ScaleRow("اندازه شماره‌ها", current.houseNumberScale, .1f, 1.8f) {
                    updateDisplay(current.copy(houseNumberScale = it))
                }
            }

            if (publicTab) {
                SettingsCard("کالیبراسیون نمایش عمومی") {
                    Text(
                        "این تنظیمات فقط موقعیت لایه‌های روی نقشه TV را تغییر می‌دهند و خود تصویر نقشه جابه‌جا نمی‌شود.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    OffsetRow("X Offset", current.overlayOffsetX, -400f, 400f) {
                        updateDisplay(current.copy(overlayOffsetX = it))
                    }
                    OffsetRow("Y Offset", current.overlayOffsetY, -400f, 400f) {
                        updateDisplay(current.copy(overlayOffsetY = it))
                    }
                    ScaleRow("Scale نقاط", current.overlayScale, .75f, 1.25f) {
                        updateDisplay(current.copy(overlayScale = it))
                    }
                    ScaleRow("اندازه نوشته‌های UI", current.publicUiTextScale, .7f, 2f) {
                        updateDisplay(current.copy(publicUiTextScale = it))
                    }
                    OutlinedButton(
                        onClick = {
                            updateDisplay(current.copy(
                                overlayOffsetX = 0f,
                                overlayOffsetY = 0f,
                                overlayScale = 1f,
                                publicUiTextScale = 1f
                            ))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("بازنشانی کالیبراسیون نمایش عمومی") }
                }

                SettingsCard("QR Code نمایش عمومی") {
                    Text(
                        "QR شامل آدرس فعلی سرور است و در گوشه بالا-راست نمایش عمومی ثابت می‌ماند.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    ScaleRow("اندازه QR Code", current.publicQrScale, .5f, 2f) {
                        updateDisplay(current.copy(publicQrScale = it))
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                settings = AppearanceSettings()
                onChanged(settings)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("بازگردانی تنظیمات ظاهری به حالت پیش‌فرض") }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFF1C1815), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Color(0xFFD6AD63), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
private fun ColorSettingRow(title: String, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value.uppercase(), color = Color.LightGray, fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(30.dp)
                .background(parseColor(value), CircleShape)
                .border(2.dp, Color.White.copy(.65f), CircleShape)
        )
    }
    if (open) {
        ColorPickerDialog(
            initialHex = value,
            onDismiss = { open = false },
            onConfirm = { onChange(it); open = false }
        )
    }
}

@Composable
private fun ColorPickerDialog(initialHex: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val initialInt = remember(initialHex) { runCatching { AndroidColor.parseColor(initialHex) }.getOrDefault(AndroidColor.WHITE) }
    val initialHsv = remember(initialInt) { FloatArray(3).also { AndroidColor.colorToHSV(initialInt, it) } }
    var hue by remember(initialHex) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialHex) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialHex) { mutableFloatStateOf(initialHsv[2]) }
    val selectedInt = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    val selectedHex = colorToHex(selectedInt)
    val selectedColor = Color(selectedInt)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF211C18),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("انتخاب رنگ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).background(selectedColor, CircleShape).border(2.dp, Color.White.copy(.7f), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(selectedHex, color = Color.White, fontSize = 14.sp)
                }

                Text("رنگ", color = Color.LightGray, fontSize = 12.sp)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("اشباع", color = Color.LightGray, fontSize = 12.sp)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                Text("روشنایی", color = Color.LightGray, fontSize = 12.sp)
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)

                Text("رنگ‌های آماده", color = Color.LightGray, fontSize = 12.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    COLOR_PRESETS.chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { hex ->
                                Box(
                                    Modifier.size(34.dp)
                                        .background(parseColor(hex), CircleShape)
                                        .border(1.dp, Color.White.copy(.55f), CircleShape)
                                        .clickable {
                                            val c = AndroidColor.parseColor(hex)
                                            val hsv = FloatArray(3).also { AndroidColor.colorToHSV(c, it) }
                                            hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                                        }
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("لغو") }
                    Button(onClick = { onConfirm(selectedHex) }) { Text("تأیید") }
                }
            }
        }
    }
}

@Composable
private fun OffsetRow(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(118.dp))
        Slider(value = value, onValueChange = onChange, valueRange = min..max, modifier = Modifier.weight(1f))
        Text("${value.toInt()} px", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.width(58.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun ScaleRow(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(118.dp))
        Slider(value = value, onValueChange = onChange, valueRange = min..max, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.width(46.dp), textAlign = TextAlign.End)
    }
}
