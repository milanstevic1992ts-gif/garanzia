package com.milanstevic.garanzia.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptFtsQueryTest {

    @Test
    fun buildsPrefixQueryFromHumanText() {
        assertEquals(
            "\"bosch\"* \"18v\"*",
            ReceiptFtsQuery.build("Bosch 18V"),
        )
    }

    @Test
    fun removesFtsPunctuationSafely() {
        assertEquals(
            "\"b\"* \"200\"*",
            ReceiptFtsQuery.build("B-200"),
        )
    }

    @Test
    fun punctuationOnlyReturnsNull() {
        assertNull(ReceiptFtsQuery.build("--- ///"))
    }
}
