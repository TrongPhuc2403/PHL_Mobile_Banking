import crypto from 'crypto';
import { ResultSetHeader, RowDataPacket } from 'mysql2';
import { pool, withTransaction } from '../config/db';
import { AppError } from '../utils/errors';
import { authorizeTransaction, assertReplayOwner } from './transactionAuthorization';

export type UtilityServiceType =
  'ELECTRIC' | 'WATER' | 'TOPUP' | 'FLIGHT' | 'CINEMA' | 'HOTEL' | 'ECOMMERCE';

interface BankAccountRow extends RowDataPacket {
  id: number;
  customerId: number;
  accountNumber: string;
  accountType: string;
  currentBalance: number;
  availableBalance: number;
  status: string;
}

export function lookupUtilityCatalog(serviceType: UtilityServiceType, billCode?: string) {
  const catalogs: Record<UtilityServiceType, unknown> = {
    ELECTRIC: {
      supplier: 'EVN HCMC',
      billCode: billCode || 'PE0700123456',
      customerName: 'TRAN HUU LONG',
      period: '11/2026',
      amount: 685000,
    },
    WATER: {
      supplier: 'SAWACO - Cấp nước Nhà Bè',
      billCode: billCode || 'WA0700987654',
      customerName: 'TRAN HUU LONG',
      period: '11/2026',
      amount: 142000,
    },
    TOPUP: {
      suppliers: ['Viettel', 'Mobifone', 'Vinaphone'],
      denominations: [50000, 100000, 200000, 500000],
    },
    FLIGHT: {
      routes: [
        { code: 'VN240', from: 'SGN', to: 'HAN', airline: 'Vietnam Airlines', price: 1850000 },
        { code: 'VJ132', from: 'SGN', to: 'DAD', airline: 'Vietjet Air', price: 980000 },
      ],
    },
    CINEMA: {
      movies: [
        {
          code: 'MOV01',
          title: 'Đất Rừng Phương Nam',
          cinema: 'CGV Crescent Mall Q7',
          price: 110000,
        },
        {
          code: 'MOV02',
          title: 'Interstellar IMAX',
          cinema: 'Lotte Cinema Nam Sài Gòn',
          price: 145000,
        },
      ],
    },
    HOTEL: {
      hotels: [
        { code: 'HT01', name: 'Oakwood Residence Saigon Q7', nights: 1, price: 2450000 },
        { code: 'HT02', name: 'Liberty Central Saigon Riverside', nights: 1, price: 2900000 },
      ],
    },
    ECOMMERCE: {
      merchants: ['ShopeePay', 'Tiki', 'Lazada'],
      sampleOrderCode: billCode || 'ECO-20261123-8899',
      amount: 459000,
    },
  };

  return catalogs[serviceType];
}

export async function payUtilityOrder(params: {
  userId: number;
  customerId: number;
  fromAccountNumber: string;
  serviceType: UtilityServiceType;
  supplier: string;
  billCode?: string;
  amount: number;
  ticketData?: Record<string, unknown>;
  idempotencyKey: string;
  otpCode: string;
  ekycToken?: string;
}) {
  if (params.amount <= 0) {
    throw new AppError(400, 'INVALID_AMOUNT', 'Số tiền thanh toán phải lớn hơn 0');
  }

  const [existingTx] = await pool.query<RowDataPacket[]>(
    'SELECT * FROM Transaction WHERE idempotencyKey = ? LIMIT 1',
    [params.idempotencyKey],
  );
  if (existingTx[0]) {
    await assertReplayOwner(
      existingTx[0],
      params.customerId,
      params.fromAccountNumber,
      params.amount,
    );
    return { idempotentReplay: true, transaction: existingTx[0] };
  }

  const authorization = await authorizeTransaction(
    params.userId,
    { ...params, operation: 'UTILITY' },
    params.otpCode,
    params.ekycToken,
  );
  try {
    const result = await withTransaction(async (conn) => {
      const [accRows] = await conn.query<BankAccountRow[]>(
        'SELECT * FROM BankAccount WHERE accountNumber = ? AND customerId = ? FOR UPDATE',
        [params.fromAccountNumber, params.customerId],
      );
      const acc = accRows[0];
      if (!acc || acc.accountType !== 'CHECKING' || acc.status !== 'ACTIVE') {
        throw new AppError(
          400,
          'INVALID_ACCOUNT',
          'Tài khoản thanh toán không hợp lệ hoặc đang khóa',
        );
      }
      if (acc.availableBalance < params.amount) {
        throw new AppError(400, 'INSUFFICIENT_BALANCE', 'Số dư khả dụng không đủ');
      }

      const traceId = `TRC-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${crypto.randomInt(100000, 999999)}`;
      const [txInsert] = await conn.query<ResultSetHeader>(
        `INSERT INTO Transaction
       (traceId, idempotencyKey, fromAccId, toAccId, toBankCode, toAccName, amount, fee, content, type, status, createdAt)
       VALUES (?, ?, ?, NULL, ?, ?, ?, 0, ?, 'UTILITY_PAYMENT', 'SUCCESS', NOW())`,
        [
          traceId,
          params.idempotencyKey,
          acc.id,
          params.serviceType,
          params.supplier.toUpperCase(),
          params.amount,
          `Thanh toán ${params.serviceType} - ${params.supplier}${params.billCode ? ` (${params.billCode})` : ''}`,
        ],
      );

      const txId = txInsert.insertId;
      const after = acc.availableBalance - params.amount;

      await conn.query(
        'UPDATE BankAccount SET currentBalance = ?, availableBalance = ? WHERE id = ?',
        [acc.currentBalance - params.amount, after, acc.id],
      );

      await conn.query(
        `INSERT INTO BalanceFluctuation
       (transactionId, bankAccountId, changeAmount, balanceBefore, balanceAfter, type, timestamp)
       VALUES (?, ?, ?, ?, ?, 'DEBIT', NOW())`,
        [txId, acc.id, -params.amount, acc.availableBalance, after],
      );

      const [orderInsert] = await conn.query<ResultSetHeader>(
        `INSERT INTO UtilityOrder
       (customerId, serviceType, billCode, supplier, amount, holdExpiresAt, orderStatus, ticketData)
       VALUES (?, ?, ?, ?, ?, NULL, 'PAID', ?)`,
        [
          params.customerId,
          params.serviceType,
          params.billCode || traceId,
          params.supplier,
          params.amount,
          JSON.stringify({ ...(params.ticketData || {}), linkedTraceId: traceId }),
        ],
      );

      const [createdTx] = await conn.query<RowDataPacket[]>(
        'SELECT * FROM Transaction WHERE id = ? LIMIT 1',
        [txId],
      );

      return {
        idempotentReplay: false,
        orderId: orderInsert.insertId,
        transaction: createdTx[0],
      };
    });
    authorization.complete();
    return result;
  } catch (error) {
    authorization.release();
    throw error;
  }
}

export async function listCustomerUtilityOrders(customerId: number) {
  const [rows] = await pool.query<RowDataPacket[]>(
    'SELECT * FROM UtilityOrder WHERE customerId = ? ORDER BY id DESC LIMIT 50',
    [customerId],
  );
  return rows;
}
