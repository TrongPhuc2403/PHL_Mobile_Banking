import { RowDataPacket } from 'mysql2';
import { pool } from '../config/db';
import { AppError } from '../utils/errors';

export async function getCustomerAccounts(customerId: number) {
  const [accounts] = await pool.query<RowDataPacket[]>(
    `SELECT ba.*,
            sa.termMonths AS savingsTermMonths, sa.interestRate AS savingsInterestRate,
            sa.depositDate, sa.maturityDate, sa.productType, sa.accruedInterest,
            ml.id AS loanId, ml.loanAmount, ml.remainingPrincipal,
            ml.interestRate AS loanInterestRate, ml.termMonths AS loanTermMonths,
            ml.repaymentCycle, ml.collateralInfo
     FROM BankAccount ba
     LEFT JOIN SavingsAccount sa ON sa.bankAccountId = ba.id
     LEFT JOIN MortgageLoan ml ON ml.bankAccountId = ba.id
     WHERE ba.customerId = ?
     ORDER BY FIELD(ba.accountType, 'CHECKING', 'SAVINGS', 'MORTGAGE'), ba.id ASC`,
    [customerId],
  );

  return accounts;
}

export async function getAccountByNumber(accountNumber: string) {
  const [rows] = await pool.query<RowDataPacket[]>(
    `SELECT ba.id, ba.accountNumber, ba.accountType, ba.status, cp.fullName AS ownerName
     FROM BankAccount ba
     JOIN CustomerProfile cp ON cp.id = ba.customerId
     WHERE ba.accountNumber = ? LIMIT 1`,
    [accountNumber.trim()],
  );
  if (!rows[0]) {
    throw new AppError(404, 'ACCOUNT_NOT_FOUND', 'Không tìm thấy số tài khoản này');
  }
  return rows[0];
}

export async function getSavingsSummary(customerId: number) {
  const [rows] = await pool.query<RowDataPacket[]>(
    `SELECT ba.accountNumber, ba.currentBalance, ba.status,
            sa.termMonths, sa.interestRate, sa.depositDate, sa.maturityDate,
            sa.productType, sa.accruedInterest
     FROM BankAccount ba
     JOIN SavingsAccount sa ON sa.bankAccountId = ba.id
     WHERE ba.customerId = ?`,
    [customerId],
  );
  return rows;
}

export async function getMortgageSchedules(customerId: number) {
  const [loans] = await pool.query<RowDataPacket[]>(
    `SELECT ml.*, ba.accountNumber
     FROM MortgageLoan ml
     JOIN BankAccount ba ON ba.id = ml.bankAccountId
     WHERE ba.customerId = ?`,
    [customerId],
  );

  const results = [];
  for (const loan of loans) {
    const [schedules] = await pool.query<RowDataPacket[]>(
      'SELECT * FROM RepaymentSchedule WHERE loanId = ? ORDER BY periodNo ASC',
      [loan.id],
    );
    results.push({ ...loan, schedules });
  }
  return results;
}

export async function getAccountHistory(customerId: number, accountId?: number) {
  const params: unknown[] = [customerId];
  let accountFilter = '';
  if (accountId) {
    accountFilter = 'AND ba.id = ?';
    params.push(accountId);
  }

  const [rows] = await pool.query<RowDataPacket[]>(
    `SELECT bf.id AS fluctuationId, bf.changeAmount, bf.balanceBefore, bf.balanceAfter,
            bf.type AS direction, bf.timestamp,
            ba.accountNumber, ba.accountType,
            t.id AS transactionId, t.traceId, t.amount, t.fee, t.content,
            t.type AS transactionType, t.status AS transactionStatus,
            t.toBankCode, t.toAccName
     FROM BalanceFluctuation bf
     JOIN BankAccount ba ON ba.id = bf.bankAccountId
     JOIN Transaction t ON t.id = bf.transactionId
     WHERE ba.customerId = ? ${accountFilter}
     ORDER BY bf.timestamp DESC, bf.id DESC
     LIMIT 100`,
    params,
  );

  return rows;
}
