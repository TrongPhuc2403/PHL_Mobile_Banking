# Kiểm thử eKYC thực tế

## Phạm vi

Đối chiếu đề gốc trang2 (tài liệu nhóm cung cấp riêng) và đặc tả nhóm (tài liệu nhóm cung cấp riêng): cần ảnh gốc từ khách hàng, đối chiếu khi giao dịch lớn, eKYC trước OTP.

Bản này thực hiện chụp JPEG thật bằng CameraX, phát hiện đúng một khuôn mặt bằng YuNet, so sánh vector SFace trên backend. **Kiểm tra tư thế hai ảnh không phải liveness/PAD được chứng nhận**, không xác minh CCCD với cơ quan định danh và không chứng minh đạt tiêu chuẩn ngân hàng.

## Chuẩn bị

- MySQL/backend đang chạy, Android là APK mới; không import lại seed để sửa lỗi.
- Máy có Python + OpenCV + NumPy + Pillow và model chính thức. [requirements.txt](../backend/face_engine/requirements.txt), [setup_models.py](../backend/face_engine/setup_models.py).
- Emulator Front camera = Webcam0 hoặc điện thoại có camera trước. Cấp quyền qua hộp thoại Android/Windows bình thường; không có fallback “mô phỏng thành công”.
- Khách hàng tự nguyện chụp; tự đăng ký cần mật khẩu + OTP của tài khoản. Nếu nhân viên hỗ trợ thì kiểm tra giấy tờ trước khi chụp. Không dùng ảnh mẫu để gán cho người dùng.

## Khách hàng tự đăng ký lần đầu

1. Đăng nhập `phuc.customer` hoặc `hoan.customer` → **Cá nhân → Sinh trắc học khuôn mặt → Đăng ký sinh trắc học ngay**.
2. Nhập mật khẩu hiện tại, nhấn gửi OTP. Server kiểm tra trạng thái/khóa tài khoản, sai mật khẩu không tạo OTP. Gửi lại tối thiểu30 giây.
3. Nhập tay OTP DEV hiển thị trong hộp thoại; không tự điền. OTP chỉ dùng để đăng ký, có requestId riêng, không thể lấy OTP chuyển tiền/quên mật khẩu để thay thế.
4. Xác nhận OTP → mở camera → đọc/đồng ý lưu ảnh gốc và đặc trưng khuôn mặt. Quyền chụp đăng ký tối đa5 phút; challenge chụp2phút, dùng một lần.
5. Chụp nhìn thẳng rồi xoay nhẹ đầu, đủ sáng, chỉ một người. Thành công mới lưu reference vào đúng hồ sơ user đăng nhập và Audit `SELF_ENROLL_EKYC`, actorRole `CUSTOMER`.
6. Trở lại Cá nhân sẽ hiển thị đã đăng ký; chọn kiểm tra khuôn mặt nếu muốn. Đăng ký không cấp `ekycToken` cho giao dịch.
7. Hủy hoặc ảnh không hợp lệ không thay đổi trạng thái. Ảnh sai có thể chụp lại trong phiên được xác nhận; phiên hết hạn thì làm lại mật khẩu/OTP.

## Nhân viên hỗ trợ

`long.officer` → Khách hàng → đúng hồ sơ → Đăng ký ảnh gốc eKYC. Nhân viên kiểm tra CCCD, khách hàng đồng ý và chụp hai ảnh. Audit sử dụng vai trò OFFICER như trước.

Cả hai luồng đều chỉ **đăng ký lần đầu**. Ảnh gốc hợp lệ không được tự ghi đè; thay/khôi phục cần quy trình riêng chưa triển khai. Mật khẩu/OTP chứng minh quyền kiểm soát tài khoản, không tự động xác minh CCCD pháp lý; OpenCV chưa cung cấp PAD được chứng nhận.

## Giao dịch mẫu

- Nguồn `phuc.customer` / STK `52400028`; đích `52400114` của Hoàn.
- Số tiền thử `15.000.000 VND`, yêu cầu nguồn đủ số dư. Không cần thực hiện bước cuối nếu chỉ kiểm tra giao diện chặn.
- Tiếp tục → xác nhận hiển thị eKYC → OTP → thực hiện. OTP và thực hiện bị khóa.
- Bấm Xác minh → chụp hai ảnh → khớp ảnh gốc → mới lấy OTP.
- Mã DEV hiển thị nhưng không tự điền; nhập tay rồi xác nhận. OTP gắn chính xác số tiền/đích/nội dung/key.
- Khi sửa giao dịch phải đóng sheet và tạo xác nhận mới; xác minh cũ không dùng cho yêu cầu mới.

## Ma trận kết quả mong đợi

