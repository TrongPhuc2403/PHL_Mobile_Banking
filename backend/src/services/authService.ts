import bcrypt from 'bcryptjs';
import jwt, { SignOptions } from 'jsonwebtoken';
import { RowDataPacket } from 'mysql2';
import { pool } from '../config/db';
import { env } from '../config/env';
import { AuthTokenPayload, UserRole } from '../types/auth';
import { AppError } from '../utils/errors';

interface UserRow extends RowDataPacket {
  id: number;
  username: string;
  passwordHash: string;
  role: UserRole;
  status: string;
  failedAttempts: number;
  lockedUntil: Date | null;
}

interface ProfileRow extends RowDataPacket {
  id: number;
  fullName: string;
  email: string | null;
  phone: string | null;
  address?: string | null;
  cifNumber?: string;
  eKYCStatus?: string;
  employeeCode?: string;
  position?: string | null;
}

function signTokens(payload: AuthTokenPayload) {
  const accessToken = jwt.sign(payload, env.jwt.accessSecret, {
    expiresIn: env.jwt.accessExpiresIn as SignOptions['expiresIn'],
  });
  const refreshToken = jwt.sign(payload, env.jwt.refreshSecret, {
    expiresIn: env.jwt.refreshExpiresIn as SignOptions['expiresIn'],
  });
  return { accessToken, refreshToken };
}

export async function login(username: string, password: string) {
  const [users] = await pool.query<UserRow[]>(
    'SELECT * FROM UserAccount WHERE username = ? LIMIT 1',
    [username.trim()],
  );

  const user = users[0];
  if (!user) {
    throw new AppError(401, 'INVALID_CREDENTIALS', 'Tên đăng nhập hoặc mật khẩu không đúng');
  }

  if (user.status !== 'ACTIVE') {
    throw new AppError(403, 'ACCOUNT_INACTIVE', 'Tài khoản hiện đang bị khóa hoặc tạm ngưng');
  }

  if (user.lockedUntil && new Date(user.lockedUntil) > new Date()) {
    throw new AppError(
      423,
      'ACCOUNT_LOCKED',
      'Tài khoản tạm khóa do đăng nhập sai nhiều lần. Vui lòng thử lại sau.',
    );
  }

  const matched = await bcrypt.compare(password, user.passwordHash);
  if (!matched) {
    const nextAttempts = user.failedAttempts + 1;
    if (nextAttempts >= env.security.maxLoginAttempts) {
      await pool.query(
        'UPDATE UserAccount SET failedAttempts = ?, lockedUntil = DATE_ADD(NOW(), INTERVAL ? MINUTE) WHERE id = ?',
        [nextAttempts, env.security.lockoutMinutes, user.id],
      );
      throw new AppError(
        423,
        'ACCOUNT_LOCKED',
        `Bạn đã nhập sai quá ${env.security.maxLoginAttempts} lần. Tài khoản bị khóa ${env.security.lockoutMinutes} phút.`,
      );
    }

    await pool.query('UPDATE UserAccount SET failedAttempts = ? WHERE id = ?', [
      nextAttempts,
      user.id,
    ]);
    throw new AppError(401, 'INVALID_CREDENTIALS', 'Tên đăng nhập hoặc mật khẩu không đúng');
  }

  if (user.failedAttempts > 0 || user.lockedUntil) {
    await pool.query('UPDATE UserAccount SET failedAttempts = 0, lockedUntil = NULL WHERE id = ?', [
      user.id,
    ]);
  }

  const profile = await getProfileByUserId(user.id, user.role);
  const payload: AuthTokenPayload = {
    userId: user.id,
    username: user.username,
    role: user.role,
    profileId: profile.id,
  };

  return {
    ...signTokens(payload),
    user: {
      id: user.id,
      username: user.username,
      role: user.role,
      profile,
    },
  };
}

