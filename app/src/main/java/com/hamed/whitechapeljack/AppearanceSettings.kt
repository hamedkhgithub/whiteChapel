package com.hamed.whitechapeljack

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

data class DisplayAppearance(
    val policeColors: List<String> = DEFAULT_POLICE_COLORS,
    val policeSize: Float = 1f,
    val policeAlpha: Float = 1f,
    val victimRealColor: String = DEFAULT_REAL_VICTIM,
    val victimFakeColor: String = DEFAULT_FAKE_VICTIM,
    val victimSize: Float = 1f,
    val victimAlpha: Float = 1f,
    val clueColor: String = DEFAULT_CLUE,
    val clueSize: Float = 1f,
    val clueAlpha: Float = 1f,
    val textScale: Float = 1f
) {
    fun policeColor(tokenId: Int, real: Boolean, identityVisible: Boolean): String {
        if (!identityVisible || !real) return policeColors.getOrElse(5) { "#111111" }
        return policeColors.getOrElse((tokenId - 1).coerceIn(0, 4)) { DEFAULT_POLICE_COLORS[(tokenId - 1).coerceIn(0, 4)] }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("policeColors", JSONArray().apply { policeColors.take(6).forEach(::put) })
        .put("policeSize", policeSize)
        .put("policeAlpha", policeAlpha)
        .put("victimRealColor", victimRealColor)
        .put("victimFakeColor", victimFakeColor)
        .put("victimSize", victimSize)
        .put("victimAlpha", victimAlpha)
        .put("clueColor", clueColor)
        .put("clueSize", clueSize)
        .put("clueAlpha", clueAlpha)
        .put("textScale", textScale)

    companion object {
        fun fromJson(o: JSONObject?): DisplayAppearance {
            if (o == null) return DisplayAppearance()
            val arr = o.optJSONArray("policeColors")
            val colors = MutableList(6) { DEFAULT_POLICE_COLORS[it] }
            if (arr != null) for (i in 0 until minOf(6, arr.length())) colors[i] = arr.optString(i, colors[i])
            return DisplayAppearance(
                policeColors = colors,
                policeSize = o.optDouble("policeSize", 1.0).toFloat().coerceIn(.5f, 2f),
                policeAlpha = o.optDouble("policeAlpha", 1.0).toFloat().coerceIn(.2f, 1f),
                victimRealColor = o.optString("victimRealColor", DEFAULT_REAL_VICTIM),
                victimFakeColor = o.optString("victimFakeColor", DEFAULT_FAKE_VICTIM),
                victimSize = o.optDouble("victimSize", 1.0).toFloat().coerceIn(.5f, 2f),
                victimAlpha = o.optDouble("victimAlpha", 1.0).toFloat().coerceIn(.2f, 1f),
                clueColor = o.optString("clueColor", DEFAULT_CLUE),
                clueSize = o.optDouble("clueSize", 1.0).toFloat().coerceIn(.5f, 2f),
                clueAlpha = o.optDouble("clueAlpha", 1.0).toFloat().coerceIn(.2f, 1f),
                textScale = o.optDouble("textScale", 1.0).toFloat().coerceIn(.75f, 1.75f)
            )
        }
    }
}

data class AppearanceSettings(
    val phone: DisplayAppearance = DisplayAppearance(),
    val publicDisplay: DisplayAppearance = DisplayAppearance(
        policeSize = 1.15f,
        victimSize = 1.15f,
        clueSize = 1.15f
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

private fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.White)

private val COLOR_CHOICES = listOf(
    "#FFFFFF", "#111111", "#D32F2F", "#F9A825", "#F0C52B", "#2E7D32",
    "#1976D2", "#00ACC1", "#7B1FA2", "#EC407A", "#FB8C00", "#6D4C41"
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
            Text("تنظیمات ظاهر", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.width(64.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !publicTab, onClick = { publicTab = false }, label = { Text("گوشی") }, modifier = Modifier.weight(1f))
            FilterChip(selected = publicTab, onClick = { publicTab = true }, label = { Text("نمایش عمومی") }, modifier = Modifier.weight(1f))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                ScaleRow("شفافیت پلیس", current.policeAlpha, .2f, 1f, percentOnly = true) { updateDisplay(current.copy(policeAlpha = it)) }
            }

            SettingsCard("قربانی") {
                ColorSettingRow("قربانی واقعی", current.victimRealColor) { updateDisplay(current.copy(victimRealColor = it)) }
                ColorSettingRow("قربانی جعلی / مخفی", current.victimFakeColor) { updateDisplay(current.copy(victimFakeColor = it)) }
                ScaleRow("اندازه قربانی", current.victimSize, .5f, 2f) { updateDisplay(current.copy(victimSize = it)) }
                ScaleRow("شفافیت قربانی", current.victimAlpha, .2f, 1f, percentOnly = true) { updateDisplay(current.copy(victimAlpha = it)) }
            }

            SettingsCard("سرنخ") {
                ColorSettingRow("رنگ سرنخ", current.clueColor) { updateDisplay(current.copy(clueColor = it)) }
                ScaleRow("اندازه سرنخ", current.clueSize, .5f, 2f) { updateDisplay(current.copy(clueSize = it)) }
                ScaleRow("شفافیت سرنخ", current.clueAlpha, .2f, 1f, percentOnly = true) { updateDisplay(current.copy(clueAlpha = it)) }
            }

            SettingsCard("نوشته‌ها") {
                ScaleRow("اندازه نوشته‌ها", current.textScale, .75f, 1.75f) { updateDisplay(current.copy(textScale = it)) }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(24.dp).background(parseColor(value), CircleShape).border(1.dp, Color.White.copy(.55f), CircleShape))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            COLOR_CHOICES.forEach { hex ->
                val selected = hex.equals(value, ignoreCase = true)
                Box(
                    Modifier.size(if (selected) 30.dp else 26.dp)
                        .background(parseColor(hex), CircleShape)
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFFD6AD63) else Color.Gray, CircleShape)
                        .clickable { onChange(hex) }
                )
            }
        }
    }
}

@Composable
private fun ScaleRow(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    percentOnly: Boolean = false,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(118.dp))
        Slider(value = value, onValueChange = onChange, valueRange = min..max, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.width(46.dp), textAlign = TextAlign.End)
    }
}
