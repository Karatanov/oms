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
    private const val RecentUsernamesKey = "oms.recent.usernames"
    private const val MaxRecentUsernames = 8

    fun load(): SavedCredentials? = runCatching {
        val username = localStorage.getItem(UsernameKey)?.takeIf { it.isNotBlank() }
        val password = localStorage.getItem(PasswordKey)?.takeIf { it.isNotBlank() }
        if (username != null && password != null) SavedCredentials(username, password) else null
    }.getOrNull()

    fun save(username: String, password: String) {
        runCatching {
            localStorage.setItem(UsernameKey, username)
            localStorage.setItem(PasswordKey, password)
            rememberUsername(username)
        }
    }

    /** Stores only successful login identifiers for local autocomplete, never their passwords. */
    fun rememberUsername(username: String) {
        val normalized = username.trim()
        if (normalized.isBlank()) return
        runCatching {
            val values = recentUsernames().filterNot { it.equals(normalized, ignoreCase = true) }
            localStorage.setItem(RecentUsernamesKey, (listOf(normalized) + values).take(MaxRecentUsernames).joinToString("\n"))
        }
    }

    fun recentUsernames(): List<String> = runCatching {
        localStorage.getItem(RecentUsernamesKey).orEmpty().lineSequence()
            .map(String::trim).filter(String::isNotBlank).distinct().take(MaxRecentUsernames).toList()
    }.getOrDefault(emptyList())

    fun clear() {
        runCatching {
            localStorage.removeItem(UsernameKey)
            localStorage.removeItem(PasswordKey)
        }
    }
}
