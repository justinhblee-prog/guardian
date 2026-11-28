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
    const commitSha = typeof source.upstreamCommitSha === 'string' &&
      /^[a-f0-9]{40,64}$/i.test(source.upstreamCommitSha);
    let sourceUrlPinsCommit = false;
    if (commitSha && typeof source.url === 'string' && cleanHttpsUrl(source.upstreamRepository)) {
      try {
        const sourceUrl = new URL(source.url);
        const repositoryUrl = new URL(source.upstreamRepository);
        const repositoryParts = repositoryUrl.pathname.split('/').filter(Boolean);
        const owner = repositoryParts[0]?.toLowerCase();
        const repository = repositoryParts[1]?.replace(/\.git$/i, '').toLowerCase();
        const sourceParts = sourceUrl.pathname.split('/').filter(Boolean);
        const revision = source.upstreamCommitSha.toLowerCase();
        if (repositoryUrl.hostname.toLowerCase() === 'github.com' &&
            owner && repository && repositoryParts.length === 2) {
          if (sourceUrl.hostname.toLowerCase() === 'raw.githubusercontent.com') {
            sourceUrlPinsCommit = sourceParts.length >= 4 &&
              sourceParts[0].toLowerCase() === owner &&
              sourceParts[1].toLowerCase() === repository &&
              sourceParts[2].toLowerCase() === revision;
          } else if (sourceUrl.hostname.toLowerCase() === 'cdn.jsdelivr.net') {
            sourceUrlPinsCommit = sourceParts.length >= 4 &&
              sourceParts[0] === 'gh' &&
              sourceParts[1].toLowerCase() === owner &&
              sourceParts[2].toLowerCase() === repository + '@' + revision;
          }
        }
      } catch {
        sourceUrlPinsCommit = false;
      }
    }
    if (!commitSha) {
      errors.push(id + ': upstreamCommitSha must be a full 40-64 character immutable Git commit hash.');
    } else if (!sourceUrlPinsCommit) {
      errors.push(id + ': source url must contain the exact upstreamCommitSha as a path segment or @revision.');
    }
    if (typeof source.url === 'string' &&
        /@(?:latest|main|master|head)(?:\/|$)/i.test(source.url)) {
      errors.push(id + ': floating @latest/@main/@master/@head source references are not allowed.');
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
