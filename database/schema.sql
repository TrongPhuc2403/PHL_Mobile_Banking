-- PHL Mobile Banking: ERD 13 entities (design proposal based on specification)
-- MySQL 8.0; validate against actual backend before deployment
CREATE DATABASE IF NOT EXISTS phl_mobile_banking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE phl_mobile_banking;

CREATE TABLE `UserAccount` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(50) NOT NULL UNIQUE,
  `passwordHash` VARCHAR(255) NOT NULL,
  `role` VARCHAR(30) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `failedAttempts` INT NOT NULL DEFAULT 0,
  `lockedUntil` DATETIME NULL,
  `createdAt` DATETIME NOT NULL
) ENGINE=InnoDB;

CREATE TABLE `CustomerProfile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `userId` BIGINT NOT NULL UNIQUE,
  `cifNumber` VARCHAR(30) NOT NULL UNIQUE,
  `fullName` VARCHAR(150) NOT NULL,
  `idCardNumber` CHAR(12) NOT NULL UNIQUE,
  `dob` DATE NOT NULL,
  `phone` VARCHAR(20) NOT NULL UNIQUE,
  `email` VARCHAR(150) NULL,
  `address` VARCHAR(255) NULL,
  `faceVector` TEXT NULL,
  `eKYCStatus` VARCHAR(30) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE `OfficerProfile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `userId` BIGINT NOT NULL UNIQUE,
  `employeeCode` VARCHAR(30) NOT NULL UNIQUE,
  `fullName` VARCHAR(150) NOT NULL,
  `email` VARCHAR(150) NULL,
  `phone` VARCHAR(20) NULL,
  `branchId` BIGINT NULL,
  `position` VARCHAR(100) NULL
) ENGINE=InnoDB;

CREATE TABLE `BankAccount` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `customerId` BIGINT NOT NULL,
  `accountNumber` VARCHAR(30) NOT NULL UNIQUE,
  `accountType` VARCHAR(20) NOT NULL,
  `currentBalance` BIGINT NOT NULL DEFAULT 0,
  `availableBalance` BIGINT NOT NULL DEFAULT 0,
  `holdAmount` BIGINT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL,
  `openedAt` DATETIME NOT NULL
) ENGINE=InnoDB;

CREATE TABLE `SavingsAccount` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `bankAccountId` BIGINT NOT NULL UNIQUE,
  `termMonths` INT NOT NULL,
  `interestRate` DECIMAL(7,4) NOT NULL,
  `depositDate` DATE NULL,
  `maturityDate` DATE NULL,
  `productType` VARCHAR(30) NOT NULL,
  `accruedInterest` BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE `MortgageLoan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `bankAccountId` BIGINT NOT NULL UNIQUE,
  `loanAmount` BIGINT NOT NULL,
  `remainingPrincipal` BIGINT NOT NULL,
  `interestRate` DECIMAL(7,4) NOT NULL,
  `termMonths` INT NOT NULL,
  `repaymentCycle` VARCHAR(20) NOT NULL,
  `collateralInfo` TEXT NULL
) ENGINE=InnoDB;

CREATE TABLE `RepaymentSchedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `loanId` BIGINT NOT NULL,
  `periodNo` INT NOT NULL,
  `dueDate` DATE NOT NULL,
  `principalAmount` BIGINT NOT NULL,
  `interestAmount` BIGINT NOT NULL,
  `totalAmount` BIGINT NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `paidAt` DATETIME NULL,
  UNIQUE KEY uq_loan_period (`loanId`, `periodNo`)
) ENGINE=InnoDB;

CREATE TABLE `Transaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `traceId` VARCHAR(40) NOT NULL UNIQUE,
  `idempotencyKey` VARCHAR(100) NOT NULL UNIQUE,
  `fromAccId` BIGINT NULL,
  `toAccId` BIGINT NULL,
  `toBankCode` VARCHAR(20) NULL,
  `toAccName` VARCHAR(150) NULL,
  `amount` BIGINT NOT NULL,
  `fee` BIGINT NOT NULL DEFAULT 0,
  `content` VARCHAR(255) NULL,
  `type` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `createdAt` DATETIME NOT NULL
) ENGINE=InnoDB;

CREATE TABLE `BalanceFluctuation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `transactionId` BIGINT NOT NULL,
  `bankAccountId` BIGINT NOT NULL,
  `changeAmount` BIGINT NOT NULL,
  `balanceBefore` BIGINT NOT NULL,
  `balanceAfter` BIGINT NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `timestamp` DATETIME NOT NULL
) ENGINE=InnoDB;

