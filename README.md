# PHL Mobile Banking

Ứng dụng ngân hàng đồ án TDTU: Android Java/XML + REST API Express/TypeScript + MySQL 8.0.

> [!IMPORTANT]
> **Không thể kết luận toàn bộ project đã hoàn thành 100% hoặc đạt chuẩn ngân hàng.** Build/lint và kiểm tra mã nguồn không thay thế kiểm thử chức năng trên thiết bị. Các kết luận “100%/245 tests = hoàn tất mọi yêu cầu” trước đây đã được gỡ.

## 1. Yêu cầu và tình trạng thực tế

Đề gốc yêu cầu đăng ký thông tin sinh trắc/ảnh gốc, dùng ảnh đó để xác minh khi giao dịch giá trị cao, và tất cả giao dịch phải qua 2FA. Ngưỡng **trên 10.000.000 VND**, thứ tự **eKYC → OTP** và OTP 120 giây được quy định thêm trong đặc tả chi tiết của nhóm.

Đối chiếu yêu cầu: [docs/requirements.md](docs/requirements.md). Đề gốc và tài liệu phân công được nhóm cung cấp riêng, không được tự động sao chép vào repo.

| Nhóm chức năng | Hiện trạng, không đồng nghĩa nghiệm thu |
|---|---|
| Khách hàng / nhân viên | Có hai giao diện và phân quyền `CUSTOMER` / `OFFICER`; **không có ADMIN riêng**. |
| Android / API / MySQL | Đã có màn hình và API cho các nhóm use case; cơ sở dữ liệu thiết kế 13 bảng. Còn cần kiểm thử toàn bộ ma trận nghiệp vụ. |
| **eKYC** | Đã thay timestamp giả bằng **hai ảnh JPEG chụp thật bằng CameraX**, YuNet phát hiện mặt và SFace đối chiếu ảnh gốc trên server. Có **tự đăng ký trong Cá nhân** bằng mật khẩu + OTP riêng, luồng nhân viên hỗ trợ và Audit Log. |
| Chặn giao dịch giá trị cao | Trên 10 triệu: eKYC bắt buộc **trước khi cấp OTP**, áp dụng chuyển nội bộ/liên ngân hàng, cổng thanh toán, nạp/rút và API tiện ích. Token gắn người dùng + chi tiết giao dịch + thời hạn, dùng một lần. |
| **Giới hạn eKYC** | Kiểm tra thay đổi tư thế hai ảnh chỉ là **kiểm tra chuyển động cho đồ án**, KHÔNG phải liveness/PAD chống ảnh in/video/deepfake đã được chứng nhận; chưa xác thực pháp lý CCCD tự động. Không quy đổi cosine score thành “% tin cậy”. |
| OTP | Mã động 6 số, lưu hash, tối đa 5 lần sai, mặc định 120 giây, gắn giao dịch. **DEV hiển thị mã để nhập tay, chưa tích hợp SMS thật**; vì vậy chưa phải 2FA production hoàn chỉnh. |
| VNPay / Stripe / liên ngân hàng | Hiện vẫn có luồng mô phỏng/sandbox nội bộ; chưa chứng minh giao dịch qua nhà cung cấp thật. Không dùng dữ liệu/tiền thật. |
| Tiện ích / AI / bản đồ | Có giao diện/API, một số dữ liệu mẫu/fallback. Chưa đủ bằng chứng xác nhận mọi tích hợp ngoài chạy thật hoặc đường đi bộ tối ưu. |
| Báo cáo / video / Google Play | Chưa có bằng chứng đã hoàn tất/phát hành. |

## 2. Xem và thử bước eKYC ngay

**Trang chủ có banner sinh trắc học**. Trong **Cá nhân**, thẻ **Sinh trắc học khuôn mặt** được đặt trước phần liên lạc để đăng ký hoặc kiểm tra ngay.

### Khách hàng tự đăng ký sinh trắc học

