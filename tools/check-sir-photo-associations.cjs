const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const service = fs.readFileSync(path.join(root, 'server/src/main/kotlin/oms/ufsi/service/InspectionReportFileService.kt'), 'utf8');
const screen = fs.readFileSync(path.join(root, 'composeApp/src/webMain/kotlin/oms/screens/ReportsScreen.kt'), 'utf8');

assert.match(service, /photoAssociationIndex\(readSirWorkbook\(workbook, report\)\)/,
  'import must index SIR rows before materialising photos');
assert.match(service, /\[oms:\$it\]/,
  'imported photo names must preserve the resolved row key');
assert.match(service, /singleOrNull\(\)/,
  'ambiguous identical captions must not be assigned arbitrarily');
assert.match(screen, /forImportedCaptions/,
  'already materialised imported photos need caption fallback');
assert.doesNotMatch(screen, /index == 0\) unassignedPhotos/,
  'unassigned evidence must never be forced onto the first activity');
console.log('SIR imported-photo association checks passed.');
