import { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { AuthTokenPayload, UserRole } from '../types/auth';
import { AppError } from '../utils/errors';

const revokedTokens = new Set<string>();
const revokedUserBefore = new Map<number, number>();

export function revokeToken(token: string): void {
  revokedTokens.add(token);
}

export function revokeAllTokens(userId: number): void {
  revokedUserBefore.set(userId, Math.floor(Date.now() / 1000));
}

export function isUserTokenRevoked(userId: number, issuedAtSeconds?: number): boolean {
  const revokedAt = revokedUserBefore.get(userId);
  if (revokedAt === undefined) {
    return false;
  }
  return (issuedAtSeconds ?? 0) <= revokedAt;
}

export function requireAuth(req: Request, _res: Response, next: NextFunction): void {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    next(new AppError(401, 'UNAUTHORIZED', 'Thiếu token xác thực'));
    return;
  }

  const token = header.slice(7).trim();
  if (revokedTokens.has(token)) {
    next(new AppError(401, 'TOKEN_REVOKED', 'Phiên đăng nhập đã kết thúc'));
    return;
  }

  try {
    const decoded = jwt.verify(token, env.jwt.accessSecret) as AuthTokenPayload & { iat?: number };
    if (isUserTokenRevoked(decoded.userId, decoded.iat)) {
      next(new AppError(401, 'TOKEN_REVOKED', 'Phiên đăng nhập đã kết thúc'));
      return;
    }
    req.user = decoded;
    next();
  } catch {
    next(new AppError(401, 'INVALID_TOKEN', 'Token không hợp lệ hoặc đã hết hạn'));
  }
}

export function requireRole(...allowedRoles: UserRole[]) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (!req.user) {
      next(new AppError(401, 'UNAUTHORIZED', 'Chưa xác thực'));
      return;
    }
    if (!allowedRoles.includes(req.user.role)) {
      next(new AppError(403, 'FORBIDDEN', 'Bạn không có quyền thực hiện thao tác này'));
      return;
    }
    next();
  };
}
