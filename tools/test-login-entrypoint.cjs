const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));

const login = fs.readFileSync('composeApp/src/webMain/resources/login.html', 'utf8');

(async () => {
  const browser = await chromium.launch();
  try {
    for (const deployment of [
      { origin: 'https://ua-oms.com', entry: '/', app: 'https://ua-oms.com/app.html', api: 'https://ua-oms.com/api/v1' },
      { origin: 'https://karatanov.github.io', entry: '/oms/', app: 'https://karatanov.github.io/oms/app.html', api: 'https://oms-3j46.onrender.com/api/v1' }
    ]) {
      const context = await browser.newContext();
      const page = await context.newPage();
      const entry = deployment.origin + deployment.entry;
      const requests = [];
      page.on('request', request => requests.push(request.url()));
      await context.route(`${deployment.origin}/**`, route => {
        const pathname = new URL(route.request().url()).pathname;
        if (pathname === '/' || pathname === '/oms/') return route.fulfill({ contentType: 'text/html', body: login });
        if (pathname.endsWith('/app.html')) return route.fulfill({ contentType: 'text/html', body: '<title>App fixture</title>' });
        return route.fulfill({ status: 404 });
      });
      await context.route(`${deployment.api}/auth/login`, route => route.fulfill({
        contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': deployment.origin, 'Access-Control-Allow-Credentials': 'true' },
        body: JSON.stringify({ browserBearerSupported: true, accessToken: 'fixture-token' })
      }));
      await page.goto(entry);
      assert.equal(requests.some(url => /composeApp.*\.js/.test(url)), false, 'login must not preload Compose');
      await page.locator('#username').fill('fixture');
      await page.locator('#password').fill('fixture');
      await Promise.all([page.waitForURL(deployment.app), page.locator('#submit').click()]);
      assert.equal(requests.includes(`${deployment.api}/auth/login`), true);
      assert.equal(await page.evaluate(() => sessionStorage.getItem('oms.accessToken')), 'fixture-token');
      await context.close();
      console.log(`PASS lightweight login routing: ${entry}`);
    }
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
