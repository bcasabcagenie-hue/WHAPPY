package com.whappy.chat

import java.text.Normalizer
import java.util.Locale

internal object SearchNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9+@]+"), " ")
        .trim()

    fun matches(query: String, vararg values: String): Boolean {
        val needle = normalize(query)
        if (needle.isBlank()) return true
        val haystack = normalize(values.joinToString(" "))
        val numericNeedle = query.filter(Char::isDigit)
        if (numericNeedle.length >= 3 && query.none(Char::isLetter)) return numericNeedle in values.joinToString(" ").filter(Char::isDigit)
        return needle.split(' ').filter { it.isNotBlank() }.all { it in haystack }
    }
}
