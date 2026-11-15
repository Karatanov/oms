// Test the exact production native factory in browser engines, without network or user data.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const playwright = require(path.resolve('build/date-picker-test/node_modules/playwright'));
const source = fs.readFileSync('composeApp/src/webMain/kotlin/oms/charts/ChartLabelCollation.kt', 'utf8');
const expression = source.match(/js\("([^"\n]+)"\)/)?.[1];
if (!expression) throw Error('Cannot locate the production Intl.Collator factory');

(async () => {
  for (const name of (process.env.OMS_COLLATION_BROWSERS || 'chromium,webkit,firefox').split(',')) {
    const options = name === 'chromium' && process.env.OMS_CHROME_CHANNEL ? { channel: process.env.OMS_CHROME_CHANNEL } : {};
    const browser = await playwright[name].launch(options);
    try {
      const page = await browser.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(String(error)));
      const result = await page.evaluate(expression => {
        const create = new Function('locale', `return (${expression});`);
        const uk = create('uk'), en = create('en');
        return {
          uk: ['Харківська', 'Івано-Франківська', 'Київська'].sort(uk.compare),
          alphabet: ['Ї', 'Є', 'Й', 'И', 'Е', 'І', 'Ж', 'Г', 'Ґ', 'Д'].sort(uk.compare),
          en: ['Zoo', 'Energy', 'éducation', 'Beta', 'alpha'].sort(en.compare),
          mixedUk: ['Kyiv', 'Київ'].sort(uk.compare), mixedEn: ['Kyiv', 'Київ'].sort(en.compare),
          caseComparison: en.compare('Alpha', 'alpha')
        };
      }, expression);
      assert.deepEqual(result.uk, ['Івано-Франківська', 'Київська', 'Харківська']);
      assert.deepEqual(result.alphabet, ['Г', 'Ґ', 'Д', 'Е', 'Є', 'Ж', 'И', 'І', 'Ї', 'Й']);
      assert.deepEqual(result.en, ['alpha', 'Beta', 'éducation', 'Energy', 'Zoo']);
      assert.deepEqual(result.mixedUk, ['Київ', 'Kyiv']);
      assert.deepEqual(result.mixedEn, ['Kyiv', 'Київ']);
      assert.equal(result.caseComparison, 0);
      assert.deepEqual(errors, []);
      console.log(`PASS: native UA/EN chart collation in ${name}`);
    } finally { await browser.close(); }
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
