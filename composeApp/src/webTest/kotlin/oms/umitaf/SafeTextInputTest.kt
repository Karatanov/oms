package oms.umitaf

import kotlin.test.Test
import kotlin.test.assertEquals
import oms.components.safeTextInput

class SafeTextInputTest {
    @Test
    fun removesNullCharactersAndKeepsShortText() {
        assertEquals("safe text", "safe\u0000 text".safeTextInput(100))
    }

    @Test
    fun truncatesOversizedClipboardPayloadBeforeUiFiltering() {
        assertEquals("abc", "abcdef".safeTextInput(3))
    }
}
