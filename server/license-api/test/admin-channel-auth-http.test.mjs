test('administrator channel verification fails closed without server configuration and is rate-limited', async () => {
  const request = () => fetch(\`${baseUrl}/v1/admin/auth/verify\`);
  assert.equal((await request()).status, 503);
  assert.equal((await request()).status, 503);
  const limited = await request();
  assert.equal(limited.status, 429);
  assert.ok(Number(limited.headers.get('retry-after')) >= 1);
});

// The endpoint is also exercised by the full HTTP integration suite.
