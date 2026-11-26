import crypto from 'crypto';
import fs from 'fs/promises';
import path from 'path';
import { RowDataPacket } from 'mysql2';
import { pool, withTransaction } from '../config/db';
import { AppError } from '../utils/errors';
import { intentHash, TransactionIntent } from '../security/transactionIntent';
import { ekycSessions } from '../security/ekycSessions';
import { modelsReady, runFaceEngine } from './faceEngine';
import {
  selfEnrollmentSessions,
  enrollmentOtpType,
  enrollmentTokenHash,
} from '../security/selfEnrollmentSessions';
import { requireActiveCustomer, verifyEnrollmentPassword } from './reauthenticationService';
import { issueOtp, verifyOtp } from './otpService';

type FaceReference = {
  engine: string;
  embedding: number[];
  referenceFile: string;
  enrolledAt: string;
};
const privateDirectory = path.resolve(__dirname, '../../.data/ekyc');
function referenceOf(profile: RowDataPacket): FaceReference | null {
  try {
    const value = JSON.parse(profile.faceVector || 'null');
    return profile.eKYCStatus === 'VERIFIED' &&
      value?.engine === 'OpenCV-SFace-v1' &&
      Array.isArray(value.embedding) &&
      value.embedding.length === 128 &&
      value.embedding.every((n: unknown) => typeof n === 'number' && Number.isFinite(n))
      ? value
      : null;
  } catch {
    return null;
  }
}
async function profileByUser(userId: number) {
  const [rows] = await pool.query<RowDataPacket[]>(
    'SELECT id, eKYCStatus, faceVector FROM CustomerProfile WHERE userId = ? LIMIT 1',
    [userId],
  );
  if (!rows[0]) throw new AppError(404, 'PROFILE_NOT_FOUND', 'Không tìm thấy hồ sơ khách hàng.');
  return rows[0];
}
export async function getEkycStatus(userId: number) {
  const profile = await profileByUser(userId);
  const reference = referenceOf(profile);
  return {
    enrolled: !!reference,
    status: reference ? 'VERIFIED' : 'PENDING',
    enrolledAt: reference?.enrolledAt,
    engineReady: modelsReady(),
    certifiedLiveness: false,
    thresholdVnd: 10000000,
    canSelfEnroll: !reference,
    message: reference
      ? 'Đã lưu ảnh gốc. Giao dịch trên 10 triệu vẫn cần chụp lại trước OTP.'
      : 'Chưa đăng ký sinh trắc học. Bạn có thể tự đăng ký tại Cá nhân bằng mật khẩu và OTP, hoặc nhờ nhân viên hỗ trợ.',
  };
}
export async function createVerificationChallenge(userId: number, intent?: TransactionIntent) {
  const profile = await profileByUser(userId);
  if (!referenceOf(profile))
    throw new AppError(
      409,
      'EKYC_NOT_ENROLLED',
      'Chưa có ảnh gốc eKYC. Vào Cá nhân → Đăng ký sinh trắc học để đăng ký trước.',
    );
  if (!modelsReady())
    throw new AppError(503, 'EKYC_ENGINE_UNAVAILABLE', 'Chưa cài mô hình đối chiếu khuôn mặt.');
  return ekycSessions.challenge(
    userId,
    intent ? 'TRANSACTION' : 'CHECK',
    intent ? intentHash(userId, intent) : undefined,
  );
}
export async function createEnrollmentChallenge(actorUserId: number, customerId: number) {
  const [rows] = await pool.query<RowDataPacket[]>(
    'SELECT id, faceVector, eKYCStatus FROM CustomerProfile WHERE id = ? LIMIT 1',
    [customerId],
  );
  if (!rows[0]) throw new AppError(404, 'PROFILE_NOT_FOUND', 'Không tìm thấy hồ sơ khách hàng.');
  if (referenceOf(rows[0]))
    throw new AppError(
      409,
      'EKYC_ALREADY_ENROLLED',
      'Hồ sơ đã có ảnh gốc. Không được ghi đè qua chức năng đăng ký lần đầu.',
    );
  return ekycSessions.challenge(actorUserId, 'ENROLL', undefined, customerId);
}
type EnrollmentCapture = {
  challengeId: string;
  imageBase64: string;
  motionImageBase64: string;
  consent: boolean;
};

