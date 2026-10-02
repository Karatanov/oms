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
const overview = {
  recentInspections: [{ uuid: 'inspection-1' }, { uuid: 'inspection-2' }],
  monthlyActPayments: [],
  subprojectFunding: [{ projectUuid: 'one', amountEur: 100 }, { projectUuid: 'two', amountEur: 200 }],
  subprojectProgress: [{ projectUuid: 'one', completionPct: 40 }, { projectUuid: 'two', completionPct: 60 }],
  procurementStatusCounts: [{ label: 'active', value: 3 }, { label: 'done', value: 4 }]
};

(async () => {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    await page.addInitScript(() => sessionStorage.setItem('oms.accessToken', 'fixture-token'));
    await page.route(`${origin}/**`, async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/app.html') return route.fulfill({ contentType: 'text/html', body: source });
      if (url.pathname === '/api/v1/dashboard/overview') {
        assert.equal(route.request().headers().authorization, 'Bearer fixture-token');
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify(overview) });
      }
      return route.fulfill({ status: 204 });
    });
    const started = Date.now();
    await page.goto(`${origin}/app.html`);
    await page.locator('#oms-startup-status').waitFor();
    await page.waitForFunction(() => Number(document.querySelector('#oms-startup-percent').textContent.replace('%', '')) >= 91);
    assert.ok(Date.now() - started < 2_000, 'Authenticated shell should render within two seconds');
    assert.equal(await page.evaluate(() => performance.getEntriesByName('oms-shell-to-useful').length), 1);
    assert.ok((await page.evaluate(() => window.takeOmsBootstrappedDashboard())).includes('inspection-1'));
    assert.equal(await page.evaluate(() => window.takeOmsBootstrappedDashboard()), '');
    assert.ok(await page.locator('#oms-loading-overlay').evaluate(el => el.classList.contains('visible')),
      'Consuming a response must not dismiss startup before Compose paints');
    await page.evaluate(() => finishOmsStartup());
    await page.waitForFunction(() => !document.querySelector('#oms-loading-overlay.visible'));
    assert.ok(await page.evaluate(() => performance.getEntriesByName('oms-dashboard-handoff').length > 0));
    console.log('PASS authenticated startup shell: login backdrop, measured progress and bootstrap consumption');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
