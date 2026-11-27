import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { verifyProductionRedistributionApprovals } from '../scripts/verify-production-approvals.mjs';

const policyPath = new URL('../sources.example.json', import.meta.url);
const exampleConfig = JSON.parse(await readFile(policyPath, 'utf8'));

const reviewedSource = overrides => ({
  id: 'reviewed_filter',
  url: 'https://raw.githubusercontent.com/example/filter/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/filter.txt',
  minimumRules: 1,
  upstreamRepository: 'https://github.com/example/filter',
  licenseUrl: 'https://github.com/example/filter/blob/main/LICENSE',
  upstreamVersion: '1.0.0',
  upstreamCommitSha: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  thirdPartyProvenanceReviewed: true,
  complianceEvidenceRef: 'PROVENANCE-REVIEW-2026-001',
  licenseId: 'GPL-3.0',
  redistributionApproved: true,
  redistributionApprovalRef: 'LEGAL-REVIEW-2026-001',
  ...overrides
});

test('production approval gate blocks every currently unreviewed example filter source', () => {
  assert.throws(
    () => verifyProductionRedistributionApprovals(exampleConfig),
    error => {
      assert.match(error.message, /Production filter redistribution approval gate blocked release/);
      for (const source of exampleConfig.sources) assert.match(error.message, new RegExp(source.id));
      return true;
    }
  );
});

test('production approval gate accepts complete source-specific compliance evidence', () => {
  assert.deepEqual(
    verifyProductionRedistributionApprovals({ sources: [reviewedSource()] }),
    { approvedSourceCount: 1 }
  );
});

test('production approval gate rejects floating upstream versions and mutable CDN references', () => {
  for (const overrides of [
    { upstreamVersion: 'rolling-unpinned' },
    { upstreamVersion: 'latest' },
    { upstreamCommitSha: 'b'.repeat(40) },
    { upstreamCommitSha: undefined },
    { url: 'https://cdn.jsdelivr.net/gh/example/filter@latest/list.txt' },
    { url: 'https://cdn.jsdelivr.net/gh/example/filter@main/list.txt' }
  ]) {
    assert.throws(
      () => verifyProductionRedistributionApprovals({
        sources: [reviewedSource(overrides)]
      }),
      /pinned|floating|upstreamCommitSha/
    );
  }
});

test('production approval gate rejects missing provenance review before approval', () => {
  assert.throws(
    () => verifyProductionRedistributionApprovals({
      sources: [reviewedSource({ thirdPartyProvenanceReviewed: false })]
    }),
    /thirdPartyProvenanceReviewed/
  );
});

test('production approval gate rejects missing or malformed compliance evidence', () => {
  for (const complianceEvidenceRef of ['', '   ', 'bad ref', 'x'.repeat(257)]) {
    assert.throws(
      () => verifyProductionRedistributionApprovals({
        sources: [reviewedSource({ complianceEvidenceRef })]
      }),
      /complianceEvidenceRef/
    );
  }
});

test('production approval gate rejects approval without a traceable reference', () => {
  assert.throws(
    () => verifyProductionRedistributionApprovals({
      sources: [reviewedSource({ redistributionApprovalRef: '   ' })]
    }),
    /redistributionApprovalRef/
  );
});

test('production approval gate rejects duplicate source ids and missing license identifiers', () => {
  assert.throws(
    () => verifyProductionRedistributionApprovals({
      sources: [
        reviewedSource({ id: 'duplicate', licenseId: '' }),
        reviewedSource({ id: 'duplicate' })
      ]
    }),
    error => /licenseId/.test(error.message) && /duplicate source id/.test(error.message)
  );
});

test('production approval gate rejects malformed source, repository and license URLs', () => {
  for (const [field, value] of [
    ['url', 'http://filters.example/filter.txt'],
    ['url', 'https://user:password@filters.example/filter.txt'],
    ['url', 'https://filters.example/filter.txt#fragment'],
    ['upstreamRepository', 'file:///etc/passwd'],
    ['licenseUrl', 'https://filters.example/LICENSE?token=secret']
  ]) {
    assert.throws(
      () => verifyProductionRedistributionApprovals({
        sources: [reviewedSource({ [field]: value })]
      }),
      new RegExp(field)
    );
  }
});

test('production approval gate requires a positive integer minimum rule count', () => {
  for (const minimumRules of [0, -1, 1.5, '100', Number.MAX_SAFE_INTEGER + 1, null]) {
    assert.throws(
      () => verifyProductionRedistributionApprovals({
        sources: [reviewedSource({ minimumRules })]
      }),
      /minimumRules/
    );
  }
});

test('production approval gate rejects empty or malformed source configuration', () => {
  assert.throws(() => verifyProductionRedistributionApprovals({ sources: [] }), /non-empty sources array/);
  assert.throws(() => verifyProductionRedistributionApprovals(null), /non-empty sources array/);
});
