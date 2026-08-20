package oms.data

import kotlinx.browser.localStorage

data class SavedCredentials(
    val username: String
)

/**
 * "Remember me" retains only the login identifier in this browser. Passwords
 * and bearer tokens must never be persisted in browser storage.
 */
object BrowserCredentialStorage {
    private const val UsernameKey = "oms.saved.username"

    fun load(): SavedCredentials? = runCatching {
        val username = localStorage.getItem(UsernameKey)?.takeIf { it.isNotBlank() }
        if (username != null) SavedCredentials(username) else null
    }.getOrNull()

    fun save(username: String) {
        runCatching {
            localStorage.setItem(UsernameKey, username)
        }
    }

    fun clear() {
        runCatching {
            localStorage.removeItem(UsernameKey)
        }
    }
}
