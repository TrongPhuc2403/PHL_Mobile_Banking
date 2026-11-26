import crypto from 'crypto';
import { AppError } from '../utils/errors';

type Challenge = {
  userId: number;
  scope: string;
  hash?: string;
  customerId?: number;
  expiresAt: number;
};
type Grant = { userId: number; hash: string; expiresAt: number; reserved: boolean };
/** Ephemeral, single-process sessions. Restart invalidates them; never bypasses verification. */
export class EkycSessions {
  private challenges = new Map<string, Challenge>();
  private grants = new Map<string, Grant>();
  constructor(private now: () => number = Date.now) {}
  private prune() {
    for (const [key, value] of this.challenges)
      if (value.expiresAt <= this.now()) this.challenges.delete(key);
    for (const [key, value] of this.grants)
      if (value.expiresAt <= this.now() && !value.reserved) this.grants.delete(key);
  }
  challenge(userId: number, scope: string, hash?: string, customerId?: number) {
    this.prune();
    if (this.challenges.size >= 1000)
      throw new AppError(429, 'EKYC_BUSY', 'Hệ thống eKYC đang bận.');
    const challengeId = crypto.randomUUID();
    this.challenges.set(challengeId, {
      userId,
      scope,
      hash,
      customerId,
      expiresAt: this.now() + 120000,
    });
    return { challengeId, action: 'TURN_HEAD', expiresInSeconds: 120 };
  }
  assertCaptureChallenge(userId: number, challengeId: string) {
    const value = this.challenges.get(challengeId);
    if (
      !value ||
      value.userId !== userId ||
      value.expiresAt <= this.now() ||
      !['CHECK', 'TRANSACTION', 'ENROLL', 'SELF_ENROLL'].includes(value.scope)
    ) {
      throw new AppError(
        403,
        'EKYC_CHALLENGE_INVALID',
        'Phiên chụp đã hết hạn. Vui lòng tạo lại phiên và chụp từ ảnh 1.',
      );
    }
  }
  takeChallenge(userId: number, challengeId: string, scope: string, customerId?: number) {
    const value = this.challenges.get(challengeId);
    if (
      !value ||
      value.userId !== userId ||
      value.scope !== scope ||
      value.customerId !== customerId ||
      value.expiresAt <= this.now()
    )
      throw new AppError(
        403,
        'EKYC_CHALLENGE_INVALID',
        'Phiên chụp đã hết hạn hoặc không hợp lệ. Vui lòng bắt đầu lại.',
      );
    this.challenges.delete(challengeId);
    return value;
  }
  grant(userId: number, hash: string) {
    this.prune();
    const ekycToken = crypto.randomUUID();
    this.grants.set(ekycToken, { userId, hash, expiresAt: this.now() + 300000, reserved: false });
    return { ekycToken, expiresInSeconds: 300 };
  }
  assert(userId: number, hash: string, token?: string) {
    const value = token ? this.grants.get(token) : undefined;
    if (!token)
      throw new AppError(
        403,
        'EKYC_REQUIRED',
        'Giao dịch trên 10.000.000 VNĐ phải xác minh khuôn mặt trước OTP.',
      );
    if (!value || value.userId !== userId || value.hash !== hash || value.expiresAt <= this.now()) {
      throw new AppError(
        403,
        'EKYC_EXPIRED',
        'Xác minh eKYC đã hết hạn hoặc không thuộc giao dịch này.',
      );
    }
    if (value.reserved) throw new AppError(409, 'EKYC_IN_USE', 'Giao dịch này đang được xử lý.');
    return value;
  }
  reserve(userId: number, hash: string, token?: string) {
    const value = this.assert(userId, hash, token);
    value.reserved = true;
    return {
      complete: () => {
        this.grants.delete(token!);
      },
      release: () => {
        value.reserved = false;
      },
    };
  }
}
export const ekycSessions = new EkycSessions();