1. Đăng nhập đúng tài khoản của bạn (`phuc.customer` hoặc `hoan.customer`) → **Cá nhân → Sinh trắc học khuôn mặt → Đăng ký sinh trắc học ngay**.
2. Nhập **mật khẩu hiện tại** → **Xác nhận mật khẩu và gửi OTP**. Mã đăng ký chỉ dùng cho mục đích này, không phải mã chuyển tiền.
3. Nhập tay OTP (DEV hiển thị mã ngay trong hộp thoại, chưa gửi SMS thật). OTP có hiệu lực120 giây, gửi lại sau30 giây; nhập sai tối đa5 lần.
4. **Xác nhận OTP và mở camera** → đọc và đồng ý việc lưu ảnh/đặc trưng khuôn mặt. Phiên đăng ký có hiệu lực5 phút.
5. Chụp **khuôn mặt của chính bạn**: ảnh 1 nhìn thẳng. App hiện **ảnh thu nhỏ vừa chụp**, kiểm tra ảnh đó với server; chỉ khi báo **Ảnh1 đạt** mới sang chụp ảnh 2 xoay nhẹ đầu. Ảnh nào lỗi được báo cụ thể; ảnh 2 lỗi có thể chụp lại trong phiên, không cần chụp lại ảnh 1. Server vẫn kiểm tra lại cả hai ảnh, cùng người và thay đổi tư thế trước khi lưu ảnh gốc + vector + Audit Log `SELF_ENROLL_EKYC` với vai trò `CUSTOMER`.
6. Thành công trở lại Cá nhân: trạng thái **Đã đăng ký sinh trắc học**; nút đổi thành **Kiểm tra khuôn mặt đã đăng ký**. Đăng ký không cấp token giao dịch, không bỏ qua xác minh cho giao dịch lớn.

**Không cần đăng nhập nhân viên để tự đăng ký lần đầu.** Hủy/từ chối chụp, sai mật khẩu/OTP hoặc ảnh lỗi không đánh dấu đăng ký thành công. Server tự xác định hồ sơ từ user đang đăng nhập; không nhận ID người khác từ app.

### Nếu camera hiện mặt nhưng server báo không có mặt

- Đã bổ sung chuẩn hóa EXIF, phát hiện hướng ảnh webcam0°/90°/180°/270° và phát hiện ở ba kích thước (ảnh gốc, tối đa640px, tối đa320px). Lỗi YuNet bỏ sót mặt chụp gần ở960px đã được tái hiện bằng fixture riêng; tọa độ/landmark được quy về ảnh gốc trước so khớp. Không hạ ngưỡng phát hiện, không bỏ kiểm tra cùng người/chuyển động.
- CameraX xem trước/chụp dùng cùng cấu hình tỷ lệ/viewport và chờ camera phát hình trước khi bật chụp. Xem **ảnh chụp thật thu nhỏ + mã lỗi/lý do đầy đủ ngay bên cạnh** để phân biệt với live preview; khung oval chỉ là chỉ dẫn, không phải kết quả AI.
- Bước kiểm tra từng ảnh chỉ báo chất lượng, **không** đánh dấu đã đăng ký hay cấp token OTP/giao dịch.
- Phiên chụp hết hạn thì nhấn tạo lại và chụp từ ảnh 1. Approval đăng ký hết5 phút thì trở lại Cá nhân xác nhận mật khẩu/OTP mới.

### Nhân viên hỗ trợ (vẫn giữ)

`long.officer` → Khách hàng → chọn đúng hồ sơ → **Đăng ký ảnh gốc eKYC (camera)**. Nhân viên kiểm tra CCCD và khách hàng đồng ý trước khi chụp. Tài khoản vừa tạo cũng có lời nhắc đăng ký. Ảnh đã đăng ký hợp lệ không thể bị ghi đè bởi luồng cá nhân hoặc nhân viên đăng ký lần đầu; đổi/khôi phục ảnh gốc cần quy trình riêng chưa triển khai.

> [!WARNING]
> Vector `MOCK_FACE_VEC_*` trong dữ liệu cũ không phải ảnh gốc hợp lệ; bạn có thể tự đăng ký mới qua Cá nhân. Không tự gán ảnh mẫu hoặc ảnh của người khác cho Phúc/Hoàn. Xác nhận mật khẩu + OTP ràng buộc tài khoản, **chưa đối chiếu CCCD với cơ sở dữ liệu định danh**; kiểm tra chuyển động chưa phải liveness/PAD đạt chuẩn.

