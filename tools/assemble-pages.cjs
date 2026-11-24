const fs = require('node:fs');
const path = require('node:path');

/** Package the shared, unbranded frontend artifact without running Gradle again. */
function assemblePages(frontend, destination, revision, buildDate = new Date().toISOString()) {
    if (!/^[0-9a-f]{40}$/.test(revision)) throw new Error('A full immutable source revision is required');
    for (const dir of ['processed', 'executable', 'source-resources']) {
        if (!fs.statSync(path.join(frontend, dir)).isDirectory()) throw new Error(`Missing frontend ${dir}`);
    }
    fs.mkdirSync(destination, { recursive: true });
    for (const dir of ['processed', 'executable']) fs.cpSync(path.join(frontend, dir), destination, { recursive: true });
    for (const file of ['login.html', 'favicon.svg', 'social-preview.png']) {
        fs.copyFileSync(path.join(frontend, 'source-resources', file), path.join(destination, file));
    }
    const wasm = fs.readdirSync(destination).filter(name => name.endsWith('.wasm') && name !== 'skiko.wasm');
    if (wasm.length !== 1) throw new Error('Expected exactly one fingerprinted WASM renderer');
    const jsPath = path.join(destination, 'composeApp.js');
    fs.writeFileSync(jsPath, fs.readFileSync(jsPath, 'utf8').replaceAll('skiko.wasm', wasm[0]));
    const app = fs.readFileSync(path.join(destination, 'index.html'), 'utf8')
        .replaceAll('__OMS_WASM_ASSET__', wasm[0])
        .replaceAll('__OMS_GIT_COMMIT__', revision.slice(0, 12))
        .replaceAll('__OMS_BUILD_DATE__', buildDate);
    fs.writeFileSync(path.join(destination, 'app.html'), app);
    fs.copyFileSync(path.join(destination, 'login.html'), path.join(destination, 'index.html'));
    fs.writeFileSync(path.join(destination, 'assets-manifest.json'), JSON.stringify({
        version: revision,
        assets: ['composeApp.js', wasm[0]].map(url => ({ url, bytes: fs.statSync(path.join(destination, url)).size }))
    }) + '\n');
    fs.writeFileSync(path.join(destination, '.nojekyll'), '');
}

if (require.main === module) assemblePages(...process.argv.slice(2));
module.exports = { assemblePages };