CREATE TABLE `OTPVerification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `userId` BIGINT NOT NULL,
  `transactionId` BIGINT NULL,
  `otpHash` VARCHAR(255) NOT NULL,
  `otpType` VARCHAR(30) NOT NULL,
  `expiresAt` DATETIME NOT NULL,
  `attempts` INT NOT NULL DEFAULT 0,
  `isUsed` BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;

CREATE TABLE `UtilityOrder` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `customerId` BIGINT NOT NULL,
  `serviceType` VARCHAR(30) NOT NULL,
  `billCode` VARCHAR(60) NULL,
  `supplier` VARCHAR(100) NULL,
  `amount` BIGINT NOT NULL,
  `holdExpiresAt` DATETIME NULL,
  `orderStatus` VARCHAR(30) NOT NULL,
  `ticketData` JSON NULL
) ENGINE=InnoDB;

CREATE TABLE `BranchLocation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `branchName` VARCHAR(150) NOT NULL,
  `address` VARCHAR(255) NOT NULL,
  `latitude` DECIMAL(10,7) NOT NULL,
  `longitude` DECIMAL(10,7) NOT NULL,
  `isMockData` BOOLEAN NOT NULL DEFAULT TRUE,
  `phone` VARCHAR(20) NULL
) ENGINE=InnoDB;

CREATE TABLE `AuditLog` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `actorId` BIGINT NOT NULL,
  `actorRole` VARCHAR(30) NOT NULL,
  `action` VARCHAR(100) NOT NULL,
  `entityName` VARCHAR(100) NULL,
  `entityId` BIGINT NULL,
  `oldValue` JSON NULL,
  `newValue` JSON NULL,
  `timestamp` DATETIME NOT NULL
) ENGINE=InnoDB;

ALTER TABLE `CustomerProfile` ADD CONSTRAINT `fk_CustomerProfile_userId` FOREIGN KEY (`userId`) REFERENCES `UserAccount` (`id`);
ALTER TABLE `OfficerProfile` ADD CONSTRAINT `fk_OfficerProfile_userId` FOREIGN KEY (`userId`) REFERENCES `UserAccount` (`id`);
ALTER TABLE `OTPVerification` ADD CONSTRAINT `fk_OTPVerification_userId` FOREIGN KEY (`userId`) REFERENCES `UserAccount` (`id`);
ALTER TABLE `BankAccount` ADD CONSTRAINT `fk_BankAccount_customerId` FOREIGN KEY (`customerId`) REFERENCES `CustomerProfile` (`id`);
ALTER TABLE `SavingsAccount` ADD CONSTRAINT `fk_SavingsAccount_bankAccountId` FOREIGN KEY (`bankAccountId`) REFERENCES `BankAccount` (`id`);
ALTER TABLE `MortgageLoan` ADD CONSTRAINT `fk_MortgageLoan_bankAccountId` FOREIGN KEY (`bankAccountId`) REFERENCES `BankAccount` (`id`);
ALTER TABLE `RepaymentSchedule` ADD CONSTRAINT `fk_RepaymentSchedule_loanId` FOREIGN KEY (`loanId`) REFERENCES `MortgageLoan` (`id`);
ALTER TABLE `Transaction` ADD CONSTRAINT `fk_Transaction_fromAccId` FOREIGN KEY (`fromAccId`) REFERENCES `BankAccount` (`id`);
ALTER TABLE `Transaction` ADD CONSTRAINT `fk_Transaction_toAccId` FOREIGN KEY (`toAccId`) REFERENCES `BankAccount` (`id`);
ALTER TABLE `BalanceFluctuation` ADD CONSTRAINT `fk_BalanceFluctuation_transactionId` FOREIGN KEY (`transactionId`) REFERENCES `Transaction` (`id`);
ALTER TABLE `BalanceFluctuation` ADD CONSTRAINT `fk_BalanceFluctuation_bankAccountId` FOREIGN KEY (`bankAccountId`) REFERENCES `BankAccount` (`id`);
ALTER TABLE `UtilityOrder` ADD CONSTRAINT `fk_UtilityOrder_customerId` FOREIGN KEY (`customerId`) REFERENCES `CustomerProfile` (`id`);
ALTER TABLE `OfficerProfile` ADD CONSTRAINT `fk_OfficerProfile_branchId` FOREIGN KEY (`branchId`) REFERENCES `BranchLocation` (`id`);
ALTER TABLE `AuditLog` ADD CONSTRAINT `fk_AuditLog_actorId` FOREIGN KEY (`actorId`) REFERENCES `UserAccount` (`id`);
ALTER TABLE `OTPVerification` ADD CONSTRAINT `fk_OTPVerification_transactionId` FOREIGN KEY (`transactionId`) REFERENCES `Transaction` (`id`);

-- Important: model does not yet have a direct UtilityOrder -> Transaction FK.
-- Business rules (account type, three initial accounts, transaction invariants) need application logic or further constraints.
