import assert from 'node:assert/strict';
import test, { after } from 'node:test';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import { AddressInfo } from 'node:net';
import { pool } from '../src/config/db';
import { assertReplayOwner } from '../src/services/transactionAuthorization';
import { env } from '../src/config/env';
import { EkycSessions, ekycSessions } from '../src/security/ekycSessions';
import {
  SelfEnrollmentSessions,
  selfEnrollmentSessions,
  enrollmentOtpType,
} from '../src/security/selfEnrollmentSessions';
import { intentHash, transactionOtpType } from '../src/security/transactionIntent';
import { authorizeTransaction } from '../src/services/transactionAuthorization';
import { verifyOtp } from '../src/services/otpService';
import {
  internalTransfer,
  depositOrWithdraw,
  interbankTransfer,
  gatewayPayment,
} from '../src/services/transactionService';
import { payUtilityOrder } from '../src/services/utilityService';
import { runFaceEngine } from '../src/services/faceEngine';
import app from '../src/app';

// Test seam belongs to this test process only: no SQL or requests to the running dev API.
let writes = 0;
let balanceMutations = 0;
let otp: any = null;
const selfUser: any = {
  id: 900001,
  role: 'CUSTOMER',
  status: 'ACTIVE',
  failedAttempts: 0,
  lockedUntil: null,
  passwordHash: bcrypt.hashSync('SelfTest@2026', 4),
};
const selfProfile: any = {
  id: 1,
  userId: 900001,
  eKYCStatus: 'VERIFIED',
  faceVector: 'MOCK_FACE_VEC_1',
};
let ledgerMode = false;
let failLedger = false;
let committedTx: any = null;
let ledger = { source: 30000000, recipient: 5000000 };
const originalGetConnection = pool.getConnection;
(pool as any).getConnection = async () => {
  if (!ledgerMode) throw new Error('DB connection forbidden outside isolated ledger test');
  const before = { ...ledger };
  return {
    beginTransaction: async () => {},
    commit: async () => {},
    rollback: async () => {
      ledger = before;
      committedTx = null;
    },
    release: () => {},
    query: async (sql: string, values: any[] = []) => {
      if (failLedger) throw new Error('Simulated database failure');
      if (/SELECT \* FROM BankAccount WHERE accountNumber/.test(sql))
        return [
          [
            {
              id: 1,
              customerId: 900001,
              accountType: 'CHECKING',
              status: 'ACTIVE',
              currentBalance: ledger.source,
              availableBalance: ledger.source,
            },
          ],
          [],
        ];
      if (/SELECT ba\.\*/.test(sql))
        return [
          [
            {
              id: 2,
              customerId: 900002,
              accountType: 'CHECKING',
              status: 'ACTIVE',
              currentBalance: ledger.recipient,
              availableBalance: ledger.recipient,
              ownerName: 'Test recipient',
            },
          ],
          [],
        ];
      if (/INSERT INTO Transaction/.test(sql)) {
        committedTx = {
          id: 1,
          traceId: values[0],
          idempotencyKey: values[1],
          fromAccId: values[2],
          toAccId: values[3],
          amount: values[5],
          fee: 0,
        };
        return [{ insertId: 1, affectedRows: 1 }, []];
      }
      if (/UPDATE BankAccount/.test(sql)) {
        if (values[2] === 1) ledger.source = values[1];
        else ledger.recipient = values[1];
        return [{ affectedRows: 1 }, []];
      }
      if (/INSERT INTO BalanceFluctuation/.test(sql)) return [{ insertId: 1, affectedRows: 1 }, []];
      if (/SELECT \* FROM Transaction WHERE id =/.test(sql)) return [[committedTx], []];
      throw new Error('Unexpected ledger SQL: ' + sql);
    },
  };
};
const originalQuery = pool.query;
(pool as any).query = async (sql: string, values: any[] = []) => {
  if (/SELECT \* FROM Transaction WHERE idempotencyKey/.test(sql))
    return [
      [...(ledgerMode && committedTx?.idempotencyKey === values[0] ? [committedTx] : [])],
      [],
    ];
  if (/SELECT id FROM BankAccount WHERE customerId/.test(sql))
    return [[...(values[0] === 900001 && values[1] === '52400028' ? [{ id: 1 }] : [])], []];
  if (/SELECT id, eKYCStatus, faceVector FROM CustomerProfile/.test(sql))
    return [[selfProfile], []];
  if (/SELECT \* FROM UserAccount WHERE id/.test(sql))
    return [[...(values[0] === 900001 ? [selfUser] : [])], []];
  if (/UPDATE UserAccount SET failedAttempts = 0/.test(sql)) {
    selfUser.failedAttempts = 0;
    selfUser.lockedUntil = null;
    return [{ affectedRows: 1 }, []];
  }
  if (/UPDATE UserAccount SET failedAttempts = \?, lockedUntil/.test(sql)) {
    selfUser.failedAttempts = values[0];
    selfUser.lockedUntil = new Date(Date.now() + 900000);
    return [{ affectedRows: 1 }, []];
  }
  if (/UPDATE UserAccount SET failedAttempts = \? WHERE id/.test(sql)) {
    selfUser.failedAttempts = values[0];
    return [{ affectedRows: 1 }, []];
  }
  if (/SELECT \* FROM OTPVerification/.test(sql))
    return [[...(otp && otp.userId === values[0] && otp.otpType === values[1] ? [otp] : [])], []];
  if (/INSERT INTO OTPVerification/.test(sql)) {
    writes++;
    otp = {
      id: writes,
      userId: values[0],
      otpType: values[3],
      otpHash: values[2],
      expiresAt: new Date(Date.now() + 120000),
      attempts: 0,
      isUsed: false,
    };
    return [{ insertId: writes, affectedRows: 1 }, []];
  }
  if (/UPDATE OTPVerification SET isUsed = TRUE WHERE id/.test(sql)) {
    const changed = otp && !otp.isUsed;
    if (changed) otp.isUsed = true;
    return [{ affectedRows: changed ? 1 : 0 }, []];
  }
  if (/UPDATE OTPVerification SET attempts = attempts/.test(sql)) {
    if (otp) otp.attempts++;
    return [{ affectedRows: 1 }, []];
  }
  if (/UPDATE OTPVerification/.test(sql)) return [{ affectedRows: 1 }, []];
  if (/UPDATE BankAccount|INSERT INTO Transaction/.test(sql)) balanceMutations++;
  throw new Error('Unexpected SQL in isolated test: ' + sql);
};
const server = app.listen(0, '127.0.0.1');
const listening = new Promise<void>((resolve) => server.once('listening', () => resolve()));
after(async () => {
  await new Promise<void>((resolve) => server.close(() => resolve()));
  (pool as any).query = originalQuery;
  (pool as any).getConnection = originalGetConnection;
  await pool.end();
});
const intent = (amount = 15000000) => ({
  operation: 'INTERNAL',
  fromAccountNumber: '52400028',
  toAccountNumber: '52400114',
  amount,
  content: 'Test',
  idempotencyKey: crypto.randomUUID(),
});
async function request(
  path: string,
  body?: unknown,
  role: 'CUSTOMER' | 'OFFICER' = 'CUSTOMER',
  userId = 900001,
) {
  await listening;
  const token = jwt.sign({ userId, profileId: userId, role }, env.jwt.accessSecret, {
    expiresIn: '1m',
  });
  const response = await fetch(
    `http://127.0.0.1:${(server.address() as AddressInfo).port}/api/${path}`,
    {
      method: body === undefined ? 'GET' : 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: body === undefined ? undefined : JSON.stringify(body),
    },
  );
  return { status: response.status, body: (await response.json()) as any };
}
function failsCode(action: () => unknown, code: string) {
  assert.throws(action, (error: any) => error.code === code);
}
async function rejectsCode(action: Promise<unknown>, code: string) {
  await assert.rejects(action, (error: any) => error.code === code);
}

