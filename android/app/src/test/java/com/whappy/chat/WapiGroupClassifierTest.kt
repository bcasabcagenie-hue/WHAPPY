package com.whappy.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WapiGroupClassifierTest {
    @Test
    fun `recognises current and legacy group documents`() {
        assertTrue(WapiGroupClassifier.isLegacyGroup("group", null, null, 2, false, null, null))
        assertTrue(WapiGroupClassifier.isLegacyGroup(null, "group", null, 2, false, null, null))
        assertTrue(WapiGroupClassifier.isLegacyGroup(null, null, true, 2, false, null, null))
        assertTrue(WapiGroupClassifier.isLegacyGroup(null, null, null, 2, true, "BCA SA", null))
        assertTrue(WapiGroupClassifier.isLegacyGroup(null, null, null, 2, false, "BCA SA", null))
    }

    @Test
    fun `does not convert an ordinary direct conversation`() {
        assertFalse(WapiGroupClassifier.isLegacyGroup("direct", null, false, 2, false, null, null))
    }
}
