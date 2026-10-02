// Native overlay contract; end-to-end Compose transitions are tested separately.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));
const origin = 'https://app.oms.test';
const source = fs.readFileSync('composeApp/src/webMain/resources/index.html', 'utf8')
  .replace(/<script src="auth\.js[^>]*><\/script>/, `<script>${fs.readFileSync('composeApp/src/webMain/resources/auth.js', 'utf8')}</script>`)
  .replace(/<script type="application\/javascript" src="composeApp[^>]*><\/script>/, '');
(async () => {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: { width: 1200, height: 800 } });
    await page.route(`${origin}/**`, route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/app.html') return route.fulfill({ contentType: 'text/html', body: source });
      if (url.pathname === '/styles.css') return route.fulfill({ contentType: 'text/css', body: fs.readFileSync('composeApp/src/webMain/resources/styles.css', 'utf8') });
      return route.fulfill({ contentType: 'application/json', body: '{}' });
    });
    await page.goto(`${origin}/app.html`);
    await page.evaluate(() => {
      finishOmsStartup();
      setOmsPaneBounds('oms-route-transition', 232, 0, 968, 800);
      window.retries = 0;
      window.datePickerClosed = false;
      window.omsCloseDatePicker = () => { window.datePickerClosed = true; };
      setOmsRouteTransition(true, false, false, false, 'B', 'A', () => window.retries++);
    });
    assert.equal(await page.evaluate(() => window.datePickerClosed), true);
    assert.equal(await page.locator('.oms-route-card').isVisible(), false);
    assert.equal(await page.locator('#oms-route-transition').evaluate(el => el.getBoundingClientRect().left), 232);
    assert.equal(await page.locator('#map-pane').evaluate(el => el.inert), true);
    await page.evaluate(() => setOmsRouteTransition(true, true, false, false, 'B', 'A', () => window.retries++));
    assert.equal(await page.locator('.oms-route-message').textContent(), 'Завантаження розділу…');
    await page.evaluate(() => setOmsRouteTransition(true, true, true, true, 'B', 'A', () => window.retries++));
    await page.getByRole('button', { name: 'Retry', exact: true }).click();
    assert.equal(await page.evaluate(() => window.retries), 1);
    assert.match(await page.locator('.oms-route-message').textContent(), /Could not load/);
    await page.evaluate(() => {
      setOmsRouteTransition(false, false, false, true, 'B', 'B', () => {});
      showOmsLoading('Upload'); showOmsLoading('Import'); hideOmsLoading();
    });
    assert.equal(await page.locator('#oms-route-transition').isVisible(), false);
    assert.equal(await page.locator('#oms-loading-overlay.visible').count(), 0);
    assert.equal(await page.locator('#oms-operation-progress.visible').count(), 1);
    assert.equal(await page.locator('#map-pane').evaluate(el => el.inert), false);
    await page.evaluate(() => hideOmsLoading());
    assert.equal(await page.locator('#oms-operation-progress.visible').count(), 0);
    console.log('PASS scoped overlay: bounds, delay, UK/EN, retry, inert panes, independent operation progress');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
