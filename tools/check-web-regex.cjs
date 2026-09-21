// Kotlin/JS creates Regex with the Unicode flag (u). JVM-valid patterns can
// otherwise throw during Compose rendering and stop the entire application.
// Run: node tools/check-web-regex.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const sourceRoot = path.join(root, 'composeApp/src/webMain/kotlin');

function* files(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
        const full = path.join(directory, entry.name);
        if (entry.isDirectory()) yield* files(full);
        else if (entry.name.endsWith('.kt')) yield full;
    }
}

let checked = 0;
const failures = [];
for (const file of files(sourceRoot)) {
    const source = fs.readFileSync(file, 'utf8');
    for (const match of source.matchAll(/\bRegex\("((?:\\.|[^"\\])*)"/g)) {
        // Interpolated patterns need their runtime values; check literals here.
        if (/\$(?:\{|[A-Za-z_])/.test(match[1])) continue;
        const line = source.slice(0, match.index).split('\n').length;
        try {
            const pattern = JSON.parse('"' + match[1] + '"');
            new RegExp(pattern, 'gu');
            checked++;
            if (pattern.startsWith('\\[oms:')) {
                const key = name => new RegExp(pattern, 'gui').exec(name)?.[1] ?? null;
                assert.equal(key('Roof [oms:activity-0].jpg'), 'activity-0');
                assert.equal(key('Roof [OMS:observation-2].png'), 'observation-2');
                assert.equal(key('Roof [oms:quality-1].jpg'), 'quality-1');
                assert.equal(key('Imported photo 1.jpg'), null);
                assert.equal(key('Invalid [oms:].jpg'), null);
                assert.equal(key('Incomplete [oms:activity-0.jpg'), null);
            }
        } catch (error) {
            failures.push(`${path.relative(root, file)}:${line}: ${error.message}`);
        }
    }
}
assert.ok(checked > 0, 'No Kotlin/JS regex literals found');
if (failures.length) {
    console.error(failures.join('\n'));
    process.exitCode = 1;
} else {
    console.log(`PASS: ${checked} Kotlin/JS regex literals and SIR photo association cases`);
}
