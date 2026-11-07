package com.example.hakatron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraSensorHudCard(
    landmarks: List<Offset>,
    activeGesture: TeacherGesture,
    pinchDist: Float,
    autoDemoEnabled: Boolean,
    liveCameraActive: Boolean = false,
    handDetectedNow: Boolean = false,
    onToggleAutoDemo: () -> Unit,
    onPointerDragged: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0B1324))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            when {
                                handDetectedNow -> Color(0xFF00F5D4)
                                liveCameraActive -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            },
                            CircleShape
                        )
                )
                Text(
                    text = when {
                        handDetectedNow -> "WEBCAM EN VIVO · MANO DETECTADA"
                        liveCameraActive -> "WEBCAM ACTIVA · MUESTRA TU MANO"
                        else -> "SENSOR MEDIAPIPE · 21 NODOS"
                    },
                    color = Color(0xFFF8FAFC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Auto-Demo / Manual Toggle Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (handDetectedNow) Color(0xFF00F5D4).copy(alpha = 0.24f)
                        else if (autoDemoEnabled) Color(0xFF38BDF8).copy(alpha = 0.18f)
                        else Color(0xFF1E293B)
                    )
                    .border(
                        1.dp,
                        if (handDetectedNow) Color(0xFF00F5D4)
                        else if (autoDemoEnabled) Color(0xFF38BDF8)
                        else Color(0xFF475569),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleAutoDemo() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when {
                        handDetectedNow -> "CAM LIVE"
                        autoDemoEnabled -> "AUTO-DEMO"
                        else -> "MANUAL"
                    },
                    color = if (handDetectedNow) Color(0xFF00F5D4)
                    else if (autoDemoEnabled) Color(0xFF38BDF8)
                    else Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Interactive 21-Point Skeleton Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF13223E), Color(0xFF070C17))
                    )
                )
                .border(1.dp, activeGesture.badgeColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val norm = Offset(
                            (change.position.x / size.width.toFloat()).coerceIn(0.05f, 0.95f),
                            (change.position.y / size.height.toFloat()).coerceIn(0.05f, 0.95f)
                        )
                        onPointerDragged(norm)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Crosshair axes
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(size.width * 0.5f, 0f),
                    end = Offset(size.width * 0.5f, size.height),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, size.height * 0.5f),
                    end = Offset(size.width, size.height * 0.5f),
                    strokeWidth = 1f
                )

                if (landmarks.size >= 21) {
                    // Draw bones
                    HandSkeletonMath.CONNECTIONS.forEach { (i, j) ->
                        val p1 = landmarks[i]
                        val p2 = landmarks[j]
                        drawLine(
                            color = activeGesture.badgeColor.copy(alpha = 0.78f),
                            start = Offset(p1.x * size.width, p1.y * size.height),
                            end = Offset(p2.x * size.width, p2.y * size.height),
                            strokeWidth = 3.2f
                        )
                    }

                    // Pinch vector between Thumb #4 and Index #8
                    val p4 = landmarks[4]
                    val p8 = landmarks[8]
                    drawLine(
                        color = Color(0xFFF59E0B),
                        start = Offset(p4.x * size.width, p4.y * size.height),
                        end = Offset(p8.x * size.width, p8.y * size.height),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )

                    // Draw 21 joint nodes
                    landmarks.forEachIndexed { idx, pt ->
                        val center = Offset(pt.x * size.width, pt.y * size.height)
                        val isKeyNode = idx == 0 || idx == 4 || idx == 8
                        drawCircle(
                            color = when (idx) {
                                8 -> Color(0xFF00F5D4)
                                4 -> Color(0xFFF59E0B)
                                0 -> Color(0xFF38BDF8)
                                else -> Color.White
                            },
                            radius = if (isKeyNode) 6.5f else 4f,
                            center = center
                        )
                        if (isKeyNode) {
                            drawCircle(
                                color = activeGesture.badgeColor.copy(alpha = 0.45f),
                                radius = 12f,
                                center = center,
                                style = Stroke(width = 1.8f)
                            )
                        }
                    }
                }
            }

            // Overlay HUD labels inside camera box
            Text(
                text = "CAM_0 · FLIP_HORIZONTAL",
                color = Color(0xFF64748B),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            Text(
                text = "d(#4,#8) = ${(pinchDist * 100).toInt() / 100f}",
                color = if (pinchDist < 0.045f) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }

        // Finger extension telemetry pills
        val fingerLabels = listOf("PULGAR", "ÍNDICE", "MEDIO", "ANULAR", "MEÑIQUE")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            fingerLabels.forEachIndexed { index, name ->
                val isUp = activeGesture.fingerStates.getOrElse(index) { false }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isUp) activeGesture.badgeColor.copy(alpha = 0.18f)
                            else Color(0xFF151F32),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (isUp) activeGesture.badgeColor.copy(alpha = 0.65f)
                            else Color(0xFF1E293B),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.take(3),
                        color = if (isUp) activeGesture.badgeColor else Color(0xFF64748B),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun GestureDeckCard(
    activeGesture: TeacherGesture,
    onSelectGesture: (TeacherGesture) -> Unit,
    onTriggerPrevSlide: () -> Unit,
    onTriggerNextSlide: () -> Unit,
    onClearInk: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0B1324))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "GESTOS DOCENTES → PYAUTOGUI",
            color = Color(0xFFF8FAFC),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )

        TeacherGesture.entries.forEach { gesture ->
            val selected = gesture == activeGesture
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (selected) gesture.badgeColor.copy(alpha = 0.16f)
                        else Color(0xFF111C33)
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) gesture.badgeColor else Color(0xFF1E293B),
                        shape = RoundedCornerShape(11.dp)
                    )
                    .clickable {
                        onSelectGesture(gesture)
                        when (gesture) {
                            TeacherGesture.NEXT_SLIDE_FIST -> onTriggerNextSlide()
                            TeacherGesture.PREV_SLIDE_PEACE -> onTriggerPrevSlide()
                            TeacherGesture.OPEN_PALM_CLEAR -> onClearInk()
                            else -> {}
                        }
                    }
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = gesture.title,
                        color = if (selected) gesture.badgeColor else Color(0xFFF1F5F9),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = gesture.pyAutoGuiCmd,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            gesture.badgeColor.copy(alpha = if (selected) 0.28f else 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = gesture.id,
                        color = gesture.badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun PyAutoGuiTelemetryCard(
    logs: List<TelemetryLog>,
    speakerNotes: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0B1324))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "NOTAS DEL PROFESOR & CONSOLA OS",
            color = Color(0xFFF8FAFC),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111C33), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF263554), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "💡 $speakerNotes",
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF060A12), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            logs.take(4).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "[${entry.timestamp}] ${entry.command}",
                        color = entry.accent,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${entry.confidence}%",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
