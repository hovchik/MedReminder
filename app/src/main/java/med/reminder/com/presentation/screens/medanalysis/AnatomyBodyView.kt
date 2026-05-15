package med.reminder.com.presentation.screens.medanalysis

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
import med.reminder.com.ai.BodyRegion
import med.reminder.com.presentation.util.translatedName

/**
 * Primary entry point — renders an interactive 3D human anatomy model.
 * Falls back to the 2D canvas version if needed.
 */
@Composable
fun AnatomyBodyView(
    highlightedRegions: List<BodyRegion>,
    modifier: Modifier = Modifier
) {
    Anatomy3DView(
        highlightedRegions = highlightedRegions,
        modifier = modifier
    )
}

// =============================================================================
//  2D Canvas Fallback (kept for offline / low-end device support)
// =============================================================================

/**
 * Holds all normalized anatomical reference points for the human body drawing.
 * All values are absolute pixel positions computed from the canvas size.
 * Based on SVG reference data from eMahtab/human-anatomy (public domain).
 */
private data class BodyPoints(
    val w: Float, val h: Float, val cx: Float,
    // Head
    val headCY: Float, val headRX: Float, val headRY: Float,
    // Neck
    val neckTopY: Float, val neckBotY: Float, val neckW: Float,
    // Shoulders & arms
    val shoulderY: Float, val shoulderX: Float,
    val armPitY: Float, val elbowY: Float, val wristY: Float,
    val upperArmW: Float, val forearmW: Float,
    val handY: Float, val handW: Float,
    // Torso
    val chestY: Float, val waistY: Float, val waistX: Float,
    val hipY: Float, val hipX: Float, val crotchY: Float,
    // Legs
    val thighTopX: Float, val kneeY: Float, val kneeX: Float,
    val calfX: Float, val ankleY: Float, val ankleX: Float,
    val footBottomY: Float, val toeX: Float, val heelX: Float,
    // Internal anatomy Y positions
    val collarBoneY: Float,
    val sternumTopY: Float, val sternumBotY: Float,
    val navelY: Float,
    val pelvisTopY: Float,
)

private fun computeBodyPoints(w: Float, h: Float): BodyPoints {
    val cx = w * 0.5f
    return BodyPoints(
        w = w, h = h, cx = cx,
        headCY = h * 0.065f,
        headRX = w * 0.078f,
        headRY = w * 0.095f,
        neckTopY = h * 0.065f + w * 0.095f, // headCY + headRY
        neckBotY = h * 0.175f,
        neckW = w * 0.042f,
        shoulderY = h * 0.195f,
        shoulderX = w * 0.295f,
        armPitY = h * 0.24f,
        elbowY = h * 0.385f,
        wristY = h * 0.50f,
        upperArmW = w * 0.042f,
        forearmW = w * 0.035f,
        handY = h * 0.56f,
        handW = w * 0.032f,
        chestY = h * 0.30f,
        waistY = h * 0.41f,
        waistX = w * 0.155f,
        hipY = h * 0.47f,
        hipX = w * 0.20f,
        crotchY = h * 0.52f,
        thighTopX = w * 0.145f,
        kneeY = h * 0.67f,
        kneeX = w * 0.115f,
        calfX = w * 0.085f,
        ankleY = h * 0.855f,
        ankleX = w * 0.060f,
        footBottomY = h * 0.925f,
        toeX = w * 0.105f,
        heelX = w * 0.070f,
        collarBoneY = h * 0.19f,
        sternumTopY = h * 0.21f,
        sternumBotY = h * 0.34f,
        navelY = h * 0.40f,
        pelvisTopY = h * 0.44f,
    )
}

