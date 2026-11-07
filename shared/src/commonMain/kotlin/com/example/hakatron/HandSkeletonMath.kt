package com.example.hakatron

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object HandSkeletonMath {
    // 21 standard MediaPipe Hand connections
    val CONNECTIONS = listOf(
        // Thumb
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        // Index
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        // Middle
        5 to 9, 9 to 10, 10 to 11, 11 to 12,
        // Ring
        9 to 13, 13 to 14, 14 to 15, 15 to 16,
        // Pinky
        13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20
    )

    val FINGERTIPS = setOf(4, 8, 12, 16, 20)

    /**
     * Generates the 21 MediaPipe landmarks normalized in [0f..1f] space
     * so that Landmark #8 (Index Finger Tip) aligns with [pointerPos] when pointing,
     * and the remaining joints articulate according to [gesture].
     */
    fun computeLandmarks(
        pointerPos: Offset,
        gesture: TeacherGesture,
        timeSec: Float
    ): List<Offset> {
        val breatheX = sin(timeSec * 2.2f) * 0.006f
        val breatheY = cos(timeSec * 1.8f) * 0.006f

        // Base anchor (Wrist #0) relative to index tip (#8)
        val wristX = (pointerPos.x + 0.06f + breatheX).coerceIn(0.15f, 0.88f)
        val wristY = (pointerPos.y + 0.34f + breatheY).coerceIn(0.28f, 0.94f)
        val wrist = Offset(wristX, wristY)

        // MCP knuckles (5, 9, 13, 17)
        val mcp5 = Offset(wrist.x - 0.07f, wrist.y - 0.14f)
        val mcp9 = Offset(wrist.x - 0.02f, wrist.y - 0.15f)
        val mcp13 = Offset(wrist.x + 0.03f, wrist.y - 0.14f)
        val mcp17 = Offset(wrist.x + 0.08f, wrist.y - 0.12f)

        // Thumb joints (1, 2, 3, 4)
        val cmc1 = Offset(wrist.x - 0.06f, wrist.y - 0.05f)
        val mcp2 = Offset(wrist.x - 0.11f, wrist.y - 0.10f)
        val ip3 = Offset(wrist.x - 0.14f, wrist.y - 0.15f)

        // Index finger (5 -> 6 -> 7 -> 8)
        val indexExtended = gesture.fingerStates[1]
        val tip8 = if (indexExtended) {
            pointerPos
        } else {
            Offset(mcp5.x + 0.01f, mcp5.y + 0.04f)
        }
        val pip6 = Offset(
            mcp5.x + (tip8.x - mcp5.x) * 0.42f,
            mcp5.y + (tip8.y - mcp5.y) * 0.42f
        )
        val dip7 = Offset(
            mcp5.x + (tip8.x - mcp5.x) * 0.72f,
            mcp5.y + (tip8.y - mcp5.y) * 0.72f
        )

        val tip4 = when (gesture) {
            TeacherGesture.PINCH_ANNOTATE -> Offset(
                tip8.x - 0.014f,
                tip8.y + 0.014f
            )
            TeacherGesture.NEXT_SLIDE_FIST -> Offset(mcp9.x - 0.02f, mcp9.y + 0.03f)
            else -> Offset(wrist.x - 0.17f, wrist.y - 0.19f)
        }

        // Helper for Middle (9..12), Ring (13..16), Pinky (17..20)
        fun buildFinger(mcp: Offset, extended: Boolean, spreadX: Float, lengthY: Float): List<Offset> {
            return if (extended) {
                val pip = Offset(mcp.x + spreadX * 0.35f, mcp.y - lengthY * 0.45f)
                val dip = Offset(mcp.x + spreadX * 0.70f, mcp.y - lengthY * 0.75f)
                val tip = Offset(mcp.x + spreadX, mcp.y - lengthY)
                listOf(pip, dip, tip)
            } else {
                // Curled into palm
                val pip = Offset(mcp.x + spreadX * 0.15f, mcp.y - 0.03f)
                val dip = Offset(mcp.x + 0.005f, mcp.y + 0.02f)
                val tip = Offset(mcp.x + 0.01f, mcp.y + 0.045f)
                listOf(pip, dip, tip)
            }
        }

        val middleJoints = buildFinger(mcp9, gesture.fingerStates[2], -0.01f, 0.19f)
        val ringJoints = buildFinger(mcp13, gesture.fingerStates[3], 0.03f, 0.17f)
        val pinkyJoints = buildFinger(mcp17, gesture.fingerStates[4], 0.06f, 0.14f)

        return listOf(
            wrist,              // 0: WRIST
            cmc1,               // 1: THUMB_CMC
            mcp2,               // 2: THUMB_MCP
            ip3,                // 3: THUMB_IP
            tip4,               // 4: THUMB_TIP
            mcp5,               // 5: INDEX_FINGER_MCP
            pip6,               // 6: INDEX_FINGER_PIP
            dip7,               // 7: INDEX_FINGER_DIP
            tip8,               // 8: INDEX_FINGER_TIP
            mcp9,               // 9: MIDDLE_FINGER_MCP
            middleJoints[0],    // 10: MIDDLE_FINGER_PIP
            middleJoints[1],    // 11: MIDDLE_FINGER_DIP
            middleJoints[2],    // 12: MIDDLE_FINGER_TIP
            mcp13,              // 13: RING_FINGER_MCP
            ringJoints[0],      // 14: RING_FINGER_PIP
            ringJoints[1],      // 15: RING_FINGER_DIP
            ringJoints[2],      // 16: RING_FINGER_TIP
            mcp17,              // 17: PINKY_MCP
            pinkyJoints[0],     // 18: PINKY_PIP
            pinkyJoints[1],     // 19: PINKY_DIP
            pinkyJoints[2]      // 20: PINKY_TIP
        )
    }

    fun pinchDistance(landmarks: List<Offset>): Float {
        if (landmarks.size <= 8) return 1f
        val p4 = landmarks[4]
        val p8 = landmarks[8]
        return hypot(p8.x - p4.x, p8.y - p4.y)
    }

    /**
     * Generates a smooth figure-8 teacher pointing trajectory when Auto-Demo is enabled.
     */
    fun autoDemoPointer(timeSec: Float, hotspots: List<SlideHotspot>): Offset {
        if (hotspots.isNotEmpty()) {
            // Dwell smoothly near each hotspot for 3.5 seconds
            val cycleDuration = 3.5f
            val totalIndex = (timeSec / cycleDuration).toInt()
            val currentSpot = hotspots[totalIndex % hotspots.size]
            val nextSpot = hotspots[(totalIndex + 1) % hotspots.size]
            val phase = (timeSec % cycleDuration) / cycleDuration
            // Smoothstep interpolation during the last 30% of the cycle
            val t = if (phase < 0.7f) 0f else ((phase - 0.7f) / 0.3f)
            val smoothT = t * t * (3f - 2f * t)
            val orbitX = cos(timeSec * (PI.toFloat())) * 0.018f
            val orbitY = sin(timeSec * (PI.toFloat() * 1.5f)) * 0.018f
            return Offset(
                (currentSpot.normalizedPos.x + (nextSpot.normalizedPos.x - currentSpot.normalizedPos.x) * smoothT + orbitX).coerceIn(0.08f, 0.92f),
                (currentSpot.normalizedPos.y + (nextSpot.normalizedPos.y - currentSpot.normalizedPos.y) * smoothT + orbitY).coerceIn(0.12f, 0.88f)
            )
        }
        return Offset(
            0.5f + 0.28f * sin(timeSec * 0.9f),
            0.5f + 0.22f * sin(timeSec * 1.8f)
        )
    }
}
