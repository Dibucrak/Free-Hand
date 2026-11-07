package com.example.hakatron

import androidx.compose.ui.geometry.Offset

data class LiveHandTelemetry(
    val cameraReady: Boolean = false,
    val handDetected: Boolean = false,
    val landmarks: List<Offset> = emptyList(),
    val indexTip: Offset = Offset(0.5f, 0.5f),
    val pinchDistance: Float = 1f,
    val extendedFingers: List<Boolean> = listOf(false, true, false, false, false),
    val detectedGesture: TeacherGesture = TeacherGesture.LASER_POINT,
    val swipeTrigger: Int = 0 // -1 prev, 0 none, +1 next
)

expect object PlatformHandTracker {
    val isRealCameraSupported: Boolean
    fun startCamera()
    fun pollTelemetry(): LiveHandTelemetry?
}
