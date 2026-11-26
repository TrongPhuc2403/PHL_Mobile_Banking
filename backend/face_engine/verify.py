"""Educational eKYC, not certified PAD/liveness or legal identity verification.
No photos/embeddings are logged. inspect checks image quality only, never identity.
"""

import base64
import hashlib
import io
import json
from pathlib import Path
import sys


class Rejected(Exception):
    def __init__(self, code, message, details=None):
        self.code, self.message, self.details = code, message, details


class FaceEngine:
    def __init__(self):
        import cv2

        self.cv2 = cv2
        cv2.setNumThreads(2)
        directory = Path(__file__).resolve().parent / "models"
        self.detector = cv2.FaceDetectorYN.create(
            str(directory / "face_detection_yunet_2023mar.onnx"), "", (320, 320), 0.9, 0.3, 5000
        )
        self.recognizer = cv2.FaceRecognizerSF.create(
            str(directory / "face_recognition_sface_2021dec.onnx"), ""
        )

    @staticmethod
    def same_face(a, b):
        # Bounding-box overlap only merges detections of the same face across image scales.
        ax, ay, aw, ah = a[:4]
        bx, by, bw, bh = b[:4]
        overlap = max(0, min(ax + aw, bx + bw) - max(ax, bx)) * max(
            0, min(ay + ah, by + bh) - max(ay, by)
        )
        union = aw * ah + bw * bh - overlap
        return union > 0 and overlap / union >= 0.45

    def detect_at_scales(self, image, frame):
        cv2 = self.cv2
        longest = max(image.shape[:2])
        faces_found = []
        # YuNet can miss close-up faces at full webcam resolution. Keep full pixels for
        # recognition/quality, but detect at native, <=640 and <=320 dimensions.
        scales = list(dict.fromkeys([1.0, min(1.0, 640 / longest), min(1.0, 320 / longest)]))
        for scale in scales:
            sample = (
                image
                if scale == 1.0
                else cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
            )
            sx = sample.shape[1] / image.shape[1]
            sy = sample.shape[0] / image.shape[0]
            self.detector.setInputSize((sample.shape[1], sample.shape[0]))
            _, faces = self.detector.detect(sample)
            if faces is None:
                continue
            if len(faces) > 1:
                raise Rejected(
                    "EKYC_FACE_COUNT",
                    frame + " có nhiều khuôn mặt. Chỉ để một người trong ảnh chụp.",
                    {"frame": frame, "reason": "MULTIPLE_FACES"},
                )
            for detected in faces:
                face = detected.copy()
                face[[0, 2, 4, 6, 8, 10, 12]] /= sx
                face[[1, 3, 5, 7, 9, 11, 13]] /= sy
                match = next(
                    (i for i, existing in enumerate(faces_found) if self.same_face(existing, face)),
                    None,
                )
                if match is None:
                    faces_found.append(face)
                elif float(face[-1]) > float(faces_found[match][-1]):
                    faces_found[match] = face
            if len(faces_found) > 1:
                # Do not choose only the most confident face when different scales see different people.
                raise Rejected(
                    "EKYC_FACE_COUNT",
                    frame + " có nhiều khuôn mặt. Chỉ để một người trong ảnh chụp.",
                    {"frame": frame, "reason": "MULTIPLE_FACES"},
                )
        return faces_found[0] if faces_found else None

    def detect_upright(self, image, frame):
        """Normalize scale/orientation while keeping score threshold 0.9 and one-face rule."""
        cv2 = self.cv2
        candidates = []
        for rotation in (0, 90, 180, 270):
            rotated = (
                image
                if rotation == 0
                else cv2.rotate(
                    image,
                    {
                        90: cv2.ROTATE_90_CLOCKWISE,
                        180: cv2.ROTATE_180,
                        270: cv2.ROTATE_90_COUNTERCLOCKWISE,
                    }[rotation],
                )
            )
            face = self.detect_at_scales(rotated, frame)
            if face is not None:
                if rotation == 0:
                    return rotated, face, rotation
                candidates.append((float(face[-1]), rotated, face, rotation))
        if not candidates:
            raise Rejected(
                "EKYC_FACE_COUNT",
                "Không tìm thấy khuôn mặt trong "
                + frame.lower()
                + ". Xem ảnh vừa chụp, nhìn vào camera và giữ toàn bộ khuôn mặt trong khung.",
                {"frame": frame, "reason": "NO_FACE"},
            )
        _, upright, face, rotation = max(candidates, key=lambda item: item[0])
        return upright, face, rotation

    def extract(self, value, frame):
        import numpy as np
        from PIL import Image, ImageOps

        cv2 = self.cv2
        try:
            raw = base64.b64decode(value, validate=True)
        except (ValueError, TypeError):
            raise Rejected(
                "EKYC_INVALID_IMAGE", frame + ": dữ liệu ảnh không hợp lệ.", {"frame": frame}
            )
        if not 2000 <= len(raw) <= 300000 or not raw.startswith(b"\xff\xd8\xff"):
            raise Rejected(
                "EKYC_INVALID_IMAGE",
                frame + ": cần ảnh JPEG thật, tối đa 300 KB.",
                {"frame": frame},
            )
        try:
            with Image.open(io.BytesIO(raw)) as header:
                width, height = header.size
                if min(width, height) < 240 or max(width, height) > 1600:
                    raise Rejected(
                        "EKYC_IMAGE_SIZE",
                        frame + ": ảnh cần kích thước 240–1600 pixel.",
                        {"frame": frame},
                    )
                if header.format != "JPEG":
                    raise Rejected(
                        "EKYC_INVALID_IMAGE", frame + ": cần ảnh JPEG.", {"frame": frame}
                    )
                # Normalize metadata once. Orientation search handles webcam pixels with bad/missing metadata.
                image = cv2.cvtColor(
                    np.array(ImageOps.exif_transpose(header).convert("RGB")), cv2.COLOR_RGB2BGR
                )
        except Rejected:
            raise
        except Exception:
            raise Rejected(
                "EKYC_INVALID_IMAGE", frame + ": không đọc được ảnh JPEG.", {"frame": frame}
            )
        image, face, rotation = self.detect_upright(image, frame)
        x, y, w, h = [int(v) for v in face[:4]]
        if min(w, h) < 70 or x < 0 or y < 0 or x + w > image.shape[1] or y + h > image.shape[0]:
            raise Rejected(
                "EKYC_FACE_QUALITY",
                frame + ": đưa toàn bộ khuôn mặt vào khung và gần camera hơn.",
                {"frame": frame, "reason": "FACE_BOUNDS"},
            )
        gray = cv2.cvtColor(image[y : y + h, x : x + w], cv2.COLOR_BGR2GRAY)
        if (
            gray.size == 0
            or cv2.Laplacian(gray, cv2.CV_64F).var() < 35
            or not 30 < gray.mean() < 230
        ):
            raise Rejected(
                "EKYC_FACE_QUALITY",
                frame + ": ảnh mờ hoặc thiếu/thừa sáng. Giữ đầu yên và chụp lại.",
                {"frame": frame, "reason": "LIGHT_OR_BLUR"},
            )
        aligned = self.recognizer.alignCrop(image, face)
        feature = self.recognizer.feature(aligned).reshape(-1).astype(float)
        norm = np.linalg.norm(feature)
        if not np.isfinite(feature).all() or norm < 0.1:
            raise Rejected(
                "EKYC_FACE_QUALITY",
                frame + ": không trích xuất được khuôn mặt rõ nét.",
                {"frame": frame},
            )
        feature /= norm
        eyes = face[4:8].reshape(2, 2)
        nose = face[8:10]
        eye_distance = float(np.linalg.norm(eyes[1] - eyes[0]))
        yaw = float((nose[0] - eyes[:, 0].mean()) / max(eye_distance, 1))
        metadata = {
            "rotationDegrees": rotation,
            "width": image.shape[1],
            "height": image.shape[0],
            "faceCount": 1,
        }
        return feature, yaw, hashlib.sha256(raw).hexdigest(), metadata


