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
    const errors = [], queries = [];
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
    console.log('PASS financial workspace: real production startup, subproject selector, API scope/reset, reload, new user login, screenshots, no runtime errors');
  } catch (error) {
    fs.mkdirSync('build/financial-workspace-smoke', { recursive: true });
    if (page) await page.screenshot({ path: 'build/financial-workspace-smoke/failure.png', fullPage: true }).catch(() => {});
    if (page) fs.writeFileSync('build/financial-workspace-smoke/failure-dom.json', JSON.stringify(await page.evaluate(() => [...document.querySelectorAll('[id]')].map(el => ({ id: el.id, rect: el.getBoundingClientRect().toJSON() }))), null, 2));
    throw error;
  } finally { if (browser) await browser.close(); server.kill(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
