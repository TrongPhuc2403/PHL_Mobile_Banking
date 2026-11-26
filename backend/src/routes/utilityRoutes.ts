import { Router } from 'express';
import { z } from 'zod';
import { requireAuth, requireRole } from '../middleware/authMiddleware';
import * as utilityService from '../services/utilityService';

const router = Router();
router.use(requireAuth, requireRole('CUSTOMER'));

const serviceEnum = z.enum([
  'ELECTRIC',
  'WATER',
  'TOPUP',
  'FLIGHT',
  'CINEMA',
  'HOTEL',
  'ECOMMERCE',
]);

router.get('/catalog/:serviceType', (req, res, next) => {
  try {
    const serviceType = serviceEnum.parse(req.params.serviceType.toUpperCase());
    const billCode = req.query.billCode ? String(req.query.billCode) : undefined;
    const data = utilityService.lookupUtilityCatalog(serviceType, billCode);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/pay', async (req, res, next) => {
  try {
    const body = z
      .object({
        fromAccountNumber: z.string().min(6),
        serviceType: serviceEnum,
        supplier: z.string().min(2).max(100),
        billCode: z.string().max(60).optional(),
        amount: z.number().int().positive(),
        ticketData: z.record(z.string(), z.unknown()).optional(),
        idempotencyKey: z.string().min(8).max(100),
        otpCode: z.string().length(6),
        ekycToken: z.string().optional(),
      })
      .parse(req.body);

    const data = await utilityService.payUtilityOrder({
      userId: req.user!.userId,
      customerId: req.user!.profileId,
      ...body,
    });
    res.status(data.idempotentReplay ? 200 : 201).json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.get('/orders', async (req, res, next) => {
  try {
    const data = await utilityService.listCustomerUtilityOrders(req.user!.profileId);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

export default router;
