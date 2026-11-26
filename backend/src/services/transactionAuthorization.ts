import { RowDataPacket } from 'mysql2';
import { pool } from '../config/db';
import { env } from '../config/env';
import { AppError } from '../utils/errors';
import { intentHash, transactionOtpType } from '../security/transactionIntent';
import { ekycSessions } from '../security/ekycSessions';
import { verifyOtp } from './otpService';

export async function authorizeTransaction(
  userId: number,
  input: unknown,
  otpCode: string,
  token?: string,
) {
  const hash = intentHash(userId, input);
  const amount = (input as { amount: number }).amount;
  const grant =
    amount > env.security.ekycThresholdVnd ? ekycSessions.reserve(userId, hash, token) : undefined;
  try {
    await verifyOtp(userId, otpCode, transactionOtpType(hash));
  } catch (error) {
    grant?.release();
    throw error;
  }
  return { complete: () => grant?.complete(), release: () => grant?.release() };
}
/** Do not leak another customer's receipt on an idempotency-key collision. */
export async function assertReplayOwner(
  tx: RowDataPacket,
  customerId: number,
  accountNumber: string,
  amount: number,
) {
  const [accounts] = await pool.query<RowDataPacket[]>(
    'SELECT id FROM BankAccount WHERE customerId = ? AND accountNumber = ? LIMIT 1',
    [customerId, accountNumber],
  );
  const accountId = accounts[0]?.id;
  if (
    !accountId ||
    (tx.fromAccId !== accountId && !(tx.fromAccId === null && tx.toAccId === accountId)) ||
    Number(tx.amount) !== amount
  ) {
    throw new AppError(
      409,
      'IDEMPOTENCY_CONFLICT',
      'Mã giao dịch đã được dùng cho một yêu cầu khác.',
    );
  }
}
