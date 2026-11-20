# Cơ sở dữ liệu PHL Mobile Banking

[`schema.sql`](schema.sql) khởi tạo cơ sở dữ liệu `phl_mobile_banking` trên MySQL 8.0. Schema dùng InnoDB và `utf8mb4`, giữ tên bảng, cột và quan hệ trong tệp thiết kế `PHL_Mobile_Banking_ERD_13_Bang.sql`.

## Khởi tạo

Từ thư mục gốc của repo, chạy bằng MySQL client. Thay `root` bằng tài khoản MySQL có quyền tạo cơ sở dữ liệu và bảng nếu cần. Client sẽ hỏi mật khẩu.

```powershell
mysql --host=127.0.0.1 --user=root --password --default-character-set=utf8mb4 --execute="source database/schema.sql"
```

Nếu `mysql` chưa có trong `PATH` trên Windows, dùng đường dẫn đầy đủ đến client.

```powershell
& "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" --host=127.0.0.1 --user=root --password --default-character-set=utf8mb4 --execute="source database/schema.sql"
```

Chạy script một lần trên schema chưa có bảng. MySQL báo lỗi nếu bảng đã tồn tại. Nếu lần import bị gián đoạn, kiểm tra các bảng và khóa ngoại đã tạo trước khi chạy tiếp phần còn thiếu.

## Kiểm tra sau khi cài

```sql
USE phl_mobile_banking;
SHOW TABLES;

SELECT COUNT(*) AS foreign_key_count
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = 'phl_mobile_banking';
```

Kết quả mong đợi là 13 bảng và 15 khóa ngoại.

| Nhóm | Bảng |
| --- | --- |
| Người dùng | `UserAccount`, `CustomerProfile`, `OfficerProfile` |
| Tài khoản và khoản vay | `BankAccount`, `SavingsAccount`, `MortgageLoan`, `RepaymentSchedule` |
| Giao dịch và bảo mật | `Transaction`, `BalanceFluctuation`, `OTPVerification`, `AuditLog` |
| Tiện ích và chi nhánh | `UtilityOrder`, `BranchLocation` |

## Tích hợp backend

- Các cột số tiền là `BIGINT` và lưu theo đơn vị đồng Việt Nam. Lãi suất dùng `DECIMAL(7,4)`.
- Backend cần nhất quán về múi giờ khi ghi và đọc `DATETIME`, kiểm tra loại tài khoản, OTP, số dư khả dụng và thực hiện hạch toán trong một giao dịch cơ sở dữ liệu. Khóa duy nhất trên `Transaction.idempotencyKey` ngăn ghi hai dòng cho cùng khóa.
- Theo thiết kế đính kèm, `UtilityOrder` liên kết với `CustomerProfile`. Lược đồ hiện chưa có khóa ngoại trực tiếp giữa `UtilityOrder` và `Transaction`.
