// Read-only release diagnostics. A successful workflow is not proof of deployment.
// node tools/check-release-status.mjs [full-commit-sha] [--require-deployed]
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

const requiredWorkflows = ['Backend regression tests', 'Project form regression tests', 'Publish OMS container'];
const displayedWorkflows = [...requiredWorkflows, 'Publish OMS frontend to GitHub Pages'];

export function summarizeRelease({ sha, remoteMaster, runs, version, health, ready }) {
  if (!/^[a-f0-9]{40}$/.test(sha)) throw Error('Expected a full 40-character commit SHA.');
  const matching = runs.filter(run => run.head_sha === sha && run.event === 'push')
    .sort((a, b) => b.id - a.id || (b.run_attempt ?? 1) - (a.run_attempt ?? 1));
  const workflows = displayedWorkflows.map(name => {
    const run = matching.find(candidate => candidate.name === name);
    return { name, status: run?.status ?? 'missing', conclusion: run?.conclusion ?? null, url: run?.html_url ?? null };
  });
  const revision = version?.revision;
  const productionMatchesCommit = typeof revision === 'string' && /^[a-f0-9]{12,40}$/.test(revision) && sha.startsWith(revision);
  const productionApiHealthy = health?.status === 'UP' && ready?.status === 'READY';
  const requiredChecksPassed = requiredWorkflows.every(name => workflows.some(run =>
    run.name === name && run.status === 'completed' && run.conclusion === 'success'));
  return {
    commit: sha,
    remoteMaster,
    masterTipMatchesCommit: remoteMaster === sha,
    workflows,
    productionRevision: revision ?? null,
    productionMatchesCommit,
    productionApiHealthy,
    requiredChecksPassed,
    releaseVerified: remoteMaster === sha && requiredChecksPassed && productionMatchesCommit && productionApiHealthy,
    browserUiVerified: false
  };
}

async function readJson(url) {
  const response = await fetch(url, {
    signal: AbortSignal.timeout(15000),
    cache: 'no-store',
    headers: { Accept: 'application/json', 'Cache-Control': 'no-cache', 'User-Agent': 'OMS-release-check' }
  });
  if (!response.ok) throw Error(`Read-only diagnostic request failed: HTTP ${response.status} (${new URL(url).pathname})`);
  return response.json();
}

async function main() {
  const args = process.argv.slice(2);
  const requireDeployed = args.includes('--require-deployed');
  const shaArgs = args.filter(arg => arg !== '--require-deployed');
  if (shaArgs.length > 1) throw Error('Usage: node tools/check-release-status.mjs [full-commit-sha] [--require-deployed]');
  const sha = shaArgs[0] ?? execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
  if (!/^[a-f0-9]{40}$/.test(sha)) throw Error('Expected a full 40-character commit SHA.');
  const remoteMaster = execFileSync('git', ['ls-remote', 'origin', 'refs/heads/master'], {
    encoding: 'utf8', timeout: 15000
  }).trim().split(/\s+/)[0];
  // SHA scoping avoids mixing commits; a nonce avoids stale intermediary lists.
  const nonce = Date.now();
  const url = `https://api.github.com/repos/Karatanov/oms/actions/runs?head_sha=${sha}&event=push&per_page=100&oms_check=${nonce}`;
  const [actions, version, health, ready] = await Promise.all([
    readJson(url),
    readJson(`https://ua-oms.com/version?oms_check=${nonce}`),
    readJson(`https://ua-oms.com/health?oms_check=${nonce}`),
    readJson(`https://ua-oms.com/ready?oms_check=${nonce}`)
  ]);
  if (!Array.isArray(actions.workflow_runs)) throw Error('GitHub did not return a workflow run list.');
  const summary = summarizeRelease({ sha, remoteMaster, runs: actions.workflow_runs, version, health, ready });
  console.log(JSON.stringify(summary, null, 2));
  if (requireDeployed && !summary.releaseVerified) process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
