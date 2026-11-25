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
  const licenseExpiry = expiresAt === null || expiresAt === undefined ? Number.POSITIVE_INFINITY : Math.floor(new Date(expiresAt).getTime() / 1000);
  const issued = Math.floor(now / 1000);
  if (!Number.isInteger(maxTtlSeconds) || maxTtlSeconds < 1) {
    throw new Error('토큰 최대 유효기간이 올바르지 않습니다.');
  }
  if (Number.isNaN(licenseExpiry) || licenseExpiry <= issued) {
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
