const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));

const origin = 'https://startup.oms.test';
const startup = fs.readFileSync('composeApp/src/webMain/resources/startup.js', 'utf8');
const auth = fs.readFileSync('composeApp/src/webMain/resources/auth.js', 'utf8');
const login = fs.readFileSync('composeApp/src/webMain/resources/login.html', 'utf8')
  .replace(/<script src="startup\.js[^>]*><\/script>/, `<script>${startup}</script>`);
const app = fs.readFileSync('composeApp/src/webMain/resources/index.html', 'utf8')
  .replace(/<script src="auth\.js[^>]*><\/script>/, `<script>${auth}</script>`)
  .replace(/<script src="startup\.js[^>]*><\/script>/, `<script>${startup}</script>`)
  .replace(/<script type="application\/javascript" src="composeApp[^>]*><\/script>/, '<script src="composeApp.fixture.js"></script><script>fetch("fixture.wasm")</script>');

(async () => {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    await page.route(`${origin}/**`, async route => {
      const pathname = new URL(route.request().url()).pathname;
      if (pathname === '/') return route.fulfill({ contentType: 'text/html', body: login });
      if (pathname === '/app.html') return route.fulfill({ contentType: 'text/html', body: app });
      if (pathname === '/assets-manifest.json') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ assets: [{ url: 'composeApp.fixture.js', bytes: 8 }, { url: 'fixture.wasm', bytes: 8 }] }) });
      if (pathname === '/composeApp.fixture.js' || pathname === '/fixture.wasm') {
        await new Promise(resolve => setTimeout(resolve, 1_200));
        return route.fulfill({ headers: { 'Cache-Control': 'public, max-age=31536000, immutable' }, body: pathname.endsWith('.js') ? '/*1234*/' : '12345678' });
      }
      if (pathname === '/api/v1/dashboard/overview') return route.fulfill({ status: 403 });
      return route.fulfill({ status: 204 });
    });

    await page.goto(`${origin}/`);
    await page.evaluate(() => { window.progressPromise = beginOmsStartup('uk'); });
    await page.locator('#oms-startup-status').waitFor();
    assert.equal(await page.locator('.brand').isVisible(), true, 'real login artwork remains visible behind progress');
    await page.waitForFunction(() => document.querySelector('#oms-startup-timer').textContent.endsWith('00:01'));
    await page.screenshot({ path: 'build/startup-login-progress.png' });
    await page.waitForFunction(() => document.querySelector('#oms-startup-percent').textContent === '88%');

    await page.goto(`${origin}/app.html`);
    await page.waitForFunction(() => Number(document.querySelector('#oms-startup-percent').textContent.replace('%', '')) >= 93);
    assert.equal(await page.locator('.oms-startup-brand').isVisible(), true, 'matching login backdrop survives document handoff');
    await page.screenshot({ path: 'build/startup-app-progress.png' });
    await page.evaluate(() => completeOmsStartup());
    await page.waitForFunction(() => !document.querySelector('#oms-loading-overlay.visible'));
    assert.equal(await page.evaluate(() => sessionStorage.getItem('oms.startup')), null);

    await page.goto(`${origin}/`);
    await page.evaluate(() => { window.progressPromise = beginOmsStartup('en'); });
    await page.goto(`${origin}/app.html`);
    await page.getByText('Online Monitoring System', { exact: true }).waitFor();
    assert.match(await page.locator('#oms-startup-timer').textContent(), /^Loading time:/);
    console.log('PASS startup progress: login backdrop, real asset progress, timer and Compose handoff');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
