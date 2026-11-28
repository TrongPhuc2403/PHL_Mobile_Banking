import { Router } from 'express';
import { z } from 'zod';
import { requireAuth, requireRole } from '../middleware/authMiddleware';
import { env } from '../config/env';
import { AppError } from '../utils/errors';
import {
  intentHash,
  transactionIntentSchema,
  transactionOtpType,
} from '../security/transactionIntent';
import { ekycSessions } from '../security/ekycSessions';
import * as ekycService from '../services/ekycService';
import * as otpService from '../services/otpService';
import { runFaceEngine } from '../services/faceEngine';

const router = Router();
export const captureSchema = z.object({
  challengeId: z.string().uuid(),
  imageBase64: z
    .string()
    .min(2000)
    .max(400000)
    .regex(/^[A-Za-z0-9+/]+={0,2}$/),
  motionImageBase64: z
    .string()
    .min(2000)
    .max(400000)
    .regex(/^[A-Za-z0-9+/]+={0,2}$/),
});
// Non-authorizing feedback for each captured image. Requires an existing, owned capture session.
router.post(
  '/ekyc/frame/check',
  requireAuth,
  requireRole('CUSTOMER', 'OFFICER'),
  async (req, res, next) => {
    try {
      const body = z
        .object({
          challengeId: z.string().uuid(),
          imageBase64: z
            .string()
            .min(2000)
            .max(400000)
            .regex(/^[A-Za-z0-9+/]+={0,2}$/),
          frameIndex: z.number().int().min(1).max(2).default(1),
        })
        .strict()
        .parse(req.body);
      ekycSessions.assertCaptureChallenge(req.user!.userId, body.challengeId);
      const result = await runFaceEngine({
        action: 'inspect',
        imageBase64: body.imageBase64,
        frameIndex: body.frameIndex,
      });
      res.json({
        success: true,
        data: {
          frameAccepted: result.frameAccepted,
          identityVerified: false,
          rotationDegrees: result.rotationDegrees,
          width: result.width,
          height: result.height,
          faceCount: result.faceCount,
        },
      });
    } catch (err) {
      next(err);
    }
  },
);
router.post('/otp/request', requireAuth, async (req, res, next) => {
  try {
    const body = z
      .object({
        otpType: z.enum(['TRANSACTION', 'CHANGE_PASSWORD']).default('TRANSACTION'),
        intent: transactionIntentSchema.optional(),
        ekycToken: z.string().uuid().optional(),
      })
      .parse(req.body ?? {});
    let purpose: string = body.otpType;
    if (body.otpType === 'TRANSACTION') {
      if (req.user!.role !== 'CUSTOMER')
        throw new AppError(403, 'FORBIDDEN', 'Chỉ khách hàng được thực hiện giao dịch.');
      if (!body.intent)
        throw new AppError(
          400,
          'TRANSACTION_CONTEXT_REQUIRED',
          'Cần thông tin giao dịch trước khi gửi OTP.',
        );
      const hash = intentHash(req.user!.userId, body.intent);
      if (body.intent.amount > env.security.ekycThresholdVnd)
        ekycSessions.assert(req.user!.userId, hash, body.ekycToken);
      purpose = transactionOtpType(hash);
    }
    const data = await otpService.issueOtp(req.user!.userId, purpose);
    res.json({
      success: true,
      message: 'Đã tạo mã OTP 6 số. Vui lòng nhập mã để xác thực.',
      data,
    });
  } catch (err) {
    next(err);
  }
});
router.get('/ekyc/status', requireAuth, requireRole('CUSTOMER'), async (req, res, next) => {
  try {
    res.json({ success: true, data: await ekycService.getEkycStatus(req.user!.userId) });
  } catch (err) {
    next(err);
  }
});
router.post('/ekyc/challenge', requireAuth, requireRole('CUSTOMER'), async (req, res, next) => {
  try {
    const body = z.object({ intent: transactionIntentSchema.optional() }).parse(req.body ?? {});
    res.json({
      success: true,
      data: await ekycService.createVerificationChallenge(req.user!.userId, body.intent),
    });
  } catch (err) {
    next(err);
  }
});
// First-time enrollment only. No customerId/userId is accepted from the client.
router.post(
  '/ekyc/enrollment/request-otp',
  requireAuth,
  requireRole('CUSTOMER'),
  async (req, res, next) => {
    try {
      const body = z
        .object({ currentPassword: z.string().min(4).max(200) })
        .strict()
        .parse(req.body);
      res.json({
        success: true,
        data: await ekycService.requestSelfEnrollmentOtp(req.user!.userId, body.currentPassword),
      });
    } catch (err) {
      next(err);
    }
  },
);
router.post(
  '/ekyc/enrollment/confirm-otp',
  requireAuth,
  requireRole('CUSTOMER'),
  async (req, res, next) => {
    try {
      const body = z
        .object({ requestId: z.string().uuid(), otpCode: z.string().regex(/^\d{6}$/) })
        .strict()
        .parse(req.body);
      res.json({
        success: true,
        data: await ekycService.confirmSelfEnrollmentOtp(
          req.user!.userId,
          body.requestId,
          body.otpCode,
        ),
      });
    } catch (err) {
      next(err);
    }
  },
);
router.post(
  '/ekyc/enrollment/challenge',
  requireAuth,
  requireRole('CUSTOMER'),
  async (req, res, next) => {
    try {
      const body = z.object({ enrollmentToken: z.string().uuid() }).strict().parse(req.body);
      res.json({
        success: true,
        data: await ekycService.createSelfEnrollmentChallenge(
          req.user!.userId,
          body.enrollmentToken,
        ),
      });
    } catch (err) {
      next(err);
    }
  },
);
router.post('/ekyc/enrollment', requireAuth, requireRole('CUSTOMER'), async (req, res, next) => {
  try {
    const body = captureSchema
      .extend({ enrollmentToken: z.string().uuid(), consent: z.literal(true) })
      .strict()
      .parse(req.body);
    res.json({ success: true, data: await ekycService.selfEnrollFace(req.user!.userId, body) });
  } catch (err) {
    next(err);
  }
});
router.post('/ekyc/verify', requireAuth, requireRole('CUSTOMER'), async (req, res, next) => {
  try {
    const body = captureSchema
      .extend({ intent: transactionIntentSchema.optional() })
      .parse(req.body);
    res.json({ success: true, data: await ekycService.verifyFaceEkyc(req.user!.userId, body) });
  } catch (err) {
    next(err);
  }
});
export default router;
