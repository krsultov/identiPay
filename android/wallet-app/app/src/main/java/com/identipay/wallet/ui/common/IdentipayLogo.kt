package com.identipay.wallet.ui.common

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

/**
 * The identiPay dot-grid logo mark, matching the website SVG.
 *
 * A 4x4 grid where some dots are filled (solid) and some are hollow (ring),
 * creating the distinctive identity pattern.
 */
@Composable
fun IdentipayLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Canvas(modifier = modifier.size(size)) {
        val cellW = this.size.width / 4f
        val cellH = this.size.height / 4f

        // Grid positions (col 0-3, row 0-3)
        // filled = true means solid dot, false means hollow ring
        val dots = listOf(
            // Row 0
            Triple(0, 0, true),
            Triple(1, 0, false),
            Triple(2, 0, true),
            Triple(3, 0, true),
            // Row 1
            Triple(0, 1, false),
            Triple(1, 1, false),
            Triple(2, 1, true),
            Triple(3, 1, true),
            // Row 2
            Triple(0, 2, true),
            Triple(1, 2, false),
            Triple(2, 2, true),
            Triple(3, 2, false),
            // Row 3
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
        // Solid filled circle
        drawCircle(
            color = color,
            radius = outerRadius,
            center = center,
        )
    } else {
        // Hollow ring
        drawCircle(
            color = color,
            radius = outerRadius - ringWidth / 2f,
            center = center,
            style = Stroke(width = ringWidth),
        )
    }
}
