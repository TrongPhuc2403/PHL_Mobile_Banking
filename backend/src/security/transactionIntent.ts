import crypto from 'crypto';
import { z } from 'zod';

const base = {
  amount: z.number().int().positive().max(9_000_000_000_000),
  idempotencyKey: z.string().min(8).max(100),
};
const account = z.string().min(6).max(30);
export const transactionIntentSchema = z.discriminatedUnion('operation', [
  z.object({
    ...base,
    operation: z.literal('INTERNAL'),
    fromAccountNumber: account,
    toAccountNumber: account,
    content: z.string().max(255).default('Chuyển tiền nội bộ PHL'),
  }),
  z.object({
    ...base,
    operation: z.literal('INTERBANK'),
    fromAccountNumber: account,
    toAccountNumber: account,
    toBankCode: z.string().min(2).max(20),
    toAccName: z.string().min(2).max(150),
    content: z.string().max(255).default('Chuyển tiền liên ngân hàng'),
  }),
  z.object({
    ...base,
    operation: z.literal('GATEWAY'),
    fromAccountNumber: account,
    provider: z.enum(['VNPAY', 'STRIPE']),
    merchantName: z.string().min(2).max(150),
    orderInfo: z.string().max(255).default('Thanh toán qua cổng điện tử'),
  }),
  z.object({ ...base, operation: z.literal('DEPOSIT'), accountNumber: account }),
  z.object({ ...base, operation: z.literal('WITHDRAW'), accountNumber: account }),
  z.object({
    ...base,
    operation: z.literal('UTILITY'),
    fromAccountNumber: account,
    serviceType: z.enum(['ELECTRIC', 'WATER', 'TOPUP', 'FLIGHT', 'CINEMA', 'HOTEL', 'ECOMMERCE']),
    supplier: z.string().min(2).max(150),
    billCode: z.string().max(100).optional(),
    ticketData: z.record(z.string(), z.unknown()).optional(),
  }),
]);
export type TransactionIntent = z.infer<typeof transactionIntentSchema>;

function stable(value: unknown): string {
  if (Array.isArray(value)) return '[' + value.map(stable).join(',') + ']';
  if (value !== null && typeof value === 'object') {
    return (
      '{' +
      Object.entries(value)
        .filter(([, v]) => v !== undefined)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => JSON.stringify(k) + ':' + stable(v))
        .join(',') +
      '}'
    );
  }
  return JSON.stringify(value);
}
export function intentHash(userId: number, input: unknown): string {
  const intent = transactionIntentSchema.parse(input);
  return crypto
    .createHash('sha256')
    .update(userId + ':' + stable(intent))
    .digest('hex');
}
export function transactionOtpType(hash: string): string {
  return 'TX:' + hash.slice(0, 24);
}
