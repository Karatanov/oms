package oms.components

import androidx.compose.runtime.Composable

/**
 * Keeps one stable composition boundary for text fields while deliberately
 * leaving the browser's native context menu in charge of Copy/Cut/Paste.
 *
 * The earlier custom Compose provider depended on internal, experimental APIs
 * that are not available to the JS production compiler used by GitHub Pages.
 * It also introduced a second focus layer around text fields. A pass-through
 * host restores the standard browser menu, keyboard shortcuts and permission
 * prompts without intercepting input events.
 */
@Composable
fun TextContextMenuHost(content: @Composable () -> Unit) = content()
