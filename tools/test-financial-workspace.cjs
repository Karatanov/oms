// Real production Compose executable against a local, read-only API fixture.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { chromium } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));
(async () => {
  const server = spawn(process.execPath, ['tools/serve-dashboard-smoke.mjs'], { env: process.env });
  server.stderr.pipe(process.stderr);
  let browser;
  try {
    await new Promise((resolve, reject) => { server.stdout.once('data', resolve); server.once('exit', reject); });
    browser = await chromium.launch(process.env.CHROME_BIN ? { executablePath: process.env.CHROME_BIN } : {});
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [], queries = [];
    page.on('pageerror', error => errors.push(String(error)));
    page.on('request', request => { if (request.url().includes('/financials/progress')) queries.push(new URL(request.url()).searchParams.get('subprojectUuid')); });
    const ready = () => page.waitForFunction(() => document.getElementById('oms-route-transition')?.dataset.displayedSection === 'Financial' && document.getElementById('oms-route-transition')?.hidden, null, { timeout: 120000 });
    const click = async id => {
      await page.waitForFunction(id => document.getElementById(id)?.getBoundingClientRect().width > 0, id);
      const bounds = await page.locator('#' + id).boundingBox();
      await page.mouse.click(bounds.x + Math.min(80, bounds.width / 2), bounds.y + bounds.height / 2);
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
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    await queryIs('smoke-lviv');
    assert.ok(queries.includes('smoke-lviv'), 'Global selector must scope the financial API');
    fs.mkdirSync('build/financial-workspace-smoke', { recursive: true });
    await page.screenshot({ path: 'build/financial-workspace-smoke/filtered.png', fullPage: true });
    await click('financial-reset-filters');
    await queryIs(null);
    assert.equal(queries.at(-1), null, 'Reset must remove backend filter parameters');
    await click('financial-subproject-filter');
    await page.keyboard.type('TEST-02'); await page.keyboard.press('ArrowDown'); await page.keyboard.press('Enter');
    await queryIs('smoke-lviv');
    assert.equal(queries.at(-1), 'smoke-lviv');
    await page.reload(); await ready();
    await queryIs(null);
    assert.equal(queries.at(-1), null, 'Browser reload must reset filters');
    await page.screenshot({ path: 'build/financial-workspace-smoke/default.png', fullPage: true });
    assert.deepEqual(errors, []);
    console.log('PASS financial workspace: real production startup, subproject selector, API scope/reset, reload, screenshots, no runtime errors');
  } finally { if (browser) await browser.close(); server.kill(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
