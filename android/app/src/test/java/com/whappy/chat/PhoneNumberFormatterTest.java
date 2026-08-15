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

    @Test
    public void supportsMoreInternationalNumbers() {
        assertEquals("+24106123456", PhoneNumberFormatter.normalize("+241", "06 12 34 56"));
        assertEquals("+2348012345678", PhoneNumberFormatter.normalize("+234", "0801 234 5678"));
        assertEquals("+12025550123", PhoneNumberFormatter.normalize("+1", "202 555 0123"));
    }

    @Test
    public void supportsGlobalLookup() {
        assertEquals("+242061234567", PhoneNumberFormatter.normalizeAny("06 123 45 67"));
        assertEquals("+33712345678", PhoneNumberFormatter.normalizeAny("+33 712345678"));
        assertEquals("+12025550123", PhoneNumberFormatter.normalizeAny("2025550123"));
        assertEquals("+242061234567", PhoneNumberFormatter.lookupCandidates("06 123 45 67", "+242").get(0));
    }

    @Test
    public void respectsSelectedCountryForLocalNumber() {
        assertEquals("+254712345678", PhoneNumberFormatter.normalize("+254", "0712 345 678"));
        assertEquals("+254712345678", PhoneNumberFormatter.lookupCandidates("0712 345 678", "+254").get(0));
    }
}
