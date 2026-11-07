package com.example.hakatron

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot

actual object PlatformHandTracker {
    actual val isRealCameraSupported: Boolean = true

    private var prevPalmX: Float? = null
    private var lastSwipeTimeMs: Double = 0.0

    actual fun startCamera() {
        try {
            js("if (window.startAulaAirCamera) { window.startAulaAirCamera(); }")
        } catch (_: Throwable) {
        }
    }

    actual fun pollTelemetry(): LiveHandTelemetry? {
        return try {
            val state: dynamic = js("window.__aulaAirHandState") ?: return null
            val ready = state.cameraReady == true
            val detected = state.handDetected == true
            if (!ready) return LiveHandTelemetry(cameraReady = false, handDetected = false)
            if (!detected) {
                prevPalmX = null
                return LiveHandTelemetry(cameraReady = true, handDetected = false)
            }

            val rawPoints: dynamic = state.landmarks ?: return LiveHandTelemetry(cameraReady = true, handDetected = false)
            val len = (rawPoints.length as? Number)?.toInt() ?: 0
            if (len < 21) return LiveHandTelemetry(cameraReady = true, handDetected = false)

            val points = ArrayList<Offset>(21)
            for (i in 0 until 21) {
                val pt = rawPoints[i]
                val rawX = (pt.x as? Number)?.toFloat() ?: 0.5f
                val rawY = (pt.y as? Number)?.toFloat() ?: 0.5f
                // Mirror X (1f - x) so moving hand right moves pointer right on slide
                val x = (1.0f - rawX).coerceIn(0.01f, 0.99f)
                val y = rawY.coerceIn(0.01f, 0.99f)
                points.add(Offset(x, y))
            }

            val thumbExtended = hypot(points[4].x - points[17].x, points[4].y - points[17].y) >
                hypot(points[3].x - points[17].x, points[3].y - points[17].y) * 1.12f
            val indexExtended = points[8].y < points[6].y
            val middleExtended = points[12].y < points[10].y
            val ringExtended = points[16].y < points[14].y
            val pinkyExtended = points[20].y < points[18].y

            val fingers = listOf(thumbExtended, indexExtended, middleExtended, ringExtended, pinkyExtended)
            val pinchDist = hypot(points[8].x - points[4].x, points[8].y - points[4].y)

            val gesture = when {
                pinchDist < 0.058f && indexExtended -> TeacherGesture.PINCH_ANNOTATE
                !indexExtended && !middleExtended && !ringExtended && !pinkyExtended -> TeacherGesture.NEXT_SLIDE_FIST
                indexExtended && middleExtended && !ringExtended && !pinkyExtended -> TeacherGesture.PREV_SLIDE_PEACE
                indexExtended && middleExtended && ringExtended && pinkyExtended -> TeacherGesture.OPEN_PALM_CLEAR
                else -> TeacherGesture.LASER_POINT
            }

            val nowMs = (js("Date.now()") as? Number)?.toDouble() ?: 0.0
            var swipe = 0
            val palmX = points[9].x
            val prev = prevPalmX
            if (prev != null && nowMs - lastSwipeTimeMs > 1100.0) {
                val dx = palmX - prev
                if (abs(dx) > 0.17f && indexExtended && middleExtended && ringExtended) {
                    swipe = if (dx > 0) 1 else -1
                    lastSwipeTimeMs = nowMs
                }
            }
            prevPalmX = palmX

            LiveHandTelemetry(
                cameraReady = true,
                handDetected = true,
                landmarks = points,
                indexTip = points[8],
                pinchDistance = pinchDist,
                extendedFingers = fingers,
                detectedGesture = gesture,
                swipeTrigger = swipe
            )
        } catch (_: Throwable) {
            null
        }
    }
}