### Giao dịch trên 10 triệu

1. Đăng nhập `phuc.customer`; ví dụ chuyển **15.000.000 VND** từ `52400028` đến `52400114` (phải đủ số dư hiện tại).
2. Nhấn Tiếp tục: phần xác nhận hiển thị rõ **Khuôn mặt → OTP → Thực hiện**. Nhập OTP/lấy OTP/thực hiện bị khóa.
3. Nhấn **Xác minh khuôn mặt eKYC**, đồng ý chụp, chụp hai ảnh theo chỉ dẫn. Chưa có ảnh gốc thì cần trở về Cá nhân đăng ký trước, không tự bỏ qua xác minh.
4. Khuôn mặt khớp ảnh gốc mới mở nút lấy OTP. Nhấn lấy OTP, **nhập tay** mã DEV hiển thị; sau đó thực hiện giao dịch.
5. Hủy/từ chối camera/không có camera/sai mặt/mất mạng/ảnh không hợp lệ đều không xác minh và không mở khóa OTP.

**Đúng 10.000.000 VND:** chỉ yêu cầu OTP theo chính sách ngưỡng; **10.000.001 VND:** bắt buộc eKYC. Đã có ảnh gốc không có nghĩa bỏ qua việc chụp lại cho giao dịch lớn.

Hướng dẫn đầy đủ: [ekyc-testing.md](docs/ekyc-testing.md).

## 3. Tài khoản demo

Mật khẩu **chỉ dành cho local demo** của ba tài khoản: `Phl@2026`. Không có OTP cố định; mã sinh mới cho từng lần yêu cầu.

| Vai trò | Tên đăng nhập | Họ tên | Email | STK thanh toán |
|---|---|---|---|---|
| OFFICER | `long.officer` | Trần Hữu Long | 52400138@student.tdtu.edu.vn | Không có tài khoản khách hàng |
| CUSTOMER | `phuc.customer` | Nguyễn Trọng Phúc | 52400028@student.tdtu.edu.vn | `52400028` |
| CUSTOMER | `hoan.customer` | Lê Khải Hoàn | 52400114@student.tdtu.edu.vn | `52400114` |

Số dư trên máy có thể đã thay đổi sau các lần thử; không mặc định bằng seed. CCCD trong seed là dữ liệu giả cho đồ án, không chứng minh danh tính thực. Tài khoản tiết kiệm/thế chấp vẫn có số tài khoản riêng, không trùng MSSV.

## 4. Chạy dự án trên Windows

### MySQL

Với DB hiện có: **chỉ chạy MySQL và backend; không import lại seed**. Seed có thao tác xóa dữ liệu mẫu trước khi thêm lại, có thể làm mất lịch sử/số dư bạn đã thử.

Chỉ với DB local mới, sao lưu trước nếu cần. Mở MySQL CLI từ thư mục gốc dự án:

```powershell
mysql -u <db_user> -p --default-character-set=utf8mb4
```

Sau đó trong MySQL CLI (không dùng toán tử `<` của shell PowerShell):

```sql
SOURCE database/schema.sql;
SOURCE database/seed.sql;
```

Cấu trúc: [schema.sql](database/schema.sql), [seed.sql](database/seed.sql).

### Backend và engine khuôn mặt

Yêu cầu Node.js20.11 trở lên, MySQL8 và Python3.11 trở lên có pip. **Model ONNX không được commit**; chạy script tải và kiểm tra SHA-256 bên dưới trên máy mới. Giấy phép model vẫn được giữ trong repo.

```powershell
cd backend
npm ci
python -m pip install -r face_engine/requirements.txt
python face_engine/setup_models.py
# Máy mới: copy .env.example .env rồi cấu hình DB/JWT; không ghi đè .env đang dùng.
npm run dev
```

Nếu `python` trỏ sai môi trường, đặt `EKYC_PYTHON` thành đường dẫn Python đã cài các thư viện. Backend gọi engine bằng tiến trình riêng, không có dịch vụ bí mật/port mới cần mở.

Mẫu cấu hình: [.env.example](backend/.env.example). Không commit mật khẩu DB, JWT secret, ảnh mặt hoặc token.

