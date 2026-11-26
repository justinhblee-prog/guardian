import { createPrivateKey, createPublicKey } from 'node:crypto';
import { readFile, stat } from 'node:fs/promises';
import { basename, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const required = [
  'NEON_API_KEY',
  'NEON_PROJECT_ID',
  'NEON_BRANCH_ID',
  'GUARDIAN_ADMIN_TOKEN',
  'GUARDIAN_LICENSE_LOOKUP_SECRET',
  'GUARDIAN_LICENSE_PRIVATE_KEY'
];

function requiredValue(name) {
  const value = process.env[name];
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`Required deployment configuration is missing: ${name}.`);
  }
  return value.trim();
}

async function validateSecrets() {
  const values = Object.fromEntries(required.map(name => [name, requiredValue(name)]));
  if (values.GUARDIAN_ADMIN_TOKEN.length < 32) {
    throw new Error('GUARDIAN_ADMIN_TOKEN must contain at least 32 characters.');
  }
  if (values.GUARDIAN_LICENSE_LOOKUP_SECRET.length < 32) {
    throw new Error('GUARDIAN_LICENSE_LOOKUP_SECRET must contain at least 32 characters.');
  }

  const pem = values.GUARDIAN_LICENSE_PRIVATE_KEY.replace(/\\n/g, '\n');
  const key = createPrivateKey(pem);
  const bits = key.asymmetricKeyDetails?.modulusLength ?? 0;
  if (key.asymmetricKeyType !== 'rsa' || bits < 2048 || bits > 4096) {
    throw new Error('GUARDIAN_LICENSE_PRIVATE_KEY must be an RSA key between 2048 and 4096 bits.');
  }

  // Bind the deployed signer to the key actually pinned by the Android source.
  // A syntactically valid but unrelated RSA key would make every license fail
  // verification on already-installed clients.
  const gradlePath = resolve(process.cwd(), '../../android/app/build.gradle.kts');
  const gradleSource = await readFile(gradlePath, 'utf8');
  const pinnedMatch = /val guardianLicensePublicKey\s*=\s*[\s\S]*?\.getOrElse\("([A-Za-z0-9+/=]+)"\)/.exec(gradleSource);
  if (!pinnedMatch) throw new Error('Could not read the default license public key pinned in Android build configuration.');
  const pinnedPublicKey = pinnedMatch[1];
  const derivedPublicKey = createPublicKey(key).export({ type: 'spki', format: 'der' }).toString('base64');
  if (derivedPublicKey !== pinnedPublicKey) {
    throw new Error('License private key does not match the public key pinned in the Android source. Refusing deployment.');
  }
  if (process.env.GUARDIAN_LICENSE_PUBLIC_KEY &&
      process.env.GUARDIAN_LICENSE_PUBLIC_KEY.trim() !== pinnedPublicKey) {
    throw new Error('GUARDIAN_LICENSE_PUBLIC_KEY repository variable differs from the Android-pinned public key.');
  }

  values.GUARDIAN_LICENSE_PRIVATE_KEY = pem;
  return values;
}

async function neonRequest(url, apiKey, init = {}) {
  const response = await fetch(url, {
    ...init,
    headers: {
      authorization: `Bearer ${apiKey}`,
      ...(init.body instanceof FormData ? {} : { accept: 'application/json' }),
      ...init.headers
    },
    signal: AbortSignal.timeout(30_000)
  });
  if (!response.ok) {
    // Never print request bodies or secret-bearing environment values.
    throw new Error(`Neon API request failed with HTTP ${response.status}.`);
  }
  return response.json();
}

