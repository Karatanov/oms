const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire } = require('node:module');
const { test } = require('node:test');

// Run after Kotlin's npm installation; these are the actual pinned build tools.
const buildRequire = createRequire(path.resolve('build/js/package.json'));
const TerserPlugin = buildRequire('terser-webpack-plugin');
const fragment = fs.readFileSync('composeApp/webpack.config.d/production-minifier.js', 'utf8');
const configure = config => {
    vm.runInNewContext(fragment, { config, require: buildRequire });
    return config;
};

test('production stays minified and uses the native backend without dropping licenses', () => {
    const config = configure({ mode: 'production' });
    assert.equal(config.optimization.minimize, true);
    const plugin = config.optimization.minimizer[0];
    assert.equal(plugin.options.minimizer.implementation, TerserPlugin.esbuildMinify);
    assert.equal(plugin.options.minimizer.options.target, 'es2015');
    assert.equal(plugin.options.minimizer.options.legalComments, 'inline');
});

test('development/test bundling is left unchanged', () => {
    const config = { mode: 'development', optimization: { minimize: false } };
    assert.equal(configure(config), config);
    assert.deepEqual(config.optimization, { minimize: false });
});

test('native minification preserves public globals, callbacks, numbers and UA/EN text', async () => {
    const source = `/*! OMS license fixture */
        (function(root) {
            const values = [0, -1.25, 0.1, 0.2, 800698.95, 2872673400];
            root.omsFixture = {
                values: values.map(v => Math.round(v * 100)),
                texts: ['Усього', 'Технічний нагляд', 'EUR', 'UAH'],
                nullValue: null, zero: 0,
                callback: value => value == null ? 'missing' : String(value)
            };
        })(globalThis);`;
    const options = configure({ mode: 'production' }).optimization.minimizer[0].options.minimizer.options;
    const result = await TerserPlugin.esbuildMinify({ 'fixture.js': source }, undefined, { ...options });
    assert.equal(result.errors, undefined);
    assert.match(result.code, /OMS license fixture/);
    const evaluate = code => {
        const context = {};
        vm.runInNewContext(code, context);
        return JSON.stringify({ ...context.omsFixture,
            callbacks: [null, 0, 'W', 'Україна'].map(context.omsFixture.callback) });
    };
    assert.equal(evaluate(result.code), evaluate(source));
    assert.ok(result.code.length < source.length);
});
