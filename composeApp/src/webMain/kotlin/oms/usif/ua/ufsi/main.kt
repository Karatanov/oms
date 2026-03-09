package oms.usif.ua.ufsi

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
/**
 * Точка входу WebAssembly застосунку.
 * Саме цей метод запускається браузером після завантаження wasm модуля.
 * Формує головний контейнер для Compose UI і інтегрує його в HTML-сторінку.
 */
@OptIn(ExperimentalComposeUiApi::class) // дозвіл використовувати експериментальний Compose API
fun main() {
    // ComposeViewport формує головний контейнер для Compose UI і інтегрує його в HTML-сторінку.
    ComposeViewport {
        App() // будує реальний інтерфейс застосунку
    }
}