test('transaction hash canonicalizes order and binds user/amount/recipient/content/key', () => {
  const value = intent();
  const hash = intentHash(1, value);
  assert.equal(intentHash(1, { ...value, ignored: 'not financial context' }), hash);
  for (const changed of [
    { ...value, amount: value.amount + 1 },
    { ...value, toAccountNumber: '99999999' },
    { ...value, content: 'Changed' },
    { ...value, idempotencyKey: crypto.randomUUID() },
  ])
    assert.notEqual(intentHash(1, changed), hash);
  assert.notEqual(intentHash(2, value), hash);
});
test('challenge is owner/scoped/expiring and single use', () => {
  let now = 1000;
  const sessions = new EkycSessions(() => now);
  const value = sessions.challenge(1, 'TRANSACTION', 'hash');
  failsCode(
    () => sessions.takeChallenge(2, value.challengeId, 'TRANSACTION'),
    'EKYC_CHALLENGE_INVALID',
  );
  failsCode(
    () => sessions.takeChallenge(1, value.challengeId, 'ENROLL', 1),
    'EKYC_CHALLENGE_INVALID',
  );
  assert.equal(sessions.takeChallenge(1, value.challengeId, 'TRANSACTION').hash, 'hash');
  failsCode(
    () => sessions.takeChallenge(1, value.challengeId, 'TRANSACTION'),
    'EKYC_CHALLENGE_INVALID',
  );
  const expired = sessions.challenge(1, 'CHECK');
  now += 120000;
  failsCode(
    () => sessions.takeChallenge(1, expired.challengeId, 'CHECK'),
    'EKYC_CHALLENGE_INVALID',
  );
});
test('verification grant blocks missing/other user/changed context/expiry/reuse/concurrent claim', () => {
  let now = 1000;
  const sessions = new EkycSessions(() => now);
  const token = sessions.grant(1, 'hash').ekycToken;
  failsCode(() => sessions.assert(1, 'hash'), 'EKYC_REQUIRED');
  failsCode(() => sessions.assert(2, 'hash', token), 'EKYC_EXPIRED');
  failsCode(() => sessions.assert(1, 'changed', token), 'EKYC_EXPIRED');
  const reservation = sessions.reserve(1, 'hash', token);
  failsCode(() => sessions.reserve(1, 'hash', token), 'EKYC_IN_USE');
  reservation.release();
  sessions.assert(1, 'hash', token);
  sessions.reserve(1, 'hash', token).complete();
  failsCode(() => sessions.assert(1, 'hash', token), 'EKYC_EXPIRED');
  const expired = sessions.grant(1, 'hash').ekycToken;
  now += 300000;
  failsCode(() => sessions.assert(1, 'hash', expired), 'EKYC_EXPIRED');
});
test('OTP request cannot proceed before eKYC and never inserts an OTP on rejected high value', async () => {
  const before = writes;
  for (const operation of ['INTERNAL', 'INTERBANK', 'GATEWAY', 'DEPOSIT', 'WITHDRAW', 'UTILITY']) {
    const value: any =
      operation === 'INTERNAL'
        ? intent()
        : operation === 'INTERBANK'
          ? { ...intent(), operation, toBankCode: 'VCB', toAccName: 'Test user' }
          : operation === 'GATEWAY'
            ? {
                operation,
                fromAccountNumber: '52400028',
                amount: 15000000,
                provider: 'VNPAY',
                merchantName: 'Shop',
                idempotencyKey: crypto.randomUUID(),
              }
            : operation === 'UTILITY'
              ? {
                  operation,
                  fromAccountNumber: '52400028',
                  amount: 15000000,
                  serviceType: 'HOTEL',
                  supplier: 'Test hotel',
                  idempotencyKey: crypto.randomUUID(),
                }
              : {
                  operation,
                  accountNumber: '52400028',
                  amount: 15000000,
                  idempotencyKey: crypto.randomUUID(),
                };
    const response = await request('security/otp/request', {
      otpType: 'TRANSACTION',
      intent: value,
    });
    assert.equal(response.status, 403);
    assert.equal(response.body.error.code, 'EKYC_REQUIRED');
  }
  assert.equal(writes, before);
  assert.equal(balanceMutations, 0);
});
test('10 million boundary allows scoped OTP, 10,000,001 requires eKYC', async () => {
  assert.equal(
    (await request('security/otp/request', { otpType: 'TRANSACTION', intent: intent(10000000) }))
      .status,
    200,
  );
  const response = await request('security/otp/request', {
    otpType: 'TRANSACTION',
    intent: intent(10000001),
  });
  assert.equal(response.status, 403);
  assert.equal(response.body.error.code, 'EKYC_REQUIRED');
});
test('valid test grant enables OTP only for its exact transaction; wrong user/key cannot borrow it', async () => {
  const value = intent();
  const token = ekycSessions.grant(900001, intentHash(900001, value)).ekycToken;
  assert.equal(
    (
      await request('security/otp/request', {
        otpType: 'TRANSACTION',
        intent: value,
        ekycToken: token,
      })
    ).status,
    200,
  );
  assert.equal(
    (
      await request('security/otp/request', {
        otpType: 'TRANSACTION',
        intent: { ...value, amount: value.amount + 1 },
        ekycToken: token,
      })
    ).status,
    403,
  );
  const foreign = ekycSessions.grant(900002, intentHash(900001, value)).ekycToken;
  assert.equal(
    (
      await request('security/otp/request', {
        otpType: 'TRANSACTION',
        intent: value,
        ekycToken: foreign,
      })
    ).status,
    403,
  );
});
test('old OTP API without transaction context is rejected; officer cannot request a financial OTP', async () => {
  assert.equal(
    (await request('security/otp/request', { otpType: 'TRANSACTION' })).body.error.code,
    'TRANSACTION_CONTEXT_REQUIRED',
  );
  assert.equal(
    (
      await request(
        'security/otp/request',
        { otpType: 'TRANSACTION', intent: intent(1) },
        'OFFICER',
      )
    ).status,
    403,
  );
});
test('all transaction service paths reject missing eKYC before balance writes', async () => {
  const params = {
    userId: 900001,
    customerId: 900001,
    fromAccountNumber: '52400028',
    toAccountNumber: '52400114',
    amount: 15000000,
    content: 'Test',
    idempotencyKey: crypto.randomUUID(),
    otpCode: '123456',
  };
  await rejectsCode(internalTransfer(params), 'EKYC_REQUIRED');
  await rejectsCode(
    interbankTransfer({ ...params, toBankCode: 'VCB', toAccName: 'Test user' }),
    'EKYC_REQUIRED',
  );
  await rejectsCode(
    gatewayPayment({ ...params, provider: 'VNPAY', merchantName: 'Shop', orderInfo: 'Test' }),
    'EKYC_REQUIRED',
  );
  for (const mode of ['DEPOSIT', 'WITHDRAW'] as const)
    await rejectsCode(
      depositOrWithdraw({ ...params, accountNumber: '52400028', mode }),
      'EKYC_REQUIRED',
    );
  await rejectsCode(
    payUtilityOrder({ ...params, serviceType: 'HOTEL', supplier: 'Test hotel' }),
    'EKYC_REQUIRED',
  );
  assert.equal(balanceMutations, 0);
});
test('mock seed vectors are not enrolled and timestamp-only payload cannot verify', async () => {
  const status = await request('security/ekyc/status');
  assert.equal(status.body.data.enrolled, false);
  assert.equal((await request('security/ekyc/challenge', {})).body.error.code, 'EKYC_NOT_ENROLLED');
  const response = await request('security/ekyc/verify', {
    facePayload: 'CAMERAX_LIVE_FRAME_1234567890',
  });
  assert.equal(response.status, 400);
  assert.equal(response.body.error.code, 'VALIDATION_ERROR');
});
test('customer cannot enroll their own reference through officer route', async () => {
  assert.equal((await request('officer/customers/1/ekyc/challenge', {})).status, 403);
});
test('real engine rejects malformed JPEG instead of granting verification', async () => {
  await rejectsCode(
    runFaceEngine({
      action: 'enroll',
      imageBase64: Buffer.from('not-an-image'.repeat(1000)).toString('base64'),
      motionImageBase64: 'AAAA',
    }),
    'EKYC_INVALID_IMAGE',
  );
});
test('wrong OTP leaves grant available; valid scoped OTP consumes once; retry needs a new OTP', async () => {
  const value = intent();
  const hash = intentHash(900001, value);
  const token = ekycSessions.grant(900001, hash).ekycToken;
  otp = {
    id: 999,
    userId: 900001,
    otpType: transactionOtpType(hash),
    otpHash: await bcrypt.hash('654321', 4),
    expiresAt: new Date(Date.now() + 120000),
    attempts: 0,
    isUsed: false,
  };
  await rejectsCode(authorizeTransaction(900001, value, '111111', token), 'OTP_INVALID');
  ekycSessions.assert(900001, hash, token);
  const authorization = await authorizeTransaction(900001, value, '654321', token);
  authorization.complete();
  failsCode(() => ekycSessions.assert(900001, hash, token), 'EKYC_EXPIRED');
  await rejectsCode(verifyOtp(900001, '654321', transactionOtpType(hash)), 'OTP_NOT_FOUND');
});
test('expired and changed-transaction OTPs cannot authorize', async () => {
  const value = intent(1000);
  const hash = intentHash(900001, value);
  otp = {
    id: 1000,
    userId: 900001,
    otpType: transactionOtpType(hash),
    otpHash: await bcrypt.hash('654321', 4),
    expiresAt: new Date(Date.now() - 1),
    attempts: 0,
    isUsed: false,
  };
  await rejectsCode(authorizeTransaction(900001, value, '654321'), 'OTP_EXPIRED');
  await rejectsCode(
    authorizeTransaction(900001, { ...value, toAccountNumber: '88888888' }, '654321'),
    'OTP_NOT_FOUND',
  );
});

