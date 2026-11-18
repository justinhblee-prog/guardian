import { readFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { pathToFileURL } from 'node:url';

/**
 * Fail closed unless every shipped third-party filter has explicit,
 * traceable redistribution approval. A public license identifier alone is
 * not evidence that this app may redistribute a given source.
 */
export function verifyProductionRedistributionApprovals(config) {
  if (!config || typeof config !== 'object' ||
      !Array.isArray(config.sources) || config.sources.length === 0) {
    throw new Error('Production filter config must contain a non-empty sources array.');
  }

  const errors = [];
  const seen = new Set();

  for (const source of config.sources) {
    const id = typeof source?.id === 'string' && /^[A-Za-z0-9_-]{1,64}$/.test(source.id)
      ? source.id
      : '(invalid source id)';

    if (!source || id === '(invalid source id)') {
      errors.push('(unknown): source id must contain 1-64 letters, digits, underscores or hyphens.');
      continue;
    }
    if (seen.has(id)) errors.push(id + ': duplicate source id.');
    seen.add(id);

    if (typeof source.licenseId !== 'string' ||
        !/^[A-Za-z0-9.+-]{1,64}$/.test(source.licenseId)) {
      errors.push(id + ': licenseId is missing or malformed.');
    }
    if (source.redistributionApproved !== true) {
      errors.push(id + ': redistributionApproved must be explicitly true after rights review.');
    }
    if (typeof source.redistributionApprovalRef !== 'string' ||
        !source.redistributionApprovalRef.trim() ||
        source.redistributionApprovalRef.length > 128) {
      errors.push(id + ': redistributionApprovalRef must identify a traceable approval record.');
    }
  }

  if (errors.length) {
    throw new Error(
      'Production filter redistribution approval gate blocked release:\n' +
      errors.map(item => ' - ' + item).join('\n') +
      '\nDo not change these fields until the corresponding rights review and approval record exist.'
    );
  }

  return { approvedSourceCount: config.sources.length };
}

async function main() {
  const configPath = path.resolve(process.argv[2] || 'server/filter-distribution/sources.example.json');
  const config = JSON.parse(await readFile(configPath, 'utf8'));
  const result = verifyProductionRedistributionApprovals(config);
  process.stdout.write(
    'Production redistribution approval verified for ' +
    result.approvedSourceCount + ' filter sources.\n'
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch(error => {
    process.stderr.write(error.message + '\n');
    process.exitCode = 1;
  });
}
