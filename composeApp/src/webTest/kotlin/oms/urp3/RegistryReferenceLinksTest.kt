package oms.urp3

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import oms.data.registryReferenceLink

class RegistryReferenceLinksTest {
    @Test fun directDreamAndEConstructionUrlsRemainDirectLinks() {
        for (url in listOf("https://dream.gov.ua/project/example", "https://e-construction.gov.ua/document_detail/doc_id=123/optype=4")) {
            val link = registryReferenceLink("  $url  ", eConstruction = false)!!
            assertEquals(url, link.url)
            assertFalse(link.search)
        }
    }
    @Test fun registrationNumbersOpenExactOfficialSearchWithoutGuessingDocumentIds() {
        for (reference in listOf("PD01:8296-0B41-7401-3709", "EM01:8585-0294-7753-3691", "EX01:8628-6276-7921-6653",
            "ЕМ01:4104-8529-9000-2378", "EMOl:3780-1342-4648-3690", "РОО1:5249-716б-10бЕ-З59б")) {
            val link = registryReferenceLink(" $reference ", eConstruction = true)!!
            assertTrue(link.search)
            val url = Url(link.url)
            assertEquals("e-construction.gov.ua", url.host)
            assertEquals("/search_in_registers", url.encodedPath)
            assertEquals(reference, url.parameters["search"])
        }
    }
    @Test fun knownProviderLinksWithoutSchemeAreNormalized() {
        assertEquals("https://dream.gov.ua/project/example", registryReferenceLink("dream.gov.ua/project/example", false)?.url)
        assertEquals("https://e-construction.gov.ua/document_detail/doc_id=123", registryReferenceLink("//e-construction.gov.ua/document_detail/doc_id=123", true)?.url)
    }
    @Test fun placeholdersUnsafeSchemesAndUnrecognizedTextDoNotBecomeLinks() {
        for (value in listOf("", "0", "—", "n/a", "javascript:alert(1)", "data:text/html,hello", "not a link", "https://", "https://bad host/path", "https://user:password@example.org/document")) {
            assertNull(registryReferenceLink(value, true), value)
        }
        assertNull(registryReferenceLink("EX01:8628-6276-7921-6653", eConstruction = false))
    }
}
