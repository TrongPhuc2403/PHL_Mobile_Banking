import crypto from 'crypto';
import { ResultSetHeader, RowDataPacket } from 'mysql2';
import { pool, withTransaction } from '../config/db';
import { AppError } from '../utils/errors';
import { authorizeTransaction, assertReplayOwner } from './transactionAuthorization';

interface BankAccountRow extends RowDataPacket {
  id: number;
  customerId: number;
  accountNumber: string;
  accountType: string;
  currentBalance: number;
  availableBalance: number;
  status: string;
}

interface TransactionRow extends RowDataPacket {
  id: number;
  traceId: string;
  idempotencyKey: string;
  fromAccId: number | null;
  toAccId: number | null;
  toBankCode: string | null;
  toAccName: string | null;
  amount: number;
  fee: number;
  content: string | null;
  type: string;
  status: string;
  createdAt: Date;
}

function buildTraceId(): string {
  const datePart = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const randPart = crypto.randomInt(100000, 999999);
  return `TRC-${datePart}-${randPart}`;
}

export async function internalTransfer(params: {
  userId: number;
  customerId: number;
  fromAccountNumber: string;
  toAccountNumber: string;
  amount: number;
  content: string;
  idempotencyKey: string;
  otpCode: string;
  ekycToken?: string;
}) {
  if (params.amount <= 0) {
    throw new AppError(400, 'INVALID_AMOUNT', 'Số tiền giao dịch phải lớn hơn 0');
  }
  if (params.fromAccountNumber === params.toAccountNumber) {
    throw new AppError(400, 'SAME_ACCOUNT', 'Tài khoản nguồn và tài khoản đích phải khác nhau');
  }

  // 1. Kiểm tra Idempotency trước khi xác thực OTP để tránh lỗi nếu client retry
  const [existing] = await pool.query<TransactionRow[]>(
    'SELECT * FROM Transaction WHERE idempotencyKey = ? LIMIT 1',
    [params.idempotencyKey],
  );
  if (existing[0]) {
    await assertReplayOwner(
      existing[0],
      params.customerId,
      params.fromAccountNumber,
      params.amount,
    );
    return { idempotentReplay: true, transaction: existing[0] };
  }

  const authorization = await authorizeTransaction(
    params.userId,
    { ...params, operation: 'INTERNAL' },
    params.otpCode,
    params.ekycToken,
  );
  try {
    // 4. Hạch toán hai phía trong một DB Transaction (ACID)
    const txRecord = await withTransaction(async (conn) => {
      const [fromRows] = await conn.query<BankAccountRow[]>(
        'SELECT * FROM BankAccount WHERE accountNumber = ? AND customerId = ? FOR UPDATE',
        [params.fromAccountNumber, params.customerId],
      );
      const fromAcc = fromRows[0];
      if (!fromAcc) {
        throw new AppError(404, 'SOURCE_ACCOUNT_NOT_FOUND', 'Không tìm thấy tài khoản nguồn');
      }
      if (fromAcc.accountType !== 'CHECKING') {
        throw new AppError(
          400,
          'INVALID_ACCOUNT_TYPE',
          'Chỉ tài khoản thanh toán mới được chuyển tiền',
        );
      }
      if (fromAcc.status !== 'ACTIVE') {
        throw new AppError(403, 'ACCOUNT_BLOCKED', 'Tài khoản nguồn đang bị khóa');
      }
      if (fromAcc.availableBalance < params.amount) {
        throw new AppError(400, 'INSUFFICIENT_BALANCE', 'Số dư khả dụng không đủ');
      }

      const [toRows] = await conn.query<BankAccountRow[]>(
        `SELECT ba.*, cp.fullName AS ownerName
       FROM BankAccount ba
       JOIN CustomerProfile cp ON cp.id = ba.customerId
       WHERE ba.accountNumber = ? FOR UPDATE`,
        [params.toAccountNumber],
      );
      const toAcc = toRows[0] as (BankAccountRow & { ownerName: string }) | undefined;
      if (!toAcc || toAcc.status !== 'ACTIVE') {
        throw new AppError(
          404,
          'DEST_ACCOUNT_NOT_FOUND',
          'Tài khoản thụ hưởng không tồn tại hoặc đang khóa',
        );
      }

      const traceId = buildTraceId();
      const [txInsert] = await conn.query<ResultSetHeader>(
        `INSERT INTO Transaction
       (traceId, idempotencyKey, fromAccId, toAccId, toBankCode, toAccName, amount, fee, content, type, status, createdAt)
       VALUES (?, ?, ?, ?, 'PHL', ?, ?, 0, ?, 'INTERNAL_TRANSFER', 'SUCCESS', NOW())`,
        [
          traceId,
          params.idempotencyKey,
          fromAcc.id,
          toAcc.id,
          toAcc.ownerName.toUpperCase(),
          params.amount,
          params.content,
        ],
      );

      const txId = txInsert.insertId;
      const fromAfter = fromAcc.availableBalance - params.amount;
      const toAfter = toAcc.availableBalance + params.amount;

      await conn.query(
        'UPDATE BankAccount SET currentBalance = ?, availableBalance = ? WHERE id = ?',
        [fromAcc.currentBalance - params.amount, fromAfter, fromAcc.id],
      );
      await conn.query(
        'UPDATE BankAccount SET currentBalance = ?, availableBalance = ? WHERE id = ?',
        [toAcc.currentBalance + params.amount, toAfter, toAcc.id],
      );

      await conn.query(
        `INSERT INTO BalanceFluctuation
       (transactionId, bankAccountId, changeAmount, balanceBefore, balanceAfter, type, timestamp)
       VALUES (?, ?, ?, ?, ?, 'DEBIT', NOW()), (?, ?, ?, ?, ?, 'CREDIT', NOW())`,
        [
          txId,
          fromAcc.id,
          -params.amount,
          fromAcc.availableBalance,
          fromAfter,
          txId,
          toAcc.id,
          params.amount,
          toAcc.availableBalance,
          toAfter,
        ],
      );

      const [created] = await conn.query<TransactionRow[]>(
        'SELECT * FROM Transaction WHERE id = ? LIMIT 1',
        [txId],
      );
      return created[0];
    });
    authorization.complete();

    return { idempotentReplay: false, transaction: txRecord };
  } catch (error) {
    authorization.release();
    throw error;
  }
}

