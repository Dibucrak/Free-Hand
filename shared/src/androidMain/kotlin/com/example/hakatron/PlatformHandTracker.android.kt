package com.example.hakatron

actual object PlatformHandTracker {
    actual val isRealCameraSupported: Boolean = false
    actual fun startCamera() {}
    actual fun pollTelemetry(): LiveHandTelemetry? = null
}
