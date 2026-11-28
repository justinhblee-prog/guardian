import { defineConfig } from '@neon/config/v1';

export default defineConfig({
  functions: {
    guardianapi: {
      name: 'Guardian License API',
      source: './functions/guardianapi/index.mjs',
      env: {
        GUARDIAN_ADMIN_TOKEN: { value: process.env.GUARDIAN_ADMIN_TOKEN, required: true },
        GUARDIAN_LICENSE_LOOKUP_SECRET: { value: process.env.GUARDIAN_LICENSE_LOOKUP_SECRET, required: true },
        GUARDIAN_LICENSE_PRIVATE_KEY: { value: process.env.GUARDIAN_LICENSE_PRIVATE_KEY, required: true },
        PG_POOL_MAX: { value: process.env.PG_POOL_MAX || '5' },
        GUARDIAN_RATE_LIMIT_WINDOW_MS: { value: process.env.GUARDIAN_RATE_LIMIT_WINDOW_MS || '60000' },
        GUARDIAN_RATE_LIMIT_MAX: { value: process.env.GUARDIAN_RATE_LIMIT_MAX || '30' }
      }
    }
  },
  branch: () => ({
    functions: {
      guardianapi: { runtime: 'nodejs24' }
    }
  })
});
