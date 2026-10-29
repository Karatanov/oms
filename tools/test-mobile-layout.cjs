// Exercise the compiled Compose UI against the read-only local API fixture.
// OMS_SMOKE_ASSETS must contain processed resources, a browser bundle and app.html.
const assert = require('node:assert/strict');
const path = require('node:path');
const { mkdir } = require('node:fs/promises');
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
    browser = await chromium.launch(process.env.CHROME_BIN ? { executablePath: process.env.CHROME_BIN } : {});
    await mkdir('build/mobile-layout-screenshots', { recursive: true });
    const viewports = [
      { width: 320, height: 568 }, { width: 320, height: 740 }, { width: 390, height: 844 },
      { width: 768, height: 1024 }, { width: 844, height: 390 },
      { width: 1440, height: 1000 },
    ];
    for (const viewport of viewports) {
      const mobile = viewport.width < 768 || (viewport.height < 500 && viewport.width < 1100);
      const page = await browser.newPage({ viewport, hasTouch: mobile });
      const errors = [];
      page.on('pageerror', error => errors.push(String(error)));
      const ready = section => page.waitForFunction(section => {
        const pane = document.querySelector('#oms-route-transition');
        return pane?.hidden && pane.dataset.displayedSection === section;
      }, section, { timeout: 120000 });
      const navigate = async (route, section) => {
        await page.evaluate(route => { pushOmsRoute(route); dispatchEvent(new PopStateEvent('popstate')); }, route);
        await ready(section);
      };
      await page.goto(`${origin}/app.html#map`);
      await ready('Projects Map');
      await page.waitForFunction(() => !document.querySelector('#oms-loading-overlay.visible'));
      const map = await page.locator('#map-pane').boundingBox();
      const minimumMapHeight = viewport.height < 500 ? 120 : Math.min(300, viewport.height - 350);
      assert.ok(map && map.height >= minimumMapHeight, `Usable map height at ${viewport.width}x${viewport.height}: ${JSON.stringify(map)}`);
      assert.ok(map.x >= 0 && map.x + map.width <= viewport.width + 1, 'Map must fit the viewport');
      const content = await page.locator('#oms-route-transition').evaluate(el => ({
        x: parseFloat(el.style.left), y: parseFloat(el.style.top),
      }));
      assert.equal(content.y, mobile ? 56 : 0, 'Content starts below the mobile toolbar');
      assert.equal(content.x, mobile ? 0 : (viewport.width < 1100 ? 72 : 248), 'Mobile content must not reserve a sidebar lane');
      await page.screenshot({ path: `build/mobile-layout-screenshots/map-${viewport.width}x${viewport.height}.png` });
      if (mobile) {
        // Real taps, rather than calling the state setter, verify the drawer's hit targets.
        await page.mouse.click(28, 28);
        await page.waitForFunction(() => document.body.classList.contains('mobile-navigation-open'));
        assert.equal(await page.locator('#map-pane').evaluate(el => getComputedStyle(el).visibility), 'hidden', 'Native map must not cover navigation');
        await page.keyboard.press('Escape');
        await page.waitForFunction(() => !document.body.classList.contains('mobile-navigation-open'));
        await page.mouse.click(28, 28);
        await page.waitForFunction(() => document.body.classList.contains('mobile-navigation-open'));
        await page.mouse.click(viewport.width - 12, 90);
        await page.waitForFunction(() => !document.body.classList.contains('mobile-navigation-open'));
        await page.mouse.click(28, 28);
        await page.waitForFunction(() => document.body.classList.contains('mobile-navigation-open'));
        await page.locator('#sidebar-projects').click();
        await ready('Projects');
        assert.equal(await page.evaluate(() => document.body.classList.contains('mobile-navigation-open')), false, 'Navigation closes the drawer');
        await navigate('map', 'Projects Map');
        // The filter toggle remains reachable in portrait and short landscape views.
        await page.mouse.click(viewport.width - 65, viewport.height < 500 ? 96 : 188);
        if (viewport.height < 500) {
          await page.waitForFunction(() => !document.body.classList.contains('map-visible'));
        } else {
          await page.waitForFunction(() => document.getElementById('map-pane').getBoundingClientRect().height < 300);
          const expanded = await page.locator('#map-pane').boundingBox();
          assert.ok(expanded.height >= 100, 'Portrait filters leave a usable map');
        }
        await page.screenshot({ path: `build/mobile-layout-screenshots/filters-${viewport.width}x${viewport.height}.png` });
        await page.mouse.click(viewport.width - 65, viewport.height < 500 ? 96 : 188);
        await page.waitForFunction(() => document.body.classList.contains('map-visible'));
        // Switch language with the toolbar button and recheck translated controls.
        await page.mouse.click(viewport.width - 28, 28);
      }
      for (const [route, section] of [
        ['projects', 'Projects'], ['project-new', 'Create Project'],
        ['dashboard', 'Dashboard'], ['procurement', 'Procurement'], ['documents', 'Documents'],
      ]) {
        await navigate(route, section);
        await page.screenshot({ path: `build/mobile-layout-screenshots/${route}-${viewport.width}x${viewport.height}.png` });
      }
      assert.deepEqual(errors, [], 'No Compose/browser errors while navigating responsive screens');
      console.log(`Responsive UI passed: ${viewport.width}x${viewport.height}`);
      await page.close();
    }
    // Guests now land on Dashboard and can open Map on a phone.
    const guest = await browser.newPage({ viewport: { width: 320, height: 568 }, hasTouch: true });
    await guest.route('**/api/v1/auth/session*', route => route.fulfill({ contentType: 'application/json', body: '{"guest":true}' }));
    await guest.goto(`${origin}/app.html#dashboard`);
    await guest.waitForFunction(() => {
      const pane = document.getElementById('oms-route-transition');
      return pane?.hidden && pane.dataset.displayedSection === 'Dashboard';
    }, null, { timeout: 120000 });
    await guest.waitForFunction(() => !document.querySelector('#oms-loading-overlay.visible'));
    await guest.screenshot({ path: 'build/mobile-layout-screenshots/guest-320x568.png' });
    await guest.evaluate(() => { pushOmsRoute('map'); window.dispatchEvent(new PopStateEvent('popstate')); });
    await guest.waitForFunction(() => {
      const pane = document.getElementById('oms-route-transition');
      return pane?.hidden && pane.dataset.displayedSection === 'Projects Map';
    }, null, { timeout: 120000 });
    await guest.screenshot({ path: 'build/mobile-layout-screenshots/guest-map-320x568.png' });
    await guest.evaluate(() => { pushOmsRoute('admin'); window.dispatchEvent(new PopStateEvent('popstate')); });
    await guest.waitForFunction(() => {
      const pane = document.getElementById('oms-route-transition');
      return location.hash === '#dashboard' && pane?.hidden && pane.dataset.displayedSection === 'Dashboard';
    }, null, { timeout: 120000 });
    await guest.close();
    console.log('Mobile guest Dashboard, Map and restricted-route redirect passed');
  } finally {
    await browser?.close();
    server.kill();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
