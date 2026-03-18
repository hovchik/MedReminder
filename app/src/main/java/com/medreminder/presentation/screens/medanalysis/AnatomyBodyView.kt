package com.medreminder.presentation.screens.medanalysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Draws a front-facing human body silhouette using smooth cubic bezier curves
 * and highlights the body regions that the medication targets.
 */
@Composable
fun AnatomyBodyView(
    highlightedRegions: List<BodyRegion>,
    modifier: Modifier = Modifier
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .width(220.dp)
                .height(400.dp)
        ) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f

            // --- Key anatomical reference points (all relative to canvas) ---
            val headCY = h * 0.065f
            val headRX = w * 0.075f        // horizontal radius (slightly oval)
            val headRY = w * 0.090f        // vertical radius
            val neckTopY = headCY + headRY
            val neckBotY = h * 0.175f
            val neckW = w * 0.042f

            val shoulderY = h * 0.195f
            val shoulderX = w * 0.295f     // half shoulder span from center

            val armPitY = h * 0.24f
            val elbowY = h * 0.385f
            val wristY = h * 0.50f
            val handY = h * 0.545f
            val upperArmW = w * 0.042f
            val forearmW = w * 0.035f
            val handW = w * 0.032f

            val chestY = h * 0.30f
            val waistY = h * 0.41f
            val waistX = w * 0.155f
            val hipY = h * 0.47f
            val hipX = w * 0.20f
            val crotchY = h * 0.52f

            val thighTopX = w * 0.145f
            val kneeY = h * 0.67f
            val kneeX = w * 0.115f
            val calfX = w * 0.085f
            val ankleY = h * 0.855f
            val ankleX = w * 0.060f
            val footBottomY = h * 0.92f
            val toeX = w * 0.105f
            val heelX = w * 0.070f

            // ============================================
            //  Draw the full body as ONE smooth path
            // ============================================
            val body = Path().apply {
                // Start at top of head
                moveTo(cx, headCY - headRY)

                // --- Right side of head ---
                cubicTo(
                    cx + headRX * 0.55f, headCY - headRY,
                    cx + headRX, headCY - headRY * 0.55f,
                    cx + headRX, headCY
                )
                cubicTo(
                    cx + headRX, headCY + headRY * 0.55f,
                    cx + headRX * 0.55f, headCY + headRY,
                    cx + neckW, neckTopY
                )

                // --- Right neck ---
                cubicTo(
                    cx + neckW, neckTopY + (neckBotY - neckTopY) * 0.3f,
                    cx + neckW * 1.1f, neckBotY - (neckBotY - neckTopY) * 0.15f,
                    cx + neckW * 1.2f, neckBotY
                )

                // --- Right shoulder (trapezius curve) ---
                cubicTo(
                    cx + shoulderX * 0.35f, shoulderY - h * 0.015f,
                    cx + shoulderX * 0.7f, shoulderY - h * 0.008f,
                    cx + shoulderX, shoulderY
                )

                // --- Right shoulder cap (deltoid curve) ---
                cubicTo(
                    cx + shoulderX + upperArmW * 0.6f, shoulderY + h * 0.012f,
                    cx + shoulderX + upperArmW, shoulderY + h * 0.025f,
                    cx + shoulderX + upperArmW * 0.7f, armPitY
                )

                // --- Right upper arm outer ---
                cubicTo(
                    cx + shoulderX + upperArmW * 0.5f, armPitY + (elbowY - armPitY) * 0.3f,
                    cx + shoulderX + upperArmW * 0.3f, armPitY + (elbowY - armPitY) * 0.7f,
                    cx + shoulderX - upperArmW * 0.2f, elbowY
                )

                // --- Right forearm outer ---
                cubicTo(
                    cx + shoulderX - upperArmW * 0.4f, elbowY + (wristY - elbowY) * 0.15f,
                    cx + shoulderX - forearmW * 0.5f, elbowY + (wristY - elbowY) * 0.5f,
                    cx + shoulderX - forearmW, wristY
                )

                // --- Right hand ---
                cubicTo(
                    cx + shoulderX - forearmW - handW * 0.3f, wristY + (handY - wristY) * 0.3f,
                    cx + shoulderX - forearmW - handW * 0.5f, handY - (handY - wristY) * 0.1f,
                    cx + shoulderX - forearmW - handW * 0.3f, handY
                )
                // hand tip
                cubicTo(
                    cx + shoulderX - forearmW + handW * 0.2f, handY + (handY - wristY) * 0.15f,
                    cx + shoulderX - forearmW + handW * 0.8f, handY,
                    cx + shoulderX + forearmW * 0.3f, wristY
                )

                // --- Right forearm inner ---
                cubicTo(
                    cx + shoulderX + forearmW * 0.5f, elbowY + (wristY - elbowY) * 0.5f,
                    cx + shoulderX - upperArmW * 0.1f, elbowY + (wristY - elbowY) * 0.15f,
                    cx + shoulderX - upperArmW * 0.5f, elbowY
                )

                // --- Right upper arm inner -> armpit ---
                cubicTo(
                    cx + shoulderX - upperArmW * 0.5f, armPitY + (elbowY - armPitY) * 0.6f,
                    cx + shoulderX - upperArmW * 1.0f, armPitY + (elbowY - armPitY) * 0.15f,
                    cx + shoulderX - upperArmW * 1.3f, armPitY
                )

                // --- Right torso side (armpit -> waist -> hip) ---
                cubicTo(
                    cx + shoulderX - upperArmW * 1.5f, armPitY + (chestY - armPitY) * 0.5f,
                    cx + waistX + w * 0.06f, chestY,
                    cx + waistX + w * 0.025f, chestY + (waistY - chestY) * 0.5f
                )
                cubicTo(
                    cx + waistX, waistY - (waistY - chestY) * 0.15f,
                    cx + waistX - w * 0.008f, waistY,
                    cx + waistX, waistY + (hipY - waistY) * 0.3f
                )
                cubicTo(
                    cx + waistX + w * 0.015f, waistY + (hipY - waistY) * 0.6f,
                    cx + hipX - w * 0.01f, hipY - (hipY - waistY) * 0.15f,
                    cx + hipX, hipY
                )

                // --- Right hip -> right outer thigh ---
                cubicTo(
                    cx + hipX + w * 0.01f, hipY + (crotchY - hipY) * 0.4f,
                    cx + thighTopX + w * 0.06f, hipY + (crotchY - hipY) * 0.7f,
                    cx + thighTopX + w * 0.04f, crotchY
                )
                cubicTo(
                    cx + thighTopX + w * 0.025f, crotchY + (kneeY - crotchY) * 0.3f,
                    cx + kneeX + w * 0.035f, crotchY + (kneeY - crotchY) * 0.7f,
                    cx + kneeX + w * 0.025f, kneeY
                )

                // --- Right outer calf ---
                cubicTo(
                    cx + kneeX + w * 0.015f, kneeY + (ankleY - kneeY) * 0.15f,
                    cx + calfX + w * 0.025f, kneeY + (ankleY - kneeY) * 0.35f,
                    cx + calfX + w * 0.015f, kneeY + (ankleY - kneeY) * 0.55f
                )
                cubicTo(
                    cx + calfX + w * 0.005f, kneeY + (ankleY - kneeY) * 0.75f,
                    cx + ankleX + w * 0.01f, ankleY - (ankleY - kneeY) * 0.08f,
                    cx + ankleX, ankleY
                )

                // --- Right foot ---
                cubicTo(
                    cx + ankleX - w * 0.01f, ankleY + (footBottomY - ankleY) * 0.25f,
                    cx + ankleX - heelX * 0.2f, footBottomY - (footBottomY - ankleY) * 0.05f,
                    cx + heelX * 0.15f, footBottomY
                )
                cubicTo(
                    cx + heelX * 0.5f, footBottomY + (footBottomY - ankleY) * 0.08f,
                    cx + toeX * 0.8f, footBottomY + (footBottomY - ankleY) * 0.05f,
                    cx + toeX, footBottomY - (footBottomY - ankleY) * 0.1f
                )

                // --- Right foot top -> inner ankle ---
                cubicTo(
                    cx + toeX * 0.85f, footBottomY - (footBottomY - ankleY) * 0.35f,
                    cx + ankleX + w * 0.02f, ankleY + (footBottomY - ankleY) * 0.2f,
                    cx + ankleX - w * 0.012f, ankleY
                )

                // --- Right inner calf ---
                cubicTo(
                    cx + ankleX - w * 0.018f, ankleY - (ankleY - kneeY) * 0.1f,
                    cx + calfX - w * 0.02f, kneeY + (ankleY - kneeY) * 0.65f,
                    cx + calfX - w * 0.015f, kneeY + (ankleY - kneeY) * 0.45f
                )
                cubicTo(
                    cx + calfX - w * 0.01f, kneeY + (ankleY - kneeY) * 0.25f,
                    cx + kneeX - w * 0.01f, kneeY + (ankleY - kneeY) * 0.1f,
                    cx + kneeX - w * 0.015f, kneeY
                )

                // --- Right inner thigh -> crotch ---
                cubicTo(
                    cx + kneeX - w * 0.015f, crotchY + (kneeY - crotchY) * 0.65f,
                    cx + thighTopX - w * 0.02f, crotchY + (kneeY - crotchY) * 0.25f,
                    cx + w * 0.025f, crotchY + h * 0.01f
                )

                // --- Crotch curve ---
                cubicTo(
                    cx + w * 0.01f, crotchY + h * 0.022f,
                    cx - w * 0.01f, crotchY + h * 0.022f,
                    cx - w * 0.025f, crotchY + h * 0.01f
                )

                // --- Left inner thigh -> knee ---
                cubicTo(
                    cx - thighTopX + w * 0.02f, crotchY + (kneeY - crotchY) * 0.25f,
                    cx - kneeX + w * 0.015f, crotchY + (kneeY - crotchY) * 0.65f,
                    cx - kneeX + w * 0.015f, kneeY
                )

                // --- Left inner calf ---
                cubicTo(
                    cx - kneeX + w * 0.01f, kneeY + (ankleY - kneeY) * 0.1f,
                    cx - calfX + w * 0.01f, kneeY + (ankleY - kneeY) * 0.25f,
                    cx - calfX + w * 0.015f, kneeY + (ankleY - kneeY) * 0.45f
                )
                cubicTo(
                    cx - calfX + w * 0.02f, kneeY + (ankleY - kneeY) * 0.65f,
                    cx - ankleX + w * 0.018f, ankleY - (ankleY - kneeY) * 0.1f,
                    cx - ankleX + w * 0.012f, ankleY
                )

                // --- Left foot top ---
                cubicTo(
                    cx - ankleX - w * 0.02f, ankleY + (footBottomY - ankleY) * 0.2f,
                    cx - toeX * 0.85f, footBottomY - (footBottomY - ankleY) * 0.35f,
                    cx - toeX, footBottomY - (footBottomY - ankleY) * 0.1f
                )

                // --- Left foot bottom ---
                cubicTo(
                    cx - heelX * 0.5f, footBottomY + (footBottomY - ankleY) * 0.05f,
                    cx - heelX * 0.5f, footBottomY + (footBottomY - ankleY) * 0.08f,
                    cx - heelX * 0.15f, footBottomY
                )
                cubicTo(
                    cx - ankleX + heelX * 0.2f, footBottomY - (footBottomY - ankleY) * 0.05f,
                    cx - ankleX + w * 0.01f, ankleY + (footBottomY - ankleY) * 0.25f,
                    cx - ankleX, ankleY
                )

                // --- Left outer calf ---
                cubicTo(
                    cx - ankleX - w * 0.01f, ankleY - (ankleY - kneeY) * 0.08f,
                    cx - calfX - w * 0.005f, kneeY + (ankleY - kneeY) * 0.75f,
                    cx - calfX - w * 0.015f, kneeY + (ankleY - kneeY) * 0.55f
                )
                cubicTo(
                    cx - calfX - w * 0.025f, kneeY + (ankleY - kneeY) * 0.35f,
                    cx - kneeX - w * 0.015f, kneeY + (ankleY - kneeY) * 0.15f,
                    cx - kneeX - w * 0.025f, kneeY
                )

                // --- Left outer thigh ---
                cubicTo(
                    cx - kneeX - w * 0.035f, crotchY + (kneeY - crotchY) * 0.7f,
                    cx - thighTopX - w * 0.025f, crotchY + (kneeY - crotchY) * 0.3f,
                    cx - thighTopX - w * 0.04f, crotchY
                )

                // --- Left hip ---
                cubicTo(
                    cx - thighTopX - w * 0.06f, hipY + (crotchY - hipY) * 0.7f,
                    cx - hipX - w * 0.01f, hipY + (crotchY - hipY) * 0.4f,
                    cx - hipX, hipY
                )

                // --- Left torso side (hip -> waist -> armpit) ---
                cubicTo(
                    cx - hipX + w * 0.01f, hipY - (hipY - waistY) * 0.15f,
                    cx - waistX - w * 0.015f, waistY + (hipY - waistY) * 0.6f,
                    cx - waistX, waistY + (hipY - waistY) * 0.3f
                )
                cubicTo(
                    cx - waistX + w * 0.008f, waistY,
                    cx - waistX, waistY - (waistY - chestY) * 0.15f,
                    cx - waistX - w * 0.025f, chestY + (waistY - chestY) * 0.5f
                )
                cubicTo(
                    cx - waistX - w * 0.06f, chestY,
                    cx - shoulderX + upperArmW * 1.5f, armPitY + (chestY - armPitY) * 0.5f,
                    cx - shoulderX + upperArmW * 1.3f, armPitY
                )

                // --- Left upper arm inner ---
                cubicTo(
                    cx - shoulderX + upperArmW * 1.0f, armPitY + (elbowY - armPitY) * 0.15f,
                    cx - shoulderX + upperArmW * 0.5f, armPitY + (elbowY - armPitY) * 0.6f,
                    cx - shoulderX + upperArmW * 0.5f, elbowY
                )

                // --- Left forearm inner ---
                cubicTo(
                    cx - shoulderX + upperArmW * 0.1f, elbowY + (wristY - elbowY) * 0.15f,
                    cx - shoulderX - forearmW * 0.5f, elbowY + (wristY - elbowY) * 0.5f,
                    cx - shoulderX - forearmW * 0.3f, wristY
                )

                // --- Left hand ---
                cubicTo(
                    cx - shoulderX - forearmW * 0.8f, handY,
                    cx - shoulderX - forearmW - handW * 0.2f, handY + (handY - wristY) * 0.15f,
                    cx - shoulderX + forearmW + handW * 0.3f, handY
                )
                cubicTo(
                    cx - shoulderX + forearmW + handW * 0.5f, handY - (handY - wristY) * 0.1f,
                    cx - shoulderX + forearmW + handW * 0.3f, wristY + (handY - wristY) * 0.3f,
                    cx - shoulderX + forearmW, wristY
                )

                // --- Left forearm outer ---
                cubicTo(
                    cx - shoulderX + forearmW * 0.5f, elbowY + (wristY - elbowY) * 0.5f,
                    cx - shoulderX + upperArmW * 0.4f, elbowY + (wristY - elbowY) * 0.15f,
                    cx - shoulderX + upperArmW * 0.2f, elbowY
                )

                // --- Left upper arm outer ---
                cubicTo(
                    cx - shoulderX - upperArmW * 0.3f, armPitY + (elbowY - armPitY) * 0.7f,
                    cx - shoulderX - upperArmW * 0.5f, armPitY + (elbowY - armPitY) * 0.3f,
                    cx - shoulderX - upperArmW * 0.7f, armPitY
                )

                // --- Left shoulder cap (deltoid) ---
                cubicTo(
                    cx - shoulderX - upperArmW, shoulderY + h * 0.025f,
                    cx - shoulderX - upperArmW * 0.6f, shoulderY + h * 0.012f,
                    cx - shoulderX, shoulderY
                )

                // --- Left shoulder to neck (trapezius) ---
                cubicTo(
                    cx - shoulderX * 0.7f, shoulderY - h * 0.008f,
                    cx - shoulderX * 0.35f, shoulderY - h * 0.015f,
                    cx - neckW * 1.2f, neckBotY
                )

                // --- Left neck ---
                cubicTo(
                    cx - neckW * 1.1f, neckBotY - (neckBotY - neckTopY) * 0.15f,
                    cx - neckW, neckTopY + (neckBotY - neckTopY) * 0.3f,
                    cx - neckW, neckTopY
                )

                // --- Left side of head ---
                cubicTo(
                    cx - headRX * 0.55f, headCY + headRY,
                    cx - headRX, headCY + headRY * 0.55f,
                    cx - headRX, headCY
                )
                cubicTo(
                    cx - headRX, headCY - headRY * 0.55f,
                    cx - headRX * 0.55f, headCY - headRY,
                    cx, headCY - headRY
                )

                close()
            }

            // Fill and stroke the body
            drawPath(body, bodyColor, style = Fill)
            drawPath(body, outlineColor, style = Stroke(1.8f, join = StrokeJoin.Round, cap = StrokeCap.Round))

            // --- Highlight regions ---
            highlightedRegions.forEach { region ->
                drawRegionHighlight(
                    region, cx, headCY, headRX, headRY,
                    neckTopY, neckBotY, neckW,
                    shoulderY, shoulderX, armPitY, elbowY, wristY,
                    upperArmW, forearmW,
                    chestY, waistY, waistX, hipY, hipX, crotchY,
                    thighTopX, kneeY, kneeX, calfX, ankleY, ankleX, footBottomY,
                    highlightColor.copy(alpha = 0.45f),
                    highlightColor.copy(alpha = 0.18f)
                )
            }
        }

        // Region labels
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(start = 160.dp),
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

