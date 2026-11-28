import { Buffer } from 'node:buffer';

const BODY_LIMIT = 16 * 1024;

export async function handleFetchRequest(request, runtime, remoteAddress = 'unknown') {
  const url = new URL(request.url);
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

  const req = {
    method: request.method,
    url: url.pathname + url.search,
    headers: Object.fromEntries(request.headers.entries()),
    socket: { remoteAddress },
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

  await runtime.handleNodeRequest(req, res);
  return new Response(responseBody ?? null, { status, headers: headersOut });
}
