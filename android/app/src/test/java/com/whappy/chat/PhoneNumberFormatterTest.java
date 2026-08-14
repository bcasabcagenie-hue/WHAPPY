package com.whappy.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class PhoneNumberFormatterTest {
    @Test
    public void keepsCongoLeadingZero() {
        assertEquals("+242061234567", PhoneNumberFormatter.normalize("+242", "06 123 45 67"));
    }

    @Test
    public void doesNotDuplicateCongoCountryCode() {
        assertEquals("+242061234567", PhoneNumberFormatter.normalize("+242", "+242 06 123 45 67"));
        assertEquals("+242061234567", PhoneNumberFormatter.normalize("+242", "242 06 123 45 67"));
        assertEquals("+242061234567", PhoneNumberFormatter.normalize("+242", "00 242 06 123 45 67"));
    }

    @Test
    public void rejectsWrongCongoLength() {
        assertNull(PhoneNumberFormatter.normalize("+242", "06 12 34"));
    }

    @Test
    public void dropsFranceDomesticZero() {
        assertEquals("+33612345678", PhoneNumberFormatter.normalize("+33", "06 12 34 56 78"));
    }
}