test('exact verified + scoped OTP transfer commits once; idempotent retry never double-debits', async () => {
  ledgerMode = true;
  committedTx = null;
  ledger = { source: 30000000, recipient: 5000000 };
  try {
    const value = intent();
    const hash = intentHash(900001, value);
    const token = ekycSessions.grant(900001, hash).ekycToken;
    otp = {
      id: 1500,
      userId: 900001,
      otpType: transactionOtpType(hash),
      otpHash: await bcrypt.hash('654321', 4),
      expiresAt: new Date(Date.now() + 120000),
      attempts: 0,
      isUsed: false,
    };
    const params = {
      ...value,
      userId: 900001,
      customerId: 900001,
      otpCode: '654321',
      ekycToken: token,
    };
    const first = await internalTransfer(params);
    assert.equal(first.idempotentReplay, false);
    assert.equal(ledger.source, 15000000);
    assert.equal(ledger.recipient, 20000000);
    const second = await internalTransfer(params);
    assert.equal(second.idempotentReplay, true);
    assert.equal(ledger.source, 15000000);
    assert.equal(ledger.recipient, 20000000);
    failsCode(() => ekycSessions.assert(900001, hash, token), 'EKYC_EXPIRED');
  } finally {
    ledgerMode = false;
    committedTx = null;
  }
});
test('DB failure does not commit balances and releases eKYC reservation for a new OTP', async () => {
  ledgerMode = true;
  failLedger = true;
  committedTx = null;
  ledger = { source: 30000000, recipient: 5000000 };
  try {
    const value = intent();
    const hash = intentHash(900001, value);
    const token = ekycSessions.grant(900001, hash).ekycToken;
    otp = {
      id: 1600,
      userId: 900001,
      otpType: transactionOtpType(hash),
      otpHash: await bcrypt.hash('654321', 4),
      expiresAt: new Date(Date.now() + 120000),
      attempts: 0,
      isUsed: false,
    };
    await assert.rejects(
      internalTransfer({
        ...value,
        userId: 900001,
        customerId: 900001,
        otpCode: '654321',
        ekycToken: token,
      }),
      /Simulated database failure/,
    );
    assert.equal(ledger.source, 30000000);
    assert.equal(ledger.recipient, 5000000);
    ekycSessions.assert(900001, hash, token);
  } finally {
    ledgerMode = false;
    failLedger = false;
    committedTx = null;
  }
});
test('idempotency collision cannot disclose a receipt owned by another customer', async () => {
  await rejectsCode(
    assertReplayOwner(
      { fromAccId: 2, toAccId: 1, amount: 15000000 } as any,
      900001,
      '52400028',
      15000000,
    ),
    'IDEMPOTENCY_CONFLICT',
  );
});

