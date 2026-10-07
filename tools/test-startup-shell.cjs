const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));

const auth = fs.readFileSync('composeApp/src/webMain/resources/auth.js', 'utf8');
const source = fs.readFileSync('composeApp/src/webMain/resources/index.html', 'utf8')
  .replace(/<script src="auth\.js[^>]*><\/script>/, `<script>${auth}</script>`)
  .replace(/<script src="startup\.js[^>]*><\/script>/, `<script>${fs.readFileSync('composeApp/src/webMain/resources/startup.js', 'utf8')}</script>`)
  .replace(/<script type="application\/javascript" src="composeApp[^>]*><\/script>/, '');
const origin = 'https://app.oms.test';

(async () => {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    const dashboardRequests = [];
    page.on('request', request => {
      if (new URL(request.url()).pathname.startsWith('/api/v1/dashboard')) dashboardRequests.push(request.url());
    });
    await page.addInitScript(() => sessionStorage.setItem('oms.accessToken', 'fixture-token'));
    await page.route(`${origin}/**`, async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/app.html') return route.fulfill({ contentType: 'text/html', body: source });
      return route.fulfill({ status: 204 });
    });
    const started = Date.now();
    await page.goto(`${origin}/app.html`);
    await page.locator('#oms-startup-status').waitFor();
    await page.waitForFunction(() => Number(document.querySelector('#oms-startup-percent').textContent.replace('%', '')) >= 91);
    assert.ok(Date.now() - started < 2_000, 'Authenticated shell should render within two seconds');
    assert.equal(await page.evaluate(() => performance.getEntriesByName('oms-shell-visible').length), 1);
    assert.deepEqual(dashboardRequests, [], 'The HTML shell must leave dashboard loading to Compose');
    assert.ok(await page.locator('#oms-loading-overlay').evaluate(el => el.classList.contains('visible')),
      'Startup must remain visible until Compose paints');
    await page.evaluate(() => finishOmsStartup());
    await page.waitForFunction(() => !document.querySelector('#oms-loading-overlay.visible'));
    assert.ok(await page.evaluate(() => performance.getEntriesByName('oms-dashboard-handoff').length > 0));
    assert.deepEqual(dashboardRequests, [], 'Finishing startup must not start a duplicate dashboard request');
    console.log('PASS authenticated startup shell: login backdrop, measured progress, no duplicate dashboard fetch and explicit handoff');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