export async function refreshSession(refreshToken: string) {
  try {
    const decoded = jwt.verify(refreshToken, env.jwt.refreshSecret) as AuthTokenPayload;
    const payload: AuthTokenPayload = {
      userId: decoded.userId,
      username: decoded.username,
      role: decoded.role,
      profileId: decoded.profileId,
    };
    return signTokens(payload);
  } catch {
    throw new AppError(401, 'INVALID_REFRESH_TOKEN', 'Refresh token không hợp lệ hoặc đã hết hạn');
  }
}

export async function changePassword(userId: number, currentPassword: string, newPassword: string) {
  const [users] = await pool.query<UserRow[]>('SELECT * FROM UserAccount WHERE id = ? LIMIT 1', [
    userId,
  ]);
  const user = users[0];
  if (!user) {
    throw new AppError(404, 'USER_NOT_FOUND', 'Không tìm thấy tài khoản');
  }

  const matched = await bcrypt.compare(currentPassword, user.passwordHash);
  if (!matched) {
    throw new AppError(400, 'WRONG_PASSWORD', 'Mật khẩu hiện tại không chính xác');
  }

  const nextHash = await bcrypt.hash(newPassword, 10);
  await pool.query('UPDATE UserAccount SET passwordHash = ? WHERE id = ?', [nextHash, userId]);
}

export async function resetPasswordWithOtp(username: string, newPassword: string) {
  const [users] = await pool.query<UserRow[]>(
    'SELECT id FROM UserAccount WHERE username = ? AND role = ? LIMIT 1',
    [username.trim(), 'CUSTOMER'],
  );
  const user = users[0];
  if (!user) {
    throw new AppError(404, 'USER_NOT_FOUND', 'Không tìm thấy tài khoản khách hàng');
  }

  const nextHash = await bcrypt.hash(newPassword, 10);
  await pool.query(
    'UPDATE UserAccount SET passwordHash = ?, failedAttempts = 0, lockedUntil = NULL WHERE id = ?',
    [nextHash, user.id],
  );
}

export async function getProfileByUserId(userId: number, role: UserRole): Promise<ProfileRow> {
  if (role === 'CUSTOMER') {
    const [rows] = await pool.query<ProfileRow[]>(
      `SELECT id, userId, cifNumber, fullName, idCardNumber, dob, phone, email, address,
        CASE WHEN eKYCStatus = 'VERIFIED' AND faceVector LIKE '{%OpenCV-SFace-v1%'
          THEN 'VERIFIED' ELSE 'PENDING' END AS eKYCStatus
        FROM CustomerProfile WHERE userId = ? LIMIT 1`,
      [userId],
    );
    if (!rows[0]) {
      throw new AppError(404, 'PROFILE_NOT_FOUND', 'Không tìm thấy hồ sơ khách hàng');
    }
    return rows[0];
  }

  const [rows] = await pool.query<ProfileRow[]>(
    'SELECT id, userId, employeeCode, fullName, email, phone, branchId, position FROM OfficerProfile WHERE userId = ? LIMIT 1',
    [userId],
  );
  if (!rows[0]) {
    throw new AppError(404, 'PROFILE_NOT_FOUND', 'Không tìm thấy hồ sơ nhân viên');
  }
  return rows[0];
}

export async function updateCustomerContact(
  userId: number,
  updates: { phone?: string; email?: string; address?: string },
) {
  const fields: string[] = [];
  const values: unknown[] = [];

  if (updates.phone !== undefined) {
    fields.push('phone = ?');
    values.push(updates.phone.trim());
  }
  if (updates.email !== undefined) {
    fields.push('email = ?');
    values.push(updates.email.trim());
  }
  if (updates.address !== undefined) {
    fields.push('address = ?');
    values.push(updates.address.trim());
  }

  if (fields.length === 0) {
    throw new AppError(400, 'NO_CHANGES', 'Không có thông tin nào cần cập nhật');
  }

  values.push(userId);
  await pool.query(`UPDATE CustomerProfile SET ${fields.join(', ')} WHERE userId = ?`, values);
  return getProfileByUserId(userId, 'CUSTOMER');
}
