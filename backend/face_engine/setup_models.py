"""Download official OpenCV Zoo models; verify pinned SHA-256 before writing."""

from pathlib import Path
import hashlib
import urllib.request

FILES = {
    "face_detection_yunet_2023mar.onnx": (
        "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx",
        "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
    ),
    "face_recognition_sface_2021dec.onnx": (
        "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx",
        "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79",
    ),
}
if __name__ == "__main__":
    directory = Path(__file__).resolve().parent / "models"
    directory.mkdir(parents=True, exist_ok=True)
    for name, (url, expected) in FILES.items():
        path = directory / name
        if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == expected:
            print(name, "OK (existing)")
            continue
        data = urllib.request.urlopen(url, timeout=120).read()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError("Unexpected model checksum: " + name)
        path.write_bytes(data)
        print(name, "OK (downloaded)")
