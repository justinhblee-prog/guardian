import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';

const PORT = Number(process.env.PORT || 8788);
const DIST_DIR = path.resolve(process.env.GUARDIAN_FILTER_DIST_DIR || 'dist');
const MAX_MANIFEST_BYTES = 16 * 1024;
const MAX_SIGNATURE_BYTES = 4096;
const MAX_FILTER_BYTES = 10 * 1024 * 1024;

function send(res, status, body, headers = {}) {
  res.writeHead(status, {
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    ...headers,
    'content-length': Buffer.byteLength(body)
  });
  res.end(body);
}

async function readBounded(filePath, maxBytes) {
  const info = await stat(filePath);
  if (!info.isFile() || info.size <= 0 || info.size > maxBytes) {
    throw Object.assign(new Error('Invalid distribution asset size.'), { status: 404 });
  }
  return readFile(filePath);
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method !== 'GET') {
      return send(res, 405, 'method_not_allowed', { allow: 'GET' });
    }
    const url = new URL(req.url, 'http://localhost');
    if (url.search || url.hash) return send(res, 400, 'invalid_request');

    if (url.pathname === '/health') {
      return send(res, 200, JSON.stringify({ ok: true, service: 'guardian-filter-distribution' }), {
        'content-type': 'application/json; charset=utf-8'
      });
    }

    const manifestMatch = /^\/([a-zA-Z0-9._-]{1,64})\.manifest$/.exec(url.pathname);
    if (manifestMatch) {
      const sourceId = manifestMatch[1];
      const manifestPath = path.join(DIST_DIR, `${sourceId}.manifest`);
      const manifest = await readBounded(manifestPath, MAX_MANIFEST_BYTES);
      const manifestText = manifest.toString('utf8');
      const sourceMatch = /^sourceId=([a-zA-Z0-9._-]{1,64})$/m.exec(manifestText);
      const versionMatch = /^sourceVersion=([a-zA-Z0-9._+-]{1,64})$/m.exec(manifestText);
      const generatedMatch = /^generatedAt=(\\d+)$/m.exec(manifestText);
      if (!sourceMatch || sourceMatch[1] !== sourceId || !versionMatch || !generatedMatch) return send(res, 404, 'manifest_not_found');
      const signaturePath = path.join(DIST_DIR, `${sourceId}.${versionMatch[1]}.${generatedMatch[1]}.sig`);
      const signature = await readBounded(signaturePath, MAX_SIGNATURE_BYTES);
      const signatureText = signature.toString('ascii').trim();
      if (!/^[A-Za-z0-9+/]+={0,2}$/.test(signatureText)) {
        return send(res, 404, 'manifest_not_found');
      }
      return send(res, 200, manifest, {
        'content-type': 'text/plain; charset=utf-8',
        'x-guardian-signature': signatureText,
        'cache-control': 'no-store'
      });
    }

    const filterMatch = /^\/filters\/([a-zA-Z0-9._-]{1,64})\/([a-zA-Z0-9._+-]{1,64})\.txt$/.exec(url.pathname);
    if (filterMatch) {
      const [, sourceId, version] = filterMatch;
      const filterPath = path.join(DIST_DIR, 'filters', sourceId, `${version}.txt`);
      const content = await readBounded(filterPath, MAX_FILTER_BYTES);
      return send(res, 200, content, {
        'content-type': 'text/plain; charset=utf-8',
        'cache-control': 'public, max-age=31536000, immutable'
      });
    }
    return send(res, 404, 'not_found');
  } catch (error) {
    const status = Number.isInteger(error.status) ? error.status : error.code === 'ENOENT' ? 404 : 500;
    return send(res, status, status === 404 ? 'not_found' : 'internal_error');
  }
});

if (process.env.NODE_ENV !== 'test') {
  server.listen(PORT, '0.0.0.0', () => {
    process.stdout.write(`Guardian filter distribution listening on :${PORT}\\n`);
  });
}
export { server };
