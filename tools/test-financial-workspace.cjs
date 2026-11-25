// Real production Compose executable against a local, read-only API fixture.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { chromium } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));
(async () => {
  const server = spawn(process.execPath, ['tools/serve-dashboard-smoke.mjs'], { env: process.env });
  server.stderr.pipe(process.stderr);
  let browser, page;
  try {
    await new Promise((resolve, reject) => { server.stdout.once('data', resolve); server.once('exit', reject); });
    browser = await chromium.launch(process.env.CHROME_BIN ? { executablePath: process.env.CHROME_BIN } : {});
    page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    // Keep Chromium's chooser interception active across both uploads.
    page.on('filechooser', () => {});
    const errors = [], queries = [];
    let uploads = 0, snapshotReads = 0, refreshFinished = false;
    let completeRefresh = false;
    // Intercept mutations in the browser: the HTTP fixture stays read-only.
    // These bytes only exercise the file chooser and multipart transport, not
    // the server's separately tested XLSX parser.
    await page.route('**/api/v1/financials/payment-monitoring/refresh/upload', async route => {
      uploads++;
      assert.match(route.request().headers()['content-type'], /multipart\/form-data/);
      await route.fulfill(uploads === 1 ? {
        json: { id: 'local-refresh', status: 'RUNNING', progress: 10, phase: 'READING' }
      } : { status: 503, json: { message: 'Local fixture: source unavailable' } });
    });
    await page.route('**/api/v1/financials/payment-monitoring/refresh/local-refresh', async route => {
      refreshFinished = completeRefresh;
      await route.fulfill({ json: {
        id: 'local-refresh', status: completeRefresh ? 'COMPLETED' : 'RUNNING',
        progress: completeRefresh ? 100 : 50, phase: 'READING', importedCount: completeRefresh ? 2 : null
      } });
    });
    await page.route('**/api/v1/financials/payment-monitoring', async route => {
      snapshotReads++;
      const response = await route.fetch();
      const snapshot = await response.json();
      if (refreshFinished) {
        snapshot.transactions[1].amountUah = 750;
        snapshot.summary.amountPaidTotalUah = 1750;
      }
      await route.fulfill({ json: snapshot });
    });
    page.on('pageerror', error => errors.push(String(error)));
    page.on('request', request => { if (request.url().includes('/financials/progress')) queries.push(new URL(request.url()).searchParams.get('subprojectUuid')); });
    const ready = () => page.waitForFunction(() => document.getElementById('oms-route-transition')?.dataset.displayedSection === 'Financial Monitoring' && document.getElementById('oms-route-transition')?.hidden, null, { timeout: 120000 });
    const click = async id => {
      // Desktop Compose does not expose a stable DOM semantics tree. Hit the
      // real painted controls at this test's fixed 1440x1000 viewport.
      if (id === 'financial-subproject-filter') await page.mouse.click(400, 120);
      else if (id === 'financial-reset-filters') await page.mouse.click(1360, 120);
      else throw Error('Unknown financial control: ' + id);
    };
    const queryIs = async expected => {
      const deadline = Date.now() + 20000;
      while (queries.at(-1) !== expected && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 50));
      assert.equal(queries.at(-1), expected);
    };
    await page.goto('http://127.0.0.1:18088/app.html#financial');
    await ready();
    await page.waitForFunction(() => !document.querySelector('#oms-loading-overlay.visible'));
    await click('financial-subproject-filter');
    await page.keyboard.type('TEST-02');
    await page.waitForTimeout(100);
    await page.mouse.click(400, 174); // Select the actual matching option.
    await queryIs('smoke-lviv');
    assert.ok(queries.includes('smoke-lviv'), 'Global selector must scope the financial API');
    fs.mkdirSync('build/financial-workspace-smoke', { recursive: true });
    await page.screenshot({ path: 'build/financial-workspace-smoke/filtered.png', fullPage: true });
    await page.waitForTimeout(300); // Let the scoped canvas finish its frame.
    await page.mouse.click(1350, 44); // Page-level Update data action.
    await page.waitForTimeout(500);
    await page.screenshot({ path: 'build/financial-workspace-smoke/refresh-confirmation.png', fullPage: true });
    const chooseWorkbook = async () => {
      const button = page.getByRole('button', { name: 'Завантажити XLSX', exact: true });
      await button.waitFor({ state: 'visible' });
      const rect = await button.boundingBox();
      await page.mouse.click(rect.x + rect.width / 2, rect.y + rect.height / 2);
      await page.locator('body > input[type=file][accept^=".xls,.xlsx"]').setInputFiles({
        name: 'local-fixture.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        buffer: Buffer.from('browser transport fixture')
      });
    };
    const beforeRefresh = snapshotReads, beforeQueries = queries.length;
    await chooseWorkbook();
    await page.waitForResponse(response => response.url().endsWith('/refresh/local-refresh'));
    await page.mouse.click(1350, 44); // A running operation must not start another.
    await page.waitForTimeout(200);
    assert.equal(uploads, 1);
    await page.screenshot({ path: 'build/financial-workspace-smoke/refresh-running.png', fullPage: true });
    completeRefresh = true;
    await page.waitForResponse(response => response.url().endsWith('/financials/payment-monitoring'));
    await page.waitForTimeout(200);
    assert.equal(snapshotReads, beforeRefresh + 1, 'Completed refresh must reload the snapshot exactly once');
    assert.ok(queries.length > beforeQueries, 'Completed refresh must update financial progress');
    assert.equal(queries.at(-1), 'smoke-lviv', 'Manual refresh must retain the selected subproject');
    await page.screenshot({ path: 'build/financial-workspace-smoke/refresh-completed.png', fullPage: true });
    await page.mouse.click(1350, 44); await page.waitForTimeout(500);
    await Promise.all([
      chooseWorkbook(),
      page.waitForResponse(response => response.url().endsWith('/refresh/upload') && response.status() === 503)
    ]);
    await page.waitForFunction(() => !document.querySelector('#oms-loading-overlay.visible'));
    await page.waitForTimeout(200);
    assert.equal(snapshotReads, beforeRefresh + 1, 'Failed upload must not reload or replace financial data');
    assert.equal(queries.at(-1), 'smoke-lviv', 'Failed refresh must retain the selected subproject');
    await page.screenshot({ path: 'build/financial-workspace-smoke/refresh-error.png', fullPage: true });
    // Failure must release the operation guard and let the user retry/cancel.
    await page.mouse.click(1350, 44); await page.waitForTimeout(500);
    await page.keyboard.press('Escape'); // Dismiss confirmation without another upload.
    await page.waitForTimeout(500);
    await click('financial-reset-filters');
    await queryIs(null);
    assert.equal(queries.at(-1), null, 'Reset must remove backend filter parameters');
    await click('financial-subproject-filter');
    await page.keyboard.type('TEST-02'); await page.keyboard.press('Enter');
    await queryIs('smoke-lviv');
    assert.equal(queries.at(-1), 'smoke-lviv');
    await page.reload(); await ready();
    await queryIs(null);
    assert.equal(queries.at(-1), null, 'Browser reload must reset filters');
    await page.screenshot({ path: 'build/financial-workspace-smoke/default.png', fullPage: true });
    // Exercise AppState's real logout/login lifecycle, not just a fresh tab.
    await page.route('**/api/v1/auth/login', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({
      user: { id: 2, username: 'second-ui-test', email: 'second@example.invalid', role: { id: 1, code: 'ADMIN', name: 'Admin' } }
    }) }));
    await click('financial-subproject-filter');
    await page.keyboard.type('TEST-02'); await page.keyboard.press('Enter'); await queryIs('smoke-lviv');
    await page.mouse.click(215, 918); // Sign out through the real sidebar control.
    await page.waitForFunction(() => window.location.hash === '#login');
    await page.waitForTimeout(200);
    await page.mouse.click(1080, 418); await page.keyboard.type('second-ui-test');
    await page.mouse.click(1080, 495); await page.keyboard.type('local-fixture-password');
    await page.mouse.click(1080, 620);
    await page.waitForFunction(() => document.getElementById('oms-route-transition')?.dataset.displayedSection === 'Dashboard' && document.getElementById('oms-route-transition')?.hidden, null, { timeout: 30000 });
    await page.mouse.click(120, 313); await ready(); await queryIs(null);
    await page.screenshot({ path: 'build/financial-workspace-smoke/new-login.png', fullPage: true });
    assert.deepEqual(errors, []);
    console.log('PASS financial workspace: production startup, selector, scoped refresh/error/retry, single reload, filter reset, browser reload, new login, no runtime errors');
  } catch (error) {
    fs.mkdirSync('build/financial-workspace-smoke', { recursive: true });
    if (page) await page.screenshot({ path: 'build/financial-workspace-smoke/failure.png', fullPage: true }).catch(() => {});
    if (page) fs.writeFileSync('build/financial-workspace-smoke/failure-dom.json', JSON.stringify(await page.evaluate(() => [...document.querySelectorAll('[id]')].map(el => ({ id: el.id, rect: el.getBoundingClientRect().toJSON() }))), null, 2));
    throw error;
  } finally { if (browser) await browser.close(); server.kill(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
