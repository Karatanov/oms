package oms.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TableSettingsTest {
    @Test fun navigationRetainsFiltersButANewSessionDoesNot() {
        TableSettingsSession.reset()
        TableSettingsSession.write("admin.projects.search", "KH08")
        TableSettingsSession.write("admin.financial.currency", "EUR")
        assertEquals("KH08", TableSettingsSession.read("admin.projects.search"))
        assertNull(TableSettingsSession.read("other.projects.search"))
        TableSettingsSession.reset()
        assertNull(TableSettingsSession.read("admin.projects.search"))
        assertNull(TableSettingsSession.read("admin.financial.currency"))
    }
    @Test fun pageSizeSurvivesLeavingAndRecreatingTheTable() {
        val storage = mutableMapOf<String, String>()
        fun mount() = TableSettingState(20, { storage["pageSize"] }, { storage["pageSize"] = it }, String::toInt)
        mount().value = Int.MAX_VALUE
        assertEquals(Int.MAX_VALUE, mount().value)
        mount().value = 50
        assertEquals(50, mount().value)
    }

    @Test fun clearedFilterDoesNotRevertToItsNonNullDefault() {
        var saved: String? = null
        fun mount() = TableSettingState<String?>("ACTIVE", { saved }, { saved = it }) { it.takeIf(String::isNotEmpty) }
        assertEquals("ACTIVE", mount().value)
        mount().value = null
        assertNull(mount().value)
    }

    @Test fun invalidSettingsAndBlockedStorageDoNotBreakControls() {
        val invalid = TableSettingState(20, { "old-format" }, {}, String::toInt)
        assertEquals(20, invalid.value)
        val blocked = TableSettingState(20, { error("Storage blocked") }, { error("Quota exceeded") }, String::toInt)
        blocked.value = 100
        assertEquals(100, blocked.value)
    }

    @Test fun filterAndSortSettingsAreWrittenImmediatelyAndRestoredTogether() {
        val storage = mutableMapOf<String, String>()
        fun search() = TableSettingState("", { storage["search"] }, { storage["search"] = it }) { it }
        fun ascending() = TableSettingState(true, { storage["ascending"] }, { storage["ascending"] = it }, String::toBooleanStrict)
        search().value = "VN08"
        ascending().value = false
        assertEquals("VN08", search().value)
        assertEquals(false, ascending().value)
        search().value = ""
        assertEquals("", search().value)
    }
}
