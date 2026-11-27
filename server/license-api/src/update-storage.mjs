import { createHmac } from 'node:crypto';

const AWS_ALGORITHM = 'AWS4-HMAC-SHA256';
const MAX_EXPIRY_SECONDS = 7 * 24 * 60 * 60;

function encode(value) {
  return encodeURIComponent(value).replace(/[!'()*]/g, character =>
    '%' + character.charCodeAt(0).toString(16).toUpperCase()
  );
}
function hmac(key, value, encoding) {
  return createHmac('sha256', key).update(value, 'utf8').digest(encoding);
}
function signingKey(secret, date, region) {
  const dateKey = hmac('AWS4' + secret, date);
  const regionKey = hmac(dateKey, region);
  const serviceKey = hmac(regionKey, 's3');
  return hmac(serviceKey, 'aws4_request');
}

export function createPresignedObjectUrl({
  method = 'GET', bucket, objectKey, expiresInSeconds = 300,
  contentType = null, environment = process.env, now = new Date()
}) {
  const accessKey = environment.AWS_ACCESS_KEY_ID;
  const secretKey = environment.AWS_SECRET_ACCESS_KEY;
  const endpoint = environment.AWS_ENDPOINT_URL_S3;
  const region = environment.AWS_REGION;
  if (![accessKey, secretKey, endpoint, region, bucket, objectKey].every(value =>
    typeof value === 'string' && value.length > 0
  )) throw Object.assign(new Error('비공개 업데이트 저장소가 구성되지 않았습니다.'), { status: 503 });
  if (!['GET', 'PUT'].includes(method)) throw new Error('Unsupported object storage method.');
  if (!Number.isInteger(expiresInSeconds) || expiresInSeconds < 1 || expiresInSeconds > MAX_EXPIRY_SECONDS) {
    throw new Error('Presigned URL expiry is out of range.');
  }
  if (!/^[a-z0-9-]{3,63}$/.test(bucket) ||
      objectKey.startsWith('/') || objectKey.split('/').some(part => !part || part === '.' || part === '..')) {
    throw new Error('Object storage key is invalid.');
  }
  const base = new URL(endpoint);
  if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash) {
    throw new Error('Object storage endpoint is invalid.');
  }
  const path = '/' + [bucket, ...objectKey.split('/')].map(encode).join('/');
  const host = base.host;
  const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, '');
  const date = amzDate.slice(0, 8);
  const scope = date + '/' + region + '/s3/aws4_request';
  const signedHeaders = contentType && method === 'PUT' ? 'content-type;host' : 'host';
  const query = {
    'X-Amz-Algorithm': AWS_ALGORITHM,
    'X-Amz-Credential': accessKey + '/' + scope,
    'X-Amz-Date': amzDate,
    'X-Amz-Expires': String(expiresInSeconds),
    'X-Amz-SignedHeaders': signedHeaders
  };
  const canonicalQuery = Object.entries(query)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => encode(key) + '=' + encode(value)).join('&');
  const canonicalHeaders = contentType && method === 'PUT'
    ? 'content-type:' + contentType.trim() + '\nhost:' + host + '\n'
    : 'host:' + host + '\n';
  const canonicalRequest = [
    method, path, canonicalQuery, canonicalHeaders, signedHeaders, 'UNSIGNED-PAYLOAD'
  ].join('\n');
  const stringToSign = [
    AWS_ALGORITHM, amzDate, scope,
    hmac(Buffer.from(signingKey(secretKey, date, region)), canonicalRequest, 'hex')
  ].join('\n');
  const signature = hmac(signingKey(secretKey, date, region), stringToSign, 'hex');
  const url = new URL(base.origin + path);
  url.search = canonicalQuery + '&X-Amz-Signature=' + signature;
  return {
    url: url.toString(),
    method,
    headers: contentType && method === 'PUT' ? { 'content-type': contentType } : {},
    expiresAt: new Date(now.getTime() + expiresInSeconds * 1000).toISOString()
  };
}
