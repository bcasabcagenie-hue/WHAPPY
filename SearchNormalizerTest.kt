package com.whappy.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizerTest {
    @Test fun ignoresAccentsCaseAndWordOrder() {
        assertTrue(SearchNormalizer.matches("brazzaville createurs", "Marché des Créateurs · Brazzaville"))
        assertFalse(SearchNormalizer.matches("pointe noire", "Brazzaville Maintenant"))
    }

    @Test fun searchesFormattedPhoneDigits() {
        assertTrue(SearchNormalizer.matches("061234", "+242 06 123 45 67"))
    }
}
