package oms.data

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter

data class RegistryReferenceLink(val url: String, val search: Boolean = false)

/** Keep source URLs; registration numbers open a search, never a fabricated document ID. */
fun registryReferenceLink(value: String, eConstruction: Boolean): RegistryReferenceLink? {
    val raw = value.trim()
    if (raw.isEmpty() || raw.any(Char::isISOControl)) return null
    val host = raw.substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
    val candidate = when {
        raw.startsWith("//") && !raw.startsWith("///") -> "https:$raw"
        host in setOf("dream.gov.ua", "www.dream.gov.ua", "e-construction.gov.ua", "www.e-construction.gov.ua") -> "https://$raw"
        else -> raw
    }
    val url = if (candidate.startsWith("https://", true) || candidate.startsWith("http://", true))
        runCatching { Url(candidate) }.getOrNull() else null
    val authority = candidate.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
    if (url != null && url.protocol in setOf(URLProtocol.HTTP, URLProtocol.HTTPS) &&
        authority.isNotBlank() && '@' !in authority && url.host.isNotBlank() && candidate.none(Char::isWhitespace)) {
        return RegistryReferenceLink(candidate)
    }
    // The source contains mixed Latin/Cyrillic glyphs. Search the exact source
    // number rather than silently correcting potentially meaningful characters.
    val code = Regex("[A-Za-zА-Яа-яІіЇїЄєҐґ0-9]{2,8}:[A-Za-zА-Яа-яІіЇїЄєҐґ0-9]{4}(?:-[A-Za-zА-Яа-яІіЇїЄєҐґ0-9]{4}){3}")
    return if (eConstruction && code.matches(raw)) RegistryReferenceLink(
        "https://e-construction.gov.ua/search_in_registers?search=${raw.encodeURLParameter()}", search = true
    ) else null
}
