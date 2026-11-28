-- PHL Mobile Banking: Seed data for development & testing
-- Run after database/schema.sql
USE phl_mobile_banking;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `AuditLog`;
TRUNCATE TABLE `OTPVerification`;
TRUNCATE TABLE `BalanceFluctuation`;
TRUNCATE TABLE `Transaction`;
TRUNCATE TABLE `RepaymentSchedule`;
TRUNCATE TABLE `MortgageLoan`;
TRUNCATE TABLE `SavingsAccount`;
TRUNCATE TABLE `BankAccount`;
TRUNCATE TABLE `UtilityOrder`;
TRUNCATE TABLE `OfficerProfile`;
TRUNCATE TABLE `CustomerProfile`;
TRUNCATE TABLE `BranchLocation`;
TRUNCATE TABLE `UserAccount`;
SET FOREIGN_KEY_CHECKS = 1;

-- 1. Branch Locations (Quận 7 & khu vực lân cận TP.HCM phục vụ UC-MAP-01)
INSERT INTO `BranchLocation` (`id`, `branchName`, `address`, `latitude`, `longitude`, `isMockData`, `phone`) VALUES
(1, 'PHL Bank - Chi nhánh Tân Phong (Gần ĐH TĐT)', '19 Nguyễn Hữu Thọ, P. Tân Phong, Quận 7, TP.HCM', 10.7326700, 106.6997600, TRUE, '02837755001'),
(2, 'PHL Bank - PGD Phú Mỹ Hưng', '101 Tôn Dật Tiên, P. Tân Phú, Quận 7, TP.HCM', 10.7292400, 106.7186100, TRUE, '02837755002'),
(3, 'PHL Bank - Chi nhánh Nguyễn Thị Thập', '312 Nguyễn Thị Thập, P. Tân Quy, Quận 7, TP.HCM', 10.7399800, 106.7095500, TRUE, '02837755003'),
(4, 'PHL Bank - PGD Lotte Mart Nam Sài Gòn', '469 Nguyễn Hữu Thọ, P. Tân Hưng, Quận 7, TP.HCM', 10.7408200, 106.7021400, TRUE, '02837755004'),
(5, 'PHL Bank - Hội sở Quận 1', '72 Lê Thánh Tôn, P. Bến Nghé, Quận 1, TP.HCM', 10.7782200, 106.7019200, TRUE, '02838221000'),
(6, 'PHL Bank - PGD Nguyễn Văn Linh', '1058 Nguyễn Văn Linh, P. Tân Phong, Quận 7, TP.HCM', 10.7297100, 106.7034800, TRUE, '02837755006');

-- 2. User Accounts (Mật khẩu mặc định cho tất cả tài khoản demo: Phl@2026)
-- Hash bcryptjs ($2a$10$...) cho 'Phl@2026'
INSERT INTO `UserAccount` (`id`, `username`, `passwordHash`, `role`, `status`, `failedAttempts`, `lockedUntil`, `createdAt`) VALUES
(1, 'phuc.customer', '$2a$10$W89C7qR/R26PwCbnCvj3tunPx0thd3ZjBAoWvd1xX97/Zr9oQiuWC', 'CUSTOMER', 'ACTIVE', 0, NULL, NOW()),
(2, 'hoan.customer', '$2a$10$W89C7qR/R26PwCbnCvj3tunPx0thd3ZjBAoWvd1xX97/Zr9oQiuWC', 'CUSTOMER', 'ACTIVE', 0, NULL, NOW()),
(3, 'long.officer',  '$2a$10$W89C7qR/R26PwCbnCvj3tunPx0thd3ZjBAoWvd1xX97/Zr9oQiuWC', 'OFFICER',  'ACTIVE', 0, NULL, NOW());

