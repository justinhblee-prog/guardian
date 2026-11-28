import { readFile, writeFile } from 'node:fs/promises';

const files = {
  html: new URL('../admin/index.html', import.meta.url),
  js: new URL('../admin/admin.js', import.meta.url),
  model: new URL('../admin/admin-model.mjs', import.meta.url)
};
const assets = {};
for (const [name, path] of Object.entries(files)) {
  assets[name] = await readFile(path, 'utf8');
}
const output = `export const adminAssets = Object.freeze({
  html: ${JSON.stringify(assets.html)},
  js: ${JSON.stringify(assets.js)},
  model: ${JSON.stringify(assets.model)}
});
`;
await writeFile(new URL('../src/admin-assets.mjs', import.meta.url), output, 'utf8');