test('self-enrollment request/approval is scoped, single-use, cooldown limited and expiring', () => {
  let now = 1000;
  const sessions = new SelfEnrollmentSessions(() => now);
  const requestId = sessions.begin(1, 11);
  failsCode(() => sessions.begin(1, 11), 'ENROLLMENT_OTP_COOLDOWN');
  failsCode(() => sessions.request(2, requestId), 'ENROLLMENT_REQUEST_INVALID');
  const token = sessions.approve(1, requestId).enrollmentToken;
  failsCode(() => sessions.approve(1, requestId), 'ENROLLMENT_REQUEST_INVALID');
  failsCode(() => sessions.assert(2, 11, token), 'ENROLLMENT_AUTH_REQUIRED');
  failsCode(() => sessions.assert(1, 12, token), 'ENROLLMENT_AUTH_REQUIRED');
  sessions.assert(1, 11, token);
  now += 30000;
  const newer = sessions.begin(1, 11);
  failsCode(() => sessions.assert(1, 11, token), 'ENROLLMENT_AUTH_REQUIRED');
  now += 120000;
  failsCode(() => sessions.approve(1, newer), 'ENROLLMENT_REQUEST_INVALID');
  const next = sessions.begin(1, 11);
  const nextToken = sessions.approve(1, next).enrollmentToken;
  sessions.complete(1, 11, nextToken);
  failsCode(() => sessions.assert(1, 11, nextToken), 'ENROLLMENT_AUTH_REQUIRED');
  now += 30000;
  const expiring = sessions.approve(1, sessions.begin(1, 11)).enrollmentToken;
  now += 300000;
  failsCode(() => sessions.assert(1, 11, expiring), 'ENROLLMENT_AUTH_REQUIRED');
});
test('first-time customer self enrollment requires password and a dedicated OTP; cannot grant transaction permission', async () => {
  const password = 'SelfTest@2026';
  const before = writes;
  const wrong = await request('security/ekyc/enrollment/request-otp', {
    currentPassword: 'Incorrect!',
  });
  assert.equal(wrong.body.error.code, 'WRONG_PASSWORD');
  assert.equal(writes, before);
  const issued = await request('security/ekyc/enrollment/request-otp', {
    currentPassword: password,
  });
  assert.equal(issued.status, 200);
  assert.equal(selfUser.failedAttempts, 0);
  const requestId = issued.body.data.requestId;
  const otpCode = issued.body.data.devOtpPreview;
  assert.equal(otp.otpType, enrollmentOtpType(requestId));
  assert.equal(otp.isUsed, false);
  assert.equal(
    (
      await request(
        'security/ekyc/enrollment/confirm-otp',
        { requestId, otpCode },
        'CUSTOMER',
        900002,
      )
    ).status,
    403,
  );
  const badCode = otpCode === '111111' ? '222222' : '111111';
  assert.equal(
    (await request('security/ekyc/enrollment/confirm-otp', { requestId, otpCode: badCode })).body
      .error.code,
    'OTP_INVALID',
  );
  const confirmed = await request('security/ekyc/enrollment/confirm-otp', { requestId, otpCode });
  assert.equal(confirmed.status, 200);
  const enrollmentToken = confirmed.body.data.enrollmentToken;
  assert.ok(enrollmentToken);
  assert.equal(confirmed.body.data.ekycToken, undefined);
  assert.equal(
    (await request('security/ekyc/enrollment/confirm-otp', { requestId, otpCode })).status,
    403,
  );
  assert.equal(
    (await request('security/ekyc/enrollment/request-otp', { currentPassword: password })).status,
    429,
  );
  const challenge = await request('security/ekyc/enrollment/challenge', { enrollmentToken });
  assert.equal(challenge.status, 200);
  const financial = await request('security/otp/request', {
    otpType: 'TRANSACTION',
    intent: intent(),
    ekycToken: enrollmentToken,
  });
  assert.equal(financial.status, 403);
  assert.equal(financial.body.error.code, 'EKYC_EXPIRED');
  const payload = {
    challengeId: challenge.body.data.challengeId,
    enrollmentToken,
    imageBase64: Buffer.from('bad image'.repeat(1000)).toString('base64'),
    motionImageBase64: Buffer.from('bad image'.repeat(1000)).toString('base64'),
    consent: true,
  };
  assert.equal(
    (await request('security/ekyc/enrollment', { ...payload, consent: false })).status,
    400,
  );
  const invalid = await request('security/ekyc/enrollment', payload);
  assert.equal(invalid.status, 422);
  assert.equal(invalid.body.error.code, 'EKYC_INVALID_IMAGE');
  assert.equal(selfProfile.faceVector, 'MOCK_FACE_VEC_1');
  assert.equal(
    (await request('security/ekyc/enrollment', payload)).body.error.code,
    'EKYC_CHALLENGE_INVALID',
  );
  assert.equal(
    (await request('security/ekyc/enrollment/challenge', { enrollmentToken })).status,
    200,
  );
});
test('self enrollment rejects caller-selected profile IDs, absent approval, officer role and locked account', async () => {
  const token = crypto.randomUUID();
  assert.equal(
    (
      await request('security/ekyc/enrollment/request-otp', {
        currentPassword: 'SelfTest@2026',
        customerId: 2,
      })
    ).status,
    400,
  );
  assert.equal(
    (await request('security/ekyc/enrollment/challenge', { enrollmentToken: token, customerId: 2 }))
      .status,
    400,
  );
  assert.equal(
    (await request('security/ekyc/enrollment/challenge', { enrollmentToken: token })).body.error
      .code,
    'ENROLLMENT_AUTH_REQUIRED',
  );
  assert.equal(
    (
      await request(
        'security/ekyc/enrollment/request-otp',
        { currentPassword: 'SelfTest@2026' },
        'OFFICER',
      )
    ).status,
    403,
  );
  selfUser.status = 'LOCKED';
  assert.equal(
    (await request('security/ekyc/enrollment/challenge', { enrollmentToken: token })).body.error
      .code,
    'ACCOUNT_INACTIVE',
  );
  selfUser.status = 'ACTIVE';
  selfUser.lockedUntil = new Date(Date.now() + 60000);
  assert.equal(
    (await request('security/ekyc/enrollment/request-otp', { currentPassword: 'SelfTest@2026' }))
      .body.error.code,
    'ACCOUNT_LOCKED',
  );
  selfUser.lockedUntil = null;
  assert.equal(balanceMutations, 0);
});
test('a valid existing reference cannot be re-enrolled by a customer', async () => {
  const previous = selfProfile.faceVector;
  selfProfile.faceVector = JSON.stringify({
    engine: 'OpenCV-SFace-v1',
    embedding: Array(128).fill(0.1),
    referenceFile: 'test.jpg',
    enrolledAt: new Date().toISOString(),
  });
  try {
    const status = await request('security/ekyc/status');
    assert.equal(status.body.data.canSelfEnroll, false);
    assert.equal(
      (await request('security/ekyc/enrollment/request-otp', { currentPassword: 'SelfTest@2026' }))
        .body.error.code,
      'EKYC_ALREADY_ENROLLED',
    );
    assert.equal(
      (
        await request('security/ekyc/enrollment/challenge', {
          enrollmentToken: crypto.randomUUID(),
        })
      ).body.error.code,
      'EKYC_ALREADY_ENROLLED',
    );
  } finally {
    selfProfile.faceVector = previous;
  }
});

