import { Router } from 'express';
import { z } from 'zod';
import { requireAuth, requireRole } from '../middleware/authMiddleware';
import * as transactionService from '../services/transactionService';

const router = Router();

router.use(requireAuth, requireRole('CUSTOMER'));

router.post('/internal-transfer', async (req, res, next) => {
  try {
    const body = z
      .object({
        fromAccountNumber: z.string().min(6),
        toAccountNumber: z.string().min(6),
        amount: z.number().int().positive(),
        content: z.string().max(255).default('Chuyển tiền nội bộ PHL'),
        idempotencyKey: z.string().min(8).max(100),
        otpCode: z.string().length(6),
        ekycToken: z.string().optional(),
      })
      .parse(req.body);

    const data = await transactionService.internalTransfer({
      userId: req.user!.userId,
      customerId: req.user!.profileId,
      ...body,
    });
    res.status(data.idempotentReplay ? 200 : 201).json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/deposit', async (req, res, next) => {
  try {
    const body = z
      .object({
        accountNumber: z.string().min(6),
        amount: z.number().int().positive(),
        idempotencyKey: z.string().min(8).max(100),
        otpCode: z.string().length(6),
        ekycToken: z.string().optional(),
      })
      .parse(req.body);

    const data = await transactionService.depositOrWithdraw({
      userId: req.user!.userId,
      customerId: req.user!.profileId,
      mode: 'DEPOSIT',
      ...body,
    });
    res.status(data.idempotentReplay ? 200 : 201).json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/withdraw', async (req, res, next) => {
  try {
    const body = z
      .object({
        accountNumber: z.string().min(6),
        amount: z.number().int().positive(),
        idempotencyKey: z.string().min(8).max(100),
        otpCode: z.string().length(6),
        ekycToken: z.string().optional(),
      })
      .parse(req.body);

    const data = await transactionService.depositOrWithdraw({
      userId: req.user!.userId,
      customerId: req.user!.profileId,
      mode: 'WITHDRAW',
      ...body,
    });
    res.status(data.idempotentReplay ? 200 : 201).json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/interbank-transfer', async (req, res, next) => {
  try {
    const body = z
      .object({
        fromAccountNumber: z.string().min(6),
        toBankCode: z.string().min(2).max(20),
        toAccountNumber: z.string().min(6),
        toAccName: z.string().min(2).max(150),
        amount: z.number().int().positive(),
        content: z.string().max(255).default('Chuyển tiền liên ngân hàng'),
        idempotencyKey: z.string().min(8).max(100),
        otpCode: z.string().length(6),
        ekycToken: z.string().optional(),
      })
      .parse(req.body);

    const data = await transactionService.interbankTransfer({
      userId: req.user!.userId,
      customerId: req.user!.profileId,
      ...body,
    });
    res.status(data.idempotentReplay ? 200 : 201).json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/gateway-pay', async (req, res, next) => {
  try {
    const body = z
      .object({
        fromAccountNumber: z.string().min(6),
        provider: z.enum(['VNPAY', 'STRIPE']),
        merchantName: z.string().min(2).max(150),
        amount: z.number().int().positive(),
        orderInfo: z.string().max(255).default('Thanh toán qua cổng điện tử'),
        idempotencyKey: z.string().min(8).max(100),
        otpCode: z.string().length(6),
        ekycToken: z.string().optional(),
      })
      .parse(req.body);

    const data = await transactionService.gatewayPayment({
      userId: req.user!.userId,
      customerId: req.user!.profileId,
      ...body,
    });
    res.status(data.idempotentReplay ? 200 : 201).json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.get('/:traceId/receipt', async (req, res, next) => {
  try {
    const data = await transactionService.getTransactionReceipt(
      req.user!.profileId,
      req.params.traceId,
    );
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

export default router;
