package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun SparklineCanvas(
    modifier: Modifier = Modifier,
    dataPoints: List<Float>,
    lineColor: Color,
    fillColor: Color
) {
    if (dataPoints.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val minVal = dataPoints.minOrNull() ?: 0f
        val maxVal = dataPoints.maxOrNull() ?: 1f
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val points = dataPoints.mapIndexed { index, valF ->
            val x = index * (width / (dataPoints.size - 1).coerceAtLeast(1))
            val y = height - ((valF - minVal) / range) * (height * 0.7f) - (height * 0.15f)
            Pair(x, y)
        }

        val strokePath = Path().apply {
            moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val controlX = (p0.first + p1.first) / 2f
                cubicTo(controlX, p0.second, controlX, p1.second, p1.first, p1.second)
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = fillPath,
            color = fillColor
        )

        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = 3f)
        )
    }
}
