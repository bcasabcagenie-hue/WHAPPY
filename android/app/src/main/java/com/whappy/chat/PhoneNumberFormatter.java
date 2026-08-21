package com.whappy.chat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PhoneNumberFormatter {
    private static final Map<String, Integer> NATIONAL_LENGTHS = new LinkedHashMap<>();

    static {
        NATIONAL_LENGTHS.put("+242", 9);
        NATIONAL_LENGTHS.put("+243", 9);
        NATIONAL_LENGTHS.put("+237", 9);
        NATIONAL_LENGTHS.put("+225", 10);
        NATIONAL_LENGTHS.put("+221", 9);
        NATIONAL_LENGTHS.put("+33", 9);
        NATIONAL_LENGTHS.put("+241", 8);
        NATIONAL_LENGTHS.put("+244", 9);
        NATIONAL_LENGTHS.put("+234", 10);
        NATIONAL_LENGTHS.put("+27", 9);
        NATIONAL_LENGTHS.put("+1", 10);
    }

    private PhoneNumberFormatter() {}

    static String normalize(String selectedCountryCode, String rawValue) {
        if (rawValue == null) return null;
        String raw = rawValue.trim();
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;

        String candidate;
        if (raw.startsWith("+")) {
            candidate = "+" + digits;
        } else if (digits.startsWith("00")) {
            candidate = "+" + digits.substring(2);
        } else {
            String countryDigits = selectedCountryCode.substring(1);
            String national = digits.startsWith(countryDigits) && digits.length() > countryDigits.length()
                    ? digits.substring(countryDigits.length())
                    : digits;
            if (dropsDomesticZero(selectedCountryCode) && national.startsWith("0")) {
                national = national.substring(1);
            }
            candidate = selectedCountryCode + national;
        }

        if (!candidate.matches("\\+[1-9]\\d{7,14}")) return null;
        for (Map.Entry<String, Integer> entry : NATIONAL_LENGTHS.entrySet()) {
            if (!candidate.startsWith(entry.getKey())) continue;
            int nationalLength = candidate.length() - entry.getKey().length();
            return nationalLength == entry.getValue() ? candidate : null;
        }
        return candidate;
    }

    static List<String> lookupCandidates(String rawValue) {
        return lookupCandidates(rawValue, "+242");
    }

    static List<String> lookupCandidates(String rawValue, String preferredCountryCode) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String preferred = preferredCountryCode == null || preferredCountryCode.isBlank() ? "+242" : preferredCountryCode;
        String normalized = normalize(preferred, rawValue);
        if (normalized != null) candidates.add(normalized);
        if (rawValue == null) return new ArrayList<>(candidates);

        String digits = rawValue.replaceAll("\\D", "");
        if (rawValue.startsWith("00")) {
            String withCountry = "+" + digits;
            String fromInternational = normalize(preferred, withCountry);
            if (fromInternational != null) candidates.add(fromInternational);
        }

        if (rawValue.startsWith("+")) {
            for (Map.Entry<String, Integer> entry : NATIONAL_LENGTHS.entrySet()) {
                String candidate = normalize(entry.getKey(), rawValue);
                if (candidate != null) candidates.add(candidate);
            }
            return new ArrayList<>(candidates);
        }

        if (rawValue.startsWith("00")) {
            digits = digits.length() > 2 ? digits.substring(2) : "";
        }
        if (digits.startsWith("242") && digits.length() > 3) {
            String withCountry = "+" + digits;
            for (Map.Entry<String, Integer> entry : NATIONAL_LENGTHS.entrySet()) {
                String candidate = normalize(entry.getKey(), withCountry);
                if (candidate != null) candidates.add(candidate);
            }
            // Congo numbers are commonly typed with or without the domestic
            // leading zero. Firebase can store either representation, so
            // search both canonical forms.
            String national = digits.substring(3);
            if (national.startsWith("0") && national.length() > 1) {
                String withoutDomesticZero = normalize("+242", "+242" + national.substring(1));
                if (withoutDomesticZero != null) candidates.add(withoutDomesticZero);
            } else {
                String withDomesticZero = normalize("+242", "+2420" + national);
                if (withDomesticZero != null) candidates.add(withDomesticZero);
            }
        }
        if (!rawValue.startsWith("+") && !rawValue.startsWith("00") && !digits.startsWith("242") && digits.length() <= 10) {
            String national = digits;
            String withDomesticZero = normalize("+242", "+242" + (national.startsWith("0") ? national : "0" + national));
            String withoutDomesticZero = normalize("+242", "+242" + (national.startsWith("0") ? national.substring(1) : national));
            if (withDomesticZero != null) candidates.add(withDomesticZero);
            if (withoutDomesticZero != null) candidates.add(withoutDomesticZero);
        }
        for (Map.Entry<String, Integer> entry : NATIONAL_LENGTHS.entrySet()) {
            String fallback = normalize(entry.getKey(), rawValue);
            if (fallback != null) candidates.add(fallback);
        }
        return new ArrayList<>(candidates);
    }

    static String normalizeAny(String rawValue) {
        if (rawValue != null) {
            String digits = rawValue.replaceAll("\\D", "");
            // A ten-digit local number beginning with 2–9 is unambiguously
            // North American in the formats supported by WHAPPY. Prefer it
            // before countries that also use ten national digits.
            if (!rawValue.trim().startsWith("+") && !digits.startsWith("00")
                    && digits.length() == 10 && !digits.startsWith("0")) {
                String northAmerican = normalize("+1", rawValue);
                if (northAmerican != null) return northAmerican;
            }
        }
        for (String countryCode : NATIONAL_LENGTHS.keySet()) {
            String normalized = normalize(countryCode, rawValue);
            if (normalized != null) return normalized;
        }
        return null;
    }

    private static boolean dropsDomesticZero(String countryCode) {
        return !countryCode.equals("+242") && !countryCode.equals("+241") && !countryCode.equals("+225") && !countryCode.equals("+1");
    }
}
