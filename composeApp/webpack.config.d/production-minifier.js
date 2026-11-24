// Kotlin already eliminates unused declarations in the production executable.
// Terser's repeated whole-program compression takes ~17 minutes on the Compose
// bundle. Keep production mode/minification, but use its supported native backend.
if (config.mode === 'production') {
    const TerserPlugin = require('terser-webpack-plugin');
    config.optimization = config.optimization || {};
    config.optimization.minimize = true;
    config.optimization.minimizer = [new TerserPlugin({
        minify: TerserPlugin.esbuildMinify,
        parallel: false,
        extractComments: false,
        terserOptions: { target: 'es2015', legalComments: 'inline' }
    })];
}
