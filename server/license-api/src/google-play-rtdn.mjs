import { createPublicKey, verify as verifySignature } from 'node:crypto';

const GOOGLE_CERTS_URI = 'https://www.googleapis.com/oauth2/v1/certs';

export class GooglePlayRtdnAuthError extends Error {
  constructor(code, message, status = 401) {
    super(message);
    this.name = 'GooglePlayRtdnAuthError';
    this.code = code;
    this.status = status;
  }
}

function decodeSegment(value, label) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]+$/.test(value)) {
    throw new GooglePlayRtdnAuthError('rtdn_jwt_invalid', 'Google Play 알림 인증 토큰 형식이 올바르지 않습니다.');
  }
  try {
    return JSON.parse(Buffer.from(value, 'base64url').toString('utf8'));
  } catch {
    throw new GooglePlayRtdnAuthError('rtdn_jwt_invalid', 'Google Play 알림 인증 토큰을 해석할 수 없습니다.');
  }
}

function maxAgeMs(headers) {
  const value = headers?.get?.('cache-control') || '';
  const match = /(?:^|,\s*)max-age=(\d+)/i.exec(value);
  const seconds = match ? Number(match[1]) : 300;
  return Math.min(Math.max(seconds, 60), 3600) * 1000;
}

/**
 * Validates authenticated Pub/Sub push OIDC tokens offline using Google's
 * rotating public certificates. Audience and service-account email are
 * mandatory; a valid Google signature alone is not sufficient authorization.
 */
export function createGooglePlayRtdnVerifier({
  audience = process.env.GOOGLE_PLAY_RTDN_AUDIENCE,
  serviceAccountEmail = process.env.GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL,
  fetchImpl = globalThis.fetch,
  now = () => Date.now()
} = {}) {
  let certificates = null;
  let certificatesExpireAt = 0;
  let certificateRequest = null;

  async function getCertificates() {
    const current = now();
    if (certificates && certificatesExpireAt > current) return certificates;
    if (certificateRequest) return certificateRequest;
    certificateRequest = (async () => {
      let response;
      try {
        response = await fetchImpl(GOOGLE_CERTS_URI, { headers: { accept: 'application/json' } });
      } catch {
        throw new GooglePlayRtdnAuthError('rtdn_certificates_unavailable', 'Google 알림 서명 인증서를 가져올 수 없습니다.', 503);
      }
      if (!response.ok) {
        throw new GooglePlayRtdnAuthError('rtdn_certificates_unavailable', 'Google 알림 서명 인증서를 가져올 수 없습니다.', 503);
      }
      let body;
      try {
        body = await response.json();
      } catch {
        throw new GooglePlayRtdnAuthError('rtdn_certificates_invalid', 'Google 알림 서명 인증서 응답이 올바르지 않습니다.', 503);
      }
      if (!body || typeof body !== 'object' || Array.isArray(body) ||
          Object.keys(body).length === 0 || Object.keys(body).length > 32) {
        throw new GooglePlayRtdnAuthError('rtdn_certificates_invalid', 'Google 알림 서명 인증서 응답이 올바르지 않습니다.', 503);
      }
      for (const [kid, pem] of Object.entries(body)) {
        if (!/^[A-Za-z0-9_-]{1,128}$/.test(kid) || typeof pem !== 'string' || (!pem.includes('BEGIN CERTIFICATE') && !pem.includes('BEGIN PUBLIC KEY'))) {
          throw new GooglePlayRtdnAuthError('rtdn_certificates_invalid', 'Google 알림 서명 인증서 응답이 올바르지 않습니다.', 503);
        }
      }
      certificates = body;
      certificatesExpireAt = current + maxAgeMs(response.headers);
      return certificates;
    })();
    try {
      return await certificateRequest;
    } finally {
      certificateRequest = null;
    }
  }

  return Object.freeze({
    async verifyAuthorization(authorization) {
      if (typeof audience !== 'string' || !audience.trim() ||
          typeof serviceAccountEmail !== 'string' ||
          !/^[^\s@]+@[^\s@]+\.iam\.gserviceaccount\.com$/.test(serviceAccountEmail)) {
        throw new GooglePlayRtdnAuthError('rtdn_not_configured', 'Google Play 실시간 알림 인증 설정이 없습니다.', 503);
      }
      if (typeof authorization !== 'string' || authorization.length > 16_384) {
        throw new GooglePlayRtdnAuthError('rtdn_authorization_missing', 'Google Play 실시간 알림 인증이 필요합니다.');
      }
      const match = /^Bearer\s+([A-Za-z0-9._-]+)$/i.exec(authorization);
      if (!match) {
        throw new GooglePlayRtdnAuthError('rtdn_authorization_invalid', 'Google Play 실시간 알림 인증 형식이 올바르지 않습니다.');
      }
      const parts = match[1].split('.');
      if (parts.length !== 3) {
        throw new GooglePlayRtdnAuthError('rtdn_jwt_invalid', 'Google Play 알림 인증 토큰 형식이 올바르지 않습니다.');
      }
      const header = decodeSegment(parts[0], 'header');
      const claims = decodeSegment(parts[1], 'claims');
      if (header.alg !== 'RS256' || typeof header.kid !== 'string') {
        throw new GooglePlayRtdnAuthError('rtdn_jwt_algorithm_invalid', 'Google Play 알림 인증 알고리즘이 올바르지 않습니다.');
      }
      const currentSeconds = Math.floor(now() / 1000);
      const issuedAt = Number(claims.iat);
      const expiresAt = Number(claims.exp);
      const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
      if (!['https://accounts.google.com', 'accounts.google.com'].includes(claims.iss) ||
          !audiences.includes(audience) ||
          claims.email !== serviceAccountEmail ||
          claims.email_verified !== true ||
          !Number.isSafeInteger(issuedAt) || !Number.isSafeInteger(expiresAt) ||
          issuedAt > currentSeconds + 60 || expiresAt <= currentSeconds ||
          expiresAt <= issuedAt || expiresAt - issuedAt > 3600) {
        throw new GooglePlayRtdnAuthError('rtdn_claims_invalid', 'Google Play 알림 인증 정보가 일치하지 않습니다.');
      }
      const certs = await getCertificates();
      const certificate = certs[header.kid];
      if (!certificate) {
        // Refresh once for key rotation before rejecting an unknown kid.
        certificatesExpireAt = 0;
        const refreshed = await getCertificates();
        if (!refreshed[header.kid]) {
          throw new GooglePlayRtdnAuthError('rtdn_signing_key_unknown', 'Google 알림 서명 키를 확인할 수 없습니다.');
        }
      }
      const pem = certificates[header.kid];
      let publicKey;
      try {
        publicKey = createPublicKey(pem);
      } catch {
        throw new GooglePlayRtdnAuthError('rtdn_certificates_invalid', 'Google 알림 공개 인증서가 올바르지 않습니다.', 503);
      }
      const unsigned = parts[0] + '.' + parts[1];
      let valid = false;
      try {
        valid = verifySignature(
          'RSA-SHA256',
          Buffer.from(unsigned, 'ascii'),
          publicKey,
          Buffer.from(parts[2], 'base64url')
        );
      } catch {
        valid = false;
      }
      if (!valid) {
        throw new GooglePlayRtdnAuthError('rtdn_signature_invalid', 'Google Play 알림 서명을 검증하지 못했습니다.');
      }
      return Object.freeze({
        email: claims.email,
        audience,
        issuedAt,
        expiresAt
      });
    }
  });
}
