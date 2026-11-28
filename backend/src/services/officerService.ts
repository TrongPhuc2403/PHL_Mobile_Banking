import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import { ResultSetHeader, RowDataPacket } from 'mysql2';
import { pool, withTransaction } from '../config/db';
import { AppError } from '../utils/errors';

export async function searchCustomers(
  keyword?: string,
  options: { limit?: number; offset?: number } = {},
) {
  const limit = Math.min(Math.max(options.limit ?? 20, 1), 100);
  const offset = Math.max(options.offset ?? 0, 0);
  const q = keyword ? `%${keyword.trim()}%` : '%';

  const [rows] = await pool.query<RowDataPacket[]>(
    `SELECT cp.id AS customerId, cp.userId, cp.cifNumber, cp.fullName, cp.idCardNumber,
            cp.dob, cp.phone, cp.email, cp.address,
            CASE WHEN cp.eKYCStatus = 'VERIFIED' AND cp.faceVector LIKE '{%OpenCV-SFace-v1%'
                 THEN 'VERIFIED' ELSE 'PENDING' END AS eKYCStatus,
            ua.username, ua.status AS accountStatus, ua.createdAt,
            (SELECT COUNT(*) FROM BankAccount ba WHERE ba.customerId = cp.id) AS totalAccounts
     FROM CustomerProfile cp
     JOIN UserAccount ua ON ua.id = cp.userId
     WHERE cp.fullName LIKE ? OR cp.cifNumber LIKE ? OR cp.phone LIKE ? OR cp.idCardNumber LIKE ?
     ORDER BY cp.id DESC
     LIMIT ? OFFSET ?`,
    [q, q, q, q, limit, offset],
  );

  return {
    data: rows,
    limit,
    offset,
    hasMore: rows.length === limit,
  };
}

export async function createCustomerAccount(params: {
  actorUserId: number;
  username: string;
  password: string;
  fullName: string;
  idCardNumber: string;
  dob: string;
  phone: string;
  email?: string;
  address?: string;
  initialDeposit: number;
}) {
  const passwordHash = await bcrypt.hash(params.password, 10);
  const cifNumber = `CIF${new Date().getFullYear()}${crypto.randomInt(1000, 9999)}`;
  const accountNumber = `190010${crypto.randomInt(100000, 999999)}`;

  return withTransaction(async (conn) => {
    const [dup] = await conn.query<RowDataPacket[]>(
      'SELECT id FROM UserAccount WHERE username = ? LIMIT 1',
      [params.username.trim()],
    );
    if (dup[0]) {
      throw new AppError(409, 'USERNAME_EXISTS', 'Tên đăng nhập đã tồn tại trong hệ thống');
    }

    // Kiểm tra trùng CCCD
    const [dupIdCard] = await conn.query<RowDataPacket[]>(
      'SELECT id FROM CustomerProfile WHERE idCardNumber = ? LIMIT 1',
      [params.idCardNumber.trim()],
    );
    if (dupIdCard[0]) {
      throw new AppError(409, 'ID_CARD_EXISTS', 'Số CCCD đã được đăng ký trong hệ thống');
    }

    // Kiểm tra trùng SĐT
    const [dupPhone] = await conn.query<RowDataPacket[]>(
      'SELECT id FROM CustomerProfile WHERE phone = ? LIMIT 1',
      [params.phone.trim()],
    );
    if (dupPhone[0]) {
      throw new AppError(409, 'PHONE_EXISTS', 'Số điện thoại đã được đăng ký trong hệ thống');
    }

    const [userInsert] = await conn.query<ResultSetHeader>(
      `INSERT INTO UserAccount (username, passwordHash, role, status, failedAttempts, lockedUntil, createdAt)
       VALUES (?, ?, 'CUSTOMER', 'ACTIVE', 0, NULL, NOW())`,
      [params.username.trim(), passwordHash],
    );
    const userId = userInsert.insertId;

    const [profileInsert] = await conn.query<ResultSetHeader>(
      `INSERT INTO CustomerProfile (userId, cifNumber, fullName, idCardNumber, dob, phone, email, address, eKYCStatus)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')`,
      [
        userId,
        cifNumber,
        params.fullName.trim(),
        params.idCardNumber.trim(),
        params.dob,
        params.phone.trim(),
        params.email?.trim() || null,
        params.address?.trim() || null,
      ],
    );
    const customerId = profileInsert.insertId;

    const [accInsert] = await conn.query<ResultSetHeader>(
      `INSERT INTO BankAccount (customerId, accountNumber, accountType, currentBalance, availableBalance, holdAmount, status, openedAt)
       VALUES (?, ?, 'CHECKING', ?, ?, 0, 'ACTIVE', NOW())`,
      [customerId, accountNumber, params.initialDeposit, params.initialDeposit],
    );

    await conn.query(
      `INSERT INTO AuditLog (actorId, actorRole, action, entityName, entityId, oldValue, newValue, timestamp)
       VALUES (?, 'OFFICER', 'CREATE_CUSTOMER_ACCOUNT', 'CustomerProfile', ?, NULL, ?, NOW())`,
      [
        params.actorUserId,
        customerId,
        JSON.stringify({
          cifNumber,
          accountNumber,
          fullName: params.fullName,
          initialDeposit: params.initialDeposit,
        }),
      ],
    );

    return {
      userId,
      customerId,
      cifNumber,
      bankAccountId: accInsert.insertId,
      accountNumber,
    };
  });
}

