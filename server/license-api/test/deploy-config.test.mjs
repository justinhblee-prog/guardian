import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { readBoundedIntegerSetting } from '../src/license-api-runtime.mjs';

test('Neon deployment guard reads the Android-pinned license key and API URL', async () => {
  const gradlePath = new URL('../../../android/app/build.gradle.kts', import.meta.url);
  const gradle = await readFile(gradlePath, 'utf8');
  const publicKey = /val guardianLicensePublicKey\s*=\s*[\s\S]*?\.getOrElse\("([A-Za-z0-9+/=]+)"\)/.exec(gradle)?.[1];
  const apiUrl = /val guardianLicenseApiBaseUrl\s*=\s*[\s\S]*?\.getOrElse\("([^"]+)"\)/.exec(gradle)?.[1];

  assert.ok(publicKey, 'Android must define a default license verifier public key.');
  assert.ok(publicKey.length >= 300, 'Android verifier key must be a substantial X.509 RSA key.');
  assert.ok(apiUrl, 'Android must define a default license API URL.');
  assert.ok(apiUrl.startsWith('https://'), 'Android license API URL must use HTTPS.');

  const deployScript = await readFile(new URL('../scripts/deploy-neon-function.mjs', import.meta.url), 'utf8');
  assert.match(deployScript, /process\.env\.GUARDIAN_LICENSE_PUBLIC_KEY \|\| defaultPinnedPublicKey/);
  assert.match(deployScript, /derivedPublicKey !== expectedPublicKey/);
  assert.match(deployScript, /GUARDIAN_LICENSE_PUBLIC_KEY must be a valid Base64 X\.509 RSA public key/);
  assert.match(deployScript, /GUARDIAN_LICENSE_API_BASE_URL !== invocationUrl/);
});

test('license runtime enforces the same RSA signer size ceiling as deployment', async () => {
  const runtime = await readFile(new URL('../src/license-api-runtime.mjs', import.meta.url), 'utf8');
  assert.ok(runtime.includes('(key.asymmetricKeyDetails?.modulusLength || 0) < 2048'));
  assert.ok(runtime.includes('(key.asymmetricKeyDetails?.modulusLength || 0) > 4096'));
});

test('runtime rejects malformed or unsafe operational limit settings', () => {
  assert.equal(readBoundedIntegerSetting({}, 'GUARDIAN_RATE_LIMIT_MAX', 30, 1, 10000), 30);
  assert.equal(readBoundedIntegerSetting({ LIMIT: '250' }, 'LIMIT', 30, 1, 10000), 250);
  for (const value of ['0', '-1', 'NaN', '1.5', '10001']) {
    assert.throws(
      () => readBoundedIntegerSetting({ LIMIT: value }, 'LIMIT', 30, 1, 10000),
      /LIMIT must be an integer between 1 and 10000/
    );
  }
});
