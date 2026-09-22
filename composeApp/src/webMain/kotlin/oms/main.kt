package oms

import androidx.compose.ui.window.ComposeViewport

/**
 * Точка входу WebAssembly застосунку.
 * Саме цей метод запускається браузером після завантаження wasm модуля.
 * Формує головний контейнер для Compose UI і інтегрує його в HTML-сторінку.
 */
fun main() {
    ComposeViewport(viewportContainerId = "compose-host") {
        App()
    }
}
