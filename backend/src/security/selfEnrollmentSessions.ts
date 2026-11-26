import crypto from 'crypto';
import { AppError } from '../utils/errors';

type Request = { userId: number; customerId: number; expiresAt: number };
type Approval = Request;
/** Separate from transaction grants: enrollment never authorizes a financial transaction. */
export class SelfEnrollmentSessions {
  private requests = new Map<string, Request>();
  private approvals = new Map<string, Approval>();
  private resendAfter = new Map<number, number>();
  constructor(private now: () => number = Date.now) {}
  private prune() {
    for (const map of [this.requests, this.approvals])
      for (const [key, value] of map) {
        if (value.expiresAt <= this.now()) map.delete(key);
      }
    for (const [key, until] of this.resendAfter)
      if (until <= this.now()) this.resendAfter.delete(key);
  }
  begin(userId: number, customerId: number) {
    this.prune();
    if ((this.resendAfter.get(userId) || 0) > this.now()) {
      throw new AppError(
        429,
        'ENROLLMENT_OTP_COOLDOWN',
        'Vui lòng chờ 30 giây trước khi gửi lại OTP đăng ký.',
      );
    }
    if (this.requests.size + this.approvals.size >= 2000)
      throw new AppError(429, 'EKYC_BUSY', 'Đăng ký đang bận. Vui lòng thử lại.');
    for (const map of [this.requests, this.approvals])
      for (const [key, value] of map) {
        if (value.userId === userId) map.delete(key);
      }
    const requestId = crypto.randomUUID();
    this.requests.set(requestId, { userId, customerId, expiresAt: this.now() + 120000 });
    this.resendAfter.set(userId, this.now() + 30000);
    return requestId;
  }
  request(userId: number, requestId: string) {
    const value = this.requests.get(requestId);
    if (!value || value.userId !== userId || value.expiresAt <= this.now()) {
      throw new AppError(
        403,
        'ENROLLMENT_REQUEST_INVALID',
        'Phiên OTP đăng ký không hợp lệ hoặc đã hết hạn. Hãy gửi mã mới.',
      );
    }
    return value;
  }
  approve(userId: number, requestId: string) {
    const value = this.request(userId, requestId);
    this.requests.delete(requestId);
    const enrollmentToken = crypto.randomUUID();
    this.approvals.set(enrollmentToken, { ...value, expiresAt: this.now() + 300000 });
    return { enrollmentToken, expiresInSeconds: 300 };
  }
  assert(userId: number, customerId: number, token: string) {
    const value = this.approvals.get(token);
    if (
      !value ||
      value.userId !== userId ||
      value.customerId !== customerId ||
      value.expiresAt <= this.now()
    ) {
      throw new AppError(
        403,
        'ENROLLMENT_AUTH_REQUIRED',
        'Cần xác nhận mật khẩu và OTP đăng ký trước khi chụp. Phiên có hiệu lực 5 phút.',
      );
    }
  }
  complete(userId: number, customerId: number, token: string) {
    const value = this.approvals.get(token);
    if (value?.userId === userId && value.customerId === customerId) this.approvals.delete(token);
  }
}
export function enrollmentOtpType(requestId: string) {
  return 'EREG:' + crypto.createHash('sha256').update(requestId).digest('hex').slice(0, 24);
}
export function enrollmentTokenHash(token: string) {
  return crypto.createHash('sha256').update(token).digest('hex');
}
export const selfEnrollmentSessions = new SelfEnrollmentSessions();
