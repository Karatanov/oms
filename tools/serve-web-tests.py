"""Serve compiled Kotlin/Wasm unit tests locally; never serves repository secrets.

Build :composeApp:compileTestDevelopmentExecutableKotlinWasmJs and
:composeApp:wasmJsTestTestDevelopmentExecutableCompileSync first.
Open http://127.0.0.1:18087/ui-tests.html in the in-app browser.
"""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TESTS = ROOT / 'build/wasm/packages/umitaf-composeApp-test/kotlin'


class TestHandler(SimpleHTTPRequestHandler):
    extensions_map = {
        **SimpleHTTPRequestHandler.extensions_map,
        '.mjs': 'text/javascript', '.wasm': 'application/wasm',
    }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(TESTS), **kwargs)

    def translate_path(self, path):
        if path.split('?')[0] == '/ui-tests.html':
            return str(ROOT / 'composeApp/src/webTest/resources/ui-tests.html')
        if path.split('?')[0] == '/js-joda.mjs':
            return str(ROOT / 'build/wasm/node_modules/@js-joda/core/dist/js-joda.esm.js')
        return super().translate_path(path)


if __name__ == '__main__':
    if not (TESTS / 'umitaf-composeApp-test.mjs').is_file():
        raise SystemExit('Compile and sync the Kotlin/Wasm test executable first.')
    print('OMS Kotlin/Wasm tests: http://127.0.0.1:18087/ui-tests.html', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 18087), TestHandler).serve_forever()
