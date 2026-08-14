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
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalize("+242", rawValue);
        if (normalized != null) candidates.add(normalized);
        if (rawValue == null) return new ArrayList<>(candidates);

        String digits = rawValue.replaceAll("\\D", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.startsWith("242") && digits.length() > 3) {
            String withCountry = "+" + digits;
            if (normalize("+242", withCountry) != null) candidates.add(withCountry);
        }
        if (digits.length() == 9) {
            String local = "+242" + digits;
            if (normalize("+242", local) != null) candidates.add(local);
        }
        return new ArrayList<>(candidates);
    }

    private static boolean dropsDomesticZero(String countryCode) {
        return !countryCode.equals("+242") && !countryCode.equals("+225");
    }
}
