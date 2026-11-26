import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('Neon deployment guard reads the Android-pinned license key and API URL', async () => {
  const gradlePath = new URL('../../../android/app/build.gradle.kts', import.meta.url);
  const gradle = await readFile(gradlePath, 'utf8');
  const publicKey = /val guardianLicensePublicKey\\s*=\\s*[\\s\\S]*?\\.getOrElse\\("([A-Za-z0-9+/=]+)"\\)/.exec(gradle)?.[1];
  const apiUrl = /val guardianLicenseApiBaseUrl\\s*=\\s*[\\s\\S]*?\\.getOrElse\\("([^"]+)"\\)/.exec(gradle)?.[1];

  assert.ok(publicKey, 'Android must define a default license verifier public key.');
  assert.ok(publicKey.length >= 300, 'Android verifier key must be a substantial X.509 RSA key.');
  assert.ok(apiUrl, 'Android must define a default license API URL.');
  assert.match(apiUrl, /^https:\\/\\//, 'Android license API URL must use HTTPS.');

  const deployScript = await readFile(new URL('../scripts/deploy-neon-function.mjs', import.meta.url), 'utf8');
  assert.match(deployScript, /derivedPublicKey !== pinnedPublicKey/);
  assert.match(deployScript, /GUARDIAN_LICENSE_API_BASE_URL !== invocationUrl/);
});
