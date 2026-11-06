package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import androidx.compose.ui.input.key.*
import oms.localization.LocalizationManager
import oms.theme.OmsDimensions

/** Anchored in the application overlay host: never measured as page content. */
@Composable
fun <T> InlineOptionPicker(
    options: List<T>, selected: T?, prompt: String, onSelect: (T) -> Unit,
    itemLabel: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier, fillWidth: Boolean = true, enabled: Boolean = true,
    clearLabel: String? = null, onClear: (() -> Unit)? = null,
    loadOptionsOnOpen: (suspend () -> List<T>)? = null
) {
    val host = LocalOptionOverlay.current
    val id = remember { Any() }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val expanded = host.menu?.id === id
    val restoreFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    fun showMenu(openOptions: List<T>) {
        val canClear = clearLabel != null && onClear != null
        host.menu = OptionMenu(
            id, anchor, (if (canClear) listOf(clearLabel!!) else emptyList()) + openOptions.map(itemLabel),
            openOptions.indexOf(selected).let { if (it < 0) 0 else it + if (canClear) 1 else 0 },
            onChoose = { index ->
                host.dismiss(id)
                if (canClear && index == 0) onClear?.invoke() else onSelect(openOptions[index - if (canClear) 1 else 0])
                restoreFocus.requestFocus()
            },
            onDismiss = { host.dismiss(id); restoreFocus.requestFocus() }
        )
    }
    DisposableEffect(id, enabled) {
        onDispose {
            loadJob?.cancel()
            loading = false
            host.dismiss(id)
        }
    }
    OutlinedButton(
        enabled = enabled,
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = OmsDimensions.ControlHeight)
            .onGloballyPositioned { anchor = it.boundsInRoot() }
            .onPreviewKeyEvent { event ->
                if (loading && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    loadJob?.cancel()
                    loading = false
                    true
                } else false
            }
            .semantics {
                stateDescription = LocalizationManager.t(
                    when {
                        loading -> "loading_records"
                        loadFailed -> "picker_load_failed"
                        expanded -> "expanded"
                        else -> "collapsed"
                    }
                )
            }
            .then(Modifier.focusRequesterForPicker(restoreFocus)),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (loadFailed && enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        onClick = {
            if (!loading) {
                loadFailed = false
                if (expanded) host.dismiss(id)
                else if (loadOptionsOnOpen == null) showMenu(options)
                else {
                    loading = true
                    loadJob = scope.launch {
                        try {
                            val loadedOptions = loadOptionsOnOpen()
                            if (isActive) showMenu(loadedOptions)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (isActive) loadFailed = true
                        } finally {
                            if (isActive) loading = false
                        }
                    }
                }
            }
        }
    ) {
        Column(Modifier.weight(1f, fill = fillWidth)) {
            Text(
                selected?.let(itemLabel) ?: prompt,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            if (loadFailed && enabled) {
                Text(
                    LocalizationManager.t("picker_retry_load"),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                when {
                    loadFailed -> Icons.Default.Refresh
                    expanded -> Icons.Default.ArrowDropUp
                    else -> Icons.Default.ArrowDropDown
                },
                contentDescription = if (loadFailed) LocalizationManager.t("retry") else null,
                modifier = Modifier.size(20.dp),
                tint = if (loadFailed && enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun Modifier.focusRequesterForPicker(requester: androidx.compose.ui.focus.FocusRequester): Modifier =
    this.then(androidx.compose.ui.Modifier.focusRequester(requester))
