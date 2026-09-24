// Exercise the actual DOM bridge in both engines, not a copy of its implementation.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { chromium, webkit } = require('../build/date-picker-test/node_modules/playwright');
const html = fs.readFileSync('composeApp/src/webMain/resources/index.html', 'utf8');
const start = html.indexOf('function openNativeDatePicker(');
const end = html.indexOf('function formatOmsDate(', start);
const bridge = html.slice(start, end);
(async () => {
  for (const engine of [chromium, webkit]) {
    const browser = await engine.launch();
    try {
      for (const scale of [1, 2]) {
        const page = await browser.newPage({ viewport: { width: 800, height: 600 }, deviceScaleFactor: scale });
        await page.setContent('<button id="open">Calendar</button>');
        await page.addScriptTag({ content: 'function currentIsoDate(){return "2026-09-24";}' + bridge });
        await page.evaluate(() => {
          document.querySelector('#open').onclick = () => openNativeDatePicker('2026-09-24', 300, 180, v => window.selected = v);
        });
        await page.click('#open');
        const rect = await page.locator('.oms-native-date-input').boundingBox();
        assert.equal(rect.x, 300); assert.equal(rect.y, 180);
        await page.locator('.oms-native-date-input').fill('2026-10-05');
        assert.equal(await page.evaluate(() => window.selected), '2026-10-05');
        // Test fallback without showPicker, including viewport edges and Escape.
        await page.evaluate(() => {
          HTMLInputElement.prototype.showPicker = undefined;
          openNativeDatePicker('', 790, 590, () => {});
        });
        const edge = await page.locator('.oms-native-date-input').boundingBox();
        assert.ok(edge.x >= 0 && edge.x + edge.width <= 800);
        assert.ok(edge.y >= 0 && edge.y + edge.height <= 600);
        await page.keyboard.press('Escape');
        assert.equal(await page.locator('.oms-native-date-input').count(), 0);
        await page.evaluate(() => openNativeDatePicker('', 200, 200, () => {}));
        await page.evaluate(() => window.dispatchEvent(new Event('scroll')));
        assert.equal(await page.locator('.oms-native-date-input').count(), 0);
        await page.close();
      }
      console.log(`PASS date bridge: ${engine.name()} (scale 1/2, bounds, select, fallback, close)`);
    } finally { await browser.close(); }
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