def main(data):
    import numpy as np

    engine = FaceEngine()
    if data.get("action") == "check":
        return {"engine": "OpenCV-SFace-v1", "ready": True, "certifiedLiveness": False}
    first_label = (
        "Ảnh 2 xoay nhẹ đầu"
        if data.get("action") == "inspect" and data.get("frameIndex") == 2
        else "Ảnh 1 nhìn thẳng"
    )
    first, yaw1, hash1, info1 = engine.extract(data.get("imageBase64"), first_label)
    if data.get("action") == "inspect":
        # Quality feedback only. No reference is written and no identity/OTP authorization is granted.
        return {"frameAccepted": True, "identityVerified": False, **info1}
    second, yaw2, hash2, info2 = engine.extract(data.get("motionImageBase64"), "Ảnh 2 xoay nhẹ đầu")
    threshold = float(data.get("threshold", 0.5))
    if not 0.363 <= threshold <= 0.95:
        raise Rejected("EKYC_CONFIG_INVALID", "Cấu hình ngưỡng đối chiếu không hợp lệ.")
    if float(np.dot(first, second)) < threshold:
        raise Rejected(
            "EKYC_FACE_MISMATCH",
            "Hai ảnh không khớp cùng một người. Giữ mặt rõ, chỉ xoay nhẹ đầu, rồi chụp lại từ ảnh 1.",
        )
    # Movement check remains mandatory; this is not certified anti-spoofing.
    if hash1 == hash2 or abs(yaw2 - yaw1) < 0.10 or abs(yaw2 - yaw1) > 0.8:
        raise Rejected(
            "EKYC_MOVEMENT_REQUIRED",
            "Cần hai tư thế khác nhau: ảnh 1 nhìn thẳng, ảnh 2 xoay nhẹ đầu sang một bên (không nghiêng điện thoại).",
        )
    if data.get("action") == "enroll":
        return {"engine": "OpenCV-SFace-v1", "embedding": first.tolist(), "movementChecked": True}
    if data.get("action") != "verify":
        raise Rejected("EKYC_INVALID_ACTION", "Thao tác ảnh không hợp lệ.")
    reference = np.array(data.get("embedding", []), dtype=float)
    if (
        reference.shape != (128,)
        or not np.isfinite(reference).all()
        or np.linalg.norm(reference) < 0.1
    ):
        raise Rejected(
            "EKYC_NOT_ENROLLED", "Chưa có ảnh gốc hợp lệ. Vui lòng đăng ký trong Cá nhân trước."
        )
    reference /= np.linalg.norm(reference)
    score = min(float(np.dot(first, reference)), float(np.dot(second, reference)))
    if score < threshold:
        raise Rejected(
            "EKYC_FACE_MISMATCH", "Khuôn mặt không khớp ảnh gốc. Không thể tiếp tục OTP."
        )
    return {"engine": "OpenCV-SFace-v1", "cosineScore": round(score, 4), "movementChecked": True}


if __name__ == "__main__":
    try:
        print(json.dumps({"ok": True, **main(json.load(sys.stdin))}, ensure_ascii=True))
    except Rejected as error:
        print(
            json.dumps(
                {
                    "ok": False,
                    "code": error.code,
                    "message": error.message,
                    "details": error.details,
                },
                ensure_ascii=True,
            )
        )
    except Exception:
        print(
            json.dumps(
                {
                    "ok": False,
                    "code": "EKYC_ENGINE_UNAVAILABLE",
                    "message": "Dịch vụ đối chiếu khuôn mặt chưa sẵn sàng. Kiểm tra Python, thư viện và mô hình OpenCV.",
                },
                ensure_ascii=True,
            )
        )