export async function depositOrWithdraw(params: {
  userId: number;
  customerId: number;
  accountNumber: string;
  amount: number;
  mode: 'DEPOSIT' | 'WITHDRAW';
  idempotencyKey: string;
  otpCode: string;
  ekycToken?: string;
}) {
  if (params.amount <= 0) {
    throw new AppError(400, 'INVALID_AMOUNT', 'Số tiền phải lớn hơn 0');
  }

  const [existing] = await pool.query<TransactionRow[]>(
    'SELECT * FROM Transaction WHERE idempotencyKey = ? LIMIT 1',
    [params.idempotencyKey],
  );
  if (existing[0]) {
    await assertReplayOwner(existing[0], params.customerId, params.accountNumber, params.amount);
    return { idempotentReplay: true, transaction: existing[0] };
  }

  const authorization = await authorizeTransaction(
    params.userId,
    { ...params, operation: params.mode },
    params.otpCode,
    params.ekycToken,
  );
  try {
    const txRecord = await withTransaction(async (conn) => {
      const [rows] = await conn.query<BankAccountRow[]>(
        'SELECT * FROM BankAccount WHERE accountNumber = ? AND customerId = ? FOR UPDATE',
        [params.accountNumber, params.customerId],
      );
      const acc = rows[0];
      if (!acc || acc.status !== 'ACTIVE') {
        throw new AppError(404, 'ACCOUNT_NOT_FOUND', 'Tài khoản không hợp lệ hoặc đang bị khóa');
      }
      if (acc.accountType !== 'CHECKING') {
        throw new AppError(
          400,
          'INVALID_ACCOUNT_TYPE',
          'Chỉ tài khoản thanh toán (Checking) mới được phép nạp/rút tiền trực tiếp',
        );
      }
      if (params.mode === 'WITHDRAW' && acc.availableBalance < params.amount) {
        throw new AppError(400, 'INSUFFICIENT_BALANCE', 'Số dư khả dụng không đủ');
      }

      const delta = params.mode === 'DEPOSIT' ? params.amount : -params.amount;
      const after = acc.availableBalance + delta;
      const traceId = buildTraceId();

      const [txInsert] = await conn.query<ResultSetHeader>(
        `INSERT INTO Transaction
       (traceId, idempotencyKey, fromAccId, toAccId, toBankCode, toAccName, amount, fee, content, type, status, createdAt)
       VALUES (?, ?, ?, ?, 'PHL', 'CHINH CHU', ?, 0, ?, ?, 'SUCCESS', NOW())`,
        [
          traceId,
          params.idempotencyKey,
          params.mode === 'WITHDRAW' ? acc.id : null,
          params.mode === 'DEPOSIT' ? acc.id : null,
          params.amount,
          params.mode === 'DEPOSIT' ? 'Nạp tiền vào tài khoản' : 'Rút tiền khỏi tài khoản',
          params.mode,
        ],
      );

      const txId = txInsert.insertId;
      await conn.query(
        'UPDATE BankAccount SET currentBalance = ?, availableBalance = ? WHERE id = ?',
        [acc.currentBalance + delta, after, acc.id],
      );

      await conn.query(
        `INSERT INTO BalanceFluctuation
       (transactionId, bankAccountId, changeAmount, balanceBefore, balanceAfter, type, timestamp)
       VALUES (?, ?, ?, ?, ?, ?, NOW())`,
        [
          txId,
          acc.id,
          delta,
          acc.availableBalance,
          after,
          params.mode === 'DEPOSIT' ? 'CREDIT' : 'DEBIT',
        ],
      );

      const [created] = await conn.query<TransactionRow[]>(
        'SELECT * FROM Transaction WHERE id = ? LIMIT 1',
        [txId],
      );
      return created[0];
    });
    authorization.complete();

    return { idempotentReplay: false, transaction: txRecord };
  } catch (error) {
    authorization.release();
    throw error;
  }
}

