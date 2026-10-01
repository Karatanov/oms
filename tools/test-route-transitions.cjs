// Exercises the actual compiled Compose application, not a replacement HTML UI.
const assert = require('node:assert/strict');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { chromium } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));
const origin = 'http://127.0.0.1:18088';

(async () => {
  const server = spawn(process.execPath, ['tools/serve-dashboard-smoke.mjs'], { env: process.env });
  server.stderr.pipe(process.stderr);
  let browser;
  try {
    await new Promise((resolve, reject) => {
      server.stdout.once('data', resolve);
      server.once('exit', code => reject(new Error(`Fixture exited: ${code}`)));
    });
    browser = await chromium.launch();
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', error => errors.push(String(error)));
    let procurementDelay = 0, procurementFailure = false;
    await page.route('**/api/v1/procurements', async route => {
      const fail = procurementFailure;
      await new Promise(resolve => setTimeout(resolve, procurementDelay));
      try { await route.fulfill({ status: fail ? 503 : 200, contentType: 'application/json', body: fail ? '{}' : '[]' }); }
      catch { /* A superseded Compose request can be cancelled. */ }
    });
    await page.route('**/api/v1/financials', route => route.fulfill({ contentType: 'application/json', body: '[]' }));
    const ready = section => page.waitForFunction(section => {
      const pane = document.querySelector('#oms-route-transition');
      return pane?.hidden && pane.dataset.displayedSection === section;
    }, section, { timeout: 120000 });
    const navigate = route => page.evaluate(route => { location.hash = route; }, route);
    await page.goto(`${origin}/app.html#projects`);
    await ready('Projects');
    assert.equal(await page.locator('#oms-loading-overlay.visible').count(), 0);
    const canvas = await page.locator('#compose-host canvas').first().elementHandle();

    // Slow transition: retain the old composition, bound the overlay to content.
    procurementDelay = 1400;
    await navigate('procurement');
    await page.waitForSelector('#oms-route-transition.indicating:not([hidden])');
    const state = await page.locator('#oms-route-transition').evaluate(el => ({
      previous: el.dataset.displayedSection, requested: el.dataset.requestedSection,
      left: el.getBoundingClientRect().left, right: el.getBoundingClientRect().right
    }));
    assert.equal(state.previous, 'Projects');
    assert.equal(state.requested, 'Procurement');
    assert.ok(state.left >= 70 && state.right <= 1441, 'Sidebar must not be covered');
    assert.ok(await canvas.evaluate(el => el.isConnected), 'Root canvas must remain mounted');
    await ready('Procurement');

    // Error keeps the preceding section; Retry starts a new attempt.
    await navigate('projects'); await ready('Projects');
    procurementDelay = 100; procurementFailure = true;
    await navigate('procurement');
    const retry = page.locator('#oms-route-transition button');
    await retry.waitFor({ state: 'visible' });
    assert.equal(await page.locator('#oms-route-transition').getAttribute('data-displayed-section'), 'Projects');
    assert.equal(await retry.textContent(), 'Повторити');
    procurementFailure = false;
    await retry.click(); await ready('Procurement');

    // Superseded responses must neither promote a screen nor dismiss another loader.
    await navigate('projects'); await ready('Projects');
    procurementDelay = 1800;
    await navigate('procurement');
    await page.waitForSelector('#oms-route-transition.indicating:not([hidden])');
    await navigate('documents'); await ready('Documents');
    await page.waitForTimeout(2100);
    await ready('Documents');
    assert.equal(await page.locator('#oms-loading-overlay.visible').count(), 0);

    // Browser history uses the same transaction path.
    procurementDelay = 0;
    await page.goBack(); await ready('Procurement');
    await page.goForward(); await ready('Documents');

    // Fast/cached transition: no delayed visual indicator should appear.
    await page.evaluate(() => {
      window.routeFlashes = 0;
      new MutationObserver(() => {
        const el = document.querySelector('#oms-route-transition');
        if (!el.hidden && el.classList.contains('indicating')) window.routeFlashes++;
      }).observe(document.querySelector('#oms-route-transition'), { attributes: true });
    });
    await navigate('project-new'); await ready('Create Project');
    assert.equal(await page.evaluate(() => window.routeFlashes), 0);

    // Native operation feedback does not resurrect the startup/fullscreen overlay.
    await page.evaluate(() => { showOmsLoading('Photo upload'); hideOmsLoading(); });
    assert.equal(await page.locator('#oms-loading-overlay.visible').count(), 0);
    await ready('Create Project');
    assert.deepEqual(errors, []);
    console.log('PASS actual Compose routes: slow/error/retry/race/history/fast/local loading');
  } finally {
    await browser?.close();
    server.kill();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
