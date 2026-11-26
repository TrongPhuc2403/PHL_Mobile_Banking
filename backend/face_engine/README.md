# Local face engine (educational demo)

This is **not certified liveness/PAD** or legal identity verification. It uses YuNet detection and SFace comparison, requires one clear face and two distinct poses, and never treats frame inspection as identity verification.

## Setup

From the backend directory:

```powershell
python -m pip install -r face_engine/requirements.txt
python face_engine/setup_models.py
```

Model weights are deliberately ignored by Git. The downloader uses official OpenCV Zoo URLs and verifies pinned SHA-256 checksums before writing them. `models/LICENSE-YUNET` and `models/LICENSE-SFACE` retain the upstream licenses. See [OpenCV Zoo YuNet](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet) and [SFace](https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface).

## Checks

```powershell
python face_engine/test_engine.py
python -m pip install -r face_engine/requirements-dev.txt
python -m black --check face_engine
```

Tests download public fixtures in memory and never enroll a bank customer. Private reference portraits are stored separately under the backend data directory only after authorized enrollment. Do not commit portraits, embeddings, OTPs or tokens. Production needs HTTPS, encrypted biometric storage and a certified anti-spoofing/identity provider.
