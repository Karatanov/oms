const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const test = require('node:test');

const source = fs.readFileSync('composeApp/src/webMain/kotlin/oms/charts/ChartLabelCollation.kt', 'utf8');
const expression = source.match(/js\("([^"\n]+)"\)/)?.[1];
if (!expression) throw Error('Cannot locate the production Intl.Collator factory');
const create = locale => vm.runInNewContext(expression, { Intl, locale });

test('Ukrainian alphabet includes Є І Ї Ґ in their proper positions', () => {
  const uk = create('uk');
  assert.equal(uk.resolvedOptions().locale, 'uk');
  assert.deepEqual(['Харківська', 'Івано-Франківська', 'Київська'].sort(uk.compare), ['Івано-Франківська', 'Київська', 'Харківська']);
  assert.deepEqual(['Ї', 'Є', 'Й', 'И', 'Е', 'І', 'Ж'].sort(uk.compare), ['Е', 'Є', 'Ж', 'И', 'І', 'Ї', 'Й']);
  assert.deepEqual(['Д', 'Ґ', 'Г'].sort(uk.compare), ['Г', 'Ґ', 'Д']);
});
test('English accent-sensitive case-insensitive sorting preserves lexical label numbers', () => {
  const en = create('en');
  assert.deepEqual(['Zoo', 'Energy', 'éducation', 'Beta', 'alpha'].sort(en.compare), ['alpha', 'Beta', 'éducation', 'Energy', 'Zoo']);
  assert.equal(en.compare('Alpha', 'alpha'), 0);
  assert.notEqual(en.compare('e', 'é'), 0);
  assert.deepEqual(['A2', 'A10'].sort(en.compare), ['A10', 'A2']);
});
test('Changing the locale changes mixed-script order without leaking the previous collator', () => {
  assert.deepEqual(['Kyiv', 'Київ'].sort(create('uk').compare), ['Київ', 'Kyiv']);
  assert.deepEqual(['Kyiv', 'Київ'].sort(create('en').compare), ['Kyiv', 'Київ']);
});