export async function getTransactionReceipt(customerId: number, traceId: string) {
  const [rows] = await pool.query<RowDataPacket[]>(
    `SELECT t.*, fa.accountNumber AS fromAccountNumber, ta.accountNumber AS toAccountNumber
     FROM Transaction t
     LEFT JOIN BankAccount fa ON fa.id = t.fromAccId
     LEFT JOIN BankAccount ta ON ta.id = t.toAccId
     WHERE t.traceId = ? AND (fa.customerId = ? OR ta.customerId = ?)
     LIMIT 1`,
    [traceId, customerId, customerId],
  );

  if (!rows[0]) {
    throw new AppError(404, 'RECEIPT_NOT_FOUND', 'Không tìm thấy biên lai giao dịch');
  }
  return rows[0];
}

export async function interbankTransfer(params: {
  userId: number;
  customerId: number;
  fromAccountNumber: string;
  toBankCode: string;
  toAccountNumber: string;
  toAccName: string;
  amount: number;
  content: string;
  idempotencyKey: string;
  otpCode: string;
  ekycToken?: string;
}) {
  if (params.amount <= 0) {
    throw new AppError(400, 'INVALID_AMOUNT', 'Số tiền giao dịch phải lớn hơn 0');
  }

  const [existing] = await pool.query<TransactionRow[]>(
    'SELECT * FROM Transaction WHERE idempotencyKey = ? LIMIT 1',
    [params.idempotencyKey],
  );
  if (existing[0]) {
    await assertReplayOwner(
      existing[0],
      params.customerId,
      params.fromAccountNumber,
      params.amount,
    );
    return { idempotentReplay: true, transaction: existing[0] };
  }

  const authorization = await authorizeTransaction(
    params.userId,
    { ...params, operation: 'INTERBANK' },
    params.otpCode,
    params.ekycToken,
  );
  try {
    const fee = params.amount >= 500_000 ? 0 : 3300; // Napas 24/7 sandbox fee policy
    const totalDebit = params.amount + fee;

    const txRecord = await withTransaction(async (conn) => {
      const [fromRows] = await conn.query<BankAccountRow[]>(
        'SELECT * FROM BankAccount WHERE accountNumber = ? AND customerId = ? FOR UPDATE',
        [params.fromAccountNumber, params.customerId],
      );
      const fromAcc = fromRows[0];
      if (!fromAcc || fromAcc.accountType !== 'CHECKING' || fromAcc.status !== 'ACTIVE') {
        throw new AppError(400, 'INVALID_SOURCE_ACCOUNT', 'Tài khoản nguồn không hợp lệ');
      }
      if (fromAcc.availableBalance < totalDebit) {
        throw new AppError(400, 'INSUFFICIENT_BALANCE', 'Số dư khả dụng không đủ (gồm phí)');
      }

      const traceId = buildTraceId();
      const [txInsert] = await conn.query<ResultSetHeader>(
        `INSERT INTO Transaction
       (traceId, idempotencyKey, fromAccId, toAccId, toBankCode, toAccName, amount, fee, content, type, status, createdAt)
       VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, 'INTERBANK_TRANSFER', 'SUCCESS', NOW())`,
        [
          traceId,
          params.idempotencyKey,
          fromAcc.id,
          params.toBankCode.toUpperCase(),
          `${params.toAccName.toUpperCase()} (${params.toAccountNumber})`,
          params.amount,
          fee,
          params.content,
        ],
      );

      const txId = txInsert.insertId;
      const after = fromAcc.availableBalance - totalDebit;

      await conn.query(
        'UPDATE BankAccount SET currentBalance = ?, availableBalance = ? WHERE id = ?',
        [fromAcc.currentBalance - totalDebit, after, fromAcc.id],
      );

      await conn.query(
        `INSERT INTO BalanceFluctuation
       (transactionId, bankAccountId, changeAmount, balanceBefore, balanceAfter, type, timestamp)
       VALUES (?, ?, ?, ?, ?, 'DEBIT', NOW())`,
        [txId, fromAcc.id, -totalDebit, fromAcc.availableBalance, after],
      );

      const [created] = await conn.query<TransactionRow[]>(
        'SELECT * FROM Transaction WHERE id = ? LIMIT 1',
        [txId],
      );
      return created[0];
    });
    authorization.complete();

    return {
      idempotentReplay: false,
      gateway: 'NAPAS_247_SANDBOX',
      transaction: txRecord,
    };
  } catch (error) {
    authorization.release();
    throw error;
  }
}