@Composable
internal fun AnatomyBodyView2D(
    highlightedRegions: List<BodyRegion>,
    modifier: Modifier = Modifier
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val anatomyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val anatomyStrokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .width(240.dp)
                .height(420.dp)
        ) {
            val bp = computeBodyPoints(size.width, size.height)

            // Layer 1: Body silhouette fill
            val body = buildBodyPath(bp)
            drawPath(body, bodyColor, style = Fill)

            // Layer 2: Internal anatomy (always visible, subtle)
            drawSkeletonDetails(bp, anatomyStrokeColor)
            drawOrganDetails(bp, anatomyColor, anatomyStrokeColor)

            // Layer 3: Region highlights
            highlightedRegions.forEach { region ->
                drawRegionHighlight(
                    region, bp,
                    highlightColor.copy(alpha = 0.45f),
                    highlightColor.copy(alpha = 0.18f)
                )
            }

            // Layer 4: Body outline (on top)
            drawPath(body, outlineColor, style = Stroke(1.6f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        }

        // Region labels
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(start = 170.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            highlightedRegions.forEach { region ->
                Text(
                    text = region.translatedName(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// =============================================================================
//  Body silhouette path — smooth bezier curves with detailed hands (fingers)
//  Reference: eMahtab/human-anatomy SVG paths (public domain)
// =============================================================================

private fun buildBodyPath(bp: BodyPoints): Path = Path().apply {
    val cx = bp.cx

    // ---- Top of head ----
    moveTo(cx, bp.headCY - bp.headRY)

    // Right side of head (oval arc approximation via cubics)
    cubicTo(
        cx + bp.headRX * 0.55f, bp.headCY - bp.headRY,
        cx + bp.headRX, bp.headCY - bp.headRY * 0.55f,
        cx + bp.headRX, bp.headCY
    )
    // Right ear bump
    cubicTo(
        cx + bp.headRX * 1.02f, bp.headCY + bp.headRY * 0.10f,
        cx + bp.headRX * 1.06f, bp.headCY + bp.headRY * 0.20f,
        cx + bp.headRX * 1.02f, bp.headCY + bp.headRY * 0.35f
    )
    cubicTo(
        cx + bp.headRX * 0.95f, bp.headCY + bp.headRY * 0.50f,
        cx + bp.headRX * 0.65f, bp.headCY + bp.headRY * 0.85f,
        cx + bp.headRX * 0.35f, bp.headCY + bp.headRY * 0.95f
    )
    // Jaw line to chin
    cubicTo(
        cx + bp.headRX * 0.20f, bp.headCY + bp.headRY,
        cx + bp.neckW * 1.1f, bp.neckTopY - bp.headRY * 0.05f,
        cx + bp.neckW, bp.neckTopY
    )

    // Right neck
    cubicTo(
        cx + bp.neckW, bp.neckTopY + (bp.neckBotY - bp.neckTopY) * 0.3f,
        cx + bp.neckW * 1.1f, bp.neckBotY - (bp.neckBotY - bp.neckTopY) * 0.15f,
        cx + bp.neckW * 1.2f, bp.neckBotY
    )

    // Right trapezius -> shoulder
    cubicTo(
        cx + bp.shoulderX * 0.35f, bp.shoulderY - bp.h * 0.015f,
        cx + bp.shoulderX * 0.7f, bp.shoulderY - bp.h * 0.008f,
        cx + bp.shoulderX, bp.shoulderY
    )

    // Right deltoid cap
    cubicTo(
        cx + bp.shoulderX + bp.upperArmW * 0.6f, bp.shoulderY + bp.h * 0.012f,
        cx + bp.shoulderX + bp.upperArmW, bp.shoulderY + bp.h * 0.025f,
        cx + bp.shoulderX + bp.upperArmW * 0.7f, bp.armPitY
    )

    // Right upper arm outer (bicep bulge)
    cubicTo(
        cx + bp.shoulderX + bp.upperArmW * 0.6f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.2f,
        cx + bp.shoulderX + bp.upperArmW * 0.45f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.55f,
        cx + bp.shoulderX - bp.upperArmW * 0.2f, bp.elbowY
    )

    // Right forearm outer
    cubicTo(
        cx + bp.shoulderX - bp.upperArmW * 0.35f, bp.elbowY + (bp.wristY - bp.elbowY) * 0.1f,
        cx + bp.shoulderX - bp.forearmW * 0.3f, bp.elbowY + (bp.wristY - bp.elbowY) * 0.45f,
        cx + bp.shoulderX - bp.forearmW, bp.wristY
    )

    // ---- Right hand with fingers (based on SVG hand reference) ----
    val rHandBaseX = cx + bp.shoulderX - bp.forearmW
    val fLen = (bp.handY - bp.wristY) * 0.75f  // finger length
    val fW = bp.handW * 0.28f                    // finger width
    val palmBot = bp.wristY + (bp.handY - bp.wristY) * 0.45f

    // Wrist to outer palm
    cubicTo(
        rHandBaseX - bp.handW * 0.1f, bp.wristY + fW,
        rHandBaseX - bp.handW * 0.35f, palmBot - fW * 2,
        rHandBaseX - bp.handW * 0.5f, palmBot
    )
    // Pinky finger
    lineTo(rHandBaseX - bp.handW * 0.55f, palmBot + fLen * 0.65f)
    cubicTo(
        rHandBaseX - bp.handW * 0.52f, palmBot + fLen * 0.72f,
        rHandBaseX - bp.handW * 0.42f, palmBot + fLen * 0.72f,
        rHandBaseX - bp.handW * 0.38f, palmBot + fLen * 0.63f
    )
    // Ring finger
    lineTo(rHandBaseX - bp.handW * 0.30f, palmBot + fLen * 0.85f)
    cubicTo(
        rHandBaseX - bp.handW * 0.27f, palmBot + fLen * 0.92f,
        rHandBaseX - bp.handW * 0.17f, palmBot + fLen * 0.92f,
        rHandBaseX - bp.handW * 0.13f, palmBot + fLen * 0.83f
    )
    // Middle finger (longest)
    lineTo(rHandBaseX - bp.handW * 0.05f, palmBot + fLen)
    cubicTo(
        rHandBaseX - bp.handW * 0.02f, palmBot + fLen * 1.07f,
        rHandBaseX + bp.handW * 0.08f, palmBot + fLen * 1.07f,
        rHandBaseX + bp.handW * 0.12f, palmBot + fLen * 0.98f
    )
    // Index finger
    lineTo(rHandBaseX + bp.handW * 0.18f, palmBot + fLen * 0.8f)
    cubicTo(
        rHandBaseX + bp.handW * 0.22f, palmBot + fLen * 0.87f,
        rHandBaseX + bp.handW * 0.32f, palmBot + fLen * 0.87f,
        rHandBaseX + bp.handW * 0.35f, palmBot + fLen * 0.78f
    )
    // Index to thumb web
    lineTo(rHandBaseX + bp.handW * 0.4f, palmBot + fLen * 0.3f)
    // Thumb
    cubicTo(
        rHandBaseX + bp.handW * 0.5f, palmBot + fLen * 0.15f,
        rHandBaseX + bp.handW * 0.65f, palmBot - fW,
        rHandBaseX + bp.handW * 0.7f, palmBot - fW * 2.5f
    )
    cubicTo(
        rHandBaseX + bp.handW * 0.72f, palmBot - fW * 3.2f,
        rHandBaseX + bp.handW * 0.55f, palmBot - fW * 3.8f,
        rHandBaseX + bp.handW * 0.45f, palmBot - fW * 3.5f
    )
    // Back to wrist inner
    cubicTo(
        rHandBaseX + bp.handW * 0.5f, bp.wristY + fW * 1.2f,
        cx + bp.shoulderX + bp.forearmW * 0.4f, bp.wristY + fW * 0.3f,
        cx + bp.shoulderX + bp.forearmW * 0.3f, bp.wristY
    )

    // Right forearm inner
    cubicTo(
        cx + bp.shoulderX + bp.forearmW * 0.5f, bp.elbowY + (bp.wristY - bp.elbowY) * 0.5f,
        cx + bp.shoulderX - bp.upperArmW * 0.1f, bp.elbowY + (bp.wristY - bp.elbowY) * 0.15f,
        cx + bp.shoulderX - bp.upperArmW * 0.5f, bp.elbowY
    )

    // Right upper arm inner -> armpit
    cubicTo(
        cx + bp.shoulderX - bp.upperArmW * 0.5f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.6f,
        cx + bp.shoulderX - bp.upperArmW * 1.0f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.15f,
        cx + bp.shoulderX - bp.upperArmW * 1.3f, bp.armPitY
    )

    // Right torso: armpit -> chest -> waist -> hip
    cubicTo(
        cx + bp.shoulderX - bp.upperArmW * 1.5f, bp.armPitY + (bp.chestY - bp.armPitY) * 0.5f,
        cx + bp.waistX + bp.w * 0.06f, bp.chestY,
        cx + bp.waistX + bp.w * 0.025f, bp.chestY + (bp.waistY - bp.chestY) * 0.5f
    )
    cubicTo(
        cx + bp.waistX, bp.waistY - (bp.waistY - bp.chestY) * 0.15f,
        cx + bp.waistX - bp.w * 0.008f, bp.waistY,
        cx + bp.waistX, bp.waistY + (bp.hipY - bp.waistY) * 0.3f
    )
    cubicTo(
        cx + bp.waistX + bp.w * 0.015f, bp.waistY + (bp.hipY - bp.waistY) * 0.6f,
        cx + bp.hipX - bp.w * 0.01f, bp.hipY - (bp.hipY - bp.waistY) * 0.15f,
        cx + bp.hipX, bp.hipY
    )

    // Right hip -> outer thigh
    cubicTo(
        cx + bp.hipX + bp.w * 0.01f, bp.hipY + (bp.crotchY - bp.hipY) * 0.4f,
        cx + bp.thighTopX + bp.w * 0.06f, bp.hipY + (bp.crotchY - bp.hipY) * 0.7f,
        cx + bp.thighTopX + bp.w * 0.04f, bp.crotchY
    )
    cubicTo(
        cx + bp.thighTopX + bp.w * 0.025f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.3f,
        cx + bp.kneeX + bp.w * 0.035f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.7f,
        cx + bp.kneeX + bp.w * 0.025f, bp.kneeY
    )

    // Right outer calf (gastrocnemius bulge)
    cubicTo(
        cx + bp.kneeX + bp.w * 0.02f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.1f,
        cx + bp.calfX + bp.w * 0.032f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.28f,
        cx + bp.calfX + bp.w * 0.025f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.42f
    )
    cubicTo(
        cx + bp.calfX + bp.w * 0.015f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.6f,
        cx + bp.ankleX + bp.w * 0.012f, bp.ankleY - (bp.ankleY - bp.kneeY) * 0.12f,
        cx + bp.ankleX, bp.ankleY
    )

    // Right foot
    cubicTo(
        cx + bp.ankleX - bp.w * 0.008f, bp.ankleY + (bp.footBottomY - bp.ankleY) * 0.3f,
        cx + bp.ankleX - bp.heelX * 0.15f, bp.footBottomY - (bp.footBottomY - bp.ankleY) * 0.05f,
        cx + bp.heelX * 0.1f, bp.footBottomY
    )
    // Toe bumps
    val footTipY = bp.footBottomY + (bp.footBottomY - bp.ankleY) * 0.06f
    cubicTo(
        cx + bp.heelX * 0.35f, footTipY,
        cx + bp.toeX * 0.45f, footTipY + (bp.footBottomY - bp.ankleY) * 0.02f,
        cx + bp.toeX * 0.6f, footTipY
    )
    cubicTo(
        cx + bp.toeX * 0.75f, footTipY - (bp.footBottomY - bp.ankleY) * 0.02f,
        cx + bp.toeX * 0.9f, bp.footBottomY - (bp.footBottomY - bp.ankleY) * 0.05f,
        cx + bp.toeX, bp.footBottomY - (bp.footBottomY - bp.ankleY) * 0.12f
    )
    // Foot dorsum -> inner ankle
    cubicTo(
        cx + bp.toeX * 0.85f, bp.footBottomY - (bp.footBottomY - bp.ankleY) * 0.4f,
        cx + bp.ankleX + bp.w * 0.018f, bp.ankleY + (bp.footBottomY - bp.ankleY) * 0.2f,
        cx + bp.ankleX - bp.w * 0.012f, bp.ankleY
    )

    // Right inner calf
    cubicTo(
        cx + bp.ankleX - bp.w * 0.018f, bp.ankleY - (bp.ankleY - bp.kneeY) * 0.1f,
        cx + bp.calfX - bp.w * 0.015f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.65f,
        cx + bp.calfX - bp.w * 0.012f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.42f
    )
    cubicTo(
        cx + bp.calfX - bp.w * 0.008f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.2f,
        cx + bp.kneeX - bp.w * 0.008f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.08f,
        cx + bp.kneeX - bp.w * 0.015f, bp.kneeY
    )

    // Right inner thigh -> crotch
    cubicTo(
        cx + bp.kneeX - bp.w * 0.015f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.65f,
        cx + bp.thighTopX - bp.w * 0.02f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.25f,
        cx + bp.w * 0.025f, bp.crotchY + bp.h * 0.01f
    )

    // Crotch arch
    cubicTo(
        cx + bp.w * 0.01f, bp.crotchY + bp.h * 0.022f,
        cx - bp.w * 0.01f, bp.crotchY + bp.h * 0.022f,
        cx - bp.w * 0.025f, bp.crotchY + bp.h * 0.01f
    )

    // ---- Left leg (mirror) ----

    // Left inner thigh
    cubicTo(
        cx - bp.thighTopX + bp.w * 0.02f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.25f,
        cx - bp.kneeX + bp.w * 0.015f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.65f,
        cx - bp.kneeX + bp.w * 0.015f, bp.kneeY
    )

    // Left inner calf
    cubicTo(
        cx - bp.kneeX + bp.w * 0.008f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.08f,
        cx - bp.calfX + bp.w * 0.008f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.2f,
        cx - bp.calfX + bp.w * 0.012f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.42f
    )
    cubicTo(
        cx - bp.calfX + bp.w * 0.015f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.65f,
        cx - bp.ankleX + bp.w * 0.018f, bp.ankleY - (bp.ankleY - bp.kneeY) * 0.1f,
        cx - bp.ankleX + bp.w * 0.012f, bp.ankleY
    )

    // Left foot dorsum
    cubicTo(
        cx - bp.ankleX - bp.w * 0.018f, bp.ankleY + (bp.footBottomY - bp.ankleY) * 0.2f,
        cx - bp.toeX * 0.85f, bp.footBottomY - (bp.footBottomY - bp.ankleY) * 0.4f,
        cx - bp.toeX, bp.footBottomY - (bp.footBottomY - bp.ankleY) * 0.12f
    )
    // Toe bumps
    cubicTo(
        cx - bp.toeX * 0.9f, bp.footBottomY - (bp.footBottomY - bp.ankleY) * 0.05f,
        cx - bp.toeX * 0.75f, footTipY - (bp.footBottomY - bp.ankleY) * 0.02f,
        cx - bp.toeX * 0.6f, footTipY
    )
    cubicTo(
        cx - bp.toeX * 0.45f, footTipY + (bp.footBottomY - bp.ankleY) * 0.02f,
        cx - bp.heelX * 0.35f, footTipY,
        cx - bp.heelX * 0.1f, bp.footBottomY
    )
    // Left sole -> heel -> ankle
    cubicTo(
        cx - bp.ankleX + bp.heelX * 0.15f, bp.footBottomY - (bp.footBottomY - bp.ankleY) * 0.05f,
        cx - bp.ankleX + bp.w * 0.008f, bp.ankleY + (bp.footBottomY - bp.ankleY) * 0.3f,
        cx - bp.ankleX, bp.ankleY
    )

    // Left outer calf
    cubicTo(
        cx - bp.ankleX - bp.w * 0.012f, bp.ankleY - (bp.ankleY - bp.kneeY) * 0.12f,
        cx - bp.calfX - bp.w * 0.015f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.6f,
        cx - bp.calfX - bp.w * 0.025f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.42f
    )
    cubicTo(
        cx - bp.calfX - bp.w * 0.032f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.28f,
        cx - bp.kneeX - bp.w * 0.02f, bp.kneeY + (bp.ankleY - bp.kneeY) * 0.1f,
        cx - bp.kneeX - bp.w * 0.025f, bp.kneeY
    )

    // Left outer thigh
    cubicTo(
        cx - bp.kneeX - bp.w * 0.035f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.7f,
        cx - bp.thighTopX - bp.w * 0.025f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.3f,
        cx - bp.thighTopX - bp.w * 0.04f, bp.crotchY
    )

    // Left hip
    cubicTo(
        cx - bp.thighTopX - bp.w * 0.06f, bp.hipY + (bp.crotchY - bp.hipY) * 0.7f,
        cx - bp.hipX - bp.w * 0.01f, bp.hipY + (bp.crotchY - bp.hipY) * 0.4f,
        cx - bp.hipX, bp.hipY
    )

    // Left torso side: hip -> waist -> armpit
    cubicTo(
        cx - bp.hipX + bp.w * 0.01f, bp.hipY - (bp.hipY - bp.waistY) * 0.15f,
        cx - bp.waistX - bp.w * 0.015f, bp.waistY + (bp.hipY - bp.waistY) * 0.6f,
        cx - bp.waistX, bp.waistY + (bp.hipY - bp.waistY) * 0.3f
    )
    cubicTo(
        cx - bp.waistX + bp.w * 0.008f, bp.waistY,
        cx - bp.waistX, bp.waistY - (bp.waistY - bp.chestY) * 0.15f,
        cx - bp.waistX - bp.w * 0.025f, bp.chestY + (bp.waistY - bp.chestY) * 0.5f
    )
    cubicTo(
        cx - bp.waistX - bp.w * 0.06f, bp.chestY,
        cx - bp.shoulderX + bp.upperArmW * 1.5f, bp.armPitY + (bp.chestY - bp.armPitY) * 0.5f,
        cx - bp.shoulderX + bp.upperArmW * 1.3f, bp.armPitY
    )

    // Left upper arm inner
    cubicTo(
        cx - bp.shoulderX + bp.upperArmW * 1.0f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.15f,
        cx - bp.shoulderX + bp.upperArmW * 0.5f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.6f,
        cx - bp.shoulderX + bp.upperArmW * 0.5f, bp.elbowY
    )

    // Left forearm inner
    cubicTo(
        cx - bp.shoulderX + bp.upperArmW * 0.1f, bp.elbowY + (bp.wristY - bp.elbowY) * 0.15f,
        cx - bp.shoulderX - bp.forearmW * 0.5f, bp.elbowY + (bp.wristY - bp.elbowY) * 0.5f,
        cx - bp.shoulderX - bp.forearmW * 0.3f, bp.wristY
    )

    // ---- Left hand with fingers (mirror of right) ----
    val lHandBaseX = cx - bp.shoulderX + bp.forearmW

    // Back to wrist from thumb
    cubicTo(
        lHandBaseX - bp.handW * 0.5f, bp.wristY + fW * 1.2f,
        lHandBaseX - bp.handW * 0.45f, palmBot - fW * 3.5f,
        lHandBaseX - bp.handW * 0.45f, palmBot - fW * 3.5f
    )
    // Left thumb
    cubicTo(
        lHandBaseX - bp.handW * 0.55f, palmBot - fW * 3.8f,
        lHandBaseX - bp.handW * 0.72f, palmBot - fW * 3.2f,
        lHandBaseX - bp.handW * 0.7f, palmBot - fW * 2.5f
    )
    cubicTo(
        lHandBaseX - bp.handW * 0.65f, palmBot - fW,
        lHandBaseX - bp.handW * 0.5f, palmBot + fLen * 0.15f,
        lHandBaseX - bp.handW * 0.4f, palmBot + fLen * 0.3f
    )
    // Index
    lineTo(lHandBaseX - bp.handW * 0.35f, palmBot + fLen * 0.78f)
    cubicTo(
        lHandBaseX - bp.handW * 0.32f, palmBot + fLen * 0.87f,
        lHandBaseX - bp.handW * 0.22f, palmBot + fLen * 0.87f,
        lHandBaseX - bp.handW * 0.18f, palmBot + fLen * 0.8f
    )
    // Middle
    lineTo(lHandBaseX - bp.handW * 0.12f, palmBot + fLen * 0.98f)
    cubicTo(
        lHandBaseX - bp.handW * 0.08f, palmBot + fLen * 1.07f,
        lHandBaseX + bp.handW * 0.02f, palmBot + fLen * 1.07f,
        lHandBaseX + bp.handW * 0.05f, palmBot + fLen
    )
    // Ring
    lineTo(lHandBaseX + bp.handW * 0.13f, palmBot + fLen * 0.83f)
    cubicTo(
        lHandBaseX + bp.handW * 0.17f, palmBot + fLen * 0.92f,
        lHandBaseX + bp.handW * 0.27f, palmBot + fLen * 0.92f,
        lHandBaseX + bp.handW * 0.30f, palmBot + fLen * 0.85f
    )
    // Pinky
    lineTo(lHandBaseX + bp.handW * 0.38f, palmBot + fLen * 0.63f)
    cubicTo(
        lHandBaseX + bp.handW * 0.42f, palmBot + fLen * 0.72f,
        lHandBaseX + bp.handW * 0.52f, palmBot + fLen * 0.72f,
        lHandBaseX + bp.handW * 0.55f, palmBot + fLen * 0.65f
    )
    // Palm outer -> wrist
    lineTo(lHandBaseX + bp.handW * 0.5f, palmBot)
    cubicTo(
        lHandBaseX + bp.handW * 0.35f, palmBot - fW * 2,
        lHandBaseX + bp.handW * 0.1f, bp.wristY + fW,
        cx - bp.shoulderX + bp.forearmW, bp.wristY
    )

    // Left forearm outer
    cubicTo(
        cx - bp.shoulderX + bp.forearmW * 0.5f, bp.elbowY + (bp.wristY - bp.elbowY) * 0.5f,
        cx - bp.shoulderX + bp.upperArmW * 0.4f, bp.elbowY + (bp.wristY - bp.elbowY) * 0.15f,
        cx - bp.shoulderX + bp.upperArmW * 0.2f, bp.elbowY
    )

    // Left upper arm outer
    cubicTo(
        cx - bp.shoulderX - bp.upperArmW * 0.3f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.7f,
        cx - bp.shoulderX - bp.upperArmW * 0.45f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.2f,
        cx - bp.shoulderX - bp.upperArmW * 0.7f, bp.armPitY
    )

    // Left deltoid
    cubicTo(
        cx - bp.shoulderX - bp.upperArmW, bp.shoulderY + bp.h * 0.025f,
        cx - bp.shoulderX - bp.upperArmW * 0.6f, bp.shoulderY + bp.h * 0.012f,
        cx - bp.shoulderX, bp.shoulderY
    )

    // Left trapezius -> neck
    cubicTo(
        cx - bp.shoulderX * 0.7f, bp.shoulderY - bp.h * 0.008f,
        cx - bp.shoulderX * 0.35f, bp.shoulderY - bp.h * 0.015f,
        cx - bp.neckW * 1.2f, bp.neckBotY
    )

    // Left neck
    cubicTo(
        cx - bp.neckW * 1.1f, bp.neckBotY - (bp.neckBotY - bp.neckTopY) * 0.15f,
        cx - bp.neckW, bp.neckTopY + (bp.neckBotY - bp.neckTopY) * 0.3f,
        cx - bp.neckW, bp.neckTopY
    )

    // Left jaw
    cubicTo(
        cx - bp.neckW * 1.1f, bp.neckTopY - bp.headRY * 0.05f,
        cx - bp.headRX * 0.20f, bp.headCY + bp.headRY,
        cx - bp.headRX * 0.35f, bp.headCY + bp.headRY * 0.95f
    )
    // Left cheek -> ear
    cubicTo(
        cx - bp.headRX * 0.65f, bp.headCY + bp.headRY * 0.85f,
        cx - bp.headRX * 0.95f, bp.headCY + bp.headRY * 0.50f,
        cx - bp.headRX * 1.02f, bp.headCY + bp.headRY * 0.35f
    )
    // Left ear bump
    cubicTo(
        cx - bp.headRX * 1.06f, bp.headCY + bp.headRY * 0.20f,
        cx - bp.headRX * 1.02f, bp.headCY + bp.headRY * 0.10f,
        cx - bp.headRX, bp.headCY
    )
    // Left side of head -> top
    cubicTo(
        cx - bp.headRX, bp.headCY - bp.headRY * 0.55f,
        cx - bp.headRX * 0.55f, bp.headCY - bp.headRY,
        cx, bp.headCY - bp.headRY
    )

    close()
}

// =============================================================================
//  Skeleton details — always visible at low opacity
// =============================================================================

private fun DrawScope.drawSkeletonDetails(bp: BodyPoints, color: Color) {
    val cx = bp.cx
    val boneStroke = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // --- Collar bones (clavicles) ---
    val clavPath = Path().apply {
        moveTo(cx - bp.neckW * 1.5f, bp.collarBoneY)
        cubicTo(
            cx - bp.shoulderX * 0.4f, bp.collarBoneY - bp.h * 0.005f,
            cx - bp.shoulderX * 0.7f, bp.collarBoneY + bp.h * 0.008f,
            cx - bp.shoulderX * 0.92f, bp.shoulderY + bp.h * 0.005f
        )
    }
    drawPath(clavPath, color, style = boneStroke)
    val clavPathR = Path().apply {
        moveTo(cx + bp.neckW * 1.5f, bp.collarBoneY)
        cubicTo(
            cx + bp.shoulderX * 0.4f, bp.collarBoneY - bp.h * 0.005f,
            cx + bp.shoulderX * 0.7f, bp.collarBoneY + bp.h * 0.008f,
            cx + bp.shoulderX * 0.92f, bp.shoulderY + bp.h * 0.005f
        )
    }
    drawPath(clavPathR, color, style = boneStroke)

    // --- Sternum ---
    drawLine(color, Offset(cx, bp.sternumTopY), Offset(cx, bp.sternumBotY), strokeWidth = 1.5f, cap = StrokeCap.Round)

    // --- Rib cage (curved ribs) ---
    val ribCount = 7
    for (i in 0 until ribCount) {
        val t = i.toFloat() / (ribCount - 1)
        val ribY = bp.sternumTopY + (bp.sternumBotY - bp.sternumTopY) * (0.08f + t * 0.85f)
        val ribSpan = bp.waistX * (0.45f + (1f - t * 0.4f) * 0.6f)
        val sag = bp.h * 0.008f * (1f + t * 0.8f)  // ribs droop more at bottom

        // Left rib
        val leftRib = Path().apply {
            moveTo(cx - bp.w * 0.008f, ribY)
            cubicTo(
                cx - ribSpan * 0.4f, ribY + sag * 0.3f,
                cx - ribSpan * 0.8f, ribY + sag,
                cx - ribSpan, ribY + sag * 1.2f
            )
        }
        drawPath(leftRib, color, style = Stroke(0.9f, cap = StrokeCap.Round))

        // Right rib
        val rightRib = Path().apply {
            moveTo(cx + bp.w * 0.008f, ribY)
            cubicTo(
                cx + ribSpan * 0.4f, ribY + sag * 0.3f,
                cx + ribSpan * 0.8f, ribY + sag,
                cx + ribSpan, ribY + sag * 1.2f
            )
        }
        drawPath(rightRib, color, style = Stroke(0.9f, cap = StrokeCap.Round))
    }

    // --- Spine (vertebrae dots below sternum) ---
    val spineStart = bp.sternumBotY + bp.h * 0.01f
    val spineEnd = bp.crotchY - bp.h * 0.015f
    val vertebraeCount = 10
    for (i in 0 until vertebraeCount) {
        val t = i.toFloat() / (vertebraeCount - 1)
        val vy = spineStart + (spineEnd - spineStart) * t
        drawCircle(color, 1.4f, Offset(cx, vy))
    }

    // --- Pelvis outline ---
    val pelvis = Path().apply {
        moveTo(cx, bp.pelvisTopY)
        // Right iliac wing
        cubicTo(
            cx + bp.waistX * 0.3f, bp.pelvisTopY - bp.h * 0.005f,
            cx + bp.hipX * 0.85f, bp.pelvisTopY + bp.h * 0.01f,
            cx + bp.hipX * 0.9f, bp.hipY - bp.h * 0.005f
        )
        // Right to pubis
        cubicTo(
            cx + bp.hipX * 0.85f, bp.hipY + bp.h * 0.015f,
            cx + bp.w * 0.06f, bp.crotchY - bp.h * 0.015f,
            cx, bp.crotchY - bp.h * 0.005f
        )
        // Left pubis to iliac
        cubicTo(
            cx - bp.w * 0.06f, bp.crotchY - bp.h * 0.015f,
            cx - bp.hipX * 0.85f, bp.hipY + bp.h * 0.015f,
            cx - bp.hipX * 0.9f, bp.hipY - bp.h * 0.005f
        )
        // Left iliac wing back to top
        cubicTo(
            cx - bp.hipX * 0.85f, bp.pelvisTopY + bp.h * 0.01f,
            cx - bp.waistX * 0.3f, bp.pelvisTopY - bp.h * 0.005f,
            cx, bp.pelvisTopY
        )
    }
    drawPath(pelvis, color, style = Stroke(1.0f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // --- Kneecaps (patella) ---
    val kneecapRX = bp.w * 0.018f
    val kneecapRY = bp.h * 0.012f
    drawOval(color, Offset(cx - bp.kneeX - kneecapRX, bp.kneeY - kneecapRY), Size(kneecapRX * 2, kneecapRY * 2), style = boneStroke)
    drawOval(color, Offset(cx + bp.kneeX - kneecapRX, bp.kneeY - kneecapRY), Size(kneecapRX * 2, kneecapRY * 2), style = boneStroke)

    // --- Shoulder joints ---
    val sjR = bp.w * 0.014f
    drawCircle(color, sjR, Offset(cx - bp.shoulderX, bp.shoulderY), style = boneStroke)
    drawCircle(color, sjR, Offset(cx + bp.shoulderX, bp.shoulderY), style = boneStroke)
}

// =============================================================================
//  Organ details — always visible at low opacity
// =============================================================================

private fun DrawScope.drawOrganDetails(bp: BodyPoints, fillColor: Color, strokeColor: Color) {
    val cx = bp.cx
    val organStroke = Stroke(0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // --- Heart (anatomical shape) ---
    val heartCX = cx - bp.waistX * 0.15f
    val heartCY = bp.shoulderY + (bp.chestY - bp.shoulderY) * 0.55f
    val heartS = bp.waistX * 0.22f
    val heart = Path().apply {
        moveTo(heartCX, heartCY + heartS * 1.1f) // bottom tip
        cubicTo(
            heartCX - heartS * 1.3f, heartCY + heartS * 0.3f,
            heartCX - heartS * 1.3f, heartCY - heartS * 0.6f,
            heartCX - heartS * 0.65f, heartCY - heartS * 0.7f
        )
        cubicTo(
            heartCX - heartS * 0.2f, heartCY - heartS * 0.8f,
            heartCX, heartCY - heartS * 0.3f,
            heartCX, heartCY - heartS * 0.3f
        )
        cubicTo(
            heartCX, heartCY - heartS * 0.3f,
            heartCX + heartS * 0.2f, heartCY - heartS * 0.8f,
            heartCX + heartS * 0.65f, heartCY - heartS * 0.7f
        )
        cubicTo(
            heartCX + heartS * 1.3f, heartCY - heartS * 0.6f,
            heartCX + heartS * 1.3f, heartCY + heartS * 0.3f,
            heartCX, heartCY + heartS * 1.1f
        )
        close()
    }
    drawPath(heart, fillColor, style = Fill)
    drawPath(heart, strokeColor, style = organStroke)

    // --- Lungs ---
    val lungMidY = bp.shoulderY + (bp.chestY - bp.shoulderY) * 0.50f
    val lungW = bp.waistX * 0.55f
    val lungH = (bp.chestY - bp.shoulderY) * 0.72f

    // Right lung (3 lobes)
    val rLung = Path().apply {
        val lx = cx + bp.waistX * 0.1f
        val ly = lungMidY - lungH / 2
        moveTo(lx + bp.w * 0.01f, ly)
        cubicTo(lx + lungW * 0.6f, ly - lungH * 0.05f, lx + lungW, ly + lungH * 0.25f, lx + lungW * 0.95f, ly + lungH * 0.5f)
        cubicTo(lx + lungW * 0.9f, ly + lungH * 0.75f, lx + lungW * 0.6f, ly + lungH, lx + bp.w * 0.01f, ly + lungH * 0.9f)
        close()
    }
    drawPath(rLung, fillColor, style = Fill)
    drawPath(rLung, strokeColor, style = organStroke)
    // Lobe division lines
    val rlobeDivY = lungMidY - lungH * 0.05f
    drawLine(strokeColor, Offset(cx + bp.waistX * 0.12f, rlobeDivY), Offset(cx + bp.waistX * 0.1f + lungW * 0.85f, rlobeDivY + lungH * 0.1f), strokeWidth = 0.7f)
    val rlobeDivY2 = lungMidY + lungH * 0.15f
    drawLine(strokeColor, Offset(cx + bp.waistX * 0.12f, rlobeDivY2), Offset(cx + bp.waistX * 0.1f + lungW * 0.9f, rlobeDivY2 + lungH * 0.05f), strokeWidth = 0.7f)

    // Left lung (2 lobes)
    val lLung = Path().apply {
        val lx = cx - bp.waistX * 0.1f - lungW
        val ly = lungMidY - lungH / 2
        moveTo(lx + lungW - bp.w * 0.01f, ly)
        cubicTo(lx + lungW * 0.4f, ly - lungH * 0.05f, lx, ly + lungH * 0.25f, lx + lungW * 0.05f, ly + lungH * 0.5f)
        cubicTo(lx + lungW * 0.1f, ly + lungH * 0.75f, lx + lungW * 0.4f, ly + lungH, lx + lungW - bp.w * 0.01f, ly + lungH * 0.9f)
        close()
    }
    drawPath(lLung, fillColor, style = Fill)
    drawPath(lLung, strokeColor, style = organStroke)
    val llobeDivY = lungMidY + lungH * 0.05f
    drawLine(strokeColor, Offset(cx - bp.waistX * 0.12f, llobeDivY), Offset(cx - bp.waistX * 0.1f - lungW * 0.85f, llobeDivY + lungH * 0.08f), strokeWidth = 0.7f)

    // --- Liver (right side, large wedge shape) ---
    val liverTop = bp.chestY + (bp.waistY - bp.chestY) * 0.05f
    val liver = Path().apply {
        moveTo(cx + bp.waistX * 0.55f, liverTop)
        cubicTo(
            cx + bp.waistX * 0.6f, liverTop + bp.h * 0.015f,
            cx + bp.waistX * 0.55f, liverTop + bp.h * 0.04f,
            cx + bp.waistX * 0.1f, liverTop + bp.h * 0.035f
        )
        cubicTo(
            cx - bp.waistX * 0.1f, liverTop + bp.h * 0.03f,
            cx - bp.waistX * 0.15f, liverTop + bp.h * 0.015f,
            cx - bp.waistX * 0.05f, liverTop
        )
        cubicTo(
            cx + bp.waistX * 0.15f, liverTop - bp.h * 0.005f,
            cx + bp.waistX * 0.4f, liverTop - bp.h * 0.003f,
            cx + bp.waistX * 0.55f, liverTop
        )
        close()
    }
    drawPath(liver, fillColor, style = Fill)
    drawPath(liver, strokeColor, style = organStroke)

    // --- Stomach (J-shape on left side) ---
    val stomachTop = bp.chestY + (bp.waistY - bp.chestY) * 0.15f
    val stomach = Path().apply {
        moveTo(cx - bp.waistX * 0.05f, stomachTop)
        cubicTo(
            cx - bp.waistX * 0.45f, stomachTop - bp.h * 0.005f,
            cx - bp.waistX * 0.6f, stomachTop + bp.h * 0.02f,
            cx - bp.waistX * 0.55f, stomachTop + bp.h * 0.04f
        )
        cubicTo(
            cx - bp.waistX * 0.5f, stomachTop + bp.h * 0.055f,
            cx - bp.waistX * 0.15f, stomachTop + bp.h * 0.05f,
            cx, stomachTop + bp.h * 0.04f
        )
        cubicTo(
            cx + bp.waistX * 0.05f, stomachTop + bp.h * 0.035f,
            cx + bp.waistX * 0.05f, stomachTop + bp.h * 0.015f,
            cx - bp.waistX * 0.05f, stomachTop
        )
        close()
    }
    drawPath(stomach, fillColor, style = Fill)
    drawPath(stomach, strokeColor, style = organStroke)

    // --- Kidneys (bean shapes) ---
    val kidneyY = bp.waistY - bp.h * 0.02f
    val kidneyW = bp.waistX * 0.2f
    val kidneyH = bp.h * 0.03f
    // Left kidney
    val lKidney = Path().apply {
        val kx = cx - bp.waistX * 0.4f
        moveTo(kx, kidneyY - kidneyH)
        cubicTo(kx - kidneyW, kidneyY - kidneyH, kx - kidneyW, kidneyY + kidneyH, kx, kidneyY + kidneyH)
        cubicTo(kx + kidneyW * 0.6f, kidneyY + kidneyH, kx + kidneyW * 0.6f, kidneyY - kidneyH, kx, kidneyY - kidneyH)
        close()
    }
    drawPath(lKidney, fillColor, style = Fill)
    drawPath(lKidney, strokeColor, style = organStroke)
    // Right kidney
    val rKidney = Path().apply {
        val kx = cx + bp.waistX * 0.4f
        moveTo(kx, kidneyY - kidneyH)
        cubicTo(kx + kidneyW, kidneyY - kidneyH, kx + kidneyW, kidneyY + kidneyH, kx, kidneyY + kidneyH)
        cubicTo(kx - kidneyW * 0.6f, kidneyY + kidneyH, kx - kidneyW * 0.6f, kidneyY - kidneyH, kx, kidneyY - kidneyH)
        close()
    }
    drawPath(rKidney, fillColor, style = Fill)
    drawPath(rKidney, strokeColor, style = organStroke)

    // --- Large intestine (colon) outline ---
    val colonTop = bp.waistY + bp.h * 0.005f
    val colonBot = bp.hipY - bp.h * 0.01f
    val colonW = bp.waistX * 0.5f
    val colon = Path().apply {
        // Ascending (right)
        moveTo(cx + colonW, colonBot)
        lineTo(cx + colonW, colonTop)
        // Transverse
        cubicTo(cx + colonW * 0.5f, colonTop - bp.h * 0.008f, cx - colonW * 0.5f, colonTop - bp.h * 0.008f, cx - colonW, colonTop)
        // Descending (left)
        lineTo(cx - colonW, colonBot)
        // Sigmoid
        cubicTo(cx - colonW * 0.5f, colonBot + bp.h * 0.012f, cx - colonW * 0.1f, colonBot + bp.h * 0.008f, cx, colonBot)
    }
    drawPath(colon, strokeColor, style = Stroke(0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // --- Small intestine (squiggly lines) ---
    val siTop = colonTop + bp.h * 0.008f
    val siBot = colonBot - bp.h * 0.005f
    val siW = colonW * 0.7f
    val siLines = 4
    for (i in 0 until siLines) {
        val t = i.toFloat() / (siLines - 1)
        val sy = siTop + (siBot - siTop) * t
        val dir = if (i % 2 == 0) 1f else -1f
        val si = Path().apply {
            moveTo(cx - siW * 0.8f, sy)
            cubicTo(
                cx - siW * 0.3f, sy + dir * bp.h * 0.005f,
                cx + siW * 0.3f, sy - dir * bp.h * 0.005f,
                cx + siW * 0.8f, sy
            )
        }
        drawPath(si, strokeColor.copy(alpha = strokeColor.alpha * 0.7f), style = Stroke(0.6f, cap = StrokeCap.Round))
    }

    // --- Navel ---
    drawCircle(strokeColor, bp.w * 0.006f, Offset(cx, bp.navelY))
}

// =============================================================================
//  Region highlight overlays
// =============================================================================

@Suppress("LongParameterList")
private fun DrawScope.drawRegionHighlight(
    region: BodyRegion, bp: BodyPoints,
    solidColor: Color, glowColor: Color
) {
    val cx = bp.cx
    when (region) {
        BodyRegion.HEAD, BodyRegion.NERVOUS_SYSTEM -> {
            drawOval(solidColor, Offset(cx - bp.headRX * 1.15f, bp.headCY - bp.headRY * 1.15f),
                Size(bp.headRX * 2.3f, bp.headRY * 2.3f))
        }

        BodyRegion.EYES -> {
            val eyeR = bp.headRX * 0.18f
            drawCircle(solidColor, eyeR, Offset(cx - bp.headRX * 0.38f, bp.headCY - bp.headRY * 0.1f))
            drawCircle(solidColor, eyeR, Offset(cx + bp.headRX * 0.38f, bp.headCY - bp.headRY * 0.1f))
        }

        BodyRegion.EARS -> {
            val earR = bp.headRX * 0.2f
            drawOval(solidColor, Offset(cx - bp.headRX * 1.06f - earR * 0.5f, bp.headCY + bp.headRY * 0.08f - earR), Size(earR * 1.2f, earR * 2.5f))
            drawOval(solidColor, Offset(cx + bp.headRX * 1.06f - earR * 0.7f, bp.headCY + bp.headRY * 0.08f - earR), Size(earR * 1.2f, earR * 2.5f))
        }

        BodyRegion.THROAT -> {
            drawOval(solidColor,
                Offset(cx - bp.neckW * 1.5f, bp.neckTopY),
                Size(bp.neckW * 3f, bp.neckBotY - bp.neckTopY))
        }

        BodyRegion.HEART -> {
            val heartCX = cx - bp.waistX * 0.15f
            val heartCY = bp.shoulderY + (bp.chestY - bp.shoulderY) * 0.55f
            val heartS = bp.waistX * 0.28f
            drawCircle(solidColor, heartS, Offset(heartCX, heartCY))
            drawCircle(glowColor, heartS * 1.6f, Offset(heartCX, heartCY))
        }

        BodyRegion.LUNGS -> {
            val lungMidY = bp.shoulderY + (bp.chestY - bp.shoulderY) * 0.50f
            val lungW = bp.waistX * 0.58f
            val lungH = (bp.chestY - bp.shoulderY) * 0.75f
            drawOval(solidColor, Offset(cx + bp.waistX * 0.08f, lungMidY - lungH / 2), Size(lungW, lungH))
            drawOval(solidColor, Offset(cx - bp.waistX * 0.08f - lungW, lungMidY - lungH / 2), Size(lungW, lungH))
        }

        BodyRegion.STOMACH -> {
            val sy = bp.chestY + (bp.waistY - bp.chestY) * 0.35f
            val sw = bp.waistX * 0.75f
            val sh = (bp.waistY - bp.chestY) * 0.5f
            drawOval(solidColor, Offset(cx - sw * 0.6f, sy - sh / 2), Size(sw, sh))
        }

        BodyRegion.LIVER -> {
            val liverTop = bp.chestY + (bp.waistY - bp.chestY) * 0.05f
            drawOval(solidColor,
                Offset(cx - bp.waistX * 0.15f, liverTop - bp.h * 0.005f),
                Size(bp.waistX * 0.72f, bp.h * 0.045f))
        }

        BodyRegion.KIDNEYS -> {
            val ky = bp.waistY - bp.h * 0.02f
            val kw = bp.waistX * 0.25f
            val kh = bp.h * 0.035f
            drawOval(solidColor, Offset(cx - bp.waistX * 0.4f - kw / 2, ky - kh / 2), Size(kw, kh))
            drawOval(solidColor, Offset(cx + bp.waistX * 0.4f - kw / 2, ky - kh / 2), Size(kw, kh))
        }

        BodyRegion.JOINTS -> {
            val jr = bp.waistX * 0.18f
            drawCircle(solidColor, jr, Offset(cx - bp.shoulderX, bp.shoulderY))
            drawCircle(solidColor, jr, Offset(cx + bp.shoulderX, bp.shoulderY))
            drawCircle(solidColor, jr * 0.75f, Offset(cx - bp.shoulderX, bp.elbowY))
            drawCircle(solidColor, jr * 0.75f, Offset(cx + bp.shoulderX, bp.elbowY))
            drawCircle(solidColor, jr, Offset(cx - bp.kneeX, bp.kneeY))
            drawCircle(solidColor, jr, Offset(cx + bp.kneeX, bp.kneeY))
            drawCircle(solidColor, jr * 0.85f, Offset(cx - bp.hipX * 0.7f, bp.hipY))
            drawCircle(solidColor, jr * 0.85f, Offset(cx + bp.hipX * 0.7f, bp.hipY))
            drawCircle(solidColor, jr * 0.6f, Offset(cx - bp.shoulderX, bp.wristY))
            drawCircle(solidColor, jr * 0.6f, Offset(cx + bp.shoulderX, bp.wristY))
            drawCircle(solidColor, jr * 0.55f, Offset(cx - bp.ankleX, bp.ankleY))
            drawCircle(solidColor, jr * 0.55f, Offset(cx + bp.ankleX, bp.ankleY))
        }

        BodyRegion.SKIN -> {
            drawOval(glowColor, Offset(cx - bp.headRX * 1.4f, bp.headCY - bp.headRY * 1.4f),
                Size(bp.headRX * 2.8f, bp.headRY * 2.8f))
            drawOval(glowColor,
                Offset(cx - bp.shoulderX - bp.upperArmW * 1.2f, bp.shoulderY - bp.h * 0.01f),
                Size((bp.shoulderX + bp.upperArmW * 1.2f) * 2, bp.hipY - bp.shoulderY + bp.h * 0.03f))
            drawOval(glowColor, Offset(cx - bp.hipX, bp.crotchY), Size(bp.hipX * 2, bp.ankleY - bp.crotchY))
        }

        BodyRegion.BLOOD, BodyRegion.IMMUNE -> {
            val dotR = bp.waistX * 0.09f
            val points = listOf(
                Offset(cx, bp.shoulderY + (bp.chestY - bp.shoulderY) * 0.4f),
                Offset(cx - bp.waistX * 0.5f, bp.chestY + (bp.waistY - bp.chestY) * 0.3f),
                Offset(cx + bp.waistX * 0.4f, bp.chestY + (bp.waistY - bp.chestY) * 0.6f),
                Offset(cx, bp.waistY + (bp.hipY - bp.waistY) * 0.5f),
                Offset(cx - bp.kneeX * 0.8f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.4f),
                Offset(cx + bp.kneeX * 0.8f, bp.crotchY + (bp.kneeY - bp.crotchY) * 0.5f),
                Offset(cx - bp.shoulderX * 0.7f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.5f),
                Offset(cx + bp.shoulderX * 0.7f, bp.armPitY + (bp.elbowY - bp.armPitY) * 0.4f),
            )
            points.forEach { drawCircle(solidColor, dotR, it) }
        }

        BodyRegion.HORMONES -> {
            // Thyroid
            drawOval(solidColor,
                Offset(cx - bp.neckW * 1.8f, bp.neckTopY + (bp.neckBotY - bp.neckTopY) * 0.2f),
                Size(bp.neckW * 3.6f, (bp.neckBotY - bp.neckTopY) * 0.6f))
            // Pituitary
            drawCircle(solidColor, bp.waistX * 0.12f, Offset(cx, bp.headCY + bp.headRY * 0.3f))
            // Adrenals (above kidneys)
            val adY = bp.waistY - bp.h * 0.038f
            drawOval(solidColor, Offset(cx - bp.waistX * 0.5f, adY), Size(bp.waistX * 0.22f, bp.h * 0.012f))
            drawOval(solidColor, Offset(cx + bp.waistX * 0.28f, adY), Size(bp.waistX * 0.22f, bp.h * 0.012f))
            // Pancreas
            drawOval(solidColor, Offset(cx - bp.waistX * 0.35f, bp.waistY - bp.waistX * 0.08f),
                Size(bp.waistX * 0.7f, bp.waistX * 0.16f))
        }

        BodyRegion.BONES -> {
            val boneStroke = 5f
            // Spine
            drawLine(solidColor, Offset(cx, bp.neckBotY), Offset(cx, bp.crotchY), strokeWidth = boneStroke, cap = StrokeCap.Round)
            // Clavicles
            drawLine(solidColor, Offset(cx - bp.neckW * 1.5f, bp.collarBoneY), Offset(cx - bp.shoulderX * 0.9f, bp.shoulderY), strokeWidth = boneStroke * 0.7f, cap = StrokeCap.Round)
            drawLine(solidColor, Offset(cx + bp.neckW * 1.5f, bp.collarBoneY), Offset(cx + bp.shoulderX * 0.9f, bp.shoulderY), strokeWidth = boneStroke * 0.7f, cap = StrokeCap.Round)
            // Ribs
            for (i in 0..5) {
                val ry = bp.sternumTopY + (bp.sternumBotY - bp.sternumTopY) * (0.1f + i * 0.17f)
                val ribHalf = bp.waistX * (0.6f + (1f - i * 0.07f) * 0.5f)
                val sag = bp.h * 0.01f * (1f + i * 0.3f)
                val ribL = Path().apply {
                    moveTo(cx, ry)
                    cubicTo(cx - ribHalf * 0.5f, ry + sag * 0.4f, cx - ribHalf * 0.85f, ry + sag, cx - ribHalf, ry + sag * 1.1f)
                }
                drawPath(ribL, solidColor, style = Stroke(boneStroke * 0.5f, cap = StrokeCap.Round))
                val ribR = Path().apply {
                    moveTo(cx, ry)
                    cubicTo(cx + ribHalf * 0.5f, ry + sag * 0.4f, cx + ribHalf * 0.85f, ry + sag, cx + ribHalf, ry + sag * 1.1f)
                }
                drawPath(ribR, solidColor, style = Stroke(boneStroke * 0.5f, cap = StrokeCap.Round))
            }
            // Pelvis
            val pelvis = Path().apply {
                moveTo(cx, bp.pelvisTopY)
                cubicTo(cx + bp.waistX * 0.3f, bp.pelvisTopY, cx + bp.hipX * 0.85f, bp.pelvisTopY + bp.h * 0.01f, cx + bp.hipX * 0.9f, bp.hipY)
                cubicTo(cx + bp.hipX * 0.85f, bp.hipY + bp.h * 0.015f, cx + bp.w * 0.06f, bp.crotchY - bp.h * 0.015f, cx, bp.crotchY)
                cubicTo(cx - bp.w * 0.06f, bp.crotchY - bp.h * 0.015f, cx - bp.hipX * 0.85f, bp.hipY + bp.h * 0.015f, cx - bp.hipX * 0.9f, bp.hipY)
                cubicTo(cx - bp.hipX * 0.85f, bp.pelvisTopY + bp.h * 0.01f, cx - bp.waistX * 0.3f, bp.pelvisTopY, cx, bp.pelvisTopY)
            }
            drawPath(pelvis, solidColor, style = Stroke(boneStroke * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Femurs
            drawLine(solidColor, Offset(cx - bp.thighTopX * 0.7f, bp.crotchY), Offset(cx - bp.kneeX, bp.kneeY), strokeWidth = boneStroke, cap = StrokeCap.Round)
            drawLine(solidColor, Offset(cx + bp.thighTopX * 0.7f, bp.crotchY), Offset(cx + bp.kneeX, bp.kneeY), strokeWidth = boneStroke, cap = StrokeCap.Round)
            // Tibias
            drawLine(solidColor, Offset(cx - bp.kneeX, bp.kneeY), Offset(cx - bp.ankleX, bp.ankleY), strokeWidth = boneStroke * 0.8f, cap = StrokeCap.Round)
            drawLine(solidColor, Offset(cx + bp.kneeX, bp.kneeY), Offset(cx + bp.ankleX, bp.ankleY), strokeWidth = boneStroke * 0.8f, cap = StrokeCap.Round)
            // Humeri
            drawLine(solidColor, Offset(cx - bp.shoulderX, bp.shoulderY), Offset(cx - bp.shoulderX, bp.elbowY), strokeWidth = boneStroke * 0.7f, cap = StrokeCap.Round)
            drawLine(solidColor, Offset(cx + bp.shoulderX, bp.shoulderY), Offset(cx + bp.shoulderX, bp.elbowY), strokeWidth = boneStroke * 0.7f, cap = StrokeCap.Round)
            // Ulna/Radius
            drawLine(solidColor, Offset(cx - bp.shoulderX, bp.elbowY), Offset(cx - bp.shoulderX - bp.forearmW, bp.wristY), strokeWidth = boneStroke * 0.6f, cap = StrokeCap.Round)
            drawLine(solidColor, Offset(cx + bp.shoulderX, bp.elbowY), Offset(cx + bp.shoulderX - bp.forearmW, bp.wristY), strokeWidth = boneStroke * 0.6f, cap = StrokeCap.Round)
        }

        BodyRegion.FULL_BODY -> {
            drawOval(glowColor.copy(alpha = glowColor.alpha * 1.3f),
                Offset(cx - bp.headRX * 1.2f, bp.headCY - bp.headRY * 1.2f),
                Size(bp.headRX * 2.4f, bp.headRY * 2.4f))
            drawOval(glowColor.copy(alpha = glowColor.alpha * 1.3f),
                Offset(cx - bp.shoulderX, bp.shoulderY),
                Size(bp.shoulderX * 2f, bp.hipY - bp.shoulderY))
            drawOval(glowColor.copy(alpha = glowColor.alpha * 1.3f),
                Offset(cx - bp.hipX, bp.crotchY),
                Size(bp.hipX * 2, bp.ankleY - bp.crotchY))
        }
    }
}
