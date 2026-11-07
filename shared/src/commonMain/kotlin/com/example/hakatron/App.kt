package com.example.hakatron

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
@Preview
fun App() {
    MaterialTheme {
        val slides = remember { LectureRepository.slides }
        var currentSlideIndex by remember { mutableStateOf(0) }
        val currentSlide = slides[currentSlideIndex]

        var activeGesture by remember { mutableStateOf(TeacherGesture.LASER_POINT) }
        var pointerPos by remember { mutableStateOf(Offset(0.74f, 0.28f)) }
        var autoDemoEnabled by remember { mutableStateOf(true) }
        var showPythonModal by remember { mutableStateOf(false) }

        // Per-slide chalk annotations
        val slideStrokesMap = remember { mutableStateMapOf<Int, List<InkStroke>>() }
        var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

        // Time ticker for 60fps skeleton & wave physics + lecture timer
        var timeSec by remember { mutableStateOf(0f) }
        var lectureSeconds by remember { mutableStateOf(0) }
        var liveLandmarks by remember { mutableStateOf<List<Offset>?>(null) }
        var liveCameraActive by remember { mutableStateOf(false) }
        var handDetectedNow by remember { mutableStateOf(false) }
        var lastGestureTriggerSec by remember { mutableStateOf(-5f) }

        var telemetryLogs by remember {
            mutableStateOf(
                listOf(
                    TelemetryLog("00:00", "INIT", "MediaPipe Hands Webcam + KMP Ready", 99, Color(0xFF00F5D4)),
                    TelemetryLog("00:01", "POINT", "pyautogui.moveTo(1420, 302)", 98, Color(0xFF00F5D4))
                )
            )
        }

        fun pushLog(gesture: TeacherGesture, customCmd: String? = null) {
            val mins = (lectureSeconds / 60).toString().padStart(2, '0')
            val secs = (lectureSeconds % 60).toString().padStart(2, '0')
            val entry = TelemetryLog(
                timestamp = "$mins:$secs",
                gestureName = gesture.id,
                command = customCmd ?: gesture.pyAutoGuiCmd,
                confidence = (94..99).random(),
                accent = gesture.badgeColor
            )
            telemetryLogs = (listOf(entry) + telemetryLogs).take(8)
        }

        fun goNextSlide() {
            currentSlideIndex = (currentSlideIndex + 1) % slides.size
            pushLog(TeacherGesture.NEXT_SLIDE_FIST, "pyautogui.press('right') -> Lámina 0${currentSlideIndex + 1}")
        }

        fun goPrevSlide() {
            currentSlideIndex = if (currentSlideIndex - 1 < 0) slides.size - 1 else currentSlideIndex - 1
            pushLog(TeacherGesture.PREV_SLIDE_PEACE, "pyautogui.press('left') -> Lámina 0${currentSlideIndex + 1}")
        }

        fun clearCurrentSlideInk() {
            slideStrokesMap[currentSlide.number] = emptyList()
            currentStrokePoints = emptyList()
            pushLog(TeacherGesture.OPEN_PALM_CLEAR, "Pizarra limpia en Lámina 0${currentSlide.number}")
        }

        // Start real browser camera if supported
        LaunchedEffect(Unit) {
            if (PlatformHandTracker.isRealCameraSupported) {
                PlatformHandTracker.startCamera()
            }
        }

        // 30 FPS kinematic + live webcam MediaPipe loop
        LaunchedEffect(autoDemoEnabled, currentSlideIndex) {
            while (true) {
                delay(33L)
                timeSec += 0.033f

                val live = if (PlatformHandTracker.isRealCameraSupported) {
                    PlatformHandTracker.pollTelemetry()
                } else null

                if (live != null && live.cameraReady && live.handDetected && live.landmarks.size >= 21) {
                    liveCameraActive = true
                    handDetectedNow = true
                    liveLandmarks = live.landmarks
                    // Smooth EMA interpolation for the laser pointer
                    pointerPos = Offset(
                        pointerPos.x * 0.45f + live.indexTip.x * 0.55f,
                        pointerPos.y * 0.45f + live.indexTip.y * 0.55f
                    )

                    val prevGesture = activeGesture
                    activeGesture = live.detectedGesture

                    if (live.detectedGesture == TeacherGesture.PINCH_ANNOTATE) {
                        currentStrokePoints = currentStrokePoints + pointerPos
                    } else if (prevGesture == TeacherGesture.PINCH_ANNOTATE && currentStrokePoints.size >= 2) {
                        val existing = slideStrokesMap[currentSlide.number] ?: emptyList()
                        slideStrokesMap[currentSlide.number] = existing + InkStroke(
                            points = currentStrokePoints,
                            color = Color(0xFFF59E0B)
                        )
                        pushLog(TeacherGesture.PINCH_ANNOTATE, "Trazo webcam guardado (${currentStrokePoints.size} pts)")
                        currentStrokePoints = emptyList()
                    }

                    // Cooldown-gated slide navigation via Fist, Peace (2 fingers), or Swipe
                    if (timeSec - lastGestureTriggerSec > 1.45f) {
                        when {
                            live.detectedGesture == TeacherGesture.NEXT_SLIDE_FIST || live.swipeTrigger > 0 -> {
                                lastGestureTriggerSec = timeSec
                                goNextSlide()
                            }
                            live.detectedGesture == TeacherGesture.PREV_SLIDE_PEACE || live.swipeTrigger < 0 -> {
                                lastGestureTriggerSec = timeSec
                                goPrevSlide()
                            }
                        }
                    }
                } else {
                    liveCameraActive = live?.cameraReady == true
                    handDetectedNow = false
                    liveLandmarks = null
                    if (autoDemoEnabled) {
                        pointerPos = HandSkeletonMath.autoDemoPointer(timeSec, currentSlide.hotspots)
                    }
                }
            }
        }

        // 1s Lecture chronometer
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000L)
                lectureSeconds += 1
            }
        }

        val landmarks = remember(pointerPos, activeGesture, timeSec, liveLandmarks) {
            liveLandmarks ?: HandSkeletonMath.computeLandmarks(pointerPos, activeGesture, timeSec)
        }
        val pinchDist = remember(landmarks) {
            HandSkeletonMath.pinchDistance(landmarks)
        }
        val hoveredHotspot = remember(pointerPos, currentSlide) {
            findHoveredHotspot(pointerPos, currentSlide.hotspots)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF050811),
                            Color(0xFF090F1E),
                            Color(0xFF050811)
                        )
                    )
                )
                .safeContentPadding()
        ) {
            val isWideScreen = maxWidth >= 860.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isWideScreen) 20.dp else 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Studio Lectern Bar
                TopStudioLecternBar(
                    platformName = remember { getPlatform().name },
                    lectureSeconds = lectureSeconds,
                    activeGesture = activeGesture,
                    onOpenPythonModal = { showPythonModal = true }
                )

                if (isWideScreen) {
                    // Desktop / Web / Tablet Landscape Layout: 68% Stage + 32% Teacher Sensor Deck
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.68f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SlideStageCanvas(
                                slide = currentSlide,
                                pointerPos = pointerPos,
                                activeGesture = activeGesture,
                                landmarks = landmarks,
                                inkStrokes = slideStrokesMap[currentSlide.number] ?: emptyList(),
                                currentStroke = currentStrokePoints,
                                hoveredHotspot = hoveredHotspot,
                                timeSec = timeSec,
                                onPointerMoved = { norm, isAnnotating ->
                                    autoDemoEnabled = false
                                    pointerPos = norm
                                    if (isAnnotating) {
                                        currentStrokePoints = currentStrokePoints + norm
                                    } else {
                                        val px = (norm.x * 1920).toInt()
                                        val py = (norm.y * 1080).toInt()
                                        pushLog(activeGesture, "pyautogui.moveTo($px, $py)")
                                    }
                                },
                                onStrokeFinished = {
                                    if (currentStrokePoints.size >= 2) {
                                        val existing = slideStrokesMap[currentSlide.number] ?: emptyList()
                                        slideStrokesMap[currentSlide.number] = existing + InkStroke(
                                            points = currentStrokePoints,
                                            color = Color(0xFFF59E0B)
                                        )
                                        pushLog(TeacherGesture.PINCH_ANNOTATE, "Trazo guardado (${currentStrokePoints.size} pts)")
                                    }
                                    currentStrokePoints = emptyList()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )

                            BottomSlideCarouselStrip(
                                slides = slides,
                                currentIndex = currentSlideIndex,
                                onSelectSlide = { idx ->
                                    currentSlideIndex = idx
                                    pushLog(activeGesture, "Salto directo -> Lámina 0${idx + 1}")
                                },
                                onPrev = ::goPrevSlide,
                                onNext = ::goNextSlide,
                                onClearInk = ::clearCurrentSlideInk
                            )
                        }

                        // Right Column: Sensor HUD + Gesture Deck + OS Telemetry
                        Column(
                            modifier = Modifier
                                .weight(0.32f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CameraSensorHudCard(
                                landmarks = landmarks,
                                activeGesture = activeGesture,
                                pinchDist = pinchDist,
                                autoDemoEnabled = autoDemoEnabled,
                                liveCameraActive = liveCameraActive,
                                handDetectedNow = handDetectedNow,
                                onToggleAutoDemo = { autoDemoEnabled = !autoDemoEnabled },
                                onPointerDragged = { norm ->
                                    autoDemoEnabled = false
                                    pointerPos = norm
                                }
                            )

                            GestureDeckCard(
                                activeGesture = activeGesture,
                                onSelectGesture = { g ->
                                    activeGesture = g
                                    pushLog(g)
                                },
                                onTriggerPrevSlide = ::goPrevSlide,
                                onTriggerNextSlide = ::goNextSlide,
                                onClearInk = ::clearCurrentSlideInk
                            )

                            PyAutoGuiTelemetryCard(
                                logs = telemetryLogs,
                                speakerNotes = currentSlide.speakerNotes
                            )
                        }
                    }
                } else {
                    // Mobile / Compact Portrait Layout (Android & iOS phones)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SlideStageCanvas(
                            slide = currentSlide,
                            pointerPos = pointerPos,
                            activeGesture = activeGesture,
                            landmarks = landmarks,
                            inkStrokes = slideStrokesMap[currentSlide.number] ?: emptyList(),
                            currentStroke = currentStrokePoints,
                            hoveredHotspot = hoveredHotspot,
                            timeSec = timeSec,
                            onPointerMoved = { norm, isAnnotating ->
                                autoDemoEnabled = false
                                pointerPos = norm
                                if (isAnnotating) {
                                    currentStrokePoints = currentStrokePoints + norm
                                }
                            },
                            onStrokeFinished = {
                                if (currentStrokePoints.size >= 2) {
                                    val existing = slideStrokesMap[currentSlide.number] ?: emptyList()
                                    slideStrokesMap[currentSlide.number] = existing + InkStroke(
                                        points = currentStrokePoints,
                                        color = Color(0xFFF59E0B)
                                    )
                                }
                                currentStrokePoints = emptyList()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(430.dp)
                        )

                        BottomSlideCarouselStrip(
                            slides = slides,
                            currentIndex = currentSlideIndex,
                            onSelectSlide = { idx -> currentSlideIndex = idx },
                            onPrev = ::goPrevSlide,
                            onNext = ::goNextSlide,
                            onClearInk = ::clearCurrentSlideInk
                        )

                        CameraSensorHudCard(
                            landmarks = landmarks,
                            activeGesture = activeGesture,
                            pinchDist = pinchDist,
                            autoDemoEnabled = autoDemoEnabled,
                            liveCameraActive = liveCameraActive,
                            handDetectedNow = handDetectedNow,
                            onToggleAutoDemo = { autoDemoEnabled = !autoDemoEnabled },
                            onPointerDragged = { norm ->
                                autoDemoEnabled = false
                                pointerPos = norm
                            }
                        )

                        GestureDeckCard(
                            activeGesture = activeGesture,
                            onSelectGesture = { g ->
                                activeGesture = g
                                pushLog(g)
                            },
                            onTriggerPrevSlide = ::goPrevSlide,
                            onTriggerNextSlide = ::goNextSlide,
                            onClearInk = ::clearCurrentSlideInk
                        )

                        PyAutoGuiTelemetryCard(
                            logs = telemetryLogs,
                            speakerNotes = currentSlide.speakerNotes
                        )
                    }
                }
            }

            // Modal with the <80-line Python script
            AnimatedVisibility(
                visible = showPythonModal,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PythonScriptModal(
                    onDismiss = { showPythonModal = false }
                )
            }
        }
    }
}

