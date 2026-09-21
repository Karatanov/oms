package oms

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/**
 * Точка входу WebAssembly застосунку.
 * Саме цей метод запускається браузером після завантаження wasm модуля.
 * Формує головний контейнер для Compose UI і інтегрує його в HTML-сторінку.
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
fun main() {
    // Use the provider API so TextContextMenuHost can replace native Popups.
    androidx.compose.foundation.ComposeFoundationFlags.isNewContextMenuEnabled = true
    ComposeViewport(viewportContainerId = "compose-host") {
        App()
    }
}
