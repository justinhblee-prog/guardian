import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash, createHmac } from 'node:crypto';
import { createPresignedObjectUrl } from '../src/update-storage.mjs';

const environment = {
  AWS_ACCESS_KEY_ID: 'AKIDEXAMPLE',
  AWS_SECRET_ACCESS_KEY: 'wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY',
  AWS_ENDPOINT_URL_S3: 'https://storage.example.test/',
  AWS_REGION: 'ap-southeast-1'
};
const now = new Date('2026-11-27T12:34:56.000Z');

test('private update object GET URL is signed, short-lived and scoped to one bucket/key', () => {
  const result = createPresignedObjectUrl({
    bucket: 'guardian-private-updates',
    objectKey: 'android/dev/74-a'.padEnd(64, 'a') + '.apk',
    expiresInSeconds: 300,
    environment,
    now
  });
  const url = new URL(result.url);
  assert.equal(result.method, 'GET');
  assert.equal(result.headers && Object.keys(result.headers).length, 0);
  assert.equal(url.protocol, 'https:');
  assert.equal(url.host, 'storage.example.test');
  assert.match(url.pathname, /^\/guardian-private-updates\/android\/dev\//);
  assert.equal(url.searchParams.get('X-Amz-Algorithm'), 'AWS4-HMAC-SHA256');
  assert.equal(url.searchParams.get('X-Amz-Date'), '20261127T123456Z');
  assert.equal(url.searchParams.get('X-Amz-Expires'), '300');
  assert.equal(url.searchParams.get('X-Amz-SignedHeaders'), 'host');
  assert.match(url.searchParams.get('X-Amz-Signature'), /^[a-f0-9]{64}$/);
  // Independently reconstruct AWS SigV4; the canonical request uses SHA-256.
  const encode = value => encodeURIComponent(value).replace(/[!'()*]/g, ch =>
    '%' + ch.charCodeAt(0).toString(16).toUpperCase()
  );
  const query = [...url.searchParams.entries()]
    .filter(([key]) => key !== 'X-Amz-Signature')
    .sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
    .map(([key, value]) => encode(key) + '=' + encode(value)).join('&');
  const canonicalRequest = [
    'GET', url.pathname, query, 'host:' + url.host + '\\n',
    'host', 'UNSIGNED-PAYLOAD'
  ].join('\\n');
  const dateKey = createHmac('sha256', 'AWS4' + environment.AWS_SECRET_ACCESS_KEY).update('20261127').digest();
  const regionKey = createHmac('sha256', dateKey).update(environment.AWS_REGION).digest();
  const serviceKey = createHmac('sha256', regionKey).update('s3').digest();
  const signingKey = createHmac('sha256', serviceKey).update('aws4_request').digest();
  const scope = '20261127/' + environment.AWS_REGION + '/s3/aws4_request';
  const stringToSign = [
    'AWS4-HMAC-SHA256', '20261127T123456Z', scope,
    createHash('sha256').update(canonicalRequest).digest('hex')
  ].join('\\n');
  const expectedSignature = createHmac('sha256', signingKey).update(stringToSign).digest('hex');
  assert.equal(url.searchParams.get('X-Amz-Signature'), expectedSignature);
  assert.equal(result.expiresAt, '2026-11-27T12:39:56.000Z');
});

test('private update PUT URL binds APK content type and upload method', () => {
  const result = createPresignedObjectUrl({
    method: 'PUT',
    bucket: 'guardian-private-updates',
    objectKey: 'android/beta/80-release.apk',
    contentType: 'application/vnd.android.package-archive',
    expiresInSeconds: 600,
    environment,
    now
  });
  const url = new URL(result.url);
  assert.equal(result.method, 'PUT');
  assert.deepEqual(result.headers, { 'content-type': 'application/vnd.android.package-archive' });
  assert.equal(url.searchParams.get('X-Amz-SignedHeaders'), 'content-type;host');
});

test('private update URLs fail closed without storage credentials', () => {
  assert.throws(() => createPresignedObjectUrl({
    bucket: 'guardian-private-updates',
    objectKey: 'android/dev/74.apk',
    environment: {}
  }), /비공개 업데이트 저장소/);
});

test('private update URLs reject unsafe keys, public HTTP endpoints and excessive expiry', () => {
  assert.throws(() => createPresignedObjectUrl({
    bucket: 'guardian-private-updates', objectKey: '../latest.apk', environment
  }), /Object storage key/);
  assert.throws(() => createPresignedObjectUrl({
    bucket: 'guardian-private-updates', objectKey: 'android/dev/a.apk',
    environment: { ...environment, AWS_ENDPOINT_URL_S3: 'http://storage.example.test/' }
  }), /endpoint/);
  assert.throws(() => createPresignedObjectUrl({
    bucket: 'guardian-private-updates', objectKey: 'android/dev/a.apk',
    expiresInSeconds: 604801, environment
  }), /expiry/);
});
