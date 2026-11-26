"""Run with python face_engine/test_engine.py. Downloads public test photos; never enrolls a bank user."""

import base64
import json
from pathlib import Path
import subprocess
import sys
import unittest
import urllib.request
import cv2
import numpy as np

ENGINE = Path(__file__).resolve().parent


class FaceEngineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.images = {}
        cls.originals = {}
        for name in ["obama.jpg", "obama2.jpg", "biden.jpg"]:
            url = (
                "https://raw.githubusercontent.com/ageitgey/face_recognition/master/examples/"
                + name
            )
            raw = urllib.request.urlopen(url, timeout=30).read()
            cls.originals[name] = raw
            image = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
            factor = min(1, 960 / max(image.shape[:2]))
            image = cv2.resize(image, None, fx=factor, fy=factor)
            cls.images[name] = base64.b64encode(
                cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, 82])[1]
            ).decode()
        cls.first = cls.images["obama.jpg"]
        cls.second = cls.images["obama2.jpg"]
        cls.enrollment = cls.call(
            {"action": "enroll", "imageBase64": cls.first, "motionImageBase64": cls.second}
        )

    @staticmethod
    def call(data):
        process = subprocess.run(
            [sys.executable, str(ENGINE / "verify.py")],
            input=json.dumps(data),
            text=True,
            capture_output=True,
            check=True,
            timeout=35,
        )
        return json.loads(process.stdout)

    def test_same_person_enrollment_and_matching(self):
        self.assertTrue(self.enrollment["ok"])
        result = self.call(
            {
                "action": "verify",
                "imageBase64": self.first,
                "motionImageBase64": self.second,
                "embedding": self.enrollment["embedding"],
            }
        )
        self.assertTrue(result["ok"])
        self.assertGreaterEqual(result["cosineScore"], 0.5)

    def test_different_person_frames_rejected(self):
        result = self.call(
            {
                "action": "enroll",
                "imageBase64": self.first,
                "motionImageBase64": self.images["biden.jpg"],
            }
        )
        self.assertEqual(result["code"], "EKYC_FACE_MISMATCH")

    def test_different_reference_rejected(self):
        detector = cv2.FaceDetectorYN.create(
            str(ENGINE / "models/face_detection_yunet_2023mar.onnx"), "", (320, 320), 0.9, 0.3, 5000
        )
        recognizer = cv2.FaceRecognizerSF.create(
            str(ENGINE / "models/face_recognition_sface_2021dec.onnx"), ""
        )
        image = cv2.imdecode(
            np.frombuffer(base64.b64decode(self.images["biden.jpg"]), np.uint8), cv2.IMREAD_COLOR
        )
        detector.setInputSize((image.shape[1], image.shape[0]))
        _, faces = detector.detect(image)
        reference = recognizer.feature(recognizer.alignCrop(image, faces[0])).reshape(-1).tolist()
        result = self.call(
            {
                "action": "verify",
                "imageBase64": self.first,
                "motionImageBase64": self.second,
                "embedding": reference,
            }
        )
        self.assertEqual(result["code"], "EKYC_FACE_MISMATCH")

    def test_duplicate_frame_rejected(self):
        result = self.call(
            {"action": "enroll", "imageBase64": self.first, "motionImageBase64": self.first}
        )
        self.assertEqual(result["code"], "EKYC_MOVEMENT_REQUIRED")

    def test_no_face_rejected(self):
        image = np.ones((480, 640, 3), dtype=np.uint8) * 255
        encoded = base64.b64encode(cv2.imencode(".jpg", image)[1]).decode()
        result = self.call(
            {"action": "enroll", "imageBase64": encoded, "motionImageBase64": encoded}
        )
        self.assertEqual(result["code"], "EKYC_FACE_COUNT")

    def test_timestamp_is_not_an_image(self):
        result = self.call(
            {
                "action": "enroll",
                "imageBase64": "CAMERAX_LIVE_FRAME_1234567890",
                "motionImageBase64": self.second,
            }
        )
        self.assertEqual(result["code"], "EKYC_INVALID_IMAGE")

    def test_rotated_jpegs_without_exif_are_normalized(self):
        image = cv2.imdecode(
            np.frombuffer(base64.b64decode(self.first), np.uint8), cv2.IMREAD_COLOR
        )
        for angle, flag in [
            (90, cv2.ROTATE_90_CLOCKWISE),
            (180, cv2.ROTATE_180),
            (270, cv2.ROTATE_90_COUNTERCLOCKWISE),
        ]:
            with self.subTest(angle=angle):
                rotated = cv2.rotate(image, flag)
                encoded = base64.b64encode(
                    cv2.imencode(".jpg", rotated, [cv2.IMWRITE_JPEG_QUALITY, 90])[1]
                ).decode()
                inspected = self.call({"action": "inspect", "imageBase64": encoded})
                self.assertTrue(inspected.get("frameAccepted"), inspected)
                self.assertFalse(inspected.get("identityVerified"))
                self.assertEqual(inspected["rotationDegrees"], (360 - angle) % 360)
                self.assertNotIn("embedding", inspected)
                self.assertNotIn("ekycToken", inspected)
                verified = self.call(
                    {
                        "action": "verify",
                        "imageBase64": encoded,
                        "motionImageBase64": self.second,
                        "embedding": self.enrollment["embedding"],
                    }
                )
                self.assertTrue(verified.get("ok"), verified)

    def test_jpeg_exif_orientation_is_applied_once(self):
        from PIL import Image
        import io

        image = Image.open(io.BytesIO(base64.b64decode(self.first)))
        rotated = image.transpose(Image.Transpose.ROTATE_270)
        exif = Image.Exif()
        exif[274] = 8
        output = io.BytesIO()
        rotated.save(output, format="JPEG", quality=90, exif=exif)
        encoded = base64.b64encode(output.getvalue()).decode()
        result = self.call({"action": "inspect", "imageBase64": encoded})
        self.assertTrue(result.get("frameAccepted"), result)
        self.assertEqual(result["rotationDegrees"], 0)

    def test_inspection_cannot_register_or_verify(self):
        result = self.call({"action": "inspect", "imageBase64": self.first})
        self.assertTrue(result.get("frameAccepted"), result)
        self.assertFalse(result["identityVerified"])
        for key in ["embedding", "ekycToken", "enrollmentToken", "verified", "enrolled"]:
            self.assertNotIn(key, result)

    def test_multiple_people_rejected_even_in_frame_inspection(self):
        first = cv2.imdecode(
            np.frombuffer(base64.b64decode(self.first), np.uint8), cv2.IMREAD_COLOR
        )
        second = cv2.imdecode(
            np.frombuffer(base64.b64decode(self.second), np.uint8), cv2.IMREAD_COLOR
        )
        height = 480
        first = cv2.resize(first, (int(first.shape[1] * height / first.shape[0]), height))
        second = cv2.resize(second, (int(second.shape[1] * height / second.shape[0]), height))
        composite = np.concatenate([first, second], axis=1)
        encoded = base64.b64encode(cv2.imencode(".jpg", composite)[1]).decode()
        result = self.call({"action": "inspect", "imageBase64": encoded})
        self.assertFalse(result.get("ok"), result)
        self.assertEqual(result["code"], "EKYC_FACE_COUNT")
        self.assertEqual(result["details"]["reason"], "MULTIPLE_FACES")

    def test_no_face_in_second_frame_is_labelled(self):
        image = np.ones((480, 640, 3), dtype=np.uint8) * 255
        encoded = base64.b64encode(cv2.imencode(".jpg", image)[1]).decode()
        result = self.call(
            {"action": "enroll", "imageBase64": self.first, "motionImageBase64": encoded}
        )
        self.assertEqual(result["code"], "EKYC_FACE_COUNT")
        self.assertEqual(result["details"]["frame"], "Ảnh 2 xoay nhẹ đầu")
        self.assertEqual(result["details"]["reason"], "NO_FACE")

    def test_close_up_detection_is_not_lost_at_large_input(self):
        from verify import FaceEngine

        image = cv2.imdecode(
            np.frombuffer(base64.b64decode(self.first), np.uint8), cv2.IMREAD_COLOR
        )
        detector = cv2.FaceDetectorYN.create(
            str(ENGINE / "models/face_detection_yunet_2023mar.onnx"),
            "",
            (image.shape[1], image.shape[0]),
            0.9,
            0.3,
            5000,
        )
        _, faces = detector.detect(image)
        self.assertIsNotNone(faces)
        x, y, w, h = map(int, faces[0, :4])
        roi = image[
            max(0, int(y - h * 0.2)) : min(image.shape[0], int(y + h * 1.2)),
            max(0, int(x - w * 0.2)) : min(image.shape[1], int(x + w * 1.2)),
        ]
        factor = 960 / max(roi.shape[:2])
        close = cv2.resize(roi, None, fx=factor, fy=factor)
        detector.setInputSize((close.shape[1], close.shape[0]))
        _, native = detector.detect(close)
        self.assertIsNone(native, "Fixture must reproduce native-resolution miss")
        engine = FaceEngine()
        face = engine.detect_at_scales(close, "Test close-up")
        self.assertIsNotNone(face)
        self.assertGreaterEqual(face[-1], 0.9)
        self.assertGreater(
            face[2],
            close.shape[1] * 0.4,
            "Landmarks/bounds must be mapped back to original dimensions",
        )

    def test_clear_close_up_photo_passes_quality_and_matches_reference(self):
        image = cv2.imdecode(np.frombuffer(self.originals["obama.jpg"], np.uint8), cv2.IMREAD_COLOR)
        detector = cv2.FaceDetectorYN.create(
            str(ENGINE / "models/face_detection_yunet_2023mar.onnx"),
            "",
            (image.shape[1], image.shape[0]),
            0.9,
            0.3,
            5000,
        )
        _, faces = detector.detect(image)
        self.assertIsNotNone(faces)
        x, y, w, h = map(int, faces[0, :4])
        roi = image[
            max(0, int(y - h * 0.2)) : min(image.shape[0], int(y + h * 1.2)),
            max(0, int(x - w * 0.2)) : min(image.shape[1], int(x + w * 1.2)),
        ]
        factor = 960 / max(roi.shape[:2])
        close = cv2.resize(roi, None, fx=factor, fy=factor)
        encoded = base64.b64encode(
            cv2.imencode(".jpg", close, [cv2.IMWRITE_JPEG_QUALITY, 82])[1]
        ).decode()
        result = self.call({"action": "inspect", "imageBase64": encoded})
        self.assertTrue(result.get("frameAccepted"), result)
        self.assertFalse(result.get("identityVerified"))
        self.assertEqual(result["faceCount"], 1)
        verified = self.call(
            {
                "action": "verify",
                "imageBase64": encoded,
                "motionImageBase64": self.second,
                "embedding": self.enrollment["embedding"],
            }
        )
        self.assertTrue(verified.get("ok"), verified)


if __name__ == "__main__":
    unittest.main(verbosity=2)
