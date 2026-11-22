package oms.data

import kotlinx.browser.document

/** The default also serves the standalone Pages build. */
internal object WorkspaceBrand {
    private val configuredName = document.documentElement?.getAttribute("data-oms-name")
    val name = configuredName ?: "UR III"
    val title = "$name OMS"
    val legacySources = configuredName == null
    val caption = if (legacySources) "TA to the implementation\nof UR III Programme" else "Online Project Monitoring System"
}