| Ca thử | Kết quả |
|---|---|
| 10.000.000 VND | Yêu cầu OTP, không bắt buộc eKYC theo ngưỡng. |
| 10.000.001 VND trở lên | Bắt buộc eKYC trước cấp OTP; API cũng chặn nếu gửi trực tiếp. |
| Seed ghi VERIFIED nhưng vector MOCK | Xem như chưa đăng ký, được tự đăng ký trong Cá nhân hoặc nhờ nhân viên. |
| Đăng ký với mật khẩu sai / OTP sai/hết hạn | Không cấp quyền chụp đăng ký, không lưu ảnh. |
| Gửi ID người khác tới API đăng ký | Bị từ chối; API tự lấy ID từ user đăng nhập. |
| Dùng approval đăng ký để chuyển tiền | Bị từ chối, không thay thế token eKYC giao dịch. |
| Đã có ảnh gốc hợp lệ | Không được đăng ký lại/ghi đè, UI chuyển sang kiểm tra khuôn mặt. |
| Từ chối đồng ý / Back / hủy | Không cấp kết quả xác minh; OTP và thực hiện vẫn khóa. |
| Từ chối Camera / camera lỗi / thiếu camera trước | Không được chụp/verify; không có đường giả lập. |
| Ảnh không mặt / nhiều mặt / mờ / tối | Báo chụp lại, không cấp token. |
| Hai ảnh giống hệt / chưa đổi tư thế | Báo cần xoay nhẹ đầu, không cấp token. |
| Người khác / không khớp ảnh gốc | EKYC_FACE_MISMATCH; không mở bước OTP. |
| Token khác user / đổi tiền/đích/key / hết hạn / đã dùng | API từ chối. |
| Không có token nhưng gọi OTP cho >10 triệu | EKYC_REQUIRED; không tạo OTP. |
| Sai OTP | Không ghi giao dịch, vẫn có thể nhập lại trong giới hạn. |
| OTP hết hạn/đã dùng | Không ghi giao dịch, yêu cầu OTP mới. |
| Backend restart | Challenge/token cũ không còn hợp lệ; tạo phiên và xác minh lại. |
| Thành công rồi retry cùng idempotency key | Trả giao dịch cũ, không trừ tiền hai lần. |

## Kiểm chứng đã có

- Backend **21/21** kiểm thử độc lập qua; kiểm tra ledger/idempotency dùng DB giả lập trong tiến trình test, không thay DB đang chạy.
- Kiểm thử persistence đăng ký **1/1**: OpenCV thật + ảnh mẫu công khai, DB/lưu ảnh giả lập; kiểm tra Audit CUSTOMER, chỉ hồ sơ chính chủ, không cấp token tiền, không ghi đè và rollback/xóa ảnh khi lỗi.
- Engine ảnh công khai: cùng người so khớp đạt cosine 0.7601 với ngưỡng 0.5; khác người/khác reference bị từ chối; ảnh lặp và không mặt bị từ chối. Cosine **không phải % tin cậy** và các ảnh mẫu không chứng minh khả năng chống giả mạo.
- Emulator: UI đăng nhập thật → Cá nhân → đăng ký → mật khẩu → nhập tay OTP → màn hình đồng ý chụp; từ chối chụp trở về vẫn chưa đăng ký. Đã kiểm tra lại giao dịch 15 triệu: OTP/execute vẫn khóa và hủy eKYC không mở khóa. Không lưu ảnh mặt hoặc chuyển tiền.
- **Chưa chứng thực** toàn bộ luồng webcam/đăng ký/so khớp bằng khuôn mặt Phúc và Hoàn. Phần này cần chính chủ thực hiện.

Nguồn chính: [FaceEkycActivity.java](../android/app/src/main/java/vn/edu/tdtu/phlbanking/ui/customer/FaceEkycActivity.java), [ekycService.ts](../backend/src/services/ekycService.ts), [verify.py](../backend/face_engine/verify.py), [security.test.ts](../backend/tests/security.test.ts).


## Chạy lại kiểm thử engine riêng

```powershell
cd backend
python face_engine/test_engine.py
```

