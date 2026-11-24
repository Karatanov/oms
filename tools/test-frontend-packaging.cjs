const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');
const { assemblePages } = require('./assemble-pages.cjs');

function fixture(t) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'oms-packaging-'));
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const input = path.join(root, 'frontend');
    for (const dir of ['processed', 'executable', 'source-resources']) fs.mkdirSync(path.join(input, dir), { recursive: true });
    fs.writeFileSync(path.join(input, 'processed/index.html'), '__OMS_WASM_ASSET__ __OMS_GIT_COMMIT__ __OMS_BUILD_DATE__ composeApp.js?omsRetry=${encodeURIComponent(retryNonce)}');
    fs.writeFileSync(path.join(input, 'processed/startup.js'), 'startup');
    fs.writeFileSync(path.join(input, 'executable/composeApp.js'), 'fetch("skiko.wasm")');
    fs.writeFileSync(path.join(input, 'executable/0123456789abcdef0123.wasm'), 'renderer');
    for (const name of ['login.html', 'favicon.svg', 'social-preview.png']) fs.writeFileSync(path.join(input, 'source-resources', name), name);
    return { root, input, output: path.join(root, 'pages') };
}

test('packages the cached artifact with exact current release metadata and renderer', t => {
    const { input, output } = fixture(t);
    const sha = 'a'.repeat(40);
    assemblePages(input, output, sha, '2026-11-24T00:00:00Z');
    assert.equal(fs.readFileSync(path.join(output, 'index.html'), 'utf8'), 'login.html');
    assert.equal(fs.readFileSync(path.join(output, 'startup.js'), 'utf8'), 'startup');
    assert.equal(fs.readFileSync(path.join(output, 'composeApp.js'), 'utf8'), 'fetch("0123456789abcdef0123.wasm")');
    const app = fs.readFileSync(path.join(output, 'app.html'), 'utf8');
    assert.ok(app.includes(sha.slice(0, 12)));
    assert.ok(!app.includes('__OMS_'));
    assert.ok(app.includes('composeApp.js?omsRetry=${encodeURIComponent(retryNonce)}'));
    const manifest = JSON.parse(fs.readFileSync(path.join(output, 'assets-manifest.json')));
    assert.equal(manifest.version, sha);
    for (const asset of manifest.assets) assert.equal(asset.bytes, fs.statSync(path.join(output, asset.url)).size);
    assert.ok(fs.existsSync(path.join(output, '.nojekyll')));
});

test('reused frontend receives new commit metadata instead of a cached build revision', t => {
    const { input, output } = fixture(t);
    assemblePages(input, output, 'a'.repeat(40));
    assemblePages(input, output, 'b'.repeat(40));
    assert.equal(JSON.parse(fs.readFileSync(path.join(output, 'assets-manifest.json'))).version, 'b'.repeat(40));
    assert.ok(fs.readFileSync(path.join(output, 'app.html'), 'utf8').includes('b'.repeat(12)));
});

test('missing/ambiguous renderers and non-immutable revisions fail closed', t => {
    const { input, output } = fixture(t);
    assert.throws(() => assemblePages(input, output, 'master'), /immutable/);
    fs.writeFileSync(path.join(input, 'executable/second.wasm'), 'other');
    assert.throws(() => assemblePages(input, output, 'a'.repeat(40)), /exactly one/);
});
