package com.whappy.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhappyLinkTest {
    @Test fun parsesContactSchemesAndWebLinks() {
        assertEquals(WhappyLink.Contact("+242061234567"), WhappyLink.parse("whappy://contact/+242061234567"))
        assertEquals(WhappyLink.Contact("+33612345678"), WhappyLink.parse("https://whappy.chat/contact/%2B33612345678"))
        assertEquals(WhappyLink.Contact("+33612345678"), WhappyLink.parse("WHAPPY:CONTACT:+33612345678"))
        assertEquals(WhappyLink.Contact("+242061234567"), WhappyLink.parse("WhApPy:CoNtAcT:+242061234567"))
    }

    @Test fun parsesWebSearchAndChannelLinks() {
        assertEquals(WhappyLink.Search("mode"), WhappyLink.parse("whappy://search?q=mode"))
        assertEquals(WhappyLink.Channel("chaine-12"), WhappyLink.parse("https://whappy.chat/channel/chaine-12"))
    }

    @Test fun parsesChannelAndRejectsUnknownLinks() {
        assertEquals(WhappyLink.Channel("abc_123"), WhappyLink.parse("whappy://channel/abc_123"))
        assertEquals(WhappyLink.Live("live_abc-123"), WhappyLink.parse("whappy://live/live_abc-123"))
        assertEquals(WhappyLink.GroupCall("call_abc-123"), WhappyLink.parse("whappy://group-call/call_abc-123"))
        assertNull(WhappyLink.parse("https://example.com/anything"))
    }
}
