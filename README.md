# PHL Mobile Banking

Đồ án Phát triển ứng dụng di động của Khoa Công nghệ Thông tin, Trường Đại học Tôn Đức Thắng. PHL Mobile Banking mô phỏng dịch vụ ngân hàng trên Android cho hai vai trò là khách hàng và nhân viên ngân hàng.

Repo đang ở giai đoạn khởi tạo. Các thư mục bên dưới là vị trí dự kiến cho mã nguồn, dữ liệu và tài liệu khi nhóm triển khai.

## Cấu trúc repo

| Thư mục | Nội dung |
| --- | --- |
| `android/` | Ứng dụng Android, giao diện theo vai trò và kết nối API |
| `frontend/` | Mã nguồn frontend |
| `backend/` | API, xác thực, phân quyền, xử lý giao dịch và tích hợp dịch vụ |
| `database/` | Schema MySQL 8.0 gồm 13 bảng và hướng dẫn khởi tạo |
| `docs/` | Phạm vi, thiết kế và tài liệu kiểm thử |

## Phạm vi

[Danh mục 33 use case](docs/requirements.md) đối chiếu bảy nhóm chức năng của đề bài với đặc tả PHL. Ứng dụng Android, cơ sở dữ liệu, phát hành Google Play, báo cáo và video demo là các hạng mục của đồ án.

Xem [hướng dẫn cơ sở dữ liệu](database/README.md) để tạo và kiểm tra schema.

## Phân công

| Thành viên | Phụ trách |
| --- | --- |
| Phúc | Phân tích yêu cầu, đặc tả use case, sơ đồ lớp, ERD và kiến trúc |
| Long | Phát triển Android, backend, API, cơ sở dữ liệu và tích hợp dịch vụ |
| Hoàn | Kiểm thử, triển khai, báo cáo, slide và video demo |

Khi thêm mã nguồn cho một chức năng, dùng mã use case tương ứng trong mô tả pull request để nhóm đối chiếu thiết kế và ca kiểm thử. Dùng dữ liệu giả lập khi phát triển và lưu cấu hình chứa khóa dịch vụ ngoài Git.
