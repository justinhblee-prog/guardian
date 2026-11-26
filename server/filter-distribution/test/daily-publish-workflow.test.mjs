import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const workflowPath = new URL('../../../.github/workflows/guardian-daily-filter-publish.yml', import.meta.url);
const workflow = await readFile(workflowPath, 'utf8');

test('daily filter publication is restricted to the protected main branch', () => {
  const jobStart = workflow.indexOf('  build-and-publish:');
  assert.notEqual(jobStart, -1, 'daily publish job must exist');
  const publishJob = workflow.slice(jobStart);
  assert.match(
    publishJob,
    /^    if: github\.ref == 'refs\/heads\/main' && github\.event_name != 'pull_request'$/m
  );
});
