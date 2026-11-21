import { createSign } from 'node:crypto';

const TOKEN_URI = 'https://oauth2.googleapis.com/token';
const PUBLISHER_SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const PUBLISHER_API = 'https://androidpublisher.googleapis.com/androidpublisher/v3';

export class GooglePlayBillingError extends Error {
  constructor(code, message, status = 503) {
    super(message);
    this.name = 'GooglePlayBillingError';
    this.code = code;
    this.status = status;
  }
}

function base64url(value) {
  return Buffer.from(value).toString('base64url');
}

function readServiceAccount(value) {
  if (typeof value !== 'string' || value.length < 20 || value.length > 32_768) {
    throw new GooglePlayBillingError('billing_not_configured', 'Google Play 결제 검증 서버 설정이 없습니다.');
  }
  let account;
  try {
    account = JSON.parse(value);
  } catch {
    throw new GooglePlayBillingError('billing_credentials_invalid', 'Google Play 결제 검증 서버 설정 형식이 올바르지 않습니다.');
  }
  if (account.type !== 'service_account' ||
      typeof account.client_email !== 'string' ||
      typeof account.private_key !== 'string' ||
      !account.private_key.includes('PRIVATE KEY')) {
    throw new GooglePlayBillingError('billing_credentials_invalid', 'Google Play 서비스 계정 인증 정보가 올바르지 않습니다.');
  }
  return {
    clientEmail: account.client_email,
    privateKey: account.private_key.replace(/\\n/g, '\n')
  };
}

function createAssertion(account, nowSeconds) {
  const header = base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claims = base64url(JSON.stringify({
    iss: account.clientEmail,
    scope: PUBLISHER_SCOPE,
    aud: TOKEN_URI,
    iat: nowSeconds,
    exp: nowSeconds + 3600
  }));
  const unsigned = header + '.' + claims;
  try {
    const signer = createSign('RSA-SHA256');
    signer.update(unsigned);
    signer.end();
    return unsigned + '.' + signer.sign(account.privateKey).toString('base64url');
  } catch {
    throw new GooglePlayBillingError('billing_credentials_invalid', 'Google Play 서비스 계정 서명키를 사용할 수 없습니다.');
  }
}

async function readJson(response, code) {
  try {
    return await response.json();
  } catch {
    throw new GooglePlayBillingError(code, 'Google Play 응답을 해석할 수 없습니다.', 502);
  }
}

function isEntitled(subscription, nowMs) {
  const state = subscription.subscriptionState;
  const allowedState = state === 'SUBSCRIPTION_STATE_ACTIVE' ||
    state === 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD' ||
    state === 'SUBSCRIPTION_STATE_CANCELED';
  if (!allowedState || !Array.isArray(subscription.lineItems)) return false;
  return subscription.lineItems.some(item => {
    const expiry = Date.parse(item.expiryTime || '');
    return Number.isFinite(expiry) && expiry > nowMs;
  });
}

/**
 * Server-only Google Play Developer API verifier.
 * Never call this with client-supplied purchase state; only the purchase token
 * is accepted as an untrusted lookup key and entitlement is derived from the
 * authenticated Publisher API response.
 */
