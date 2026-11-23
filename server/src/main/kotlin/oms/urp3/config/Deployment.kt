package oms.urp3.config

import java.net.URI

/** Select a server-owned organization profile before connecting to storage. */
internal object Deployment {
    val brand = System.getenv("OMS_BRAND") ?: "ur3"
    val legacy = when (brand) {
        "ur3" -> true
        "usif" -> false
        else -> error("Unknown OMS_BRAND")
    }
    // Manual XLSX imports need an explicit parent when an organization has multiple projects.
    fun isImportParent(siteNumber: String, name: String): Boolean = if (legacy) {
        siteNumber.equals("URP-III", true) || name.equals("Ukraine Recovery III", true) ||
            name.equals("Ukraine Recovery Programme III", true)
    } else {
        siteNumber.equals(System.getenv("OMS_IMPORT_PARENT_SITE_NUMBER")?.takeIf(String::isNotBlank), true)
    }

    val missingImportParent: String get() = if (legacy) "The Ukraine Recovery III parent project is missing."
        else "Create a parent project and configure OMS_IMPORT_PARENT_SITE_NUMBER before importing."

    val publicOrigin = if (legacy) "https://ua-oms.com" else {
        val uri = URI(requireNotNull(System.getenv("OMS_PUBLIC_BASE_URL")))
        require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.fragment == null && uri.path in setOf("", "/"))
        require(uri.host != "ua-oms.com" && !uri.host.endsWith(".ua-oms.com"))
        "${uri.scheme}://${uri.rawAuthority}"
    }
}
