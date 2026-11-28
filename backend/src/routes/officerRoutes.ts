import { Router } from 'express';
import { z } from 'zod';
import { requireAuth, requireRole } from '../middleware/authMiddleware';
import * as officerService from '../services/officerService';
import * as ekycService from '../services/ekycService';
import { strongPasswordSchema } from './authRoutes';
import { captureSchema } from './securityRoutes';

const router = Router();
router.use(requireAuth, requireRole('OFFICER'));

router.get('/customers', async (req, res, next) => {
  try {
    const q = req.query.q ? String(req.query.q) : undefined;
    const rawLimit = req.query.limit !== undefined ? Number(req.query.limit) : undefined;
    const rawOffset = req.query.offset !== undefined ? Number(req.query.offset) : undefined;
    const result = await officerService.searchCustomers(q, {
      limit: rawLimit !== undefined && Number.isFinite(rawLimit) ? Math.trunc(rawLimit) : undefined,
      offset:
        rawOffset !== undefined && Number.isFinite(rawOffset) ? Math.trunc(rawOffset) : undefined,
    });
    res.json({ success: true, ...result });
  } catch (err) {
    next(err);
  }
});

router.post('/customers', async (req, res, next) => {
  try {
    const body = z
      .object({
        username: z.string().min(4).max(50),
        password: strongPasswordSchema,
        fullName: z.string().min(2).max(150),
        idCardNumber: z.string().length(12),
        dob: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
        phone: z.string().min(9).max(20),
        email: z.string().email().optional(),
        address: z.string().max(255).optional(),
        initialDeposit: z.number().int().min(0).default(0),
      })
      .parse(req.body);

    const data = await officerService.createCustomerAccount({
      actorUserId: req.user!.userId,
      ...body,
    });
    res.status(201).json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.patch('/customers/:customerId', async (req, res, next) => {
  try {
    const customerId = Number(req.params.customerId);
    const body = z
      .object({
        status: z.enum(['ACTIVE', 'LOCKED']).optional(),
        phone: z.string().min(9).max(20).optional(),
        email: z.string().email().optional(),
        address: z.string().max(255).optional(),
      })
      .parse(req.body);

    const data = await officerService.updateCustomerProfileAndStatus({
      actorUserId: req.user!.userId,
      customerId,
      ...body,
    });
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.patch('/interest-rates', async (req, res, next) => {
  try {
    const body = z
      .object({
        targetType: z.enum(['SAVINGS', 'MORTGAGE']),
        targetId: z.number().int().positive(),
        newInterestRate: z.number().positive().max(30),
      })
      .parse(req.body);

    const data = await officerService.adjustInterestRate({
      actorUserId: req.user!.userId,
      ...body,
    });
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.get('/audit-logs', async (req, res, next) => {
  try {
    const limit = req.query.limit ? Number(req.query.limit) : 50;
    const data = await officerService.listAuditLogs(limit);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/customers/:customerId/ekyc/challenge', async (req, res, next) => {
  try {
    const customerId = z.coerce.number().int().positive().parse(req.params.customerId);
    res.json({
      success: true,
      data: await ekycService.createEnrollmentChallenge(req.user!.userId, customerId),
    });
  } catch (err) {
    next(err);
  }
});
router.post('/customers/:customerId/ekyc/enroll', async (req, res, next) => {
  try {
    const customerId = z.coerce.number().int().positive().parse(req.params.customerId);
    const body = captureSchema.extend({ consent: z.literal(true) }).parse(req.body);
    res.json({
      success: true,
      data: await ekycService.enrollFace(req.user!.userId, customerId, body),
    });
  } catch (err) {
    next(err);
  }
});
export default router;
