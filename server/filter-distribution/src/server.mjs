import http from 'node:http';
import { constants } from 'node:fs';
import { lstat, open } from 'node:fs/promises';
import path from 'node:path';

const HOST = process.env.HOST || '127.0.0.1';
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

async function assertSafeParentDirectories(filePath) {
  const relative = path.relative(DIST_DIR, path.dirname(filePath));
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw Object.assign(new Error('Distribution path escaped its root.'), { status: 404 });
  }
  let current = DIST_DIR;
  const directories = [current, ...relative.split(path.sep).filter(Boolean).map(part => {
    current = path.join(current, part);
    return current;
  })];
  for (const directory of directories) {
    const info = await lstat(directory);
    if (!info.isDirectory() || info.isSymbolicLink()) {
      throw Object.assign(new Error('Distribution parent must be a real directory.'), { status: 404 });
    }
  }
}

async function readBounded(filePath, maxBytes) {
  // Reject symlinked parent directories as well as a symlink at the final
  // component. The final file is still validated and read through one
  // descriptor to avoid a stat/read path-swap race.
  await assertSafeParentDirectories(filePath);
  const handle = await open(filePath, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const info = await handle.stat();
    if (!info.isFile() || info.size <= 0 || info.size > maxBytes) {
      throw Object.assign(new Error('Invalid distribution asset size.'), { status: 404 });
    }
    // Do not use readFile() here: a concurrently replaced/growing file could
    // exceed the size observed by stat() and allocate unbounded memory before
    // the post-read size check. Read at most the observed size plus one byte.
    const content = Buffer.allocUnsafe(info.size + 1);
    let bytesRead = 0;
    while (bytesRead < content.length) {
      const result = await handle.read(
        content,
        bytesRead,
        content.length - bytesRead,
        bytesRead
      );
      if (result.bytesRead === 0) break;
      bytesRead += result.bytesRead;
    }
    if (bytesRead <= 0 || bytesRead !== info.size || bytesRead > maxBytes) {
      throw Object.assign(new Error('Invalid distribution asset size.'), { status: 404 });
    }
    return content.subarray(0, bytesRead);
  } finally {
    await handle.close();
  }
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

    const manifestMatch = /^\/([a-zA-Z0-9_-]{1,64})\.manifest$/.exec(url.pathname);
    if (manifestMatch) {
      const sourceId = manifestMatch[1];
      const manifestPath = path.join(DIST_DIR, `${sourceId}.manifest`);
      const manifest = await readBounded(manifestPath, MAX_MANIFEST_BYTES);
      const manifestText = manifest.toString('utf8');
      if (!Buffer.from(manifestText, 'utf8').equals(manifest)) return send(res, 404, 'manifest_not_found');
      const sourceMatch = /^sourceId=([a-zA-Z0-9_-]{1,64})$/m.exec(manifestText);
      const versionMatch = /^sourceVersion=([a-zA-Z0-9._+-]{1,64})$/m.exec(manifestText);
      const generatedMatch = /^generatedAt=(\d+)$/m.exec(manifestText);
      if (!sourceMatch || sourceMatch[1] !== sourceId || !versionMatch || !generatedMatch) return send(res, 404, 'manifest_not_found');
      const signaturePath = path.join(DIST_DIR, `${sourceId}.${versionMatch[1]}.${generatedMatch[1]}.sig`);
      const signature = await readBounded(signaturePath, MAX_SIGNATURE_BYTES);
      const signatureText = signature.toString('ascii').trim();
      if (!/^[A-Za-z0-9+/]+={0,2}$/.test(signatureText)) {
        return send(res, 404, 'manifest_not_found');
      }
      const signatureBytes = Buffer.from(signatureText, 'base64');
      // RSA signatures are binary data. Require canonical base64 and a
      // plausible RSA signature size (2048–4096-bit signing keys).
      if (signatureBytes.length < 256 || signatureBytes.length > 512 ||
          signatureBytes.toString('base64') !== signatureText) {
        return send(res, 404, 'manifest_not_found');
      }
      return send(res, 200, manifest, {
        'content-type': 'text/plain; charset=utf-8',
        'x-guardian-signature': signatureText,
        'cache-control': 'no-store'
      });
    }

    const filterMatch = /^\/filters\/([a-zA-Z0-9_-]{1,64})\/([a-zA-Z0-9._+-]{1,64})\.txt$/.exec(url.pathname);
    if (filterMatch) {
      const [, sourceId, version] = filterMatch;
      const filterPath = path.join(DIST_DIR, 'filters', sourceId, `${version}.txt`);
      const content = await readBounded(filterPath, MAX_FILTER_BYTES);
      const contentText = content.toString('utf8');
      if (!Buffer.from(contentText, 'utf8').equals(content) || content.includes(0)) {
        return send(res, 404, 'filter_not_found');
      }
      return send(res, 200, content, {
        'content-type': 'text/plain; charset=utf-8',
        'cache-control': 'public, max-age=31536000, immutable'
      });
    }
    return send(res, 404, 'not_found');
  } catch (error) {
    const status = Number.isInteger(error.status) ? error.status : ['ENOENT', 'ELOOP'].includes(error.code) ? 404 : 500;
    return send(res, status, status === 404 ? 'not_found' : 'internal_error');
  }
});

// Keep the read-only distribution endpoint resistant to slow-header and
// excessive-header resource exhaustion. Requests carry no upload body.
server.headersTimeout = 10_000;
server.requestTimeout = 15_000;
server.keepAliveTimeout = 5_000;
server.maxHeadersCount = 32;

if (process.env.NODE_ENV !== 'test') {
  server.listen(PORT, HOST, () => {
    process.stdout.write(`Guardian filter distribution listening on ${HOST}:${PORT}\n`);
  });
}
export { server };
