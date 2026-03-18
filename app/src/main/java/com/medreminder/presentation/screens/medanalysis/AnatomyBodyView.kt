package com.medreminder.presentation.screens.medanalysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medreminder.ai.BodyRegion

/**
 * Draws a front-facing anatomical body silhouette using Compose Canvas
 * and highlights the body regions that the medication targets.
 */
@Composable
fun AnatomyBodyView(
    highlightedRegions: List<BodyRegion>,
    modifier: Modifier = Modifier
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightAlpha = 0.45f
    val bodyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .width(200.dp)
                .height(380.dp)
        ) {
            val w = size.width
            val h = size.height

            // Reference points (normalized to canvas)
            val centerX = w * 0.5f
            val headCenterY = h * 0.07f
            val headRadius = w * 0.09f
            val neckTop = headCenterY + headRadius
            val neckBottom = h * 0.19f
            val shoulderY = h * 0.21f
            val shoulderWidth = w * 0.38f
            val chestBottom = h * 0.42f
            val waistY = h * 0.47f
            val waistWidth = w * 0.28f
            val hipY = h * 0.53f
            val hipWidth = w * 0.32f
            val legSplit = h * 0.55f
            val kneeY = h * 0.72f
            val ankleY = h * 0.90f
            val footY = h * 0.95f
            val armEndY = h * 0.55f

            // Draw body silhouette
            drawBodySilhouette(
                centerX, headCenterY, headRadius, neckTop, neckBottom,
                shoulderY, shoulderWidth, chestBottom, waistY, waistWidth,
                hipY, hipWidth, legSplit, kneeY, ankleY, footY, armEndY,
                bodyColor, outlineColor
            )

            // Highlight regions
            highlightedRegions.forEach { region ->
                drawRegionHighlight(
                    region, centerX, headCenterY, headRadius, neckTop, neckBottom,
                    shoulderY, shoulderWidth, chestBottom, waistY, waistWidth,
                    hipY, hipWidth, legSplit, kneeY, ankleY, footY, armEndY,
                    highlightColor.copy(alpha = highlightAlpha)
                )
            }
        }

        // Region labels positioned beside the body
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(start = 140.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            highlightedRegions.forEach { region ->
                Text(
                    text = region.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun DrawScope.drawBodySilhouette(
    cx: Float, headCY: Float, headR: Float,
    neckTop: Float, neckBottom: Float,
    shoulderY: Float, shoulderW: Float,
    chestBottom: Float, waistY: Float, waistW: Float,
    hipY: Float, hipW: Float,
    legSplit: Float, kneeY: Float, ankleY: Float, footY: Float,
    armEndY: Float,
    fillColor: Color, strokeColor: Color
) {
    val neckW = headR * 0.7f
    val legW = waistW * 0.35f
    val armW = headR * 0.5f

    // Head
    drawCircle(fillColor, headR, Offset(cx, headCY))
    drawCircle(strokeColor, headR, Offset(cx, headCY), style = Stroke(2f))

    // Neck
    drawRect(fillColor, Offset(cx - neckW, neckTop), Size(neckW * 2, neckBottom - neckTop))

    // Torso path
    val torso = Path().apply {
        moveTo(cx - shoulderW, shoulderY)
        // shoulders to waist
        lineTo(cx - shoulderW, chestBottom)
        lineTo(cx - waistW, waistY)
        lineTo(cx - hipW, hipY)
        // across hips
        lineTo(cx + hipW, hipY)
        // waist to shoulders (right side)
        lineTo(cx + waistW, waistY)
        lineTo(cx + shoulderW, chestBottom)
        lineTo(cx + shoulderW, shoulderY)
        close()
    }
    drawPath(torso, fillColor, style = Fill)
    drawPath(torso, strokeColor, style = Stroke(2f, join = StrokeJoin.Round))

    // Left arm
    val armStartX = cx - shoulderW
    drawLine(fillColor, Offset(armStartX, shoulderY), Offset(armStartX - armW * 2, armEndY), strokeWidth = armW * 2, cap = StrokeCap.Round)
    drawLine(strokeColor, Offset(armStartX, shoulderY), Offset(armStartX - armW * 2, armEndY), strokeWidth = 2f)

    // Right arm
    val armStartXR = cx + shoulderW
    drawLine(fillColor, Offset(armStartXR, shoulderY), Offset(armStartXR + armW * 2, armEndY), strokeWidth = armW * 2, cap = StrokeCap.Round)
    drawLine(strokeColor, Offset(armStartXR, shoulderY), Offset(armStartXR + armW * 2, armEndY), strokeWidth = 2f)

    // Left leg
    drawLine(fillColor, Offset(cx - legW, legSplit), Offset(cx - legW * 1.2f, ankleY), strokeWidth = legW * 2, cap = StrokeCap.Round)
    drawLine(strokeColor, Offset(cx - legW, legSplit), Offset(cx - legW * 1.2f, ankleY), strokeWidth = 2f)
    // Left foot
    drawRoundRect(fillColor, Offset(cx - legW * 2.2f, ankleY), Size(legW * 2.5f, footY - ankleY), CornerRadius(6f))
    drawRoundRect(strokeColor, Offset(cx - legW * 2.2f, ankleY), Size(legW * 2.5f, footY - ankleY), CornerRadius(6f), style = Stroke(2f))

    // Right leg
    drawLine(fillColor, Offset(cx + legW, legSplit), Offset(cx + legW * 1.2f, ankleY), strokeWidth = legW * 2, cap = StrokeCap.Round)
    drawLine(strokeColor, Offset(cx + legW, legSplit), Offset(cx + legW * 1.2f, ankleY), strokeWidth = 2f)
    // Right foot
    drawRoundRect(fillColor, Offset(cx + legW * 2.2f - legW * 2.5f, ankleY), Size(legW * 2.5f, footY - ankleY), CornerRadius(6f))
    drawRoundRect(strokeColor, Offset(cx + legW * 2.2f - legW * 2.5f, ankleY), Size(legW * 2.5f, footY - ankleY), CornerRadius(6f), style = Stroke(2f))
}

private fun DrawScope.drawRegionHighlight(
    region: BodyRegion,
    cx: Float, headCY: Float, headR: Float,
    neckTop: Float, neckBottom: Float,
    shoulderY: Float, shoulderW: Float,
    chestBottom: Float, waistY: Float, waistW: Float,
    hipY: Float, hipW: Float,
    legSplit: Float, kneeY: Float, ankleY: Float, footY: Float,
    armEndY: Float,
    color: Color
) {
    when (region) {
        BodyRegion.HEAD, BodyRegion.NERVOUS_SYSTEM -> {
            drawCircle(color, headR * 1.15f, Offset(cx, headCY))
        }
        BodyRegion.EYES -> {
            // Two small circles on the head
            drawCircle(color, headR * 0.25f, Offset(cx - headR * 0.35f, headCY - headR * 0.1f))
            drawCircle(color, headR * 0.25f, Offset(cx + headR * 0.35f, headCY - headR * 0.1f))
        }
        BodyRegion.EARS -> {
            drawCircle(color, headR * 0.22f, Offset(cx - headR * 1.05f, headCY))
            drawCircle(color, headR * 0.22f, Offset(cx + headR * 1.05f, headCY))
        }
        BodyRegion.THROAT -> {
            drawRoundRect(
                color,
                Offset(cx - headR * 0.6f, neckTop),
                Size(headR * 1.2f, neckBottom - neckTop),
                CornerRadius(6f)
            )
        }
        BodyRegion.HEART -> {
            // Left-center chest area
            val heartCX = cx - shoulderW * 0.2f
            val heartCY = shoulderY + (chestBottom - shoulderY) * 0.35f
            drawCircle(color, shoulderW * 0.2f, Offset(heartCX, heartCY))
        }
        BodyRegion.LUNGS -> {
            // Two oval regions in the chest
            val lungY = shoulderY + (chestBottom - shoulderY) * 0.4f
            val lungW = shoulderW * 0.3f
            val lungH = (chestBottom - shoulderY) * 0.45f
            drawOval(color, Offset(cx - shoulderW * 0.5f, lungY - lungH / 2), Size(lungW, lungH))
            drawOval(color, Offset(cx + shoulderW * 0.2f, lungY - lungH / 2), Size(lungW, lungH))
        }
        BodyRegion.STOMACH -> {
            val stomachCY = waistY - (waistY - chestBottom) * 0.3f
            drawOval(color, Offset(cx - waistW * 0.6f, stomachCY - waistW * 0.4f), Size(waistW * 1.2f, waistW * 0.9f))
        }
        BodyRegion.LIVER -> {
            // Right side of upper abdomen
            val liverCX = cx + waistW * 0.15f
            val liverCY = chestBottom + (waistY - chestBottom) * 0.2f
            drawOval(color, Offset(liverCX - waistW * 0.35f, liverCY - waistW * 0.2f), Size(waistW * 0.65f, waistW * 0.45f))
        }
        BodyRegion.KIDNEYS -> {
            // Two small ovals on lower back area
            val kidneyY = waistY - waistW * 0.1f
            drawOval(color, Offset(cx - waistW * 0.7f, kidneyY), Size(waistW * 0.3f, waistW * 0.4f))
            drawOval(color, Offset(cx + waistW * 0.4f, kidneyY), Size(waistW * 0.3f, waistW * 0.4f))
        }
        BodyRegion.JOINTS -> {
            // Highlight shoulder and knee joints
            drawCircle(color, shoulderW * 0.12f, Offset(cx - shoulderW, shoulderY))
            drawCircle(color, shoulderW * 0.12f, Offset(cx + shoulderW, shoulderY))
            val legW = waistW * 0.35f
            drawCircle(color, shoulderW * 0.12f, Offset(cx - legW * 1.1f, kneeY))
            drawCircle(color, shoulderW * 0.12f, Offset(cx + legW * 1.1f, kneeY))
        }
        BodyRegion.SKIN -> {
            // Outline glow around entire body
            drawCircle(color.copy(alpha = 0.15f), headR * 1.3f, Offset(cx, headCY))
            val torsoH = hipY - shoulderY
            drawRoundRect(
                color.copy(alpha = 0.15f),
                Offset(cx - shoulderW * 1.1f, shoulderY - 5),
                Size(shoulderW * 2.2f, torsoH + 10),
                CornerRadius(12f)
            )
        }
        BodyRegion.BLOOD, BodyRegion.IMMUNE -> {
            // Distributed dots throughout body to represent systemic
            val points = listOf(
                Offset(cx, shoulderY + (chestBottom - shoulderY) * 0.3f),
                Offset(cx - shoulderW * 0.4f, chestBottom),
                Offset(cx + shoulderW * 0.3f, waistY),
                Offset(cx - waistW * 0.5f, legSplit + (kneeY - legSplit) * 0.4f),
                Offset(cx + waistW * 0.5f, legSplit + (kneeY - legSplit) * 0.4f),
            )
            points.forEach { pt ->
                drawCircle(color, shoulderW * 0.08f, pt)
            }
        }
        BodyRegion.HORMONES -> {
            // Thyroid (neck) + adrenal area
            drawOval(color, Offset(cx - headR * 0.5f, neckTop), Size(headR, (neckBottom - neckTop) * 0.8f))
            drawCircle(color, waistW * 0.15f, Offset(cx, waistY))
        }
        BodyRegion.BONES -> {
            // Highlight along limbs (line highlights)
            val legW = waistW * 0.35f
            drawLine(color, Offset(cx - legW, legSplit), Offset(cx - legW * 1.2f, ankleY), strokeWidth = 8f, cap = StrokeCap.Round)
            drawLine(color, Offset(cx + legW, legSplit), Offset(cx + legW * 1.2f, ankleY), strokeWidth = 8f, cap = StrokeCap.Round)
            val armW = headR * 0.5f
            drawLine(color, Offset(cx - shoulderW, shoulderY), Offset(cx - shoulderW - armW * 2, armEndY), strokeWidth = 8f, cap = StrokeCap.Round)
            drawLine(color, Offset(cx + shoulderW, shoulderY), Offset(cx + shoulderW + armW * 2, armEndY), strokeWidth = 8f, cap = StrokeCap.Round)
        }
        BodyRegion.FULL_BODY -> {
            // Semi-transparent overlay on entire body
            drawCircle(color.copy(alpha = 0.2f), headR * 1.1f, Offset(cx, headCY))
            drawRoundRect(
                color.copy(alpha = 0.2f),
                Offset(cx - shoulderW, shoulderY),
                Size(shoulderW * 2, hipY - shoulderY),
                CornerRadius(8f)
            )
        }
    }
}
