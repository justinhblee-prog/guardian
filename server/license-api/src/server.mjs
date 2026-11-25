import http from 'node:http';
import { createLicenseApiRuntime } from './license-api-runtime.mjs';

const runtime = createLicenseApiRuntime(process.env);
const { handleNodeRequest, pool } = runtime;
const server = http.createServer(handleNodeRequest);

if (process.env.NODE_ENV !== 'test') {
  server.listen(Number(process.env.PORT || 8787), process.env.HOST || '127.0.0.1', () => {
    console.log(`Guardian License API listening on :${Number(process.env.PORT || 8787)}`);
  });
}

export { server, pool, handleNodeRequest };
