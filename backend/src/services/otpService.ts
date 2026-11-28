import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import { ResultSetHeader, RowDataPacket } from 'mysql2';
import { pool } from '../config/db';
import { env } from '../config/env';
import { AppError } from '../utils/errors';

interface OtpRow extends RowDataPacket {
  id: number;
  userId: number;
  otpHash: string;
  otpType: string;
  expiresAt: Date;
  attempts: number;
  isUsed: number;
}

function generateSixDigitOtp(): string {
  return String(crypto.randomInt(100000, 999999));
}

export async function issueOtp(
  userId: number,
  otpType: string,
  transactionId: number | null = null,
) {
  const code = generateSixDigitOtp();
  const otpHash = await bcrypt.hash(code, 8);

  // Reissuing a transaction OTP invalidates older transaction OTPs for this user.
  if (otpType.startsWith('TX:'))
    await pool.query(
      "UPDATE OTPVerification SET isUsed = TRUE WHERE userId = ? AND otpType LIKE 'TX:%' AND isUsed = FALSE",
      [userId],
    );
  const [result] = await pool.query<ResultSetHeader>(
    `INSERT INTO OTPVerification (userId, transactionId, otpHash, otpType, expiresAt, attempts, isUsed)
     VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE), 0, FALSE)`,
    [userId, transactionId, otpHash, otpType, env.security.otpExpiresMinutes],
  );

  const expiresInSeconds = env.security.otpExpiresMinutes * 60;
  const isSandbox = env.nodeEnv !== 'production';
  if (isSandbox) {
    console.info(`[SANDBOX OTP] userId=${userId} otpType=${otpType} code=${code}`);
  }

  return {
    otpId: result.insertId,
    otpType,
    expiresInSeconds,
    // Expose test OTP only outside production. Keep legacy preview for older clients.
    debugOtp: isSandbox ? code : undefined,
    devOtpPreview: isSandbox ? code : undefined,
  };
}

export async function verifyOtp(userId: number, otpCode: string, otpType: string): Promise<void> {
  const [rows] = await pool.query<OtpRow[]>(
    `SELECT * FROM OTPVerification
     WHERE userId = ? AND otpType = ?
     ORDER BY id DESC LIMIT 1`,
    [userId, otpType],
  );

  const record = rows[0];
  if (!record || record.isUsed) {
    throw new AppError(400, 'OTP_NOT_FOUND', 'Chưa có mã OTP hợp lệ. Vui lòng yêu cầu gửi mã mới.');
  }

  if (new Date(record.expiresAt) < new Date()) {
    throw new AppError(400, 'OTP_EXPIRED', 'Mã OTP đã hết hạn.');
  }

  if (record.attempts >= 5) {
    throw new AppError(
      429,
      'OTP_TOO_MANY_ATTEMPTS',
      'Nhập sai OTP quá 5 lần. Vui lòng tạo mã mới.',
    );
  }

  const matched = await bcrypt.compare(otpCode.trim(), record.otpHash);
  if (!matched) {
    await pool.query('UPDATE OTPVerification SET attempts = attempts + 1 WHERE id = ?', [
      record.id,
    ]);
    throw new AppError(400, 'OTP_INVALID', 'Mã OTP không chính xác.');
  }

  const [used] = await pool.query<ResultSetHeader>(
    'UPDATE OTPVerification SET isUsed = TRUE WHERE id = ? AND isUsed = FALSE AND attempts < 5 AND expiresAt > NOW()',
    [record.id],
  );
  if (used.affectedRows !== 1)
    throw new AppError(409, 'OTP_ALREADY_USED', 'Mã OTP đã được dùng hoặc hết hạn.');
}

export async function issueOtpByUsername(username: string, otpType: string) {
  const [users] = await pool.query<RowDataPacket[]>(
    'SELECT id FROM UserAccount WHERE username = ? LIMIT 1',
    [username.trim()],
  );
  if (!users[0]) {
    throw new AppError(404, 'USER_NOT_FOUND', 'Không tìm thấy tài khoản');
  }
  return issueOtp(users[0].id as number, otpType);
}

export async function verifyOtpByUsername(
  username: string,
  otpCode: string,
  otpType: string,
): Promise<void> {
  const [users] = await pool.query<RowDataPacket[]>(
    'SELECT id FROM UserAccount WHERE username = ? LIMIT 1',
    [username.trim()],
  );
  if (!users[0]) {
    throw new AppError(404, 'USER_NOT_FOUND', 'Không tìm thấy tài khoản');
  }
  await verifyOtp(users[0].id as number, otpCode, otpType);
}
