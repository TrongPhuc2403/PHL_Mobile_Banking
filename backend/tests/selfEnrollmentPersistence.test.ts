import assert from 'node:assert/strict';
import test, { after, mock } from 'node:test';
import fs from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { pool } from '../src/config/db';
import { selfEnrollmentSessions } from '../src/security/selfEnrollmentSessions';
import { createSelfEnrollmentChallenge, selfEnrollFace } from '../src/services/ekycService';

// Independent test process: real face engine, fake DB and fake portrait storage. No real customer is changed.
const profile: any = { id: 77, userId: 700001, faceVector: null, eKYCStatus: 'PENDING' };
let actor: any[] = [];
let auditFailure = false;
let writes = 0;
let removes = 0;
const originalQuery = pool.query,
  originalConnection = pool.getConnection;
(pool as any).query = async (sql: string, params: any[]) => {
  if (/SELECT \* FROM UserAccount/.test(sql))
    return [
      [{ id: 700001, role: 'CUSTOMER', status: 'ACTIVE', failedAttempts: 0, lockedUntil: null }],
      [],
    ];
  if (/SELECT id, eKYCStatus, faceVector FROM CustomerProfile/.test(sql))
    return [[{ ...profile }], []];
  throw new Error('Unexpected SQL: ' + sql);
};
(pool as any).getConnection = async () => {
  const before = { ...profile };
  return {
    beginTransaction: async () => {},
    commit: async () => {},
    release: () => {},
    rollback: async () => Object.assign(profile, before),
    query: async (sql: string, params: any[]) => {
      if (/SELECT userId, faceVector, eKYCStatus/.test(sql)) return [[{ ...profile }], []];
      if (/UPDATE CustomerProfile/.test(sql)) {
        profile.faceVector = params[0];
        profile.eKYCStatus = params[1];
        return [{ affectedRows: 1 }, []];
      }
      if (/INSERT INTO AuditLog/.test(sql)) {
        if (auditFailure) throw new Error('Simulated audit failure');
        actor = params;
        return [{ insertId: 1 }, []];
      }
      throw new Error('Unexpected transactional SQL: ' + sql);
    },
  };
};
mock.method(fs, 'mkdir', async () => undefined);
mock.method(fs, 'writeFile', async () => {
  writes++;
});
mock.method(fs, 'unlink', async () => {
  removes++;
});
after(async () => {
  mock.restoreAll();
  (pool as any).query = originalQuery;
  (pool as any).getConnection = originalConnection;
  await pool.end();
});
async function fixture(name: string) {
  const response = await fetch(
    'https://raw.githubusercontent.com/ageitgey/face_recognition/master/examples/' + name,
  );
  assert.ok(response.ok, 'Public engine fixture download failed');
  const raw = Buffer.from(await response.arrayBuffer()).toString('base64');
  const code = `import base64,sys,cv2,numpy as np
im=cv2.imdecode(np.frombuffer(base64.b64decode(sys.stdin.read()),np.uint8),cv2.IMREAD_COLOR)
s=min(1,960/max(im.shape[:2]));im=cv2.resize(im,None,fx=s,fy=s)
print(base64.b64encode(cv2.imencode('.jpg',im,[cv2.IMWRITE_JPEG_QUALITY,82])[1]).decode())`;
  const result = spawnSync(process.env.EKYC_PYTHON || 'python', ['-c', code], {
    input: raw,
    encoding: 'utf8',
    maxBuffer: 2000000,
    timeout: 15000,
  });
  assert.equal(result.status, 0, 'Fixture re-encode failed');
  return result.stdout.trim();
}
test('real self-enrollment captures persist only own reference with CUSTOMER audit and no transaction token', async () => {
  const imageBase64 = await fixture('obama.jpg'),
    motionImageBase64 = await fixture('obama2.jpg');
  const token = selfEnrollmentSessions.approve(
    700001,
    selfEnrollmentSessions.begin(700001, 77),
  ).enrollmentToken;
  const challenge = await createSelfEnrollmentChallenge(700001, token);
  const data = await selfEnrollFace(700001, {
    challengeId: challenge.challengeId,
    enrollmentToken: token,
    imageBase64,
    motionImageBase64,
    consent: true,
  });
  assert.equal(data.enrolled, true);
  assert.equal((data as any).ekycToken, undefined);
  const stored = JSON.parse(profile.faceVector);
  assert.equal(stored.engine, 'OpenCV-SFace-v1');
  assert.equal(stored.embedding.length, 128);
  assert.equal(profile.eKYCStatus, 'VERIFIED');
  assert.equal(writes, 1);
  assert.deepEqual(actor.slice(0, 4), [700001, 'CUSTOMER', 'SELF_ENROLL_EKYC', 77]);
  assert.equal(JSON.parse(actor[4]).method, 'PASSWORD_OTP_SELF');
  assert.throws(
    () => selfEnrollmentSessions.assert(700001, 77, token),
    (error: any) => error.code === 'ENROLLMENT_AUTH_REQUIRED',
  );
  await assert.rejects(
    createSelfEnrollmentChallenge(700001, token),
    (error: any) => error.code === 'EKYC_ALREADY_ENROLLED',
  );
  // A DB/audit failure must roll back the profile and delete the new private portrait.
  profile.faceVector = null;
  profile.eKYCStatus = 'PENDING';
  auditFailure = true;
  const another = selfEnrollmentSessions.approve(
    700002,
    selfEnrollmentSessions.begin(700002, 77),
  ).enrollmentToken;
  // Reject approval belonging to another actor even if customerId was guessed correctly.
  await assert.rejects(
    createSelfEnrollmentChallenge(700001, another),
    (error: any) => error.code === 'ENROLLMENT_AUTH_REQUIRED',
  );
  // The same authorized photo retry is tested with a new isolated session owner/profile.
  profile.userId = 700003;
  const retry = selfEnrollmentSessions.approve(
    700003,
    selfEnrollmentSessions.begin(700003, 77),
  ).enrollmentToken;
  const retryChallenge = await createSelfEnrollmentChallenge(700003, retry);
  await assert.rejects(
    selfEnrollFace(700003, {
      challengeId: retryChallenge.challengeId,
      enrollmentToken: retry,
      imageBase64,
      motionImageBase64,
      consent: true,
    }),
    /Simulated audit failure/,
  );
  assert.equal(profile.faceVector, null);
  assert.equal(profile.eKYCStatus, 'PENDING');
  assert.equal(removes, 1);
  selfEnrollmentSessions.assert(700003, 77, retry);
});
