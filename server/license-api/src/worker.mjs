import { Buffer } from 'node:buffer';
import { createLicenseApiRuntime } from './license-api-runtime.mjs';

const BODY_LIMIT = 16 * 1024;

function responseFromNodeShim(status, headers, body) {
  return new Response(body ?? null, { status, headers });
}

export async function handleWorkerRequest(request, env) {
  const url = new URL(request.url);

  // The existing admin console remains static and is served by the Workers
  // Assets binding; API and health routes are handled by the shared runtime.
  if ((url.pathname === '/admin' || url.pathname === '/admin/') && env.ASSETS) {
    return env.ASSETS.fetch(new Request(new URL('/index.html', url), request));
  }
  if (url.pathname === '/admin.js' && env.ASSETS) {
    return env.ASSETS.fetch(new Request(new URL('/admin.js', url), request));
  }

  const declaredLength = Number(request.headers.get('content-length') || 0);
  if (declaredLength > BODY_LIMIT) {
    return Response.json({ error: '요청 크기가 너무 큽니다.' }, { status: 413 });
  }

  let bodyBytes = new Uint8Array();
  if (request.method !== 'GET' && request.method !== 'HEAD' && request.body) {
    const reader = request.body.getReader();
    const chunks = [];
    let size = 0;
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > BODY_LIMIT) {
          await reader.cancel();
          return Response.json({ error: '요청 크기가 너무 큽니다.' }, { status: 413 });
        }
        chunks.push(value);
      }
    } finally {
      reader.releaseLock();
    }
    bodyBytes = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) {
      bodyBytes.set(chunk, offset);
      offset += chunk.byteLength;
    }
  }

  const headersIn = Object.fromEntries(request.headers.entries());
  const req = {
    method: request.method,
    url: url.pathname + url.search,
    headers: headersIn,
    socket: { remoteAddress: request.headers.get('CF-Connecting-IP') || 'unknown' },
    async *[Symbol.asyncIterator]() {
      if (bodyBytes.byteLength) yield Buffer.from(bodyBytes);
    }
  };

  let status = 200;
  const headersOut = new Headers();
  let responseBody = null;
  const res = {
    writeHead(code, headers = {}) {
      status = code;
      for (const [name, value] of Object.entries(headers)) headersOut.set(name, String(value));
      return this;
    },
    setHeader(name, value) {
      headersOut.set(name, String(value));
      return this;
    },
    end(body = null) {
      responseBody = body;
      return this;
    }
  };

  const runtime = createLicenseApiRuntime({
    NODE_ENV: 'test',
    PORT: '8787',
    DATABASE_URL: env.HYPERDRIVE?.connectionString,
    GUARDIAN_ADMIN_TOKEN: env.GUARDIAN_ADMIN_TOKEN,
    GUARDIAN_LICENSE_LOOKUP_SECRET: env.GUARDIAN_LICENSE_LOOKUP_SECRET,
    GUARDIAN_LICENSE_PRIVATE_KEY: env.GUARDIAN_LICENSE_PRIVATE_KEY,
    GUARDIAN_RATE_LIMIT_WINDOW_MS: env.GUARDIAN_RATE_LIMIT_WINDOW_MS || '60000',
    GUARDIAN_RATE_LIMIT_MAX: env.GUARDIAN_RATE_LIMIT_MAX || '30'
  });

  await runtime.handleNodeRequest(req, res);
  return responseFromNodeShim(status, headersOut, responseBody);
}

export default {
  fetch(request, env) {
    return handleWorkerRequest(request, env);
  }
};