### Android

Yêu cầu JDK21 (đặt `JAVA_HOME` hoặc chọn Gradle JDK trong Android Studio) và Android SDK34. Không hard-code đường dẫn JDK theo máy.

Mở [thư mục Android](android) bằng Android Studio rồi Run. Emulator dùng API `http://10.0.2.2:8080/`. Điện thoại thật cần địa chỉ backend có thể truy cập và HTTPS phù hợp.

- Emulator: Device Manager → Edit → Advanced Settings → **Front camera = Webcam0**, cấp quyền webcam trên máy Windows nếu được hỏi.
- Điện thoại thật: cấp quyền Camera của ứng dụng; không có camera trước hoặc từ chối quyền thì luồng xác minh bị chặn.
- Camera ảo ảnh tĩnh/scene không phải khuôn mặt thật sẽ không được giả báo thành công.

```powershell
cd android
.\gradlew.bat assembleDebug lintDebug --no-daemon
```

APK được tạo sau build tại `android/app/build/outputs/apk/debug/app-debug.apk`; file build không được đưa lên Git.

## 5. Kiểm thử đã thực hiện

| Kiểm tra | Kết quả và giới hạn |
|---|---|
| Android assembleDebug / lintDebug | Thành công; lint không có error, còn warning/deprecation của SDK/Gradle. |
| Backend TypeScript build | Thành công. |
| Bộ kiểm thử bảo mật riêng | **21/21 PASS**: eKYC trước OTP, ngưỡng tiền, user/context/expiry/single-use, chặn tất cả loại giao dịch lớn, OTP sai/hết hạn, retry/idempotency/rollback, đăng ký có mật khẩu/OTP, phân quyền chính chủ, không ghi đè và không dùng token đăng ký cho chuyển tiền trên DB giả lập độc lập. **Không thay DB đang chạy.** |
| Đăng ký thành công với engine thật | **1/1 PASS**: engine OpenCV thật + ảnh công khai, DB và lưu ảnh được giả lập trong tiến trình test; xác nhận lưu đúng vector/Audit Log CUSTOMER, chống ghi đè, rollback + xóa ảnh nếu Audit lỗi. Không đăng ký vào hồ sơ thật. |
| Engine với ảnh công khai | **13/13 PASS**: đăng ký/đối chiếu cùng người thành công; khác người/khác ảnh gốc, lặp ảnh, timestamp và không có mặt bị từ chối. Thêm ảnh xoay90°/180°/270°, EXIF, ảnh chụp gần ở độ phân giải960px, chẩn đoán ảnh 1/ảnh 2 và kiểm tra từng ảnh không cấp quyền xác minh. Đây là kiểm thử engine, không phải ảnh của người dùng. |
| Emulator Android | Đã kiểm tra UI giao dịch15 triệu khóa OTP/thực hiện; luồng tự đăng ký trong Cá nhân có mật khẩu/OTP rồi mở màn hình đồng ý camera. Hủy không đăng ký. Không chuyển tiền và không đăng ký ảnh thật. |
| Kiểm thử camera với chính chủ | **Chưa xác nhận đầu-cuối**: người dùng đã thử ảnh 1 và server trả NO_FACE dù thumbnail có mặt. Đã bổ sung phát hiện đa kích thước sau khi tái hiện lỗi trên fixture ảnh chụp gần; APK mới đã cài. Cần chính chủ thử lại hai tư thế, không coi ảnh mẫu hoặc UI handoff là đăng ký thực tế thành công. |
| Bộ 245 kiểm tra cũ | **Bộ kiểm tra legacy, không dùng làm tiêu chí pass hiện tại.** Kết quả239/245 là baseline trước các lần mở rộng API/format code. Số endpoint/chuỗi source hiện đã đổi; bộ này chưa được cập nhật theo API mới. Đây là kiểm tra source/XML/APK, không phải 245 giao dịch E2E. Có các giả định cũ (số endpoint cố định, STK fallback, tên demo cũ, miễn eKYC khi nạp tiền) không còn phù hợp; có ca yêu cầu chuỗi `setEnabled(true)` thay vì điều kiện camera thực tế. Giữ nguyên các kiểm tra legacy và nêu rõ giới hạn, không nới runtime để làm chúng xanh. Không dùng báo cáo cũ để khẳng định hoàn tất. |