export async function gatewayPayment(params: {
  userId: number;
  customerId: number;
  fromAccountNumber: string;
  provider: 'VNPAY' | 'STRIPE';
  merchantName: string;
  amount: number;
  orderInfo: string;
  idempotencyKey: string;
  otpCode: string;
  ekycToken?: string;
}) {
  if (params.amount <= 0) {
    throw new AppError(400, 'INVALID_AMOUNT', 'Số tiền thanh toán phải lớn hơn 0');
  }

  const [existing] = await pool.query<TransactionRow[]>(
    'SELECT * FROM Transaction WHERE idempotencyKey = ? LIMIT 1',
    [params.idempotencyKey],
  );
  if (existing[0]) {
    await assertReplayOwner(
      existing[0],
      params.customerId,
      params.fromAccountNumber,
      params.amount,
    );
    return { idempotentReplay: true, transaction: existing[0] };
  }

  const authorization = await authorizeTransaction(
    params.userId,
    { ...params, operation: 'GATEWAY' },
    params.otpCode,
    params.ekycToken,
  );
  try {
    const txRecord = await withTransaction(async (conn) => {
      const [fromRows] = await conn.query<BankAccountRow[]>(
        'SELECT * FROM BankAccount WHERE accountNumber = ? AND customerId = ? FOR UPDATE',
        [params.fromAccountNumber, params.customerId],
      );
      const fromAcc = fromRows[0];
      if (!fromAcc || fromAcc.accountType !== 'CHECKING' || fromAcc.status !== 'ACTIVE') {
        throw new AppError(400, 'INVALID_SOURCE_ACCOUNT', 'Tài khoản nguồn không hợp lệ');
      }
      if (fromAcc.availableBalance < params.amount) {
        throw new AppError(400, 'INSUFFICIENT_BALANCE', 'Số dư khả dụng không đủ');
      }

      const traceId = buildTraceId();
      const [txInsert] = await conn.query<ResultSetHeader>(
        `INSERT INTO Transaction
       (traceId, idempotencyKey, fromAccId, toAccId, toBankCode, toAccName, amount, fee, content, type, status, createdAt)
       VALUES (?, ?, ?, NULL, ?, ?, ?, 0, ?, ?, 'SUCCESS', NOW())`,
        [
          traceId,
          params.idempotencyKey,
          fromAcc.id,
          params.provider,
          params.merchantName.toUpperCase(),
          params.amount,
          params.orderInfo,
          `${params.provider}_PAYMENT`,
        ],
      );

      const txId = txInsert.insertId;
      const after = fromAcc.availableBalance - params.amount;

      await conn.query(
        'UPDATE BankAccount SET currentBalance = ?, availableBalance = ? WHERE id = ?',
        [fromAcc.currentBalance - params.amount, after, fromAcc.id],
      );

      await conn.query(
        `INSERT INTO BalanceFluctuation
       (transactionId, bankAccountId, changeAmount, balanceBefore, balanceAfter, type, timestamp)
       VALUES (?, ?, ?, ?, ?, 'DEBIT', NOW())`,
        [txId, fromAcc.id, -params.amount, fromAcc.availableBalance, after],
      );

      const [created] = await conn.query<TransactionRow[]>(
        'SELECT * FROM Transaction WHERE id = ? LIMIT 1',
        [txId],
      );
      return created[0];
    });
    authorization.complete();

    return {
      idempotentReplay: false,
      provider: params.provider,
      sandboxRedirectUrl: `https://sandbox.${params.provider.toLowerCase()}.local/receipt/${txRecord.traceId}`,
      transaction: txRecord,
    };
  } catch (error) {
    authorization.release();
    throw error;
  }
}