async function main() {
  const values = await validateSecrets();
  const zipPath = resolve(process.argv[2] || '');
  if (!process.argv[2]) throw new Error('Usage: node scripts/deploy-neon-function.mjs <function-bundle.zip>');
  const zipInfo = await stat(zipPath);
  if (!zipInfo.isFile() || zipInfo.size < 100) throw new Error('Function bundle ZIP is missing or unexpectedly small.');
  const zipBytes = await readFile(zipPath);

  const base = `https://console.neon.tech/api/v2/projects/${encodeURIComponent(values.NEON_PROJECT_ID)}/branches/${encodeURIComponent(values.NEON_BRANCH_ID)}/functions/guardianapi`;
  const environment = {
    GUARDIAN_ADMIN_TOKEN: values.GUARDIAN_ADMIN_TOKEN,
    GUARDIAN_LICENSE_LOOKUP_SECRET: values.GUARDIAN_LICENSE_LOOKUP_SECRET,
    GUARDIAN_LICENSE_PRIVATE_KEY: values.GUARDIAN_LICENSE_PRIVATE_KEY,
    PG_POOL_MAX: '5',
    GUARDIAN_RATE_LIMIT_WINDOW_MS: '60000',
    GUARDIAN_RATE_LIMIT_MAX: '30'
  };

  const deploymentRequestedAt = Date.now();
  const form = new FormData();
  form.append('zip', new Blob([zipBytes], { type: 'application/zip' }), basename(zipPath));
  form.append('runtime', 'nodejs24');
  form.append('environment', JSON.stringify(environment));
  const requested = await neonRequest(`${base}/deployments`, values.NEON_API_KEY, {
    method: 'POST',
    body: form
  });
  const deploymentId = requested.id ?? requested.deployment?.id ?? requested.data?.id;
  if (deploymentId !== undefined) {
    console.log(`Neon accepted Guardian API deployment #${deploymentId}.`);
  } else {
    console.log('Neon accepted Guardian API deployment; checking current deployment state.');
  }

  let functionInfo;
  let current;
  let matchingDeployment = false;
  const deadline = Date.now() + 180_000;
  while (Date.now() < deadline) {
    functionInfo = await neonRequest(base, values.NEON_API_KEY);
    current = functionInfo.current_deployment ?? functionInfo.function?.current_deployment;
    matchingDeployment = deploymentId !== undefined
      ? String(current?.id) === String(deploymentId)
      : Number.isFinite(Date.parse(current?.created_at ?? '')) &&
        Date.parse(current.created_at) >= deploymentRequestedAt - 10_000;
    if (matchingDeployment && current?.status === 'completed') break;
    if (matchingDeployment && current?.status === 'failed') {
      throw new Error(`Neon reports deployment failed (deployment ${current.id ?? 'unknown'}).`);
    }
    await delay(5_000);
  }

  if (!matchingDeployment || current?.status !== 'completed') {
    throw new Error(`Requested Neon deployment did not reach completed status within 180 seconds (last status: ${current?.status ?? 'unknown'}).`);
  }
  const invocationUrl = functionInfo.invocation_url ?? functionInfo.function?.invocation_url;
  if (typeof invocationUrl !== 'string' || !invocationUrl.startsWith('https://')) {
    throw new Error('Neon reports a completed deployment but did not return an HTTPS invocation URL.');
  }

  const functionOrigin = invocationUrl.endsWith('/') ? invocationUrl : `${invocationUrl}/`;
  for (const path of ['health', 'ready']) {
    let verified = false;
    for (let attempt = 1; attempt <= 6; attempt += 1) {
      const response = await fetch(new URL(path, functionOrigin), {
        headers: { accept: 'application/json' },
        signal: AbortSignal.timeout(15_000)
      });
      if (response.ok) {
        if (path === 'health') {
          verified = true;
        } else {
          const payload = await response.json();
          verified = payload?.ok === true;
        }
        if (verified) break;
      }
      if (attempt < 6) await delay(5_000);
    }
    if (!verified) {
      throw new Error(`Neon deployment completed, but /${path} did not pass its live check. Production API remains unverified.`);
    }
  }

  console.log('Guardian License API deployment completed; /health and /ready returned successful responses.');
  console.log(`Invocation URL: ${invocationUrl}`);
}

main().catch(error => {
  console.error(`Guardian Neon deployment stopped safely: ${error.message}`);
  process.exitCode = 1;
});
