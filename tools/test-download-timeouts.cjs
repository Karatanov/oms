// Exercise the shipped auth.js; fake only transport, DOM and clock, never its logic.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('composeApp/src/webMain/resources/auth.js', 'utf8');
const api = 'https://oms.test/api/v1';

function fixture(language = 'en') {
  const timers = new Map(), alerts = [], requests = [], downloads = [];
  let nextTimer = 0, progress = 0;
  const sandbox = {
    URL: class extends URL {
      static createObjectURL() { return 'blob:fixture'; }
      static revokeObjectURL() {}
    },
    Headers, AbortController, Error, Event,
    location: { href: 'https://oms.test/app.html' },
    omsApiUrl: suffix => api + suffix,
    omsLanguage: language,
    sessionStorage: { getItem() { return ''; }, setItem() {}, removeItem() {} },
    setTimeout(fn, ms) { const id = ++nextTimer; timers.set(id, { fn, ms }); return id; },
    clearTimeout(id) { timers.delete(id); },
    alert(message) { alerts.push(message); },
    showOmsLoading() { progress++; }, hideOmsLoading() { progress--; },
    document: {
      body: { append() {} },
      createElement() { return { click() { downloads.push(this.download); }, remove() {} }; }
    },
    fetch(url, options) { return new Promise((resolve, reject) => requests.push({ url, options, resolve, reject })); }
  };
  sandbox.window = sandbox;
  vm.runInNewContext(source, sandbox);
  sandbox.setOmsAccessToken('fixture-token');
  return { sandbox, timers, alerts, requests, downloads, progress: () => progress,
    expire() {
      const timer = [...timers.values()].find(item => item.ms === 300000);
      assert.ok(timer, 'Download must have a five-minute deadline');
      timer.fn();
    }
  };
}
function abortRequest(request, stage = 'headers') {
  request.options.signal.addEventListener('abort', () => request.reject(new Error('aborted ' + stage)), { once: true });
}
function response() {
  return { ok: true, headers: new Headers({ 'Content-Disposition': 'attachment; filename="report.pdf"' }), blob: async () => new Blob(['fixture']) };
}

test('a stalled header request times out, clears progress and explains retry in UA/EN', async () => {
  for (const language of ['en', 'uk']) {
    const f = fixture(language);
    const pending = f.sandbox.openOmsDownload(api + '/export-pdf');
    abortRequest(f.requests[0]);
    assert.equal(f.progress(), 1);
    f.expire(); await pending;
    assert.equal(f.progress(), 0);
    assert.equal(f.timers.size, 0);
    assert.match(f.alerts[0], language === 'en' ? /timed out.*retry/i : /вичерпано.*Повторіть/);
  }
});
test('the timeout also covers a stalled response body', async () => {
  const f = fixture();
  const pending = f.sandbox.openOmsDownload(api + '/export-pdf');
  let rejectBody;
  let bodyStarted;
  const started = new Promise(resolve => { bodyStarted = resolve; });
  f.requests[0].resolve({ ...response(), blob: () => new Promise((_, reject) => { rejectBody = reject; bodyStarted(); }) });
  await started;
  assert.ok(rejectBody, 'Body transfer must have started');
  f.requests[0].options.signal.addEventListener('abort', () => rejectBody(new Error('body aborted')), { once: true });
  f.expire(); await pending;
  assert.equal(f.progress(), 0);
  assert.equal(f.downloads.length, 0);
});
test('successful POST downloads preserve credentials, payload and filename and remove the deadline', async () => {
  const f = fixture();
  const pending = f.sandbox.openOmsJsonDownload(api + '/export-pdf', '{"ids":[1]}');
  const request = f.requests[0];
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.body, '{"ids":[1]}');
  assert.equal(request.options.headers.get('Authorization'), 'Bearer fixture-token');
  assert.equal(request.options.credentials, 'include');
  request.resolve(response()); await pending;
  assert.deepEqual(f.downloads, ['report.pdf']);
  assert.equal(f.progress(), 0);
  assert.equal(f.alerts.length, 0);
  assert.ok([...f.timers.values()].every(timer => timer.ms !== 300000));
});
test('HTTP failures clear the deadline and keep existing authentication messages', async () => {
  const f = fixture();
  const pending = f.sandbox.openOmsDownload(api + '/export-pdf');
  f.requests[0].resolve({ ok: false, status: 401, json: async () => ({}) });
  await pending;
  assert.match(f.alerts[0], /sign in again/i);
  assert.equal(f.timers.size, 0);
  assert.equal(f.progress(), 0);
});
test('external URLs are ignored without a token, request or progress indicator', async () => {
  const f = fixture();
  await f.sandbox.openOmsDownload('https://foreign.test/export-pdf');
  assert.equal(f.requests.length, 0);
  assert.equal(f.timers.size, 0);
  assert.equal(f.progress(), 0);
});
test('one timed-out download does not cancel another active download', async () => {
  const f = fixture();
  const first = f.sandbox.openOmsDownload(api + '/first');
  const second = f.sandbox.openOmsDownload(api + '/second');
  abortRequest(f.requests[0]);
  assert.equal(f.progress(), 2);
  f.expire(); await first;
  assert.equal(f.progress(), 1);
  assert.equal(f.requests[1].options.signal.aborted, false);
  f.requests[1].resolve(response()); await second;
  assert.equal(f.progress(), 0);
  assert.deepEqual(f.downloads, ['report.pdf']);
});
test('a fresh download after a timeout can succeed', async () => {
  const f = fixture();
  const first = f.sandbox.openOmsDownload(api + '/export-pdf');
  abortRequest(f.requests[0]); f.expire(); await first;
  const retry = f.sandbox.openOmsDownload(api + '/export-pdf');
  assert.equal(f.requests[1].options.signal.aborted, false);
  f.requests[1].resolve(response()); await retry;
  assert.deepEqual(f.downloads, ['report.pdf']);
  assert.equal(f.progress(), 0);
  assert.equal(f.alerts.length, 1);
});
