import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { pathToFileURL } from 'node:url';

export async function createGuardianUpdateManifest({
  apkPath,
  signatureReportPath,
  versionName,
  versionCode,
  outputPath
}) {
  if (typeof versionName !== 'string' || !/^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/.test(versionName)) {
    throw new Error('versionName must be a stable semantic version in x.y.z form.');
  }
  const parsedVersionCode = Number(versionCode);
  if (!Number.isSafeInteger(parsedVersionCode) || parsedVersionCode < 1 || parsedVersionCode > 2147483647) {
    throw new Error('versionCode must be a positive safe integer.');
  }

  const [apk, signatureReport] = await Promise.all([
    readFile(apkPath),
    readFile(signatureReportPath, 'utf8')
  ]);
  if (!apk.length) throw new Error('APK file is empty.');
  const signerMatch = signatureReport.match(/Signer\s+#1\s+certificate SHA-256 digest:\s*([a-fA-F0-9:]{64,95})/);
  if (!signerMatch) throw new Error('APK signature report does not contain signer #1 SHA-256 digest.');
  const signerSha256 = signerMatch[1].replaceAll(':', '').toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(signerSha256)) {
    throw new Error('APK signer SHA-256 digest is malformed.');
  }

  const manifest = {
    manifestVersion: 1,
    packageName: 'com.guardian.adblock',
    versionName,
    versionCode: parsedVersionCode,
    sha256: createHash('sha256').update(apk).digest('hex'),
    signerSha256
  };
  await writeFile(outputPath, JSON.stringify(manifest, null, 2) + String.fromCharCode(10), { mode: 0o600 });
  return manifest;
}

async function main() {
  const [apkPath, signatureReportPath, versionName, versionCode, outputPath] = process.argv.slice(2);
  if (![apkPath, signatureReportPath, versionName, versionCode, outputPath].every(Boolean)) {
    throw new Error('Usage: create-guardian-update-manifest.mjs <apk> <signature-report> <version-name> <version-code> <output-json>');
  }
  const manifest = await createGuardianUpdateManifest({
    apkPath: path.resolve(apkPath),
    signatureReportPath: path.resolve(signatureReportPath),
    versionName,
    versionCode,
    outputPath: path.resolve(outputPath)
  });
  process.stdout.write(
    'Stable update manifest created: ' + manifest.versionName + ' (' +
    manifest.versionCode + '), APK SHA-256 ' + manifest.sha256 + '\\n'
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch(error => {
    process.stderr.write(error.message + '\\n');
    process.exitCode = 1;
  });
}
