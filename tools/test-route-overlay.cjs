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
    // The photo pane must not paint or intercept events over an in-canvas tooltip.
    await page.evaluate(() => {
      const host = document.getElementById('compose-host');
      host.style.background = 'white';
      const tooltip = document.createElement('div');
      tooltip.id = 'tooltip-test';
      Object.assign(tooltip.style, { position: 'absolute', left: '300px', top: '490px', width: '200px', height: '100px', background: 'black' });
      host.appendChild(tooltip);
      const photos = document.getElementById('dashboard-photo-slider');
      photos.classList.add('visible');
      setOmsPaneBounds(photos.id, 250, 500, 700, 200);
      setOmsTooltipBounds(300, 490, 200, 100);
    });
    const hit = (x, y) => page.evaluate(({ x, y }) => document.elementFromPoint(x, y)?.id, { x, y });
    assert.equal(await hit(350, 550), 'tooltip-test', 'tooltip stays above overlapping photos');
    assert.equal(await hit(600, 550), 'dashboard-photo-slider', 'other photos remain interactive');
    await page.evaluate(() => setOmsPaneBounds('dashboard-photo-slider', 280, 520, 600, 160));
    assert.equal(await hit(350, 550), 'tooltip-test', 'clipping follows layout/viewport changes');
    for (let i = 0; i < 10; i++) {
      await page.evaluate(() => setOmsTooltipBounds(0, 0, 0, 0));
      assert.equal(await hit(350, 550), 'dashboard-photo-slider', 'dismissal restores photos');
      await page.evaluate(() => setOmsTooltipBounds(300, 490, 200, 100));
      assert.equal(await hit(350, 550), 'tooltip-test');
    }
    await page.evaluate(() => setOmsTooltipBounds(10, 10, 100, 50));
    assert.equal(await page.locator('#dashboard-photo-slider').evaluate(el => el.style.clipPath), '', 'non-overlapping tooltip does not clip photos');
    await page.evaluate(() => setOmsTooltipBounds(0, 0, 0, 0));
    console.log('PASS tooltip/photo layering: overlap, native interactions, resize, dismissal, repeated toggling');
    console.log('PASS scoped overlay: bounds, delay, UK/EN, retry, inert panes, independent operation progress');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