async function persistReference(
  actorUserId: number,
  actorRole: 'OFFICER' | 'CUSTOMER',
  customerId: number,
  body: EnrollmentCapture,
  embedding: number[],
) {
  await fs.mkdir(privateDirectory, { recursive: true });
  const referenceFile = crypto.randomUUID() + '.jpg';
  const target = path.join(privateDirectory, referenceFile);
  await fs.writeFile(target, Buffer.from(body.imageBase64, 'base64'), { flag: 'wx', mode: 0o600 });
  try {
    await withTransaction(async (conn) => {
      const [profiles] = await conn.query<RowDataPacket[]>(
        'SELECT userId, faceVector, eKYCStatus FROM CustomerProfile WHERE id = ? FOR UPDATE',
        [customerId],
      );
      if (!profiles[0]) throw new AppError(404, 'PROFILE_NOT_FOUND', 'Không tìm thấy hồ sơ.');
      if (actorRole === 'CUSTOMER' && Number(profiles[0].userId) !== actorUserId) {
        throw new AppError(403, 'FORBIDDEN', 'Chỉ được đăng ký cho hồ sơ của chính bạn.');
      }
      // Row lock protects against officer/self registration racing to replace an existing reference.
      if (referenceOf(profiles[0]))
        throw new AppError(409, 'EKYC_ALREADY_ENROLLED', 'Hồ sơ đã có ảnh gốc, không thể ghi đè.');
      const reference: FaceReference = {
        engine: 'OpenCV-SFace-v1',
        embedding,
        referenceFile,
        enrolledAt: new Date().toISOString(),
      };
      await conn.query('UPDATE CustomerProfile SET faceVector = ?, eKYCStatus = ? WHERE id = ?', [
        JSON.stringify(reference),
        'VERIFIED',
        customerId,
      ]);
      await conn.query(
        `INSERT INTO AuditLog (actorId, actorRole, action, entityName, entityId, oldValue, newValue, timestamp)
        VALUES (?, ?, ?, 'CustomerProfile', ?, NULL, ?, NOW())`,
        [
          actorUserId,
          actorRole,
          actorRole === 'CUSTOMER' ? 'SELF_ENROLL_EKYC' : 'ENROLL_EKYC',
          customerId,
          JSON.stringify({
            engine: reference.engine,
            enrolledAt: reference.enrolledAt,
            consent: true,
            method: actorRole === 'CUSTOMER' ? 'PASSWORD_OTP_SELF' : 'OFFICER_ASSISTED',
          }),
        ],
      );
    });
  } catch (error) {
    await fs.unlink(target).catch(() => {});
    throw error;
  }
  return {
    enrolled: true,
    status: 'VERIFIED',
    message:
      'Đăng ký sinh trắc học thành công. Giao dịch trên 10 triệu vẫn cần xác minh khuôn mặt trước OTP.',
  };
}
export async function enrollFace(actorUserId: number, customerId: number, body: EnrollmentCapture) {
  if (!body.consent)
    throw new AppError(
      400,
      'EKYC_CONSENT_REQUIRED',
      'Cần sự đồng ý của khách hàng và nhân viên kiểm tra CCCD.',
    );
  ekycSessions.takeChallenge(actorUserId, body.challengeId, 'ENROLL', customerId);
  const result = await runFaceEngine({
    action: 'enroll',
    imageBase64: body.imageBase64,
    motionImageBase64: body.motionImageBase64,
  });
  return persistReference(actorUserId, 'OFFICER', customerId, body, result.embedding);
}
async function pendingSelfProfile(userId: number) {
  await requireActiveCustomer(userId);
  const profile = await profileByUser(userId);
  if (referenceOf(profile))
    throw new AppError(
      409,
      'EKYC_ALREADY_ENROLLED',
      'Bạn đã đăng ký sinh trắc học. Không thể tự thay thế ảnh gốc; hãy liên hệ nhân viên nếu cần cập nhật.',
    );
  return profile;
}
export async function requestSelfEnrollmentOtp(userId: number, currentPassword: string) {
  await verifyEnrollmentPassword(userId, currentPassword);
  const profile = await pendingSelfProfile(userId);
  if (!modelsReady())
    throw new AppError(
      503,
      'EKYC_ENGINE_UNAVAILABLE',
      'Chưa cài mô hình khuôn mặt. Vui lòng liên hệ nhân viên.',
    );
  const requestId = selfEnrollmentSessions.begin(userId, Number(profile.id));
  const otp = await issueOtp(userId, enrollmentOtpType(requestId));
  return { requestId, ...otp, resendAfterSeconds: 30 };
}
export async function confirmSelfEnrollmentOtp(userId: number, requestId: string, otpCode: string) {
  const profile = await pendingSelfProfile(userId);
  const request = selfEnrollmentSessions.request(userId, requestId);
  if (request.customerId !== Number(profile.id))
    throw new AppError(
      403,
      'ENROLLMENT_REQUEST_INVALID',
      'Phiên đăng ký không thuộc hồ sơ của bạn.',
    );
  await verifyOtp(userId, otpCode, enrollmentOtpType(requestId));
  return selfEnrollmentSessions.approve(userId, requestId);
}
export async function createSelfEnrollmentChallenge(userId: number, enrollmentToken: string) {
  const profile = await pendingSelfProfile(userId);
  selfEnrollmentSessions.assert(userId, Number(profile.id), enrollmentToken);
  if (!modelsReady())
    throw new AppError(503, 'EKYC_ENGINE_UNAVAILABLE', 'Dịch vụ khuôn mặt chưa sẵn sàng.');
  return ekycSessions.challenge(
    userId,
    'SELF_ENROLL',
    enrollmentTokenHash(enrollmentToken),
    Number(profile.id),
  );
}
export async function selfEnrollFace(
  userId: number,
  body: EnrollmentCapture & { enrollmentToken: string },
) {
  if (!body.consent)
    throw new AppError(
      400,
      'EKYC_CONSENT_REQUIRED',
      'Bạn cần đồng ý chụp và lưu ảnh gốc sinh trắc học.',
    );
  const profile = await pendingSelfProfile(userId);
  const customerId = Number(profile.id);
  selfEnrollmentSessions.assert(userId, customerId, body.enrollmentToken);
  const challenge = ekycSessions.takeChallenge(userId, body.challengeId, 'SELF_ENROLL', customerId);
  if (challenge.hash !== enrollmentTokenHash(body.enrollmentToken))
    throw new AppError(
      403,
      'ENROLLMENT_AUTH_REQUIRED',
      'Phiên chụp không thuộc lần xác nhận OTP này.',
    );
  const result = await runFaceEngine({
    action: 'enroll',
    imageBase64: body.imageBase64,
    motionImageBase64: body.motionImageBase64,
  });
  // Invalid photos leave the approval usable until expiry, but each retry needs a new capture challenge.
  selfEnrollmentSessions.assert(userId, customerId, body.enrollmentToken);
  const data = await persistReference(userId, 'CUSTOMER', customerId, body, result.embedding);
  selfEnrollmentSessions.complete(userId, customerId, body.enrollmentToken);
  return data;
}
export async function verifyFaceEkyc(
  userId: number,
  body: {
    challengeId: string;
    imageBase64: string;
    motionImageBase64: string;
    intent?: TransactionIntent;
  },
) {
  const scope = body.intent ? 'TRANSACTION' : 'CHECK';
  const challenge = ekycSessions.takeChallenge(userId, body.challengeId, scope);
  const hash = body.intent ? intentHash(userId, body.intent) : undefined;
  if (challenge.hash !== hash)
    throw new AppError(
      403,
      'EKYC_CONTEXT_CHANGED',
      'Thông tin giao dịch đã thay đổi, cần xác minh lại.',
    );
  const reference = referenceOf(await profileByUser(userId));
  if (!reference)
    throw new AppError(
      409,
      'EKYC_NOT_ENROLLED',
      'Chưa có ảnh gốc hợp lệ. Vui lòng đăng ký sinh trắc học tại Cá nhân trước.',
    );
  const result = await runFaceEngine({
    action: 'verify',
    imageBase64: body.imageBase64,
    motionImageBase64: body.motionImageBase64,
    embedding: reference.embedding,
  });
  return {
    verified: true,
    movementChecked: true,
    certifiedLiveness: false,
    cosineScore: result.cosineScore,
    ...(hash ? ekycSessions.grant(userId, hash) : {}),
  };
}
