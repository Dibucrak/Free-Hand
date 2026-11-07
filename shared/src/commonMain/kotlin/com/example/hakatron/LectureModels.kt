package com.example.hakatron

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class TeacherGesture(
    val id: String,
    val title: String,
    val subtitle: String,
    val pyAutoGuiCmd: String,
    val badgeColor: Color,
    val fingerStates: List<Boolean> // [Thumb, Index, Middle, Ring, Pinky]
) {
    LASER_POINT(
        id = "POINT",
        title = "Señalar / Puntero Láser",
        subtitle = "Índice extendido (#8) controla foco en diapositiva",
        pyAutoGuiCmd = "pyautogui.moveTo(ix * W, iy * H)",
        badgeColor = Color(0xFF00F5D4),
        fingerStates = listOf(false, true, false, false, false)
    ),
    PINCH_ANNOTATE(
        id = "PINCH",
        title = "Pellizco / Tiza Digital",
        subtitle = "Unir Pulgar (#4) + Índice (#8) traza sobre la pizarra",
        pyAutoGuiCmd = "pyautogui.mouseDown() # Trazo activo",
        badgeColor = Color(0xFFF59E0B),
        fingerStates = listOf(true, true, false, false, false)
    ),
    NEXT_SLIDE_FIST(
        id = "FIST",
        title = "Puño Cerrado → Avanzar",
        subtitle = "Cerrar articulaciones avanza a la siguiente lámina",
        pyAutoGuiCmd = "pyautogui.press('right')",
        badgeColor = Color(0xFF38BDF8),
        fingerStates = listOf(false, false, false, false, false)
    ),
    PREV_SLIDE_PEACE(
        id = "PEACE",
        title = "Dos Dedos (V) → Retroceder",
        subtitle = "Índice + Medio extendidos regresa a la lámina previa",
        pyAutoGuiCmd = "pyautogui.press('left')",
        badgeColor = Color(0xFFA855F7),
        fingerStates = listOf(false, true, true, false, false)
    ),
    OPEN_PALM_CLEAR(
        id = "PALM",
        title = "Palma Abierta → Limpiar / Pausa",
        subtitle = "5 dedos extendidos limpia anotaciones de la clase",
        pyAutoGuiCmd = "pyautogui.press('c') # Clear ink",
        badgeColor = Color(0xFFEC4899),
        fingerStates = listOf(true, true, true, true, true)
    )
}

enum class SlideVisualType {
    NEURAL_VISION,
    PINHOLE_GEOMETRY,
    FOURIER_WAVES,
    CODE_BRIDGE
}

data class SlideHotspot(
    val id: String,
    val label: String,
    val detail: String,
    val normalizedPos: Offset, // 0f..1f within slide canvas
    val color: Color
)

data class LectureSlide(
    val number: Int,
    val courseTag: String,
    val title: String,
    val subtitle: String,
    val keyFormulaOrInsight: String,
    val bulletPoints: List<String>,
    val speakerNotes: String,
    val visualType: SlideVisualType,
    val hotspots: List<SlideHotspot>,
    val accentColor: Color
)

data class InkStroke(
    val points: List<Offset>, // Normalized 0f..1f coordinates on slide
    val color: Color,
    val strokeWidth: Float = 5f
)

data class TelemetryLog(
    val timestamp: String,
    val gestureName: String,
    val command: String,
    val confidence: Int,
    val accent: Color
)

