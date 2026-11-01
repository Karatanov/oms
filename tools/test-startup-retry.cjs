const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));

const origin = 'https://startup-retry.oms.test';
const startup = fs.readFileSync('composeApp/src/webMain/resources/startup.js', 'utf8');
const auth = fs.readFileSync('composeApp/src/webMain/resources/auth.js', 'utf8');
const login = fs.readFileSync('composeApp/src/webMain/resources/login.html', 'utf8')
  .replace(/<script src="startup\.js[^>]*><\/script>/, `<script>${startup}</script>`);
const app = fs.readFileSync('composeApp/src/webMain/resources/index.html', 'utf8')
  .replace(/<script src="auth\.js[^>]*><\/script>/, `<script>${auth}</script>`)
  .replace(/<script src="startup\.js[^>]*><\/script>/, `<script>${startup}</script>`);

(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.OMS_CHROMIUM_EXECUTABLE || undefined
  });
  try {
    const page = await browser.newPage();
    let firstBundle = true;
    let retriedBundleUrl = '';
    await page.addInitScript(() => sessionStorage.setItem('oms.startup', JSON.stringify({
      active: true, startedAt: Date.now(), progress: 93, locale: 'uk'
    })));
    await page.route(`${origin}/**`, route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/') return route.fulfill({ contentType: 'text/html', body: login });
      if (url.pathname === '/app.html') return route.fulfill({ contentType: 'text/html', body: app });
      if (url.pathname === '/assets-manifest.json') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({
        assets: [{ url: 'composeApp.js', bytes: 16 }]
      }) });
      if (url.pathname === '/composeApp.js') {
        if (firstBundle) {
          firstBundle = false;
          return route.fulfill({ contentType: 'application/javascript', body: 'throw new Error("fixture startup failure")' });
        }
        retriedBundleUrl = route.request().url();
        return route.fulfill({ contentType: 'application/javascript', body: 'window.completeOmsStartup()' });
      }
      return route.fulfill({ status: 204 });
    });

    await page.goto(`${origin}/app.html#dashboard`);
    await page.getByRole('button', { name: 'Повторити' }).waitFor();
    await page.getByRole('button', { name: 'Повторити' }).click();
    await page.waitForFunction(() => !document.querySelector('#oms-loading-overlay.visible'));
    assert.match(retriedBundleUrl, /composeApp\.js\?omsRetry=\d+/, 'retry must execute a cache-busting bundle URL');
    assert.equal(new URL(page.url()).hash, '#dashboard', 'retry preserves the requested route');

    // An expired server session responds to /app.html with login.html. A stale
    // hand-off flag must never cover that real login form with a dead retry UI.
    await page.goto(`${origin}/`);
    await page.evaluate(() => sessionStorage.setItem('oms.startup', JSON.stringify({ active: true, startedAt: Date.now(), progress: 93, locale: 'uk' })));
    await page.reload();
    assert.equal(await page.locator('#oms-startup-status').isHidden(), true, 'login must clear a stale startup hand-off');
    assert.equal(await page.evaluate(() => sessionStorage.getItem('oms.startup')), null, 'stale startup state must be removed on login');
    console.log('PASS startup retry: fresh bundle URL, successful handoff, route preserved');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
