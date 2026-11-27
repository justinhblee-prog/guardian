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
    const cleanHttpsUrl = value => {
      if (typeof value !== 'string' || !value.trim()) return false;
      try {
        const parsed = new URL(value);
        return parsed.protocol === 'https:' && Boolean(parsed.hostname) &&
          !parsed.username && !parsed.password && !parsed.search && !parsed.hash;
      } catch {
        return false;
      }
    };
    if (!cleanHttpsUrl(source.url)) {
      errors.push(id + ': source url must use HTTPS and must not contain credentials, a query or a fragment.');
    }
    if (!cleanHttpsUrl(source.upstreamRepository)) {
      errors.push(id + ': upstreamRepository must be a clean HTTPS URL.');
    }
    if (!cleanHttpsUrl(source.licenseUrl)) {
      errors.push(id + ': licenseUrl must be a clean HTTPS URL.');
    }
    if (typeof source.upstreamVersion !== 'string' ||
        !/^[A-Za-z0-9._+-]{1,128}$/.test(source.upstreamVersion)) {
      errors.push(id + ': upstreamVersion is missing or malformed.');
    } else if (/^(rolling(?:-unpinned)?|unpinned|latest|main|master|head)$/i.test(source.upstreamVersion)) {
      errors.push(id + ': upstreamVersion must identify a pinned, reviewable source revision; floating labels are not allowed.');
    }
    if (typeof source.url === 'string' &&
        /@(?:latest|main|master|head)(?:\/|$)/i.test(source.url)) {
      errors.push(id + ': source url must pin an immutable upstream revision; floating @latest/@main/@master/@head references are not allowed.');
    }
    if (source.thirdPartyProvenanceReviewed !== true) {
      errors.push(id + ': thirdPartyProvenanceReviewed must be explicitly true after upstream data-source review.');
    }
    if (typeof source.complianceEvidenceRef !== 'string' ||
        !/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}$/.test(source.complianceEvidenceRef)) {
      errors.push(id + ': complianceEvidenceRef must identify a traceable source-specific compliance record.');
    }
    if (!Number.isSafeInteger(source.minimumRules) || source.minimumRules < 1) {
      errors.push(id + ': minimumRules must be a positive safe integer.');
    }
    if (source.redistributionApproved !== true) {
      errors.push(id + ': redistributionApproved must be explicitly true after rights review.');
    }
    if (typeof source.redistributionApprovalRef !== 'string' ||
        !/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/.test(source.redistributionApprovalRef)) {
      errors.push(id + ': redistributionApprovalRef must be a 1-128 character traceable reference using letters, digits, dot, underscore, colon, slash or hyphen.');
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
  const input = process.argv[2] || 'server/filter-distribution/sources.example.json';
  const config = input === '--from-env'
    ? JSON.parse(process.env.GUARDIAN_FILTER_APPROVALS_JSON || 'null')
    : JSON.parse(await readFile(path.resolve(input), 'utf8'));
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
