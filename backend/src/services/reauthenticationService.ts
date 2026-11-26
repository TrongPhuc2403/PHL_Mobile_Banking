import bcrypt from 'bcryptjs';
import { RowDataPacket } from 'mysql2';
import { pool } from '../config/db';
import { env } from '../config/env';
import { AppError } from '../utils/errors';

export async function requireActiveCustomer(userId: number) {
  const [rows] = await pool.query<RowDataPacket[]>(
    'SELECT * FROM UserAccount WHERE id = ? LIMIT 1',
    [userId],
  );
  const user = rows[0];
  if (!user || user.role !== 'CUSTOMER' || user.status !== 'ACTIVE') {
    throw new AppError(
      403,
      'ACCOUNT_INACTIVE',
      'Chỉ tài khoản khách hàng đang hoạt động mới được đăng ký sinh trắc học.',
    );
  }
  if (user.lockedUntil && new Date(user.lockedUntil).getTime() > Date.now()) {
    throw new AppError(
      423,
      'ACCOUNT_LOCKED',
      'Tài khoản tạm khóa do nhập sai mật khẩu nhiều lần. Vui lòng thử lại sau.',
    );
  }
  return user;
}
export async function verifyEnrollmentPassword(userId: number, currentPassword: string) {
  const user = await requireActiveCustomer(userId);
  if (!(await bcrypt.compare(currentPassword, user.passwordHash))) {
    const attempts = Number(user.failedAttempts || 0) + 1;
    if (attempts >= env.security.maxLoginAttempts) {
      await pool.query(
        'UPDATE UserAccount SET failedAttempts = ?, lockedUntil = DATE_ADD(NOW(), INTERVAL ? MINUTE) WHERE id = ?',
        [attempts, env.security.lockoutMinutes, userId],
      );
      throw new AppError(
        423,
        'ACCOUNT_LOCKED',
        'Nhập sai mật khẩu quá số lần cho phép. Tài khoản tạm khóa.',
      );
    }
    await pool.query('UPDATE UserAccount SET failedAttempts = ? WHERE id = ?', [attempts, userId]);
    throw new AppError(400, 'WRONG_PASSWORD', 'Mật khẩu hiện tại không chính xác.');
  }
  if (user.failedAttempts || user.lockedUntil) {
    await pool.query('UPDATE UserAccount SET failedAttempts = 0, lockedUntil = NULL WHERE id = ?', [
      userId,
    ]);
  }
}
