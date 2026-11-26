import { Router } from 'express';
import { requireAuth, requireRole } from '../middleware/authMiddleware';
import * as accountService from '../services/accountService';

const router = Router();

router.use(requireAuth, requireRole('CUSTOMER'));

router.get('/', async (req, res, next) => {
  try {
    const data = await accountService.getCustomerAccounts(req.user!.profileId);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.get('/lookup/:accountNumber', async (req, res, next) => {
  try {
    const data = await accountService.getAccountByNumber(req.params.accountNumber);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.get('/savings', async (req, res, next) => {
  try {
    const data = await accountService.getSavingsSummary(req.user!.profileId);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.get('/mortgages', async (req, res, next) => {
  try {
    const data = await accountService.getMortgageSchedules(req.user!.profileId);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.get('/history', async (req, res, next) => {
  try {
    const accountId = req.query.accountId ? Number(req.query.accountId) : undefined;
    const data = await accountService.getAccountHistory(req.user!.profileId, accountId);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

export default router;
