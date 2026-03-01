package com.identipay.identipaypos.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun IdentipayLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Canvas(modifier = modifier.size(size)) {
        val cellW = this.size.width / 4f
        val cellH = this.size.height / 4f

        val dots = listOf(
            Triple(0, 0, true),
            Triple(1, 0, false),
            Triple(2, 0, true),
            Triple(3, 0, true),
            Triple(0, 1, false),
            Triple(1, 1, false),
            Triple(2, 1, true),
            Triple(3, 1, true),
            Triple(0, 2, true),
            Triple(1, 2, false),
            Triple(2, 2, true),
            Triple(3, 2, false),
            Triple(0, 3, true),
            Triple(1, 3, false),
            Triple(2, 3, true),
            Triple(3, 3, false),
        )

        for ((col, row, filled) in dots) {
            val cx = cellW * (col + 0.5f)
            val cy = cellH * (row + 0.5f)
            drawDot(cx, cy, cellW, filled, color)
        }
    }
}

private fun DrawScope.drawDot(
    cx: Float,
    cy: Float,
    cellSize: Float,
    filled: Boolean,
    color: Color,
) {
    val center = Offset(cx, cy)
    val outerRadius = cellSize * 0.42f
    val ringWidth = cellSize * 0.14f

    if (filled) {
        drawCircle(
            color = color,
            radius = outerRadius,
            center = center,
        )
    } else {
        drawCircle(
            color = color,
            radius = outerRadius - ringWidth / 2f,
            center = center,
            style = Stroke(width = ringWidth),
        )
    }
}