[Test engine](../backend/face_engine/test_engine.py) chỉ tải ba ảnh công khai từ [ví dụ face_recognition](https://github.com/ageitgey/face_recognition/tree/master/examples) vào bộ nhớ để kiểm tra cùng/khác người; không ghi ảnh vào hồ sơ khách hàng, không dùng DB. Cần mạng để tải fixture. Các model từ [OpenCV Zoo SFace](https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface) và [YuNet](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet); giấy phép được giữ cạnh model. Ngưỡng cosine 0.5 là cấu hình đồ án, không phải cam kết sai số hay 85% tin cậy của đặc tả.


Kiểm thử đăng ký thành công độc lập (tải fixture công khai, không ghi dữ liệu thật):

```powershell
cd backend
npm run test:enrollment
```

Nguồn đăng ký cá nhân: [selfEnrollmentSessions.ts](../backend/src/security/selfEnrollmentSessions.ts), [EkycEnrollmentDialog.java](../android/app/src/main/java/vn/edu/tdtu/phlbanking/ui/customer/EkycEnrollmentDialog.java).


## Sửa lỗi nhận diện ảnh từ webcam (26/11)

**Bằng chứng đã có:** live preview trong hai ảnh người dùng gửi có một khuôn mặt, detector hiện tại cũng tìm được một mặt trong cả hai ảnh màn hình; log Android cho biết ảnh JPEG đã gửi tới API đăng ký và nhận422. Thông báo là EKYC_FACE_COUNT. Do ảnh chụp gốc không được lưu/log, **chưa thể khẳng định chính xác ảnh gửi lên đã bị xoay hay bị crop**; không kết luận do người dùng đặt mặt sai.

Bản sửa:

- CameraX Preview/ImageCapture dùng tỷ lệ4:3 và chung viewport, live preview fitCenter, chỉ bật chụp sau trạng thái STREAMING.
- Engine chuẩn hóa EXIF, khi không tìm thấy mặt theo chiều hiện tại thì thử các hướng còn lại (90°/180°/270°), giữ ngưỡng detector0.9 và ngưỡng so khớp0.5.
- Sau mỗi ảnh có thumbnail **ảnh vừa chụp** và kết quả riêng. Ảnh1 đạt mới sang ảnh 2. Nếu ảnh 2 thiếu mặt/mờ, chụp lại ảnh 2, giữ ảnh 1 trong phiên.
- API kiểm tra ảnh yêu cầu challenge còn hạn/đúng user. Kết quả `frameAccepted` là chất lượng, `identityVerified=false`; không lưu ảnh gốc, không cấp token giao dịch, không thay trạng thái hồ sơ.
- Final enroll/verify vẫn chạy lại cả hai ảnh và kiểm tra cùng người, thay đổi tư thế/ảnh gốc. Hết hạn/hủy/không camera vẫn bị chặn.
- 11/11 kiểm thử engine qua: ba hướng xoay không EXIF, EXIF không xoay hai lần, ảnh nhiều người/không mặt bị từ chối, báo cụ thể ảnh 2 và inspect không cấp quyền. Backend 21/21 kiểm thử bảo mật +1ca persistence qua.

Nếu vẫn lỗi, cung cấp **dòng lỗi mới** và ảnh thu nhỏ vừa chụp (nếu bạn đồng ý chia sẻ). Không cần gửi OTP, mật khẩu, base64 hoặc ảnh gốc qua log. Chính chủ vẫn cần thử hai tư thế để xác nhận đăng ký hoàn chỉnh; không dùng ảnh mẫu đăng ký thay.


### Kết quả kiểm tra webcam sau bản sửa

- APK đã build/lint thành công và cài lên Emulator; không xóa dữ liệu app/DB.
- Kiểm tra một ảnh chụp thật lần đầu trả422 (ảnh 1 chưa đạt). Ca đó chưa ghi được chi tiết và đã dừng trước ảnh 2; **không coi là pass hoặc đăng ký thành công**.
- Lần kiểm tra chẩn đoán tiếp theo gặp session đăng nhập hết hạn; sau đăng nhập lại, Android yêu cầu quyền Camera. Hiện bị chặn ở hộp thoại **While using the app / Only this time / Don't allow**. Không tự cấp quyền hoặc dùng đường bỏ qua.
- Người dùng cần chọn quyền Camera bình thường, mở lại đăng ký (OTP/approval cũ có thể hết hạn), chụp ảnh 1 và xem thumbnail/lỗi cụ thể. **Chưa xác nhận sửa xong đăng ký đầu-cuối với webcam chính chủ.**
- Các ca tự động11/11 engine,21/21 bảo mật và1ca persistence vẫn là kiểm thử tách biệt, không thay thế kiểm tra webcam này.


### Tiếp tục sửa trường hợp NO_FACE dù thumbnail có mặt

- Đã đọc thông báo đầy đủ trên màn hình: `EKYC_FACE_COUNT / NO_FACE` cho ảnh 1. Bản ảnh người dùng gửi trước chỉ chứa nhãn tổng quát, không đủ để chẩn đoán; nay UI hiện cả mã lỗi, lý do và message cạnh thumbnail lớn hơn. Log chỉ ghi số thứ tự ảnh/mã lỗi/lý do, không ghi ảnh/embedding/token.
- Tái hiện độc lập: một ảnh chụp gần từ fixture công khai, phóng tới960px, không được YuNet phát hiện ở kích thước gốc nhưng được phát hiện khi giảm về640px/320px với nguyên ngưỡng 0.9.
- Engine nay phát hiện ở ảnh gốc và hai kích thước giảm, quy đổi bbox/landmark về ảnh gốc để đánh giá độ nét và trích xuất SFace. Gộp cùng mặt bằng overlap; nhiều khuôn mặt vẫn bị từ chối. Không giảm ngưỡng so khớp0.5 hoặc bỏ yêu cầu chuyển động.
- Đã tách ca thử phát hiện mặt phóng lớn khỏi kiểm tra chất lượng ảnh (ảnh bị phóng mờ vẫn có thể bị chặn đúng). Ảnh chụp gần rõ được kiểm tra chất lượng và so khớp reference riêng.
- Kết quả mới: **13/13 engine,21/21 bảo mật,1/1 persistence** qua. Android assemble/lint qua, APK đã cài; luồng đăng ký qua mật khẩu/OTP và hủy đồng ý chụp vẫn qua. **Chưa ghi nhận đăng ký đầu-cuối bằng webcam chính chủ sau bản đa kích thước.** Người dùng cần thử lại ảnh 1 rồi ảnh 2; không tự đăng ký ảnh mẫu thay người dùng.
