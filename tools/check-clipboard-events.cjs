// The browser event must reach Compose's clipboard handler exactly once.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const html = fs.readFileSync(path.join(__dirname, '../composeApp/src/webMain/resources/index.html'), 'utf8');
const guard = html.match(/window\.addEventListener\("paste", event => \{[\s\S]*?\}, true\);/);
assert.ok(guard, 'paste size guard exists');
let handler;
let notices = 0;
vm.runInNewContext(guard[0], {
  omsMaximumPasteLength: 12000,
  showOmsPasteNotice: () => notices++,
  window: { addEventListener: (_, listener) => { handler = listener; } }
});
for (const text of ['', 'Текст із буфера', '123.45', 'a'.repeat(12000)]) {
  handler({ clipboardData: { getData: () => text },
    preventDefault: () => assert.fail('normal paste must reach the field'),
    stopImmediatePropagation: () => assert.fail('normal paste must not be intercepted') });
}
let prevented = 0, stopped = 0;
handler({ clipboardData: { getData: () => 'a'.repeat(12001) },
  preventDefault: () => prevented++, stopImmediatePropagation: () => stopped++ });
assert.deepEqual([prevented, stopped, notices], [1, 1, 1]);
assert.doesNotMatch(html, /window\.addEventListener\("(?:copy|cut|contextmenu)"/,
  'native clipboard events must not be swallowed globally');
console.log('Clipboard event regression checks passed.');
