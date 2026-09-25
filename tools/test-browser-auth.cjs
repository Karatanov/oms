const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium, webkit, firefox } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));
const source = fs.readFileSync('composeApp/src/webMain/resources/auth.js', 'utf8');
const app = 'https://app.oms.test';
const api = 'https://api.oms-other.test/api/v1';
(async () => {
  for (const engine of [chromium, webkit, firefox].filter(e => !process.env.OMS_TEST_ENGINE || e.name() === process.env.OMS_TEST_ENGINE)) {
    const browser = await engine.launch();
    try {
      for (const kind of ['user', 'guest']) {
        const context = await browser.newContext();
        const page = await context.newPage();
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        const token = `fixture-${kind}`;
        await context.route(`${app}/**`, route => route.fulfill({ contentType: 'text/html', body:
          `<script>function omsApiUrl(p){return '${api}'+p;}const omsLanguage='en';</script><script>${source}</script>` }));
        await context.route('https://foreign.oms.test/**', async route => {
          assert.equal(route.request().headers().authorization, undefined);
          await route.fulfill({ headers: { 'Access-Control-Allow-Origin': app, 'Access-Control-Allow-Credentials': 'true' }, body: 'OK' });
        });
        await context.route(`${api}/**`, async route => {
          const request = route.request();
          const pathname = new URL(request.url()).pathname;
          const headers = { 'Access-Control-Allow-Origin': app, 'Access-Control-Allow-Credentials': 'true',
            'Access-Control-Allow-Headers': 'authorization,content-type', 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
            'Access-Control-Expose-Headers': 'Content-Disposition' };
          if (request.method() === 'OPTIONS') return route.fulfill({ headers, status: 200 });
          if (pathname.endsWith('/auth/login') || pathname.endsWith('/auth/guest')) return route.fulfill({ headers,
            contentType: 'application/json', body: JSON.stringify({ accessToken: token, user: { username: 'fixture' } }) });
          if (pathname.endsWith('/auth/logout')) return route.fulfill({ headers, status: 204 });
          if (request.headers().authorization !== `Bearer ${token}`) return route.fulfill({ headers, status: 401, body: 'Login required' });
          if (pathname.endsWith('/photo')) return route.fulfill({ headers, contentType: 'image/png', body: Buffer.from(
            'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6WQ0AAAAASUVORK5CYII=', 'base64') });
          if (pathname.endsWith('/download')) return route.fulfill({ headers: { ...headers, 'Content-Disposition': "attachment; filename*=UTF-8''report.csv" }, contentType: 'text/csv', body: 'fixture,data' });
          return route.fulfill({ headers, contentType: 'application/json', body: '{"data":[1]}' });
        });
        await page.goto(app);
        assert.equal(await page.evaluate(async api => (await omsAuthenticatedFetch(api + '/projects/map')).status, api), 401);
        await page.evaluate(async ({ api, kind }) => {
          const response = await fetch(api + '/auth/' + (kind === 'user' ? 'login' : 'guest'), { method: 'POST' });
          setOmsAccessToken((await response.json()).accessToken);
        }, { api, kind });
        assert.equal((await context.cookies()).length, 0);
        assert.equal(await page.evaluate(async api => (await omsAuthenticatedFetch(api + '/projects/map')).status, api), 200);
        await page.reload();
        assert.equal(await page.evaluate(async api => (await omsAuthenticatedFetch(api + '/auth/session')).status, api), 200);
        await page.evaluate(async () => { await omsAuthenticatedFetch('https://foreign.oms.test/resource'); });
        const downloadPromise = page.waitForEvent('download');
        await page.evaluate(api => { openOmsDownload(api + '/download'); }, api);
        const download = await downloadPromise;
        assert.equal(download.suggestedFilename(), 'report.csv');
        await page.evaluate(async api => {
          const image = document.createElement('img'); document.body.append(image);
          await new Promise((resolve, reject) => { image.onload=resolve; image.onerror=reject; omsSetImageSource(image, api + '/photo'); });
          if (!image.naturalWidth) throw new Error('Image failed');
        }, api);
        await page.evaluate(() => clearOmsAuthentication());
        assert.equal(await page.evaluate(async api => (await omsAuthenticatedFetch(api + '/projects/map')).status, api), 401);
        assert.deepEqual(errors, []);
        await context.close();
        console.log(`PASS ${engine.name()}: ${kind} without cookies; map, reload, download, image, origin isolation, logout`);
      }
    } finally { await browser.close(); }
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
