package oms.data

import kotlinx.browser.localStorage

data class SavedCredentials(
    val username: String,
    val password: String
)

/** Retains credentials only when the user explicitly selects “Remember me”. */
object BrowserCredentialStorage {
    private const val UsernameKey = "oms.saved.username"
    private const val PasswordKey = "oms.saved.password"

    fun load(): SavedCredentials? = runCatching {
        val username = localStorage.getItem(UsernameKey)?.takeIf { it.isNotBlank() }
        val password = localStorage.getItem(PasswordKey)?.takeIf { it.isNotBlank() }
        if (username != null && password != null) SavedCredentials(username, password) else null
    }.getOrNull()

    fun save(username: String, password: String) {
        runCatching {
            localStorage.setItem(UsernameKey, username)
            localStorage.setItem(PasswordKey, password)
        }
    }

    fun clear() {
        runCatching {
            localStorage.removeItem(UsernameKey)
            localStorage.removeItem(PasswordKey)
        }
    }
}