-- 3. Customer Profiles & Officer Profile
INSERT INTO `CustomerProfile` (`id`, `userId`, `cifNumber`, `fullName`, `idCardNumber`, `dob`, `phone`, `email`, `address`, `faceVector`, `eKYCStatus`) VALUES
(1, 1, 'CIF20260001', 'Nguyễn Trọng Phúc', '079204839412', '2004-05-18', '0901234567', '52400028@student.tdtu.edu.vn', '19 Nguyễn Hữu Thọ, Quận 7, TP.HCM', NULL, 'UNVERIFIED'),
(2, 2, 'CIF20260002', 'Lê Khải Hoàn', '079204517289', '2004-09-22', '0909876543', '52400114@student.tdtu.edu.vn', '101 Tôn Dật Tiên, Quận 7, TP.HCM', NULL, 'PENDING');

INSERT INTO `OfficerProfile` (`id`, `userId`, `employeeCode`, `fullName`, `email`, `phone`, `branchId`, `position`) VALUES
(1, 3, 'EMP-PHL-001', 'Trần Hữu Long', '52400138@student.tdtu.edu.vn', '02837755099', 1, 'Chuyên viên Quan hệ Khách hàng');

-- 4. Bank Accounts (Khách hàng 1 có đủ 3 loại: CHECKING, SAVINGS, MORTGAGE)
INSERT INTO `BankAccount` (`id`, `customerId`, `accountNumber`, `accountType`, `currentBalance`, `availableBalance`, `holdAmount`, `status`, `openedAt`) VALUES
(1, 1, '52400028', 'CHECKING', 45500000, 45500000, 0, 'ACTIVE', '2025-01-10 09:00:00'),
(2, 1, '190020010001', 'SAVINGS',  120000000, 120000000, 0, 'ACTIVE', '2025-06-01 10:30:00'),
(3, 1, '190030010001', 'MORTGAGE', 850000000, 0, 0, 'ACTIVE', '2024-11-15 14:00:00'),
(4, 2, '52400114', 'CHECKING', 18250000, 18250000, 0, 'ACTIVE', '2025-02-20 11:15:00'),
(5, 2, '190020020001', 'SAVINGS', 80000000, 80000000, 0, 'ACTIVE', '2025-08-10 09:30:00'),
(6, 2, '190030020001', 'MORTGAGE', 500000000, 0, 0, 'ACTIVE', '2025-09-01 14:00:00');

-- 5. Savings Account details
INSERT INTO `SavingsAccount` (`id`, `bankAccountId`, `termMonths`, `interestRate`, `depositDate`, `maturityDate`, `productType`, `accruedInterest`) VALUES
(1, 2, 12, 5.8000, '2025-06-01', '2026-06-01', 'Tiết kiệm trực tuyến có kỳ hạn', 3480000),
(2, 5, 6, 5.5000, '2025-08-10', '2026-02-10', 'Tiết kiệm trực tuyến có kỳ hạn', 1833333);

-- 6. Mortgage Loan & Repayment Schedule
INSERT INTO `MortgageLoan` (`id`, `bankAccountId`, `loanAmount`, `remainingPrincipal`, `interestRate`, `termMonths`, `repaymentCycle`, `collateralInfo`) VALUES
(1, 3, 1000000000, 850000000, 8.2000, 120, 'MONTHLY', 'Căn hộ Sunrise Cityview, Quận 7, TP.HCM'),
(2, 6, 500000000, 500000000, 7.9000, 60, 'MONTHLY', 'Xe ô tô Mazda CX-5, biển số 51G-123.45');

