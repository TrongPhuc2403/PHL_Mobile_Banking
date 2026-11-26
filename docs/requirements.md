# Phạm vi nghiệp vụ

FinalProject_2026_2027.pdf (tài liệu nhóm cung cấp riêng) là đề bài chính thức và xác định bảy nhóm yêu cầu. PHL_Banking_App.docx (tài liệu nhóm cung cấp riêng) chi tiết hóa các yêu cầu thành 33 use case và bổ sung những luồng riêng của PHL. Mô tả hệ thống và phân công nhiệm vụ.docx (tài liệu nhóm cung cấp riêng) diễn giải đề bài và ghi nhận phân công trong nhóm.

## Đối chiếu với đề bài

| Nhóm yêu cầu | Use case liên quan |
| --- | --- |
| Hồ sơ người dùng | UC-AUTH-01 đến 06, UC-OFF-01 đến 04 |
| Bảo mật | UC-AUTH-01, UC-SEC-01 đến 02 |
| Quản lý tài khoản | UC-ACC-01 đến 07 |
| Quản lý giao dịch | UC-TXN-01 đến 04 |
| Tiện ích | UC-UTIL-01 đến 07 |
| Bản đồ và điều hướng | UC-MAP-01 |
| Chatbot AI | UC-AI-01 |

UC-NOTI-01 là phần thông báo bổ sung trong đặc tả PHL.

## Danh mục use case

| Mã | Chức năng | Vai trò |
| --- | --- | --- |
| UC-AUTH-01 | Đăng nhập | Khách hàng, nhân viên |
| UC-AUTH-02 | Đăng xuất | Khách hàng, nhân viên |
| UC-AUTH-03 | Đổi mật khẩu | Khách hàng, nhân viên |
| UC-AUTH-04 | Khôi phục mật khẩu | Khách hàng |
| UC-AUTH-05 | Xem thông tin cá nhân | Khách hàng, nhân viên |
| UC-AUTH-06 | Cập nhật thông tin liên lạc | Khách hàng |
| UC-SEC-01 | Xác minh sinh trắc học eKYC | Khách hàng |
| UC-SEC-02 | Xác thực hai yếu tố bằng OTP | Khách hàng, nhân viên |
| UC-ACC-01 | Xem thông tin và số dư tài khoản | Khách hàng |
| UC-ACC-02 | Xem lãi suất và tiền lãi tiết kiệm | Khách hàng |
| UC-ACC-03 | Điều chỉnh lãi suất | Nhân viên |
| UC-ACC-04 | Xem lịch trả nợ vay thế chấp | Khách hàng |
| UC-ACC-05 | Xem lịch sử giao dịch | Khách hàng |
| UC-ACC-06 | Nạp tiền vào tài khoản | Khách hàng |
| UC-ACC-07 | Rút tiền khỏi tài khoản | Khách hàng |
| UC-TXN-01 | Chuyển tiền nội bộ | Khách hàng |
| UC-TXN-02 | Chuyển tiền liên ngân hàng | Khách hàng |
| UC-TXN-03 | Thanh toán qua VNPay hoặc Stripe | Khách hàng |
| UC-TXN-04 | Xem, lưu hoặc chia sẻ biên lai điện tử | Khách hàng |
| UC-UTIL-01 | Thanh toán hóa đơn điện | Khách hàng |
| UC-UTIL-02 | Thanh toán hóa đơn nước | Khách hàng |
| UC-UTIL-03 | Nạp tiền điện thoại | Khách hàng |
| UC-UTIL-04 | Mua vé máy bay | Khách hàng |
| UC-UTIL-05 | Mua vé xem phim | Khách hàng |
| UC-UTIL-06 | Đặt phòng khách sạn | Khách hàng |
| UC-UTIL-07 | Thanh toán thương mại điện tử | Khách hàng |
| UC-NOTI-01 | Xem thông báo biến động và chính sách | Khách hàng |
| UC-MAP-01 | Tìm chi nhánh và dẫn đường đi bộ | Khách hàng |
| UC-AI-01 | Trò chuyện với chatbot và mở màn hình chức năng | Khách hàng |
| UC-OFF-01 | Liệt kê và tìm kiếm khách hàng | Nhân viên |
| UC-OFF-02 | Tạo tài khoản khách hàng | Nhân viên |
| UC-OFF-03 | Cập nhật hồ sơ và trạng thái khách hàng | Nhân viên |
| UC-OFF-04 | Xem nhật ký kiểm toán | Nhân viên |

## Ràng buộc ảnh hưởng đến thiết kế

- Khách hàng và nhân viên có giao diện và quyền hạn riêng. Backend kiểm tra quyền trên mỗi thao tác cần xác thực.
- Hệ thống hỗ trợ tài khoản thanh toán, tiết kiệm và vay thế chấp. Mỗi loại có dữ liệu hiển thị và nghiệp vụ riêng.
- Hệ thống kiểm tra tính hợp lệ, quyền thực hiện và xác thực hai yếu tố trước khi ghi nhận giao dịch. Theo đặc tả PHL, giao dịch trên 10 triệu đồng cần xác minh khuôn mặt trước bước OTP.
- Chuyển tiền nội bộ phải hạch toán cả hai phía trong một giao dịch cơ sở dữ liệu. Yêu cầu gửi lại với cùng khóa idempotency phải trả về cùng giao dịch và chỉ hạch toán một lần.
- Hiển thị tuyến đi bộ ngắn nhất tới chi nhánh gần nhất dựa trên mạng lưới đường.
- Chatbot trả lời và đưa nút mở chức năng tương ứng. Người dùng xác nhận và thực hiện giao dịch trong luồng ngân hàng.


## Bổ sung sau rà soát eKYC

- Khách hàng có thể tự đăng ký ảnh gốc thật tại Cá nhân sau xác nhận mật khẩu + OTP, hoặc nhờ nhân viên hỗ trợ; cả hai đều cần đồng ý chụp/lưu và Audit Log. Vector mô phỏng không hợp lệ.
- Giao dịch trên 10 triệu phải đối chiếu ảnh gốc **trước khi phát OTP**; backend chặn cả khi gọi API trực tiếp. Áp dụng cả nạp/rút và tiện ích, không chỉ chuyển nội bộ.
- Kiểm tra hai tư thế OpenCV trong bản đồ án **chưa đáp ứng liveness/PAD được chứng nhận**, vì vậy chưa được chốt toàn bộ yêu cầu sinh trắc/production hoàn tất.
- OTP giao dịch mặc định 120 giây, gắn context; giao nhận SMS/2FA thực tế vẫn là phần chưa tích hợp.

Hướng dẫn/bằng chứng và giới hạn: [ekyc-testing.md](ekyc-testing.md), [README.md](../README.md).