```powershell
cd backend
npm run test:security
npm run test:enrollment
npm run build
```

Nguồn test: [security.test.ts](backend/tests/security.test.ts), [EkycUiSmokeTest.java](android/app/src/androidTest/java/vn/edu/tdtu/phlbanking/EkycUiSmokeTest.java).

## 6. Dữ liệu và giới hạn bảo mật

- Không thay đổi/xóa user, tài khoản, số dư hoặc lịch sử DB hiện tại trong đợt sửa eKYC. Chỉ seed cho **cài mới** được sửa trạng thái thành PENDING.
- Vector SFace lưu tại hồ sơ DB; ảnh gốc được lưu trong thư mục private do [ekycService.ts](backend/src/services/ekycService.ts) tạo khi đăng ký thành công, không public static và bị loại khỏi Git. Không log body chứa ảnh/token/OTP.
- Tự đăng ký: mật khẩu hiện tại + OTP riêng, cooldown 30 giây, chỉ tài khoản active và chỉ hồ sơ của chính mình. Approval 5 phút dùng một lần khi lưu thành công; ảnh lỗi cần challenge chụp mới. Các phiên đăng ký cũng mất khi backend restart.
- Challenge: 120 giây; token xác minh: tối đa 5 phút, một giao dịch, một người, dùng một lần. Backend restart làm mất phiên, buộc xác minh lại. Lưu phiên hiện chỉ hỗ trợ một tiến trình; triển khai nhiều server cần kho phiên dùng chung có atomic claim.
- OTP sai không làm mất token eKYC. Nếu OTP đã đúng nhưng hạch toán thất bại, cần lấy OTP mới; giao dịch thành công retry cùng key không hạch toán lại.
- Trước production: HTTPS, mã hóa dữ liệu sinh trắc at-rest/quản lý khóa, xóa/lưu giữ theo chính sách, kiểm thử tấn công, rate limits, SMS/2FA thật, PAD/liveness có chứng nhận, nhà cung cấp thanh toán thật và kiểm thử tuân thủ. Không gọi bản đồ án là hệ thống ngân hàng an toàn để vận hành thật.

## 7. Phân công nhóm

| Thành viên | Phần phụ trách |
|---|---|
| Nguyễn Trọng Phúc — 52400028 | Phân tích, use case, sơ đồ lớp/ERD/kiến trúc |
| Trần Hữu Long — 52400138 | Android, backend/API, MySQL, tích hợp dịch vụ |
| Lê Khải Hoàn — 52400114 | Kiểm thử, triển khai, báo cáo/slide/video |

Phần lập trình của Long đã có các nhóm màn hình/API, nhưng các mục tích hợp thật, test camera chính chủ và vấn đề nêu trên vẫn phải hoàn tất/xác nhận trước khi chốt đạt toàn bộ đề.


## 8. Dọn code và kiểm tra trước khi commit

TypeScript/Java được định dạng bằng Prettier (plugin Java); Python dùng Black. Dependency được khóa trong lockfile; đã bỏ `uuid` không dùng và cập nhật Zod4.6.7 (khai báo `record` có key schema rõ ràng). Callback lỗi chụp ảnh cũ được chặn bằng generation; file JPEG tạm hết hạn được dọn trong cache riêng.

```powershell
cd backend
npm ci
npm run format:check
npm run typecheck
npm run test:security
npm run test:enrollment
npm run build
npm audit --audit-level=moderate
# Nếu muốn sửa định dạng:
npm run format
npm run format:android
python -m pip install -r face_engine/requirements-dev.txt
python -m black --check face_engine
```

Báo cáo test sinh tự động, APK/build, model ONNX tải về, môi trường Python, `.env`, khóa ký và thư mục ảnh sinh trắc private được loại khỏi Git. Tài khoản/mật khẩu demo chỉ dành cho DB local giả lập, không dùng khi triển khai thật. Chưa xác nhận toàn bộ webcam enrollment/so khớp chính chủ; các giới hạn trong phần5/6 vẫn áp dụng.
