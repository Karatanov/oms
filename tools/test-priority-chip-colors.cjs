const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const source = fs.readFileSync('composeApp/src/webMain/kotlin/oms/components/Sector.kt', 'utf8');
const block = source.split('fun priorityDirectionColor(')[1]?.split('\n}')[0];
assert.ok(block, 'Priority colour mapping must exist');
const palette = new Map([...block.matchAll(/"([a-z_]+)"\s*->\s*Color\(0xFF([A-F0-9]{6})\)/g)]
  .map(match => [match[1], match[2]]));
const keys = ['education', 'healthcare', 'housing', 'water_supply', 'sewerage', 'heat_supply',
  'shelter_education_health', 'shelter_other_public', 'security_centres'];

test('all current directions have a distinct explicit colour', () => {
  keys.forEach(key => assert.ok(palette.has(key), key));
  assert.equal(new Set(keys.map(key => palette.get(key))).size, keys.length);
});
test('badge text remains readable against its existing light tinted background', () => {
  const badge = fs.readFileSync('composeApp/src/webMain/kotlin/oms/components/WorkspaceComponents.kt', 'utf8');
  assert.match(badge, /background\(color\.copy\(alpha = \.09f\)/);
  const linear = value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  const luminance = rgb => rgb.map(linear).reduce((sum, value, i) => sum + value * [0.2126, 0.7152, 0.0722][i], 0);
  for (const [key, hex] of palette) {
    const rgb = hex.match(/../g).map(value => parseInt(value, 16) / 255);
    const background = rgb.map(value => value * 0.09 + 0.91);
    const ratio = (luminance(background) + 0.05) / (luminance(rgb) + 0.05);
    assert.ok(ratio >= 4.5, `${key}: contrast ${ratio.toFixed(2)} below 4.5`);
  }
});
