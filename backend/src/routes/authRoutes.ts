import { Router } from 'express';
import { z } from 'zod';
import { requireAuth, requireRole, revokeToken } from '../middleware/authMiddleware';
import * as authService from '../services/authService';
import * as otpService from '../services/otpService';

const router = Router();

const loginSchema = z.object({
  username: z.string().min(3),
  password: z.string().min(4),
});

router.post('/login', async (req, res, next) => {
  try {
    const body = loginSchema.parse(req.body);
    const data = await authService.login(body.username, body.password);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/refresh', async (req, res, next) => {
  try {
    const { refreshToken } = z.object({ refreshToken: z.string().min(10) }).parse(req.body);
    const data = await authService.refreshSession(refreshToken);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/logout', requireAuth, (req, res) => {
  const token = req.headers.authorization?.slice(7).trim();
  if (token) {
    revokeToken(token);
  }
  res.json({ success: true, message: 'Đăng xuất thành công' });
});

router.post('/change-password', requireAuth, async (req, res, next) => {
  try {
    const body = z
      .object({
        currentPassword: z.string().min(4),
        newPassword: z.string().min(6),
      })
      .parse(req.body);
    await authService.changePassword(req.user!.userId, body.currentPassword, body.newPassword);
    res.json({ success: true, message: 'Đổi mật khẩu thành công' });
  } catch (err) {
    next(err);
  }
});

router.post('/forgot-password/request-otp', async (req, res, next) => {
  try {
    const { username } = z.object({ username: z.string().min(3) }).parse(req.body);
    const data = await otpService.issueOtpByUsername(username, 'FORGOT_PASSWORD');
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/forgot-password/confirm', async (req, res, next) => {
  try {
    const body = z
      .object({
        username: z.string().min(3),
        otpCode: z.string().length(6),
        newPassword: z.string().min(6),
      })
      .parse(req.body);
    await otpService.verifyOtpByUsername(body.username, body.otpCode, 'FORGOT_PASSWORD');
    await authService.resetPasswordWithOtp(body.username, body.newPassword);
    res.json({ success: true, message: 'Khôi phục mật khẩu thành công' });
  } catch (err) {
    next(err);
  }
});

router.get('/me', requireAuth, async (req, res, next) => {
  try {
    const profile = await authService.getProfileByUserId(req.user!.userId, req.user!.role);
    res.json({
      success: true,
      data: {
        userId: req.user!.userId,
        username: req.user!.username,
        role: req.user!.role,
        profile,
      },
    });
  } catch (err) {
    next(err);
  }
});

router.patch('/me', requireAuth, requireRole('CUSTOMER'), async (req, res, next) => {
  try {
    const body = z
      .object({
        phone: z.string().min(9).max(15).optional(),
        email: z.string().email().optional(),
        address: z.string().min(5).max(255).optional(),
      })
      .parse(req.body);
    const profile = await authService.updateCustomerContact(req.user!.userId, body);
    res.json({ success: true, data: profile });
  } catch (err) {
    next(err);
  }
});

export default router;
