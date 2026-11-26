import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import accountRoutes from './routes/accountRoutes';
import authRoutes from './routes/authRoutes';
import officerRoutes from './routes/officerRoutes';
import securityRoutes from './routes/securityRoutes';
import serviceHubRoutes from './routes/serviceHubRoutes';
import transactionRoutes from './routes/transactionRoutes';
import utilityRoutes from './routes/utilityRoutes';
import { errorHandler } from './utils/errors';

const app = express();

app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '1mb' }));

app.get('/api/health', (_req, res) => {
  res.json({
    success: true,
    service: 'PHL Mobile Banking API',
    timestamp: new Date().toISOString(),
  });
});

app.use('/api/auth', authRoutes);
app.use('/api/security', securityRoutes);
app.use('/api/accounts', accountRoutes);
app.use('/api/transactions', transactionRoutes);
app.use('/api/utilities', utilityRoutes);
app.use('/api/hub', serviceHubRoutes);
app.use('/api/officer', officerRoutes);

app.use(errorHandler);

export default app;