export function createGooglePlayBillingVerifier({
  serviceAccountJson = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON,
  packageName = process.env.GOOGLE_PLAY_PACKAGE_NAME || 'com.guardian.adblock',
  fetchImpl = globalThis.fetch,
  now = () => Date.now()
} = {}) {
  if (typeof fetchImpl !== 'function') {
    throw new GooglePlayBillingError('billing_transport_unavailable', 'Google Play 결제 검증 네트워크 기능을 사용할 수 없습니다.');
  }
  if (typeof packageName !== 'string' || !/^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/.test(packageName)) {
    throw new GooglePlayBillingError('billing_package_invalid', 'Google Play 앱 패키지 설정이 올바르지 않습니다.');
  }
  const account = readServiceAccount(serviceAccountJson);
  let cachedAccessToken = null;
  let tokenExpiresAt = 0;
  let tokenRequest = null;

  async function accessToken() {
    const current = now();
    if (cachedAccessToken && tokenExpiresAt > current + 60_000) return cachedAccessToken;
    if (tokenRequest) return tokenRequest;
    tokenRequest = (async () => {
      const assertion = createAssertion(account, Math.floor(current / 1000));
      let response;
      try {
        response = await fetchImpl(TOKEN_URI, {
          method: 'POST',
          headers: { 'content-type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({
            grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            assertion
          })
        });
      } catch {
        throw new GooglePlayBillingError('billing_oauth_unavailable', 'Google Play 인증 서버에 연결할 수 없습니다.', 502);
      }
      const payload = await readJson(response, 'billing_oauth_invalid_response');
      if (!response.ok || typeof payload.access_token !== 'string' ||
          !Number.isFinite(Number(payload.expires_in)) || Number(payload.expires_in) <= 0) {
        throw new GooglePlayBillingError('billing_oauth_rejected', 'Google Play 서버 인증에 실패했습니다.', 502);
      }
      cachedAccessToken = payload.access_token;
      tokenExpiresAt = current + Number(payload.expires_in) * 1000;
      return cachedAccessToken;
    })();
    try {
      return await tokenRequest;
    } finally {
      tokenRequest = null;
    }
  }

  return Object.freeze({
    async verifySubscription({ purchaseToken, productId }) {
      if (typeof purchaseToken !== 'string' || purchaseToken.length < 1 || purchaseToken.length > 4096) {
        throw new GooglePlayBillingError('purchase_token_invalid', '구매 확인 정보가 올바르지 않습니다.', 400);
      }
      if (typeof productId !== 'string' || !/^[A-Za-z0-9_.-]{1,120}$/.test(productId)) {
        throw new GooglePlayBillingError('product_id_invalid', '구독 상품 정보가 올바르지 않습니다.', 400);
      }
      const token = await accessToken();
      const url = PUBLISHER_API + '/applications/' + encodeURIComponent(packageName) +
        '/purchases/subscriptionsv2/tokens/' + encodeURIComponent(purchaseToken);
      let response;
      try {
        response = await fetchImpl(url, {
          method: 'GET',
          headers: { authorization: 'Bearer ' + token, accept: 'application/json' }
        });
      } catch {
        throw new GooglePlayBillingError('billing_publisher_unavailable', 'Google Play 구매 정보를 조회할 수 없습니다.', 502);
      }
      if (response.status === 404 || response.status === 400) {
        throw new GooglePlayBillingError('purchase_not_found', 'Google Play에서 유효한 구매 정보를 찾을 수 없습니다.', 403);
      }
      const subscription = await readJson(response, 'billing_publisher_invalid_response');
      if (!response.ok) {
        throw new GooglePlayBillingError('billing_publisher_rejected', 'Google Play 구매 검증에 실패했습니다.', 502);
      }
      const matchingLineItems = Array.isArray(subscription.lineItems)
        ? subscription.lineItems.filter(item => item.productId === productId)
        : [];
      if (matchingLineItems.length === 0) {
        throw new GooglePlayBillingError('purchase_product_mismatch', '구매한 상품이 요청한 Guardian 구독과 일치하지 않습니다.', 403);
      }
      const matched = { ...subscription, lineItems: matchingLineItems };
      const expiryTime = matchingLineItems
        .map(item => Date.parse(item.expiryTime || ''))
        .filter(Number.isFinite)
        .reduce((latest, value) => Math.max(latest, value), 0);
      return Object.freeze({
        verified: true,
        entitled: isEntitled(matched, now()),
        productId,
        subscriptionState: typeof subscription.subscriptionState === 'string'
          ? subscription.subscriptionState : 'SUBSCRIPTION_STATE_UNSPECIFIED',
        expiryTime: expiryTime > 0 ? new Date(expiryTime).toISOString() : null,
        orderId: typeof matchingLineItems[0].latestSuccessfulOrderId === 'string'
          ? matchingLineItems[0].latestSuccessfulOrderId : null,
        acknowledgementState: typeof subscription.acknowledgementState === 'string'
          ? subscription.acknowledgementState : 'ACKNOWLEDGEMENT_STATE_UNSPECIFIED'
      });
    }
  });
}
