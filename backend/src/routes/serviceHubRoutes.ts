import { Router } from 'express';
import { z } from 'zod';
import { requireAuth, requireRole } from '../middleware/authMiddleware';
import * as serviceHub from '../services/serviceHubService';

const router = Router();

router.get('/notifications', requireAuth, requireRole('CUSTOMER'), async (req, res, next) => {
  try {
    const data = await serviceHub.getCustomerNotifications(req.user!.profileId);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.get('/branches', requireAuth, async (req, res, next) => {
  try {
    const lat = req.query.lat ? Number(req.query.lat) : undefined;
    const lng = req.query.lng ? Number(req.query.lng) : undefined;
    const data = await serviceHub.findBranchesWithWalkingRoute(lat, lng);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

router.post('/ai/chat', requireAuth, requireRole('CUSTOMER'), async (req, res, next) => {
  try {
    const { message } = z.object({ message: z.string().min(1).max(500) }).parse(req.body);
    const data = await serviceHub.askAiAssistant(message);
    res.json({ success: true, data });
  } catch (err) {
    next(err);
  }
});

export default router;
