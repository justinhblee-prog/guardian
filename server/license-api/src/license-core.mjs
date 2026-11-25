import {
  createHmac, randomBytes, scrypt as scryptCallback, timingSafeEqual,
  sign as rsaSign
} from 'node:crypto';
import { promisify } from 'node:util';

const scrypt = promisify(scryptCallback);
const KEY_BYTES = 64;
const KEY_PATTERN = /^[A-Z0-9-]{20,80}$/;
const DEVICE_PATTERN = /^[a-fA-F0-9]{64}$/;
const SALT_PATTERN = /^[a-fA-F0-9]{32}$/;

export function validateLicenseIssueParameters({
  maxDevices = 1,
  validDays = 365,
  planCode = 'custom'
} = {}) {
  const planDurations = { monthly_30d: 30, quarterly_90d: 90, annual_365d: 365, short_7d: 7 };
  if (!Number.isInteger(maxDevices) || maxDevices < 1 || maxDevices > 20 ||
      !Number.isInteger(validDays) || validDays < 1 || validDays > 36500 ||
      typeof planCode !== 'string' || !['custom', ...Object.keys(planDurations)].includes(planCode) ||
      (planDurations[planCode] !== undefined && validDays !== planDurations[planCode])) {
    throw Object.assign(new Error('상품 유형, 기기 수 또는 유효 기간 설정을 확인하세요.'), { status: 400 });
  }
  return { maxDevices, validDays, planCode };
}

export function validateLicenseCustomer(value) {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) {
    throw Object.assign(new Error('고객 정보는 객체 형식이어야 합니다.'), { status: 400 });
  }
  const name = typeof value.name === 'string' ? value.name.trim() : '';
  const email = typeof value.email === 'string' ? value.email.trim().toLowerCase() : '';
  const orderRef = typeof value.orderRef === 'string' ? value.orderRef.trim() : '';
  const hasControl = /[\u0000-\u001f\u007f]/;
  if (!name || name.length > 120 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ||
      email.length > 254 || orderRef.length > 120 ||
      hasControl.test(name) || hasControl.test(email) || hasControl.test(orderRef)) {
    throw Object.assign(new Error('고객명, 올바른 이메일, 주문번호를 확인하세요.'), { status: 400 });
  }
  return { name, email, orderRef: orderRef || null };
}

export function validateSupportReplyStatus(value) {
  if (value === undefined) return 'answered';
  if (typeof value !== 'string' || !['answered', 'in_progress', 'closed'].includes(value)) {
    throw Object.assign(new Error('문의 답변 상태 값이 올바르지 않습니다.'), { status: 400 });
  }
  return value;
}

export function validateDeviceBinding(value) {
  if (typeof value !== 'string' || !DEVICE_PATTERN.test(value)) {
    throw Object.assign(new Error('기기 바인딩 값이 올바르지 않습니다.'), { status: 400 });
  }
  return value.toLowerCase();
}

export async function hashLicenseKey(licenseKey, pepper, saltHex = randomBytes(16).toString('hex')) {
  if (typeof licenseKey !== 'string' || !KEY_PATTERN.test(licenseKey)) {
    throw Object.assign(new Error('라이선스 키 형식이 올바르지 않습니다.'), { status: 400 });
  }
  if (typeof pepper !== 'string' || pepper.length < 32) throw new Error('GUARDIAN_LICENSE_LOOKUP_SECRET must be at least 32 characters.');
  if (typeof saltHex !== 'string' || !SALT_PATTERN.test(saltHex)) {
    throw Object.assign(new Error('라이선스 키 salt 형식이 올바르지 않습니다.'), { status: 400 });
  }
  const derived = await scrypt(licenseKey, Buffer.from(saltHex, 'hex'), KEY_BYTES);
  const keyLookup = createHmac('sha256', pepper).update(licenseKey).digest('hex');
  return { saltHex, hashHex: Buffer.from(derived).toString('hex'), keyLookup };
}

export async function verifyLicenseKey(licenseKey, saltHex, expectedHashHex, pepper) {
  const { hashHex } = await hashLicenseKey(licenseKey, pepper, saltHex);
  const actual = Buffer.from(hashHex, 'hex');
  const expected = Buffer.from(expectedHashHex, 'hex');
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

export function issueEntitlementToken({
  licenseRef, deviceBinding, expiresAt, privateKey, now = Date.now(),
  maxTtlSeconds = 24 * 60 * 60
}) {
  const device = validateDeviceBinding(deviceBinding);
  const licenseExpiry = Math.floor(new Date(expiresAt).getTime() / 1000);
  const issued = Math.floor(now / 1000);
  if (!Number.isInteger(maxTtlSeconds) || maxTtlSeconds < 1) {
    throw new Error('토큰 최대 유효기간이 올바르지 않습니다.');
  }
  if (!Number.isFinite(licenseExpiry) || licenseExpiry <= issued) {
    throw new Error('라이선스 만료 시각이 올바르지 않습니다.');
  }
  // A signed offline entitlement must never outlive the short refresh window,
  // even when the underlying license is valid for months or years.
  const expiry = Math.min(licenseExpiry, issued + maxTtlSeconds);
  if (typeof licenseRef !== 'string' || !/^[a-zA-Z0-9._-]{8,128}$/.test(licenseRef)) {
    throw new Error('라이선스 참조 값이 올바르지 않습니다.');
  }
  const payload = [
    'tokenVersion=1',
    'product=guardian',
    'entitlement=pro',
    `issuedAt=${issued}`,
    `expiresAt=${expiry}`,
    `licenseRef=${licenseRef}`,
    `deviceBinding=${device}`
  ].join('\n');
  const signature = rsaSign('RSA-SHA256', Buffer.from(payload, 'utf8'), privateKey).toString('base64');
  return { payload, signature };
}

export function newLicenseKey() {
  const raw = randomBytes(24).toString('hex').toUpperCase();
  return `GDN-${raw.match(/.{1,8}/g).join('-')}`;
}

export function adminTokenMatches(provided, expected) {
  if (typeof provided !== 'string' || typeof expected !== 'string' || !expected) return false;
  const a = Buffer.from(provided);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}