test('frame feedback requires a current owned challenge and does not authorize enrollment or OTP', async () => {
  const imageBase64 = Buffer.from('not-a-jpeg'.repeat(1000)).toString('base64');
  assert.equal(
    (await request('security/ekyc/frame/check', { challengeId: crypto.randomUUID(), imageBase64 }))
      .body.error.code,
    'EKYC_CHALLENGE_INVALID',
  );
  const challenge = ekycSessions.challenge(900001, 'CHECK');
  const foreign = await request(
    'security/ekyc/frame/check',
    { challengeId: challenge.challengeId, imageBase64 },
    'CUSTOMER',
    900002,
  );
  assert.equal(foreign.status, 403);
  const invalid = await request('security/ekyc/frame/check', {
    challengeId: challenge.challengeId,
    imageBase64,
  });
  assert.equal(invalid.status, 422);
  assert.equal(invalid.body.error.code, 'EKYC_INVALID_IMAGE');
  // Inspect never consumes verification challenge or creates a transaction grant.
  const financial = await request('security/otp/request', {
    otpType: 'TRANSACTION',
    intent: intent(),
    ekycToken: challenge.challengeId,
  });
  assert.equal(financial.status, 403);
  assert.equal(ekycSessions.takeChallenge(900001, challenge.challengeId, 'CHECK').userId, 900001);
  const used = await request('security/ekyc/frame/check', {
    challengeId: challenge.challengeId,
    imageBase64,
  });
  assert.equal(used.body.error.code, 'EKYC_CHALLENGE_INVALID');
  assert.equal(balanceMutations, 0);
});
