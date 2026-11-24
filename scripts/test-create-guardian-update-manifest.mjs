import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createGuardianUpdateManifest } from './create-guardian-update-manifest.mjs';

test('creates stable manifest from the actual APK bytes and signer report', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'guardian-update-manifest-'));
  try {
    const apkPath = path.join(directory, 'guardian.apk');
    const signaturePath = path.join(directory, 'signature.txt');
    const outputPath = path.join(directory, 'guardian-android.json');
    const apk = Buffer.from('synthetic-test-apk-content');
    const signer = 'ab'.repeat(32);
    await writeFile(apkPath, apk);
    await writeFile(signaturePath, 'Signer #1 certificate SHA-256 digest: ' + signer.match(/../g).join(':') + '\\n');
    const manifest = await createGuardianUpdateManifest({
      apkPath, signatureReportPath: signaturePath,
      versionName: '1.2.3', versionCode: 42, outputPath
    });
    assert.equal(manifest.manifestVersion, 1);
    assert.equal(manifest.packageName, 'com.guardian.adblock');
    assert.equal(manifest.versionName, '1.2.3');
    assert.equal(manifest.versionCode, 42);
    assert.equal(manifest.sha256, (await import('node:crypto')).createHash('sha256').update(apk).digest('hex'));
    assert.equal(manifest.signerSha256, signer);
    assert.deepEqual(JSON.parse(await readFile(outputPath, 'utf8')), manifest);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('rejects prerelease version names and invalid version codes', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'guardian-update-manifest-'));
  try {
    const apkPath = path.join(directory, 'guardian.apk');
    const signaturePath = path.join(directory, 'signature.txt');
    const outputPath = path.join(directory, 'out.json');
    await writeFile(apkPath, 'apk');
    await writeFile(signaturePath, 'Signer #1 certificate SHA-256 digest: ' + 'a'.repeat(64));
    await assert.rejects(createGuardianUpdateManifest({
      apkPath, signatureReportPath: signaturePath, versionName: '1.0.0-rc1',
      versionCode: 1, outputPath
    }), /stable semantic version/);
    for (const versionName of ['01.2.3', '1.02.3', '1.2.03']) {
      await assert.rejects(createGuardianUpdateManifest({
        apkPath, signatureReportPath: signaturePath, versionName,
        versionCode: 1, outputPath
      }), /stable semantic version/);
    }
    await assert.rejects(createGuardianUpdateManifest({
      apkPath, signatureReportPath: signaturePath, versionName: '1.0.0',
      versionCode: 0, outputPath
    }), /positive safe integer/);
    await assert.rejects(createGuardianUpdateManifest({
      apkPath, signatureReportPath: signaturePath, versionName: '1.0.0',
      versionCode: 2147483648, outputPath
    }), /positive safe integer/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('rejects missing signer fingerprint and empty APK', async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'guardian-update-manifest-'));
  try {
    const apkPath = path.join(directory, 'guardian.apk');
    const signaturePath = path.join(directory, 'signature.txt');
    const outputPath = path.join(directory, 'out.json');
    await writeFile(apkPath, 'apk');
    await writeFile(signaturePath, 'No signer details');
    await assert.rejects(createGuardianUpdateManifest({
      apkPath, signatureReportPath: signaturePath, versionName: '1.0.0',
      versionCode: 1, outputPath
    }), /signer #1 SHA-256/);
    await writeFile(signaturePath, 'Signer #1 certificate SHA-256 digest: ' + 'a'.repeat(64));
    await writeFile(apkPath, '');
    await assert.rejects(createGuardianUpdateManifest({
      apkPath, signatureReportPath: signaturePath, versionName: '1.0.0',
      versionCode: 1, outputPath
    }), /APK file is empty/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
