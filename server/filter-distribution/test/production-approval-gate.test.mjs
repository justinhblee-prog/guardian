import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { verifyProductionRedistributionApprovals } from '../scripts/verify-production-approvals.mjs';

const policyPath = new URL('../sources.example.json', import.meta.url);
const exampleConfig = JSON.parse(await readFile(policyPath, 'utf8'));

test('production approval gate blocks every currently unapproved example filter source', () => {
  assert.throws(
    () => verifyProductionRedistributionApprovals(exampleConfig),
    error => {
      assert.match(error.message, /Production filter redistribution approval gate blocked release/);
      for (const source of exampleConfig.sources) assert.match(error.message, new RegExp(source.id));
      return true;
    }
  );
});

test('production approval gate accepts sources only with license, explicit approval and traceable reference', () => {
  const approved = {
    sources: [
      {
        id: 'reviewed_filter',
        url: 'https://upstream.example/filter.txt',
        minimumRules: 1,
        licenseId: 'LicenseRef-Reviewed',
        redistributionApproved: true,
        redistributionApprovalRef: 'LEGAL-REVIEW-2026-001'
      }
    ]
  };
  assert.deepEqual(
    verifyProductionRedistributionApprovals(approved),
    { approvedSourceCount: 1 }
  );
});

test('production approval gate rejects approval without a traceable reference', () => {
  assert.throws(
    () => verifyProductionRedistributionApprovals({
      sources: [{
        id: 'untraceable',
        licenseId: 'GPL-3.0',
        redistributionApproved: true,
        redistributionApprovalRef: '   '
      }]
    }),
    /redistributionApprovalRef/
  );
});

test('production approval gate rejects missing license identifiers and duplicate ids', () => {
  assert.throws(
    () => verifyProductionRedistributionApprovals({
      sources: [
        { id: 'duplicate', licenseId: '', redistributionApproved: true, redistributionApprovalRef: 'LEGAL-1' },
        { id: 'duplicate', licenseId: 'GPL-3.0', redistributionApproved: true, redistributionApprovalRef: 'LEGAL-2' }
      ]
    }),
    error => /licenseId/.test(error.message) && /duplicate source id/.test(error.message)
  );
});

test('production approval gate rejects empty or malformed source configuration', () => {
  assert.throws(() => verifyProductionRedistributionApprovals({ sources: [] }), /non-empty sources array/);
  assert.throws(() => verifyProductionRedistributionApprovals(null), /non-empty sources array/);
});

test('production approval gate rejects ambiguous or malformed approval references', () => {
  for (const reference of [' LEGAL-1', 'LEGAL 1', 'LEGAL-1\\nforged', 'LEGAL-1\\t', 'x'.repeat(129)]) {
    assert.throws(
      () => verifyProductionRedistributionApprovals({
        sources: [{
          id: 'malformed_reference',
          licenseId: 'MIT',
          redistributionApproved: true,
          redistributionApprovalRef: reference
        }]
      }),
      /redistributionApprovalRef/
    );
  }
});
