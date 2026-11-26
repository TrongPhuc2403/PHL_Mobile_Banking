import { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { AuthTokenPayload, UserRole } from '../types/auth';
import { AppError } from '../utils/errors';

const revokedTokens = new Set<string>();

export function revokeToken(token: string): void {
  revokedTokens.add(token);
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
    const decoded = jwt.verify(token, env.jwt.accessSecret) as AuthTokenPayload;
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
