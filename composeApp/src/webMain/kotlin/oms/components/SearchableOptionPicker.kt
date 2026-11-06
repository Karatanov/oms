package oms.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import oms.localization.LocalizationManager

/** Text search selector for large option sets. The canonical value changes only after selection. */
@Composable
fun <T> SearchableOptionPicker(
    options: List<T>,
    selected: T?,
    label: String,
    onSelect: (T) -> Unit,
    itemLabel: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxSuggestions: Int = 50,
    loadOptionsOnInput: (suspend () -> List<T>)? = null
) {
    val host = LocalOptionOverlay.current
    val id = remember { Any() }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var query by remember { mutableStateOf(selected?.let(itemLabel).orEmpty()) }
    var availableOptions by remember { mutableStateOf(options) }
    val scope = rememberCoroutineScope()
    var pendingLoad by remember { mutableStateOf<Job?>(null) }
    var noMatches by remember { mutableStateOf(false) }
    var emptyOptions by remember { mutableStateOf(false) }
    var moreMatches by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var searchVersion by remember { mutableStateOf(0) }
    LaunchedEffect(selected) { query = selected?.let(itemLabel).orEmpty() }
    LaunchedEffect(options) { availableOptions = options }
    DisposableEffect(id, enabled) {
        onDispose {
            searchVersion++
            pendingLoad?.cancel()
            loading = false
            host.dismiss(id)
        }
    }

    fun showMatches(entered: String, source: List<T>) {
        val limit = maxSuggestions.coerceAtLeast(1)
        val candidates = source.asSequence()
            // Opening an autocomplete should show its available choices even
            // before the first character is typed.  Requiring a keystroke
            // made a populated selector look empty, especially for payment
            // bases where a user naturally clicks the field first.
            .filter { entered.isBlank() || itemLabel(it).contains(entered, ignoreCase = true) }
            .take(limit.toLong().plus(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .toList()
        val matches = candidates.take(limit)
        moreMatches = candidates.size > limit
        emptyOptions = source.isEmpty()
        noMatches = entered.isNotBlank() && matches.isEmpty() && !emptyOptions
        if (matches.isEmpty()) {
            host.dismiss(id)
        } else {
            host.menu = OptionMenu(
                id = id,
                anchor = anchor,
                labels = matches.map(itemLabel),
                selectedIndex = matches.indexOfFirst { it == selected }.coerceAtLeast(0),
                selectedIndexes = matches.indices.filterTo(mutableSetOf()) { matches[it] == selected },
                onChoose = { index ->
                    val choice = matches[index]
                    query = itemLabel(choice)
                    host.dismiss(id)
                    onSelect(choice)
                },
                onDismiss = { host.dismiss(id) },
                focusOnOpen = false
            )
        }
    }

    fun searchOptions(text: String) {
        searchVersion++
        val version = searchVersion
        pendingLoad?.cancel()
        noMatches = false
        emptyOptions = false
        moreMatches = false
        loadFailed = false
        loading = false
        if (loadOptionsOnInput == null) {
            showMatches(text, availableOptions)
        } else {
            host.dismiss(id)
            loading = true
            pendingLoad = scope.launch {
                try {
                    val source = loadOptionsOnInput()
                    if (isActive && version == searchVersion) {
                        availableOptions = source
                        showMatches(text, source)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (isActive && version == searchVersion) loadFailed = true
                } finally {
                    if (version == searchVersion) loading = false
                }
            }
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { entered ->
            val safeQuery = entered.safeTextInput(200)
            query = safeQuery
            searchOptions(safeQuery)
        },
        label = { Text(label) },
        isError = loadFailed && enabled,
        supportingText = if (enabled && (loadFailed || noMatches || emptyOptions || moreMatches)) {
            { Text(LocalizationManager.t(when {
                loadFailed -> "picker_load_failed"
                emptyOptions -> "no_options"
                noMatches -> "picker_no_matches"
                else -> "picker_more_matches"
            }).replace("{count}", maxSuggestions.coerceAtLeast(1).toString())) }
        } else null,
        trailingIcon = {
            if (loading && enabled) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (loadFailed && enabled) {
                IconButton(onClick = { searchOptions(query) }) {
                    Icon(Icons.Default.Refresh, LocalizationManager.t("retry"))
                }
            } else {
                OmsTooltipBox(tooltip = { Text(LocalizationManager.t("picker_show_options")) }, enabled = enabled) {
                    IconButton(onClick = { searchOptions("") }, enabled = enabled,
                        modifier = if (enabled) Modifier.buttonHandCursor() else Modifier) {
                        Icon(Icons.Default.ArrowDropDown, LocalizationManager.t("picker_show_options"))
                    }
                }
            }
        },
        singleLine = true,
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
            .onGloballyPositioned { anchor = it.boundsInRoot() }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    searchOptions("")
                } else {
                    searchVersion++
                    pendingLoad?.cancel()
                    loading = false
                    noMatches = false
                    emptyOptions = false
                    moreMatches = false
                }
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && host.menu?.id === id -> {
                        host.menu = host.menu?.copy(focusOnOpen = true)
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                        searchVersion++
                        pendingLoad?.cancel()
                        loading = false
                        noMatches = false
                        emptyOptions = false
                        moreMatches = false
                        host.dismiss(id)
                        true
                    }
                    else -> false
                }
            }
    )
}
