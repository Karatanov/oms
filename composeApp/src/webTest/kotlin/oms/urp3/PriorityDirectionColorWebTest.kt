package oms.urp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import oms.components.priorityDirectionColor

class PriorityDirectionColorWebTest {
    @Test fun currentDirectionsHaveDistinctColors() {
        val keys = listOf("education", "healthcare", "housing", "water_supply", "sewerage", "heat_supply",
            "shelter_education_health", "shelter_other_public", "security_centres")
        assertEquals(keys.size, keys.map(::priorityDirectionColor).distinct().size)
    }

    @Test fun sourceLanguageDoesNotAffectCategoryColor() {
        for ((english, ukrainian) in listOf("Education" to "Освіта", "Health" to "Охорона здоров’я",
            "Sewerage" to "Водовідведення / Sewerage", "Security Centres" to "Центри безпеки")) {
            assertEquals(priorityDirectionColor(english), priorityDirectionColor(ukrainian))
        }
        assertNotEquals(priorityDirectionColor("shelter_education_health"), priorityDirectionColor("shelter_other_public"))
    }
}