// =========================================================================
//  Region highlight drawing
// =========================================================================

@Suppress("LongParameterList")
private fun DrawScope.drawRegionHighlight(
    region: BodyRegion,
    cx: Float, headCY: Float, headRX: Float, headRY: Float,
    neckTopY: Float, neckBotY: Float, neckW: Float,
    shoulderY: Float, shoulderX: Float, armPitY: Float, elbowY: Float, wristY: Float,
    upperArmW: Float, forearmW: Float,
    chestY: Float, waistY: Float, waistX: Float, hipY: Float, hipX: Float, crotchY: Float,
    thighTopX: Float, kneeY: Float, kneeX: Float, calfX: Float, ankleY: Float, ankleX: Float,
    footBottomY: Float,
    solidColor: Color, glowColor: Color
) {
    when (region) {
        BodyRegion.HEAD, BodyRegion.NERVOUS_SYSTEM -> {
            drawOval(solidColor, Offset(cx - headRX * 1.1f, headCY - headRY * 1.1f),
                Size(headRX * 2.2f, headRY * 2.2f))
        }

        BodyRegion.EYES -> {
            val eyeR = headRX * 0.18f
            drawCircle(solidColor, eyeR, Offset(cx - headRX * 0.38f, headCY - headRY * 0.1f))
            drawCircle(solidColor, eyeR, Offset(cx + headRX * 0.38f, headCY - headRY * 0.1f))
        }

        BodyRegion.EARS -> {
            val earR = headRX * 0.18f
            drawOval(solidColor, Offset(cx - headRX - earR, headCY - earR * 1.5f), Size(earR * 2, earR * 3))
            drawOval(solidColor, Offset(cx + headRX - earR, headCY - earR * 1.5f), Size(earR * 2, earR * 3))
        }

        BodyRegion.THROAT -> {
            drawOval(solidColor,
                Offset(cx - neckW * 1.5f, neckTopY),
                Size(neckW * 3f, neckBotY - neckTopY))
        }

        BodyRegion.HEART -> {
            val hx = cx - waistX * 0.25f
            val hy = shoulderY + (chestY - shoulderY) * 0.55f
            val hr = waistX * 0.32f
            drawCircle(solidColor, hr, Offset(hx, hy))
            // pulse ring
            drawCircle(glowColor, hr * 1.5f, Offset(hx, hy))
        }

        BodyRegion.LUNGS -> {
            val lungMidY = shoulderY + (chestY - shoulderY) * 0.55f
            val lungW = waistX * 0.65f
            val lungH = (chestY - shoulderY) * 0.7f
            drawOval(solidColor, Offset(cx - waistX - lungW * 0.1f, lungMidY - lungH / 2), Size(lungW, lungH))
            drawOval(solidColor, Offset(cx + waistX * 0.35f, lungMidY - lungH / 2), Size(lungW, lungH))
        }

        BodyRegion.STOMACH -> {
            val sy = chestY + (waistY - chestY) * 0.45f
            val sw = waistX * 0.9f
            val sh = (waistY - chestY) * 0.65f
            drawOval(solidColor, Offset(cx - sw / 2, sy - sh / 2), Size(sw, sh))
        }

        BodyRegion.LIVER -> {
            val lx = cx + waistX * 0.12f
            val ly = chestY + (waistY - chestY) * 0.25f
            drawOval(solidColor, Offset(lx - waistX * 0.3f, ly - waistX * 0.2f),
                Size(waistX * 0.62f, waistX * 0.42f))
        }

        BodyRegion.KIDNEYS -> {
            val ky = waistY - (waistY - chestY) * 0.05f
            val kw = waistX * 0.28f
            val kh = waistX * 0.38f
            drawOval(solidColor, Offset(cx - waistX * 0.55f, ky - kh / 2), Size(kw, kh))
            drawOval(solidColor, Offset(cx + waistX * 0.27f, ky - kh / 2), Size(kw, kh))
        }

        BodyRegion.JOINTS -> {
            val jr = waistX * 0.18f
            // Shoulders
            drawCircle(solidColor, jr, Offset(cx - shoulderX, shoulderY))
            drawCircle(solidColor, jr, Offset(cx + shoulderX, shoulderY))
            // Elbows
            drawCircle(solidColor, jr * 0.8f, Offset(cx - shoulderX, elbowY))
            drawCircle(solidColor, jr * 0.8f, Offset(cx + shoulderX, elbowY))
            // Knees
            drawCircle(solidColor, jr, Offset(cx - kneeX, kneeY))
            drawCircle(solidColor, jr, Offset(cx + kneeX, kneeY))
            // Hips
            drawCircle(solidColor, jr * 0.9f, Offset(cx - hipX * 0.7f, hipY))
            drawCircle(solidColor, jr * 0.9f, Offset(cx + hipX * 0.7f, hipY))
        }

        BodyRegion.SKIN -> {
            // Soft glow outline around the whole body
            drawOval(glowColor, Offset(cx - headRX * 1.4f, headCY - headRY * 1.4f),
                Size(headRX * 2.8f, headRY * 2.8f))
            drawOval(glowColor,
                Offset(cx - shoulderX - upperArmW * 1.2f, shoulderY - size.height * 0.01f),
                Size((shoulderX + upperArmW * 1.2f) * 2, hipY - shoulderY + size.height * 0.03f))
            // Legs
            drawOval(glowColor, Offset(cx - hipX, crotchY), Size(hipX * 2, ankleY - crotchY))
        }

        BodyRegion.BLOOD, BodyRegion.IMMUNE -> {
            val dotR = waistX * 0.1f
            val points = listOf(
                Offset(cx, shoulderY + (chestY - shoulderY) * 0.4f),
                Offset(cx - waistX * 0.5f, chestY + (waistY - chestY) * 0.3f),
                Offset(cx + waistX * 0.4f, chestY + (waistY - chestY) * 0.6f),
                Offset(cx, waistY + (hipY - waistY) * 0.5f),
                Offset(cx - kneeX * 0.8f, crotchY + (kneeY - crotchY) * 0.4f),
                Offset(cx + kneeX * 0.8f, crotchY + (kneeY - crotchY) * 0.5f),
                Offset(cx - shoulderX * 0.7f, armPitY + (elbowY - armPitY) * 0.5f),
                Offset(cx + shoulderX * 0.7f, armPitY + (elbowY - armPitY) * 0.4f),
            )
            points.forEach { drawCircle(solidColor, dotR, it) }
        }

        BodyRegion.HORMONES -> {
            // Thyroid (neck)
            drawOval(solidColor,
                Offset(cx - neckW * 1.8f, neckTopY + (neckBotY - neckTopY) * 0.2f),
                Size(neckW * 3.6f, (neckBotY - neckTopY) * 0.6f))
            // Adrenal / pituitary
            drawCircle(solidColor, waistX * 0.15f, Offset(cx, headCY))
            // Pancreas area
            drawOval(solidColor, Offset(cx - waistX * 0.35f, waistY - waistX * 0.12f),
                Size(waistX * 0.7f, waistX * 0.24f))
        }

        BodyRegion.BONES -> {
            val boneStroke = 6f
            // Spine
            drawLine(solidColor, Offset(cx, neckBotY), Offset(cx, crotchY), strokeWidth = boneStroke, cap = StrokeCap.Round)
            // Femurs
            drawLine(solidColor, Offset(cx - thighTopX * 0.7f, crotchY), Offset(cx - kneeX, kneeY), strokeWidth = boneStroke, cap = StrokeCap.Round)
            drawLine(solidColor, Offset(cx + thighTopX * 0.7f, crotchY), Offset(cx + kneeX, kneeY), strokeWidth = boneStroke, cap = StrokeCap.Round)
            // Tibias
            drawLine(solidColor, Offset(cx - kneeX, kneeY), Offset(cx - ankleX, ankleY), strokeWidth = boneStroke * 0.8f, cap = StrokeCap.Round)
            drawLine(solidColor, Offset(cx + kneeX, kneeY), Offset(cx + ankleX, ankleY), strokeWidth = boneStroke * 0.8f, cap = StrokeCap.Round)
            // Humeri
            drawLine(solidColor, Offset(cx - shoulderX, shoulderY), Offset(cx - shoulderX, elbowY), strokeWidth = boneStroke * 0.8f, cap = StrokeCap.Round)
            drawLine(solidColor, Offset(cx + shoulderX, shoulderY), Offset(cx + shoulderX, elbowY), strokeWidth = boneStroke * 0.8f, cap = StrokeCap.Round)
            // Ribs hint
            for (i in 0..3) {
                val ry = shoulderY + (chestY - shoulderY) * (0.25f + i * 0.2f)
                val ribHalf = waistX * (0.85f - i * 0.08f)
                drawLine(solidColor, Offset(cx - ribHalf, ry), Offset(cx + ribHalf, ry),
                    strokeWidth = 3f, cap = StrokeCap.Round)
            }
        }

        BodyRegion.FULL_BODY -> {
            drawOval(glowColor.copy(alpha = glowColor.alpha * 1.3f),
                Offset(cx - headRX * 1.2f, headCY - headRY * 1.2f),
                Size(headRX * 2.4f, headRY * 2.4f))
            drawOval(glowColor.copy(alpha = glowColor.alpha * 1.3f),
                Offset(cx - shoulderX, shoulderY),
                Size(shoulderX * 2f, hipY - shoulderY))
            drawOval(glowColor.copy(alpha = glowColor.alpha * 1.3f),
                Offset(cx - hipX, crotchY),
                Size(hipX * 2, ankleY - crotchY))
        }
    }
}
