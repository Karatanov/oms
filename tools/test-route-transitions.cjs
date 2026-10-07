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
  let page;
  const pendingResponses = [];
  try {
    await new Promise((resolve, reject) => {
      server.stdout.once('data', resolve);
      server.once('exit', code => reject(new Error(`Fixture exited: ${code}`)));
    });
    browser = await chromium.launch();
    page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', error => { errors.push(String(error)); console.error('Browser:', String(error)); });
    let holdProcurement = false, procurementFailure = false;
    await page.route('**/api/v1/procurements', async route => {
      const fail = procurementFailure;
      if (holdProcurement) await new Promise(resolve => pendingResponses.push(resolve));
      try { await route.fulfill({ status: fail ? 503 : 200, contentType: 'application/json', body: fail ? '{}' : '[]' }); }
      catch { /* A superseded Compose request can be cancelled. */ }
    });
    await page.route('**/api/v1/financials', route => route.fulfill({ contentType: 'application/json', body: '[]' }));
    const ready = section => page.waitForFunction(section => {
      const pane = document.querySelector('#oms-route-transition');
      return pane?.hidden && pane.dataset.displayedSection === section;
    }, section, { timeout: 120000 });
    const navigate = route => page.evaluate(route => {
      pushOmsRoute(route);
      window.dispatchEvent(new PopStateEvent('popstate'));
    }, route);
    await page.goto(`${origin}/app.html#projects`);
    await ready('Projects');
    // Route readiness and the startup-overlay fade are intentionally separate:
    // Compose can publish the first screen before the short handoff animation
    // has finished. Wait for that handoff so a faster CI runner cannot turn
    // the assertion below into a timing race.
    await page.waitForFunction(() => !document.querySelector('#oms-loading-overlay.visible'));
    assert.equal(await page.locator('#oms-loading-overlay.visible').count(), 0);
    const canvas = await page.locator('#compose-host canvas').first().elementHandle();
    await page.mouse.click(230, 10); // Real user activation enables browser Back/Forward events.

    // Slow transition: retain the old composition, bound the overlay to content.
    const contentClip = { x: 240, y: 0, width: 1200, height: 800 };
    const previousPixels = await page.screenshot({ clip: contentClip });
    holdProcurement = true;
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
    await page.locator('#oms-route-transition').evaluate(el => { el.style.visibility = 'hidden'; });
    const retainedPixels = await page.screenshot({ clip: contentClip });
    await page.locator('#oms-route-transition').evaluate(el => { el.style.visibility = ''; });
    assert.ok(previousPixels.equals(retainedPixels), 'Previous content must remain painted without replacement or layout shifts');
    pendingResponses.splice(0).forEach(resolve => resolve());
    holdProcurement = false;
    await ready('Procurement');

    // Error keeps the preceding section; Retry starts a new attempt.
    await navigate('projects'); await ready('Projects');
    procurementFailure = true;
    await navigate('procurement');
    const retry = page.locator('#oms-route-transition button');
    await retry.waitFor({ state: 'visible' });
    assert.equal(await page.locator('#oms-route-transition').getAttribute('data-displayed-section'), 'Projects');
    assert.equal(await retry.textContent(), 'Повторити');
    procurementFailure = false;
    await retry.click(); await ready('Procurement');

    // Superseded responses must neither promote a screen nor dismiss another loader.
    await navigate('projects'); await ready('Projects');
    holdProcurement = true;
    await navigate('procurement');
    await page.waitForSelector('#oms-route-transition.indicating:not([hidden])');
    await navigate('documents'); await ready('Documents');
    pendingResponses.splice(0).forEach(resolve => resolve());
    holdProcurement = false;
    await page.waitForTimeout(300);
    await ready('Documents');
    assert.equal(await page.locator('#oms-loading-overlay.visible').count(), 0);

    // Browser history uses the same transaction path.
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

    // A deliberately unfinished analytics widget must not gate its registry.
    let releaseAnalytics;
    await page.route('**/api/v1/inspection-reports/analytics', async route => {
      await new Promise(resolve => { releaseAnalytics = resolve; pendingResponses.push(resolve); });
      await route.fulfill({ contentType: 'application/json', body: '{"monthlyInspectionCounts":[],"monthlyEshsViolations":[]}' }).catch(() => {});
    });
    await navigate('inspections'); await ready('Inspection Reports');
    assert.ok(releaseAnalytics, 'Analytics request should still be pending when the registry is ready');
    releaseAnalytics();

    let releasePhotos;
    let releaseOverview;
    let overviewRequested;
    const overviewRequestStarted = new Promise(resolve => { overviewRequested = resolve; });
    let photoRequested;
    const photoRequestStarted = new Promise(resolve => { photoRequested = resolve; });
    const awaitRequest = async (request, name) => {
      let timer;
      try {
        await Promise.race([request, new Promise((_, reject) => {
          timer = setTimeout(() => reject(new Error(`Dashboard did not request ${name}`)), 10000);
        })]);
      } finally { clearTimeout(timer); }
    };
    await page.route('**/api/v1/dashboard/overview**', async route => {
      await new Promise(resolve => { releaseOverview = resolve; pendingResponses.push(resolve); overviewRequested(); });
      await route.fulfill({
      contentType: 'application/json', body: JSON.stringify({ recentInspections: [
        { uuid: 'pending-photo', inspectionCode: 'PHOTO-TEST', inspectionDate: '2026-09-01', status: 'draft' }
      ] })
      }).catch(() => {});
    });
    await page.route('**/api/v1/inspection-reports/pending-photo/photos**', async route => {
      await new Promise(resolve => { releasePhotos = resolve; pendingResponses.push(resolve); photoRequested(); });
      await route.fulfill({ contentType: 'application/json', body: '[]' }).catch(() => {});
    });
    await navigate('dashboard'); await ready('Dashboard');
    // Dashboard is usable before either overview data or its photos arrive.
    // Await request events, not arbitrary delays or the old readiness timing.
    await awaitRequest(overviewRequestStarted, 'overview');
    assert.ok(releaseOverview, 'Dashboard must render while overview is pending');
    releaseOverview();
    await awaitRequest(photoRequestStarted, 'photos');
    await ready('Dashboard');
    assert.ok(releasePhotos, 'Dashboard must render without waiting for photos');
    assert.equal(await page.locator('#oms-loading-overlay.visible').count(), 0);
    releasePhotos();
    assert.deepEqual(errors, []);
    console.log('PASS actual Compose routes: slow/error/retry/race/history/fast/local loading');
  } catch (error) {
    if (page) {
      console.error('Route state:', await page.locator('#oms-route-transition').evaluate(el => ({
        html: el.outerHTML, bounds: el.getBoundingClientRect().toJSON(),
        style: { display: getComputedStyle(el).display, visibility: getComputedStyle(el).visibility },
        hash: location.hash
      })).catch(() => null));
      await page.screenshot({ path: 'build/route-transition-failure.png' }).catch(() => {});
    }
    throw error;
  } finally {
    pendingResponses.splice(0).forEach(resolve => resolve());
    await browser?.close();
    server.kill();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
