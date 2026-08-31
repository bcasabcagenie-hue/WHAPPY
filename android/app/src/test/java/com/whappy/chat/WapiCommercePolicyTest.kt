package com.whappy.chat

import org.junit.Assert.*
import org.junit.Test

class WapiCommercePolicyTest {
    @Test fun catalogFiltersCombineAccentInsensitiveSearchCategoryAndAvailability() {
        val item = mapOf("name" to "Café crème", "description" to "Fait maison", "category" to " Boissons ", "available" to true)
        assertTrue(WapiCatalogFilter.matches(item, "cafe", "Boissons", true))
        assertTrue(WapiCatalogFilter.matches(item, "MAISON"))
        assertFalse(WapiCatalogFilter.matches(item, "cafe", "Menus"))
        assertFalse(WapiCatalogFilter.matches(item + ("available" to false), "", availableOnly = true))
        assertFalse(WapiCatalogFilter.matches(item - "available", "", availableOnly = true))
    }
    @Test fun uncategorizedProductsRemainDiscoverable() {
        val item = mapOf("name" to "Menu", "category" to "  ")
        assertEquals("Autres", WapiCatalogFilter.category(item))
        assertTrue(WapiCatalogFilter.matches(item, "", "Autres"))
        assertFalse(WapiCatalogFilter.matches(item, "pizza"))
    }
    @Test fun decimalPricesAreExactAndAcceptComma() {
        assertEquals(1250L, WapiCommercePolicy.priceMinor("12,50", "EUR"))
        assertEquals(1L, WapiCommercePolicy.priceMinor("0.01", "USD"))
        assertEquals(5500L, WapiCommercePolicy.priceMinor("5500", "XAF"))
        assertEquals("12.50", WapiCommercePolicy.priceInput(1250, "EUR"))
        assertEquals("5500", WapiCommercePolicy.priceInput(5500, "XAF"))
    }
    @Test fun malformedOrRoundedPricesAreRejected() {
        for (value in listOf("", "1e2", "-1", "1.234", "12,5.0", "100000001", "999999999999999999999")) {
            assertNull(value, WapiCommercePolicy.priceMinor(value, "EUR"))
        }
        assertNull(WapiCommercePolicy.priceMinor("1.50", "XAF"))
    }
    @Test fun onlyLatestRequestCanUpdateVisibleContent() {
        val generation = WapiRequestGeneration()
        val oldSearch = generation.begin()
        val newSearch = generation.begin()
        assertFalse(generation.accepts(oldSearch))
        assertTrue(generation.accepts(newSearch))
        generation.begin()
        assertFalse(generation.accepts(newSearch))
    }
}