export async function updateCustomerProfileAndStatus(params: {
  actorUserId: number;
  customerId: number;
  status?: 'ACTIVE' | 'LOCKED';
  phone?: string;
  email?: string;
  address?: string;
}) {
  return withTransaction(async (conn) => {
    const [rows] = await conn.query<RowDataPacket[]>(
      `SELECT cp.*, ua.status AS userStatus
       FROM CustomerProfile cp
       JOIN UserAccount ua ON ua.id = cp.userId
       WHERE cp.id = ? LIMIT 1`,
      [params.customerId],
    );
    const current = rows[0];
    if (!current) {
      throw new AppError(404, 'CUSTOMER_NOT_FOUND', 'Không tìm thấy khách hàng');
    }

    if (params.status) {
      await conn.query('UPDATE UserAccount SET status = ? WHERE id = ?', [
        params.status,
        current.userId,
      ]);
    }

    await conn.query('UPDATE CustomerProfile SET phone = ?, email = ?, address = ? WHERE id = ?', [
      params.phone ?? current.phone,
      params.email ?? current.email,
      params.address ?? current.address,
      params.customerId,
    ]);

    await conn.query(
      `INSERT INTO AuditLog (actorId, actorRole, action, entityName, entityId, oldValue, newValue, timestamp)
       VALUES (?, 'OFFICER', 'UPDATE_CUSTOMER_PROFILE', 'CustomerProfile', ?, ?, ?, NOW())`,
      [
        params.actorUserId,
        params.customerId,
        JSON.stringify({
          status: current.userStatus,
          phone: current.phone,
          email: current.email,
          address: current.address,
        }),
        JSON.stringify({
          status: params.status ?? current.userStatus,
          phone: params.phone ?? current.phone,
          email: params.email ?? current.email,
          address: params.address ?? current.address,
        }),
      ],
    );

    return { customerId: params.customerId, updated: true };
  });
}

export async function adjustInterestRate(params: {
  actorUserId: number;
  targetType: 'SAVINGS' | 'MORTGAGE';
  targetId: number;
  newInterestRate: number;
}) {
  return withTransaction(async (conn) => {
    const tableName = params.targetType === 'SAVINGS' ? 'SavingsAccount' : 'MortgageLoan';
    const [rows] = await conn.query<RowDataPacket[]>(
      `SELECT id, interestRate FROM ${tableName} WHERE id = ? LIMIT 1`,
      [params.targetId],
    );
    const current = rows[0];
    if (!current) {
      throw new AppError(404, 'PRODUCT_NOT_FOUND', 'Không tìm thấy tài khoản tiết kiệm/khoản vay');
    }

    await conn.query(`UPDATE ${tableName} SET interestRate = ? WHERE id = ?`, [
      params.newInterestRate,
      params.targetId,
    ]);

    const [accRows] = await conn.query<RowDataPacket[]>(
      `SELECT ba.customerId FROM ${tableName === 'SavingsAccount' ? 'SavingsAccount sa JOIN BankAccount ba ON ba.id = sa.bankAccountId' : 'MortgageLoan ml JOIN BankAccount ba ON ba.id = ml.bankAccountId'} WHERE ${tableName === 'SavingsAccount' ? 'sa' : 'ml'}.id = ? LIMIT 1`,
      [params.targetId],
    );
    if (accRows[0]) {
      const productLabel = params.targetType === 'SAVINGS' ? 'Tiết kiệm' : 'Vay thế chấp';
      await conn.query(
        `INSERT INTO PolicyNotification (customerId, category, title, message, isRead, publishedAt)
         VALUES (?, 'INTEREST_RATE', ?, ?, FALSE, NOW())`,
        [
          accRows[0].customerId,
          `Thay đổi lãi suất ${productLabel}`,
          `Lãi suất tài khoản ${productLabel} của bạn đã được điều chỉnh từ ${current.interestRate}%/năm thành ${params.newInterestRate}%/năm. Hiệu lực từ hôm nay.`,
        ],
      );
    }


    await conn.query(
      `INSERT INTO AuditLog (actorId, actorRole, action, entityName, entityId, oldValue, newValue, timestamp)
       VALUES (?, 'OFFICER', 'ADJUST_INTEREST_RATE', ?, ?, ?, ?, NOW())`,
      [
        params.actorUserId,
        tableName,
        params.targetId,
        JSON.stringify({ interestRate: current.interestRate }),
        JSON.stringify({ interestRate: params.newInterestRate }),
      ],
    );

    return {
      targetType: params.targetType,
      targetId: params.targetId,
      oldInterestRate: current.interestRate,
      newInterestRate: params.newInterestRate,
    };
  });
}

export async function listAuditLogs(limit = 50) {
  const [rows] = await pool.query<RowDataPacket[]>(
    `SELECT al.*, ua.username AS actorUsername
     FROM AuditLog al
     JOIN UserAccount ua ON ua.id = al.actorId
     ORDER BY al.timestamp DESC, al.id DESC
     LIMIT ?`,
    [Math.min(limit, 200)],
  );
  return rows;
}
