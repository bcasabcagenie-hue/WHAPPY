package com.whappy.chat

import java.math.BigDecimal

/** Locale-friendly entry; never round an invalid price or send floating-point money. */
internal object WapiCommercePolicy {
    fun fractionDigits(currency: String) = if (currency in setOf("EUR", "USD")) 2 else 0
    fun priceMinor(input: String, currency: String): Long? {
        val normalized = input.trim().replace(',', '.')
        if (!Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(normalized)) return null
        return runCatching {
            BigDecimal(normalized).movePointRight(fractionDigits(currency)).longValueExact()
                .takeIf { it in 0..100_000_000L }
        }.getOrNull()
    }
    fun priceInput(amount: Long, currency: String): String =
        BigDecimal.valueOf(amount, fractionDigits(currency)).toPlainString()
}

/** A slow result from a previous route/search must not replace the visible screen. */
internal class WapiRequestGeneration {
    private var current = 0L
    fun begin(): Long = ++current
    fun accepts(request: Long) = current == request
}

internal object WapiCatalogFilter {
    fun category(product: Map<String, Any?>) = (product["category"] as? String)?.trim().orEmpty().ifBlank { "Autres" }
    fun matches(product: Map<String, Any?>, query: String, selectedCategory: String = "", availableOnly: Boolean = false): Boolean =
        (!availableOnly || product["available"] == true) &&
            (selectedCategory.isEmpty() || category(product) == selectedCategory) &&
            SearchNormalizer.matches(query, (product["name"] as? String).orEmpty(), (product["description"] as? String).orEmpty(), category(product))
}
