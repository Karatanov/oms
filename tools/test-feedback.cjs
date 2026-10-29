// Tests the compiled Compose form against a local fixture; never writes to Google.
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs/promises');
const { spawn } = require('node:child_process');
const { chromium } = require(path.resolve(process.env.OMS_PLAYWRIGHT_PATH || 'build/date-picker-test/node_modules/playwright'));
(async () => {
  const server = spawn(process.execPath, ['tools/serve-dashboard-smoke.mjs'], { env: process.env });
  server.stderr.pipe(process.stderr);
  let browser;
  try {
    await new Promise((resolve, reject) => { server.stdout.once('data', resolve); server.once('exit', code => reject(new Error(`Fixture exited: ${code}`))); });
    browser = await chromium.launch(process.env.CHROME_BIN ? { executablePath: process.env.CHROME_BIN } : process.platform === 'win32' ? { channel: 'chrome' } : {});
    await fs.mkdir('build/feedback-screenshots', { recursive: true });
    for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }, { width: 320, height: 568 }]) {
      const page = await browser.newPage({ viewport });
      const errors = [], submitted = [];
      page.on('pageerror', error => errors.push(String(error)));
      await page.route('**/api/v1/feedback', route => {
        if (route.request().method() === 'GET') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ configured: true, date: '2026-10-29' }) });
        submitted.push(route.request().postDataJSON());
        return route.fulfill({ status: submitted.length === 1 ? 503 : 201, contentType: 'application/json', body: JSON.stringify(submitted.length === 1 ? { error: 'FEEDBACK_UNAVAILABLE' } : { number: 123, date: '2026-10-29' }) });
      });
      await page.goto('http://127.0.0.1:18088/app.html#dashboard');
      await page.waitForFunction(() => document.getElementById('oms-route-transition')?.hidden && !document.querySelector('#oms-loading-overlay.visible'), null, { timeout: 120000 });
      const availability = page.waitForResponse(r => r.url().endsWith('/api/v1/feedback'));
      await page.mouse.click(viewport.width > 1000 ? 120 : 26, viewport.height - (viewport.width > 1000 ? 34 : 28));
      await availability;
      await page.waitForTimeout(300);
      await page.screenshot({ path: `build/feedback-screenshots/form-${viewport.width}.png` });
      const desktop = viewport.width > 1000;
      if (desktop) {
        await page.mouse.click(600, 386); await page.waitForTimeout(200); await page.mouse.click(600, 480); await page.waitForTimeout(200);
        await page.mouse.click(600, 481); await page.waitForTimeout(200); await page.mouse.click(600, 575); await page.waitForTimeout(200);
      }
      const textY = desktop ? 575 : viewport.width === 390 ? 500 : 390;
      await page.mouse.click(desktop ? 600 : 100, textY);
      await page.keyboard.insertText('Feedback UI regression: test description');
      // Scroll the short viewport to reach the actions, as a phone user would.
      if (viewport.width === 320) { await page.mouse.move(160, 400); await page.mouse.wheel(0, 700); await page.waitForTimeout(300); }
      await page.waitForTimeout(300);
      await page.screenshot({ path: `build/feedback-screenshots/typed-${viewport.width}.png` });
      const firstSend = page.waitForResponse(r => r.url().endsWith('/api/v1/feedback') && r.request().method() === 'POST', { timeout: 10000 });
      await page.mouse.click(desktop ? 920 : viewport.width === 390 ? 300 : 240, desktop ? 738 : viewport.width === 390 ? 670 : 508);
      await firstSend;
      assert.equal(submitted.length, 1);
      assert.equal(submitted[0].page, desktop ? 'Projects' : 'Dashboard');
      assert.equal(submitted[0].type, desktop ? 'Improvement' : 'Correction');
      assert.equal(submitted[0].description, 'Feedback UI regression: test description');
      await page.waitForTimeout(300);
      if (viewport.width === 320) { await page.mouse.move(160, 400); await page.mouse.wheel(0, 700); await page.waitForTimeout(300); }
      await page.screenshot({ path: `build/feedback-screenshots/retry-${viewport.width}.png` });
      const retry = page.waitForResponse(r => r.url().endsWith('/api/v1/feedback') && r.request().method() === 'POST', { timeout: 10000 });
      await page.mouse.click(desktop ? 920 : viewport.width === 390 ? 300 : 240, desktop ? 780 : viewport.width === 390 ? 730 : 508);
      await retry;
      assert.deepEqual(submitted[0], submitted[1], 'Retry must retain the original request ID and content');
      await page.waitForTimeout(300);
      await page.screenshot({ path: `build/feedback-screenshots/success-${viewport.width}.png` });
      assert.deepEqual(errors, []);
      console.log(`PASS feedback form, submission and retry: ${viewport.width}x${viewport.height}`);
      await page.close();
    }
  } finally { await browser?.close(); server.kill(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