object LectureRepository {
    val slides = listOf(
        LectureSlide(
            number = 1,
            courseTag = "BIO-MECÁNICA & VISIÓN COMPUTACIONAL · SEMESTRE IV",
            title = "Topología Articular: Los 21 Landmarks de MediaPipe",
            subtitle = "Cómo transformar una cámara web estándar en un sensor espacial docente sin hardware adicional.",
            keyFormulaOrInsight = "d(p₄, p₈) = √[(x₈ - x₄)² + (y₈ - y₄)²] < 0.045  ⇒  CLICK / TRAZO",
            bulletPoints = listOf(
                "Nodo #0 (WRIST): Origen del sistema de coordenadas relativo de la palma.",
                "Nodo #8 (INDEX_FINGER_TIP): Vector director del puntero láser sobre la diapositiva.",
                "Nodos #4 y #8: Umbral euclidiano de pellizco para activar tiza digital o click."
            ),
            speakerNotes = "Pide a los alumnos observar el HUD de cámara a la derecha: al apuntar a los nodos del diagrama, la tarjeta de anatomía se expande en vivo sin tocar el mouse.",
            visualType = SlideVisualType.NEURAL_VISION,
            accentColor = Color(0xFF00F5D4),
            hotspots = listOf(
                SlideHotspot(
                    id = "WRIST_0",
                    label = "Nodo #0 · Carpo / Muñeca",
                    detail = "Ancla espacial (x₀, y₀, z₀) que estabiliza el temblor natural del brazo del profesor.",
                    normalizedPos = Offset(0.28f, 0.78f),
                    color = Color(0xFF38BDF8)
                ),
                SlideHotspot(
                    id = "THUMB_4",
                    label = "Nodo #4 · Falange Distal Pulgar",
                    detail = "Actúa como gatillo físico al acercarse al índice para conmutar el modo pizarra.",
                    normalizedPos = Offset(0.52f, 0.56f),
                    color = Color(0xFFF59E0B)
                ),
                SlideHotspot(
                    id = "INDEX_8",
                    label = "Nodo #8 · Puntero Índice",
                    detail = "Mapeado con interpolación suave directamente a las coordenadas (X, Y) del proyector.",
                    normalizedPos = Offset(0.74f, 0.28f),
                    color = Color(0xFF00F5D4)
                )
            )
        ),
        LectureSlide(
            number = 2,
            courseTag = "FÍSICA ÓPTICA & GEOMETRÍA PROYECTIVA · CLASE 02",
            title = "Transformación Homográfica: Del Sensor al Proyector",
            subtitle = "Mapeo del espacio normalizado de cámara [0,1]² a la resolución real del aula.",
            keyFormulaOrInsight = "P_pantalla = Flip_X(K · [R | t] · P_mano)   con   Suavizado EMA α = 0.35",
            bulletPoints = listOf(
                "Inversión especular (cv2.flip): Garantiza que mover la mano a la derecha mueva el láser a la derecha.",
                "Zona activa ergonómica: Evita que el docente tenga que estirar el brazo a los bordes extremos.",
                "Filtro exponencial: Elimina el jitter de alta frecuencia en proyectores 4K."
            ),
            speakerNotes = "Señala el plano de la cámara y el plano de proyección en el esquema óptico para iluminar el rayo homográfico. Cierra el puño para avanzar a la siguiente lámina.",
            visualType = SlideVisualType.PINHOLE_GEOMETRY,
            accentColor = Color(0xFF38BDF8),
            hotspots = listOf(
                SlideHotspot(
                    id = "HAND_PLANE",
                    label = "Plano Físico del Docente",
                    detail = "Espacio tridimensional capturado a 30-60 FPS sin guantes ni controles infrarrojos.",
                    normalizedPos = Offset(0.22f, 0.50f),
                    color = Color(0xFF00F5D4)
                ),
                SlideHotspot(
                    id = "FOCAL_CENTER",
                    label = "Centro Óptico (Lente Webcam)",
                    detail = "Convergencia de rayos proyectivos y normalización de profundidad Z.",
                    normalizedPos = Offset(0.50f, 0.50f),
                    color = Color(0xFFF59E0B)
                ),
                SlideHotspot(
                    id = "SCREEN_PLANE",
                    label = "Plano Pizarra Digital (W × H)",
                    detail = "Coordenadas finales inyectadas vía PyAutoGUI o motor nativo KMP.",
                    normalizedPos = Offset(0.80f, 0.42f),
                    color = Color(0xFFEC4899)
                )
            )
        ),
        LectureSlide(
            number = 3,
            courseTag = "PROCESAMIENTO DE SEÑALES · CINEMÁTICA GESTUAL",
            title = "Cinemática de Gestos: Histéresis y Antirrebote",
            subtitle = "Distinción matemática entre señalar un concepto y ejecutar un cambio de diapositiva.",
            keyFormulaOrInsight = "v_x = Δx₉ / Δt > 0.16 s⁻¹   ∧   Δt_cooldown > 1.2s  ⇒  KEY_RIGHT",
            bulletPoints = listOf(
                "Detección de Puño: Las 4 puntas (#8, #12, #16, #20) caen por debajo de sus articulaciones PIP (#6, #10, #14, #18).",
                "Ventana de enfriamiento (1.2s): Impide avanzar múltiples diapositivas por accidente.",
                "Tiza Magnética: Permite subrayar ecuaciones en tiempo real pellizcando el aire."
            ),
            speakerNotes = "Activa el gesto 'Pellizco / Tiza Digital' y dibuja directamente sobre la onda sinusoidal para mostrar a los estudiantes el umbral de disparo.",
            visualType = SlideVisualType.FOURIER_WAVES,
            accentColor = Color(0xFFF59E0B),
            hotspots = listOf(
                SlideHotspot(
                    id = "PEAK_VELOCITY",
                    label = "Pico de Velocidad Angular",
                    detail = "Instante exacto donde el desplazamiento horizontal supera el umbral de swipe.",
                    normalizedPos = Offset(0.36f, 0.34f),
                    color = Color(0xFFF59E0B)
                ),
                SlideHotspot(
                    id = "COOLDOWN_ZONE",
                    label = "Ventana Refractaria (1.2s)",
                    detail = "Bloqueo temporal inteligente tras cambiar de diapositiva para evitar falsos positivos.",
                    normalizedPos = Offset(0.68f, 0.65f),
                    color = Color(0xFFA855F7)
                )
            )
        ),
        LectureSlide(
            number = 4,
            courseTag = "ARQUITECTURA DEL PROYECTO · < 80 LÍNEAS DE PYTHON",
            title = "Motor Python MediaPipe + PyAutoGUI en Producción",
            subtitle = "El script completo listo para controlar PowerPoint, Keynote, PDF o este atril multiplataforma.",
            keyFormulaOrInsight = "pip install mediapipe opencv-python pyautogui  &&  python hands_free_teacher.py",
            bulletPoints = listOf(
                "Menos de 80 líneas de código limpio y auditable en hands_free_teacher.py.",
                "Cero dependencia de pasadores láser Bluetooth o baterías agotadas en mitad de clase.",
                "Multiplataforma real: Android, iOS y Web + Script Python nativo integrable."
            ),
            speakerNotes = "Puedes abrir el modal de código Python en la barra superior para copiar el script de 75 líneas o probar la cámara real en Web.",
            visualType = SlideVisualType.CODE_BRIDGE,
            accentColor = Color(0xFFA855F7),
            hotspots = listOf(
                SlideHotspot(
                    id = "MP_INIT",
                    label = "Pipeline MediaPipe Hands",
                    detail = "Inicialización ultraligera en CPU con min_detection_confidence=0.75.",
                    normalizedPos = Offset(0.30f, 0.35f),
                    color = Color(0xFF00F5D4)
                ),
                SlideHotspot(
                    id = "PYAUTOGUI_BIND",
                    label = "Inyección OS con PyAutoGUI",
                    detail = "Disparo directo al sistema operativo sin bloqueos (_pause=False).",
                    normalizedPos = Offset(0.72f, 0.62f),
                    color = Color(0xFF38BDF8)
                )
            )
        )
    )

