#!/usr/bin/env python3
"""
AulaAir - Controlador Hands-Free para Profesores (<80 líneas)
Requisitos: ./.venv/bin/pip install mediapipe opencv-python pyautogui
"""
import os, time, math, urllib.request, cv2, mediapipe as mp, pyautogui
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

pyautogui.FAILSAFE = False
screen_w, screen_h = pyautogui.size()
MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "hand_landmarker.task")
if not os.path.exists(MODEL_PATH):
    print("⬇️ Descargando modelo MediaPipe HandLandmarker...")
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
for _ in range(15):  # Espera breve mientras macOS concede permiso de cámara
    if cap.isOpened(): break
    time.sleep(0.5); cap.open(0)
last_action, prev_x = 0.0, None

def extended_fingers(lm):
    return [lm[t].y < lm[p].y for t, p in zip([8, 12, 16, 20], [6, 10, 14, 18])]

print("🎓 AulaAir Hands-Free Activo | Presiona [Q] en la ventana para salir")
while cap.isOpened():
    ok, frame = cap.read()
    if not ok: time.sleep(0.1); continue
    frame = cv2.flip(frame, 1)
    h, w, _ = frame.shape
    mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
    res = detector.detect(mp_img)
    now, status = time.time(), "ESPERANDO GESTO DOCENTE..."

    if res.hand_landmarks:
        lm = res.hand_landmarks[0]
        for c in vision.HandLandmarksConnections.HAND_CONNECTIONS:
            p1, p2 = (int(lm[c.start].x * w), int(lm[c.start].y * h)), (int(lm[c.end].x * w), int(lm[c.end].y * h))
            cv2.line(frame, p1, p2, (248, 189, 56), 2)
        for i, pt in enumerate(lm):
            cv2.circle(frame, (int(pt.x * w), int(pt.y * h)), 6 if i in (4, 8) else 4, (212, 245, 0) if i == 8 else (255, 255, 255), -1)

        idx, mid, ring, pinky = extended_fingers(lm)
        pinch = math.hypot(lm[8].x - lm[4].x, lm[8].y - lm[4].y) < 0.048

        if idx and not mid and not ring and not pinky:
            pyautogui.moveTo(int(lm[8].x * screen_w), int(lm[8].y * screen_h), _pause=False)
            status = "PUNTERO LASER ACTIVO (Indice #8)"
            if pinch and now - last_action > 0.6:
                pyautogui.click(); status, last_action = "CLICK / SUBRAYAR!", now
        elif not any([idx, mid, ring, pinky]) and now - last_action > 1.2:
            pyautogui.press("right"); status, last_action = "SIGUIENTE DIAPOSITIVA -> (Puno)", now
        elif idx and mid and not ring and not pinky and now - last_action > 1.2:
            pyautogui.press("left"); status, last_action = "<- DIAPOSITIVA ANTERIOR (2 Dedos)", now
        elif all([idx, mid, ring, pinky]):
            if prev_x is not None and now - last_action > 1.0 and abs(lm[9].x - prev_x) > 0.16:
                pyautogui.press("right" if lm[9].x > prev_x else "left")
                status, last_action = "SWIPE CAMBIO DE LAMINA", now
            prev_x = lm[9].x

    cv2.rectangle(frame, (0, 0), (w, 48), (15, 23, 42), -1)
    cv2.putText(frame, f"AULA-AIR | {status}", (16, 32), cv2.FONT_HERSHEY_SIMPLEX, 0.72, (212, 245, 0), 2)
    cv2.imshow("AulaAir - Control Docente Hands-Free", frame)
    if cv2.waitKey(1) & 0xFF == ord('q'): break

cap.release()
cv2.destroyAllWindows()
