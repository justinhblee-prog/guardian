import { attachDatabasePool } from '@neon/functions';
import { createLicenseApiRuntime } from '../../src/license-api-runtime.mjs';
import { handleFetchRequest } from '../../src/fetch-adapter.mjs';
import { adminAssets } from '../../src/admin-assets.mjs';

export function createNeonLicenseHandler(environment = process.env) {
  const databaseUrl = environment.DATABASE_URL_UNPOOLED || environment.DATABASE_URL;
  const runtime = createLicenseApiRuntime({ ...environment, DATABASE_URL: databaseUrl }, { adminAssets });
  if (runtime.pool && environment.NEON_BRANCH) attachDatabasePool(runtime.pool);

  return {
    async fetch(request) {
      // Neon Functions runs behind a managed proxy. Use the final proxy-appended
      // address in X-Forwarded-For, never the caller-controlled first entry.
      const forwarded = request.headers.get('x-forwarded-for') || '';
      const addresses = forwarded.split(',').map(value => value.trim()).filter(Boolean);
      const remoteAddress = addresses.at(-1) || 'unknown';
      return handleFetchRequest(request, runtime, remoteAddress);
    }
  };
}

const handler = createNeonLicenseHandler();
export default handler;
