package com.example.hakatron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun SlideStageCanvas(
    slide: LectureSlide,
    pointerPos: Offset,
    activeGesture: TeacherGesture,
    landmarks: List<Offset>,
    inkStrokes: List<InkStroke>,
    currentStroke: List<Offset>,
    hoveredHotspot: SlideHotspot?,
    timeSec: Float,
    onPointerMoved: (Offset, Boolean) -> Unit,
    onStrokeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0A101E),
                        Color(0xFF0F172A),
                        Color(0xFF091326)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        slide.accentColor.copy(alpha = 0.55f),
                        Color(0xFF1E293B),
                        slide.accentColor.copy(alpha = 0.25f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .pointerInput(activeGesture) {
                detectTapGestures { offset ->
                    val norm = Offset(
                        (offset.x / size.width.toFloat()).coerceIn(0.02f, 0.98f),
                        (offset.y / size.height.toFloat()).coerceIn(0.02f, 0.98f)
                    )
                    onPointerMoved(norm, activeGesture == TeacherGesture.PINCH_ANNOTATE)
                    if (activeGesture == TeacherGesture.PINCH_ANNOTATE) {
                        onStrokeFinished()
                    }
                }
            }
            .pointerInput(activeGesture) {
                detectDragGestures(
                    onDragEnd = { onStrokeFinished() },
                    onDragCancel = { onStrokeFinished() }
                ) { change, _ ->
                    change.consume()
                    val norm = Offset(
                        (change.position.x / size.width.toFloat()).coerceIn(0.02f, 0.98f),
                        (change.position.y / size.height.toFloat()).coerceIn(0.02f, 0.98f)
                    )
                    onPointerMoved(norm, activeGesture == TeacherGesture.PINCH_ANNOTATE)
                }
            }
    ) {
        // Background blueprint grid + scientific diagram + ink + laser + ghost skeleton
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawChalkboardGrid(slide.accentColor)
            drawScientificVisual(
                visualType = slide.visualType,
                accent = slide.accentColor,
                timeSec = timeSec,
                pointerPos = pointerPos
            )
            drawHotspotTargets(
                hotspots = slide.hotspots,
                hoveredHotspot = hoveredHotspot,
                timeSec = timeSec
            )
            drawChalkStrokes(inkStrokes, currentStroke, slide.accentColor)
            drawGhostHandOverlay(landmarks, activeGesture)
            drawTeacherLaserBeam(pointerPos, activeGesture, timeSec)
        }

        // Top-left Academic Header Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(slide.accentColor.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                            .border(1.dp, slide.accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LÁMINA 0${slide.number} / 0${LectureRepository.slides.size}",
                            color = slide.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = slide.courseTag,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                }

                // Active coordinates badge
                Box(
                    modifier = Modifier
                        .background(Color(0xCC0F172A), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    val px = (pointerPos.x * 1920).toInt()
                    val py = (pointerPos.y * 1080).toInt()
                    Text(
                        text = "NODO #8 → ($px, $py) px",
                        color = activeGesture.badgeColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = slide.title,
                color = Color(0xFFF8FAFC),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 27.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = slide.subtitle,
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Mathematical Formula / Key Insight Banner
            Box(
                modifier = Modifier
                    .background(Color(0xD9111C33), RoundedCornerShape(10.dp))
                    .border(1.dp, slide.accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = slide.keyFormulaOrInsight,
                    color = slide.accentColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom overlay: Key bullets + Hovered Hotspot Callout Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Hovered hotspot live inspection card (triggered when teacher points at a node!)
            if (hoveredHotspot != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    hoveredHotspot.color.copy(alpha = 0.24f),
                                    Color(0xEB0F172A)
                                )
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .border(1.5.dp, hoveredHotspot.color, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(hoveredHotspot.color, CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FOCO LÁSER EN NODO INTERACTIVO: ${hoveredHotspot.label}",
                            color = hoveredHotspot.color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = hoveredHotspot.detail,
                            color = Color(0xFFF1F5F9),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Bullet points strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC090E1A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                slide.bulletPoints.forEach { bullet ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "▸",
                            color = slide.accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = bullet,
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawChalkboardGrid(accent: Color) {
    val step = 38.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = Color(0xFF1E293B).copy(alpha = 0.45f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = Color(0xFF1E293B).copy(alpha = 0.45f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += step
    }
    // Subtle radial spotlight around center
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width * 0.55f, size.height * 0.52f),
            radius = size.minDimension * 0.55f
        ),
        radius = size.minDimension * 0.55f,
        center = Offset(size.width * 0.55f, size.height * 0.52f)
    )
}

private fun DrawScope.drawScientificVisual(
    visualType: SlideVisualType,
    accent: Color,
    timeSec: Float,
    pointerPos: Offset
) {
    when (visualType) {
        SlideVisualType.NEURAL_VISION -> {
            // Draw anatomical stylized hand topology in the center of the slide
            val p0 = Offset(size.width * 0.28f, size.height * 0.78f)
            val p4 = Offset(size.width * 0.52f, size.height * 0.56f)
            val p8 = Offset(size.width * 0.74f, size.height * 0.28f)
            val p5 = Offset(size.width * 0.48f, size.height * 0.42f)

            // Vector rays
            drawLine(
                color = Color(0xFF38BDF8).copy(alpha = 0.55f),
                start = p0,
                end = p5,
                strokeWidth = 3.5f
            )
            drawLine(
                color = accent.copy(alpha = 0.75f),
                start = p5,
                end = p8,
                strokeWidth = 4.5f
            )
            drawLine(
                color = Color(0xFFF59E0B).copy(alpha = 0.65f),
                start = p0,
                end = p4,
                strokeWidth = 3.5f
            )
            // Dashed distance line between #4 and #8
            drawLine(
                color = Color(0xFFF59E0B),
                start = p4,
                end = p8,
                strokeWidth = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), phase = timeSec * 25f)
            )
        }

        SlideVisualType.PINHOLE_GEOMETRY -> {
            val handPlaneX = size.width * 0.22f
            val focalCenter = Offset(size.width * 0.50f, size.height * 0.50f)
            val screenPlaneX = size.width * 0.80f

            // Hand plane
            drawLine(
                color = Color(0xFF00F5D4).copy(alpha = 0.7f),
                start = Offset(handPlaneX, size.height * 0.30f),
                end = Offset(handPlaneX, size.height * 0.72f),
                strokeWidth = 4f
            )
            // Projector screen plane
            drawRoundRect(
                color = Color(0xFFEC4899).copy(alpha = 0.15f),
                topLeft = Offset(screenPlaneX - 24f, size.height * 0.28f),
                size = Size(48f, size.height * 0.44f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFFEC4899).copy(alpha = 0.8f),
                topLeft = Offset(screenPlaneX - 24f, size.height * 0.28f),
                size = Size(48f, size.height * 0.44f),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 3f)
            )
            // Projective rays passing through optical center
            val rayYOffset = (pointerPos.y - 0.5f) * size.height * 0.35f
            val sourcePt = Offset(handPlaneX, size.height * 0.50f + rayYOffset)
            val targetPt = Offset(screenPlaneX, size.height * 0.50f - rayYOffset)

            drawLine(
                color = Color(0xFFF59E0B).copy(alpha = 0.8f),
                start = sourcePt,
                end = targetPt,
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), phase = -timeSec * 40f)
            )
            drawCircle(
                color = Color(0xFFF59E0B),
                radius = 12f,
                center = focalCenter
            )
        }

        SlideVisualType.FOURIER_WAVES -> {
            val midY = size.height * 0.52f
            val startX = size.width * 0.12f
            val endX = size.width * 0.88f
            val widthSpan = endX - startX

            // Threshold horizontal line
            val threshY = size.height * 0.36f
            drawLine(
                color = Color(0xFFEC4899).copy(alpha = 0.6f),
                start = Offset(startX, threshY),
                end = Offset(endX, threshY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )

            val wavePath = Path()
            val steps = 90
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val x = startX + t * widthSpan
                val envelope = sin(t * PI).toFloat()
                val y = midY - sin(t * 4f * PI.toFloat() - timeSec * 3f) * (size.height * 0.20f) * envelope
                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            drawPath(
                path = wavePath,
                color = accent,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }

        SlideVisualType.CODE_BRIDGE -> {
            val leftNode = Offset(size.width * 0.30f, size.height * 0.45f)
            val rightNode = Offset(size.width * 0.72f, size.height * 0.55f)
            drawLine(
                color = Color(0xFF38BDF8).copy(alpha = 0.7f),
                start = leftNode,
                end = rightNode,
                strokeWidth = 4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), phase = -timeSec * 50f)
            )
        }
    }
}

private fun DrawScope.drawHotspotTargets(
    hotspots: List<SlideHotspot>,
    hoveredHotspot: SlideHotspot?,
    timeSec: Float
) {
    hotspots.forEach { spot ->
        val center = Offset(spot.normalizedPos.x * size.width, spot.normalizedPos.y * size.height)
        val isHovered = hoveredHotspot?.id == spot.id
        val pulse = (sin(timeSec * 4f) + 1f) * 0.5f
        val outerRadius = if (isHovered) 34f + pulse * 10f else 22f + pulse * 5f

        drawCircle(
            color = spot.color.copy(alpha = if (isHovered) 0.35f else 0.18f),
            radius = outerRadius,
            center = center
        )
        drawCircle(
            color = spot.color,
            radius = outerRadius,
            center = center,
            style = Stroke(width = if (isHovered) 3.5f else 2f)
        )
        drawCircle(
            color = Color.White,
            radius = if (isHovered) 7f else 5f,
            center = center
        )
    }
}

private fun DrawScope.drawChalkStrokes(
    inkStrokes: List<InkStroke>,
    currentStroke: List<Offset>,
    activeColor: Color
) {
    fun renderPoints(points: List<Offset>, color: Color, strokeWidth: Float) {
        if (points.size < 2) return
        val path = Path()
        points.forEachIndexed { idx, pt ->
            val px = pt.x * size.width
            val py = pt.y * size.height
            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        // Outer glow
        drawPath(
            path = path,
            color = color.copy(alpha = 0.35f),
            style = Stroke(width = strokeWidth * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Core chalk line
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    inkStrokes.forEach { stroke ->
        renderPoints(stroke.points, stroke.color, stroke.strokeWidth)
    }
    if (currentStroke.size >= 2) {
        renderPoints(currentStroke, Color(0xFFF59E0B), 5.5f)
    }
}

private fun DrawScope.drawGhostHandOverlay(
    landmarks: List<Offset>,
    gesture: TeacherGesture
) {
    if (landmarks.size < 21) return

    // Subtle skeleton silhouette projected onto the slide so the teacher sees their hand posture
    HandSkeletonMath.CONNECTIONS.forEach { (startIdx, endIdx) ->
        val a = landmarks[startIdx]
        val b = landmarks[endIdx]
        drawLine(
            color = gesture.badgeColor.copy(alpha = 0.22f),
            start = Offset(a.x * size.width, a.y * size.height),
            end = Offset(b.x * size.width, b.y * size.height),
            strokeWidth = 2.2f
        )
    }

    landmarks.forEachIndexed { index, pt ->
        val isTip = index in HandSkeletonMath.FINGERTIPS
        drawCircle(
            color = if (index == 8) gesture.badgeColor.copy(alpha = 0.85f)
            else gesture.badgeColor.copy(alpha = if (isTip) 0.45f else 0.25f),
            radius = if (index == 8) 6f else if (isTip) 4.5f else 3f,
            center = Offset(pt.x * size.width, pt.y * size.height)
        )
    }
}

private fun DrawScope.drawTeacherLaserBeam(
    pointerPos: Offset,
    gesture: TeacherGesture,
    timeSec: Float
) {
    val center = Offset(pointerPos.x * size.width, pointerPos.y * size.height)
    val beamColor = gesture.badgeColor
    val pulse = (sin(timeSec * 6f) + 1f) * 0.5f

    // Outer halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(beamColor.copy(alpha = 0.55f), Color.Transparent),
            center = center,
            radius = 38f + pulse * 12f
        ),
        radius = 38f + pulse * 12f,
        center = center
    )

    // Crosshair precision ring
    drawCircle(
        color = beamColor,
        radius = 15f + pulse * 3f,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // Core hot laser dot
    drawCircle(
        color = Color.White,
        radius = 6f,
        center = center
    )
}

/**
 * Helper to detect if the teacher's index finger laser pointer is hovering near any interactive hotspot.
 */
fun findHoveredHotspot(pointerPos: Offset, hotspots: List<SlideHotspot>): SlideHotspot? {
    return hotspots.minByOrNull { spot ->
        hypot(pointerPos.x - spot.normalizedPos.x, pointerPos.y - spot.normalizedPos.y)
    }?.takeIf { spot ->
        hypot(pointerPos.x - spot.normalizedPos.x, pointerPos.y - spot.normalizedPos.y) < 0.095f
    }
}