INSERT INTO `RepaymentSchedule` (`id`, `loanId`, `periodNo`, `dueDate`, `principalAmount`, `interestAmount`, `totalAmount`, `status`, `paidAt`) VALUES
(1, 1, 1, '2026-10-15', 8333333, 5865000, 14198333, 'PAID', '2026-10-14 09:12:00'),
(2, 1, 2, '2026-11-15', 8333333, 5808333, 14141666, 'PAID', '2026-11-15 08:45:00'),
(3, 1, 3, '2026-12-15', 8333333, 5751666, 14084999, 'PENDING', NULL),
(4, 1, 4, '2027-01-15', 8333333, 5695000, 14028333, 'PENDING', NULL),
(5, 2, 1, '2026-10-01', 8333333, 3291667, 11625000, 'PAID', '2026-09-30 10:05:00'),
(6, 2, 2, '2026-11-01', 8333333, 3236806, 11570139, 'PAID', '2026-11-01 09:20:00'),
(7, 2, 3, '2026-12-01', 8333333, 3181944, 11515277, 'PENDING', NULL);

-- 7. Sample Transactions & Balance Fluctuations
INSERT INTO `Transaction` (`id`, `traceId`, `idempotencyKey`, `fromAccId`, `toAccId`, `toBankCode`, `toAccName`, `amount`, `fee`, `content`, `type`, `status`, `createdAt`) VALUES
(1, 'TRC-20261120-0001', 'seed-idem-0001', 1, 4, 'PHL', 'LE KHAI HOAN', 1500000, 0, 'Chuyển tiền ăn trưa nhóm đồ án', 'INTERNAL_TRANSFER', 'SUCCESS', '2026-11-20 12:15:00'),
(2, 'TRC-20261121-0002', 'seed-idem-0002', NULL, 1, 'PHL', 'NGUYEN TRONG PHUC', 10000000, 0, 'Nạp tiền vào tài khoản thanh toán', 'DEPOSIT', 'SUCCESS', '2026-11-21 09:30:00'),
(3, 'TRC-20261122-0003', 'seed-idem-0003', 1, NULL, 'VCB', 'NGUYEN VAN AN', 2000000, 0, 'Chuyen khoan lien ngan hang VCB', 'INTERBANK_TRANSFER', 'SUCCESS', '2026-11-22 10:15:00'),
(4, 'TRC-20261123-0004', 'seed-idem-0004', 1, NULL, NULL, 'EVN HCMC', 685000, 0, 'Thanh toan tien dien EVN HCMC', 'UTILITY_PAYMENT', 'SUCCESS', '2026-11-23 08:20:00'),
(5, 'TRC-20261124-0005', 'seed-idem-0005', 1, NULL, 'VNPAY', 'VNPAY GATEWAY', 459000, 0, 'Thanh toan qua cong VNPAY', 'GATEWAY_PAYMENT', 'SUCCESS', '2026-11-24 19:45:00');

INSERT INTO `BalanceFluctuation` (`id`, `transactionId`, `bankAccountId`, `changeAmount`, `balanceBefore`, `balanceAfter`, `type`, `timestamp`) VALUES
(1, 1, 1, -1500000, 37000000, 35500000, 'DEBIT', '2026-11-20 12:15:00'),
(2, 1, 4, 1500000, 16750000, 18250000, 'CREDIT', '2026-11-20 12:15:00'),
(3, 2, 1, 10000000, 35500000, 45500000, 'CREDIT', '2026-11-21 09:30:00'),
(4, 3, 1, -2000000, 45500000, 43500000, 'DEBIT', '2026-11-22 10:15:00'),
(5, 4, 1, -685000, 43500000, 42815000, 'DEBIT', '2026-11-23 08:20:00'),
(6, 5, 1, -459000, 42815000, 42356000, 'DEBIT', '2026-11-24 19:45:00');

-- 8. Sample Audit Log
INSERT INTO `AuditLog` (`id`, `actorId`, `actorRole`, `action`, `entityName`, `entityId`, `oldValue`, `newValue`, `timestamp`) VALUES
(1, 3, 'OFFICER', 'CREATE_CUSTOMER_ACCOUNT', 'BankAccount', 1, NULL, JSON_OBJECT('accountNumber', '52400028', 'accountType', 'CHECKING'), '2025-01-10 09:00:00');
