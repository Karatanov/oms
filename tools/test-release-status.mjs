import test from 'node:test';
import assert from 'node:assert/strict';
import { summarizeRelease } from './check-release-status.mjs';

const sha = 'a'.repeat(40);
const names = ['Backend regression tests', 'Project form regression tests', 'Publish OMS container'];
const runs = names.map((name, id) => ({ name, id, head_sha: sha, event: 'push', status: 'completed', conclusion: 'success' }));
const fixture = () => ({ sha, remoteMaster: sha, runs, version: { revision: sha.slice(0, 12) }, health: { status: 'UP' }, ready: { status: 'READY' } });

test('green checks plus the exact healthy production revision verify API release, not browser UI', () => {
  const result = summarizeRelease(fixture());
  assert.equal(result.releaseVerified, true);
  assert.equal(result.browserUiVerified, false);
});
test('green workflows cannot certify an older production revision', () => {
  const result = summarizeRelease({ ...fixture(), version: { revision: 'b'.repeat(12) } });
  assert.equal(result.requiredChecksPassed, true);
  assert.equal(result.releaseVerified, false);
});
test('another commit and PR runs cannot substitute for missing push checks', () => {
  for (const wrongRuns of [runs.map(run => ({ ...run, head_sha: 'b'.repeat(40) })), runs.map(run => ({ ...run, event: 'pull_request' }))]) {
    const result = summarizeRelease({ ...fixture(), runs: wrongRuns });
    assert.equal(result.requiredChecksPassed, false);
    assert.ok(result.workflows.every(run => run.status === 'missing'));
  }
});
test('a newer failed rerun supersedes older successful results', () => {
  const retry = { ...runs[0], run_attempt: 2, conclusion: 'failure' };
  assert.equal(summarizeRelease({ ...fixture(), runs: [...runs, retry] }).requiredChecksPassed, false);
});
test('pending, cancelled or missing checks are not successes', () => {
  for (const run of [{ ...runs[0], status: 'in_progress', conclusion: null }, { ...runs[0], conclusion: 'cancelled' }]) {
    assert.equal(summarizeRelease({ ...fixture(), runs: [run, ...runs.slice(1)] }).releaseVerified, false);
  }
  assert.equal(summarizeRelease({ ...fixture(), runs: [] }).releaseVerified, false);
});
test('readiness failure and ambiguous revision prefixes cannot verify a release', () => {
  assert.equal(summarizeRelease({ ...fixture(), ready: { status: 'NOT_READY' } }).releaseVerified, false);
  for (const revision of [null, 'aaaaaaa', sha + 'a', 'unknown']) {
    assert.equal(summarizeRelease({ ...fixture(), version: { revision } }).productionMatchesCommit, false);
  }
});
test('a different remote tip is reported separately, not mislabelled as an unpublished commit', () => {
  const result = summarizeRelease({ ...fixture(), remoteMaster: 'b'.repeat(40) });
  assert.equal(result.productionMatchesCommit, true);
  assert.equal(result.masterTipMatchesCommit, false);
  assert.equal(result.releaseVerified, false);
});
