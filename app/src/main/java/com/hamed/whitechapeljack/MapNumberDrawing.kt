package com.hamed.whitechapeljack

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@Composable
internal fun rememberHouseNumberPoints(): List<BoardPoint> {
    val context = LocalContext.current
    return remember {
        val a = JSONArray(context.assets.open("houses.json").bufferedReader().use { it.readText() })
        List(a.length()) { i ->
            val x = a.getJSONObject(i)
            BoardPoint(
                x.optInt("id", i + 1),
                x.getInt("x"),
                x.getInt("y"),
                x.getDouble("norm_x").toFloat(),
                x.getDouble("norm_y").toFloat(),
                x.getInt("number")
            )
        }
    }
}

internal fun DrawScope.drawHouseNumberBadges(
    houses: List<BoardPoint>,
    boardLeft: Float,
    boardTop: Float,
    boardWidth: Float,
    boardHeight: Float,
    scale: Float,
    offset: Offset,
    numberScale: Float
) {
    // One constant circle size for every number. The base diameter is sized for a 3-digit label.
    val diameter = 27.dp.toPx() * numberScale * scale
    val radius = diameter / 2f
    val fontPx = 10.5.dp.toPx() * numberScale * scale
    val borderWidth = (0.8.dp.toPx() * numberScale * scale).coerceAtLeast(1f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = fontPx
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    houses.forEach { point ->
        val center = Offset(
            (boardLeft + point.normX * boardWidth) * scale + offset.x,
            (boardTop + point.normY * boardHeight) * scale + offset.y
        )
        drawCircle(Color.White.copy(alpha = .92f), radius, center)
        drawCircle(Color.Black.copy(alpha = .45f), radius, center, style = Stroke(borderWidth))
        val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
        drawContext.canvas.nativeCanvas.drawText(point.number.toString(), center.x, baseline, paint)
    }
}
