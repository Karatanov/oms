package oms.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalTableSettingsUser = staticCompositionLocalOf { "guest" }

/** A page reload creates a fresh store; a new authenticated session clears it explicitly. */
object TableSettingsSession {
    private val values = mutableMapOf<String, String>()
    fun read(key: String): String? = values[key]
    fun write(key: String, value: String) { values[key] = value }
    fun reset() { values.clear() }
}

/** Only presentation preferences are stored, never records, selections or API state. */
internal class TableSettingState<T>(
    initial: T,
    read: () -> String?,
    private val write: (String) -> Unit,
    decode: (String) -> T
) : MutableState<T> {
    private val state = mutableStateOf(runCatching {
        val saved = read()
        if (saved == null) initial else decode(saved)
    }.getOrDefault(initial))
    override var value: T
        get() = state.value
        set(value) {
            state.value = value
            // Restricted storage/private browsing must not block table controls.
            runCatching { write(value?.toString().orEmpty()) }
        }
    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
}

@Composable
fun <T> rememberTableSetting(key: String, initial: T, decode: (String) -> T): MutableState<T> {
    val user = LocalTableSettingsUser.current
    return remember(user, key) {
        val storageKey = "oms.table.v1.${user.length}:$user.$key"
        TableSettingState(initial, { TableSettingsSession.read(storageKey) }, { TableSettingsSession.write(storageKey, it) }, decode)
    }
}

@Composable
fun rememberTablePageSize(key: String) = rememberTableSetting("$key.pageSize", 20) {
    it.toInt().also { size -> require(size in listOf(20, 50, 100, Int.MAX_VALUE)) }
}