@Composable
private fun TopStudioLecternBar(
    platformName: String,
    lectureSeconds: Int,
    activeGesture: TeacherGesture,
    onOpenPythonModal: () -> Unit
) {
    val mins = (lectureSeconds / 60).toString().padStart(2, '0')
    val secs = (lectureSeconds % 60).toString().padStart(2, '0')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B1324))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF00F5D4), Color(0xFF38BDF8))
                        ),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✋",
                    fontSize = 18.sp
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "AULA·AIR",
                        color = Color(0xFFF8FAFC),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ATRIL DOCENTE HANDS-FREE · $platformName",
                            color = Color(0xFF38BDF8),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "Control de Diapositivas, Puntero Láser y Pizarra sin depender del hardware",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Active gesture status pill
            Row(
                modifier = Modifier
                    .background(activeGesture.badgeColor.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                    .border(1.dp, activeGesture.badgeColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(activeGesture.badgeColor, CircleShape)
                )
                Text(
                    text = activeGesture.id,
                    color = activeGesture.badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Chronometer
            Box(
                modifier = Modifier
                    .background(Color(0xFF111C33), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF263554), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⏱ $mins:$secs",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Button to inspect the <80 lines Python script
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF00F5D4), Color(0xFF38BDF8))
                        )
                    )
                    .clickable { onOpenPythonModal() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "🐍 SCRIPT PYTHON (<80 LÍNEAS)",
                    color = Color(0xFF050811),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun BottomSlideCarouselStrip(
    slides: List<LectureSlide>,
    currentIndex: Int,
    onSelectSlide: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClearInk: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B1324))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Prev Slide gesture trigger button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF151F32))
                .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable { onPrev() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "✌ Anterior",
                color = Color(0xFFA855F7),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Slide thumbnails
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slides.forEachIndexed { idx, slide ->
                val isSelected = idx == currentIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) slide.accentColor.copy(alpha = 0.18f)
                            else Color(0xFF111C33)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) slide.accentColor else Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectSlide(idx) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "0${slide.number}",
                        color = if (isSelected) slide.accentColor else Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = slide.title.substringBefore(":"),
                        color = Color(0xFFF1F5F9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        // Clear chalk button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF151F32))
                .border(1.dp, Color(0xFFEC4899).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable { onClearInk() }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = "🖐 Borrar Tiza",
                color = Color(0xFFEC4899),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Next Slide gesture trigger button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                .clickable { onNext() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "✊ Siguiente →",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun PythonScriptModal(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9050811))
            .clickable { onDismiss() }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0B1324))
                .border(1.5.dp, Color(0xFF00F5D4), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "hands_free_teacher.py · Motor MediaPipe + PyAutoGUI (75 líneas)",
                        color = Color(0xFF00F5D4),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Listo para ejecutar en tu computadora y controlar cualquier presentación o este mismo atril.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "CERRAR ✕",
                        color = Color(0xFFF8FAFC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF050811))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = LectureRepository.pythonScriptCode,
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }
    }
}