    val pythonScriptCode = """
#!/usr/bin/env python3
# AulaAir - Controlador Hands-Free para Profesores (76 líneas)
import os, time, math, urllib.request, cv2, mediapipe as mp, pyautogui
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

pyautogui.FAILSAFE = False
screen_w, screen_h = pyautogui.size()
MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "hand_landmarker.task")
if not os.path.exists(MODEL_PATH):
    urllib.request.urlretrieve(
        "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task",
        MODEL_PATH
    )

opts = vision.HandLandmarkerOptions(
    base_options=mp_python.BaseOptions(model_asset_path=MODEL_PATH),
    num_hands=1, min_hand_detection_confidence=0.7, min_tracking_confidence=0.7
)
detector = vision.HandLandmarker.create_from_options(opts)
cap = cv2.VideoCapture(0)
last_action, prev_x = 0.0, None

def extended_fingers(lm):
    return [lm[t].y < lm[p].y for t, p in zip([8, 12, 16, 20], [6, 10, 14, 18])]

while cap.isOpened():
    ok, frame = cap.read()
    if not ok: continue
    frame = cv2.flip(frame, 1)
    h, w, _ = frame.shape
    mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
    res = detector.detect(mp_img)
    now = time.time()

    if res.hand_landmarks:
        lm = res.hand_landmarks[0]
        idx, mid, ring, pinky = extended_fingers(lm)
        pinch = math.hypot(lm[8].x - lm[4].x, lm[8].y - lm[4].y) < 0.048

        if idx and not mid and not ring and not pinky:
            pyautogui.moveTo(int(lm[8].x * screen_w), int(lm[8].y * screen_h), _pause=False)
            if pinch and now - last_action > 0.6:
                pyautogui.click(); last_action = now
        elif not any([idx, mid, ring, pinky]) and now - last_action > 1.2:
            pyautogui.press("right"); last_action = now
        elif idx and mid and not ring and not pinky and now - last_action > 1.2:
            pyautogui.press("left"); last_action = now
    """.trimIndent()
}
