package oms.urp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PriorityDirectionsTest {
    @Test fun oneLanguageAndBilingualAliasesProduceOneFilterOption() {
        assertEquals(setOf("healthcare"), listOf("Health", "Healthcare", "Охорона здоров'я / Health").map(::priorityDirectionKey).toSet())
        assertEquals(setOf("sewerage"), listOf("Sewerage", "Водовідведення / Sewerage").map(::priorityDirectionKey).toSet())
        assertEquals("heat_supply", priorityDirectionKey("Heating"))
        assertEquals("security_centres", priorityDirectionKey("Security Centres"))
    }
    @Test fun shelterDirectionsStayDistinctWhileTheirBilingualVariantsDeduplicate() {
        val education = "Укриття: Заклади освіти та охорони здоров'я / Shelters: Education and health buildings"
        val other = "Укриття: Інші громадські будівлі / Shelters: Other public buildings"
        assertEquals("shelter_education_health", priorityDirectionKey(education))
        assertEquals(priorityDirectionKey(education), priorityDirectionKey("Shelters: Education and health buildings"))
        assertEquals("shelter_other_public", priorityDirectionKey(other))
        assertNotEquals(priorityDirectionKey(education), priorityDirectionKey(other))
    }
    @Test fun canonicalFilterKeysAreStableAcrossRepeatedNormalization() {
        for (key in listOf("education", "healthcare", "sewerage", "shelter_education_health", "shelter_other_public", "security_centres")) {
            assertEquals(key, priorityDirectionKey(key))
        }
    }
    @Test fun currentRegistryCaptionsResolveToNineDirectionsWithoutRawEnglishDuplicates() {
        val captions = listOf("Водовідведення / Sewerage", "Водопостачання / Water supply", "Житло /Housing",
            "Освіта /Education", "Охорона здоров'я / Health", "Теплопостачання /Heat supply",
            "Укриття: Заклади освіти та охорони здоров'я / Shelters: Education and health buildings",
            "Укриття: Інші громадські будівлі / Shelters: Other public buildings", "Центри безпеки /Security Centres",
            "Education", "Health", "Heat supply", "Heating", "Security Centres", "Sewerage",
            "Shelters: Education and health buildings", "Water supply")
        assertEquals(setOf("sewerage", "water_supply", "housing", "education", "healthcare", "heat_supply",
            "shelter_education_health", "shelter_other_public", "security_centres"), captions.map(::priorityDirectionKey).toSet())
    }
}
