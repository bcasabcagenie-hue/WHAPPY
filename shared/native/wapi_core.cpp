#include "wapi_core.h"

#include <algorithm>
#include <cstring>
#include <string>

namespace {

std::string digitsOnly(const char* value) {
    std::string result;
    if (value == nullptr) return result;
    for (const unsigned char character : std::string(value)) {
        if (character >= '0' && character <= '9') result.push_back(static_cast<char>(character));
    }
    return result;
}

std::string normalizePhone(const char* input, const char* defaultCountryCode) {
    const std::string raw = input == nullptr ? "" : input;
    std::string digits = digitsOnly(input);
    const std::string country = digitsOnly(defaultCountryCode);
    if (digits.size() >= 2 && digits[0] == '0' && digits[1] == '0') digits.erase(0, 2);
    const bool explicitlyInternational = raw.find('+') != std::string::npos ||
        (!country.empty() && digits.rfind(country, 0) == 0);
    if (!digits.empty() && !country.empty() && !explicitlyInternational) digits.insert(0, country);
    return digits;
}

void hashBytes(std::uint64_t& hash, const char* value) {
    if (value == nullptr) return;
    for (const unsigned char character : std::string(value)) {
        hash ^= character;
        hash *= 1099511628211ULL;
    }
}

}  // namespace

std::size_t wapi_core_normalize_phone(
    const char* input,
    const char* default_country_code,
    char* output,
    const std::size_t output_capacity
) {
    const std::string normalized = normalizePhone(input, default_country_code);
    if (output != nullptr && output_capacity > 0) {
        const std::size_t count = std::min(normalized.size(), output_capacity - 1);
        std::memcpy(output, normalized.data(), count);
        output[count] = '\0';
    }
    return normalized.size();
}

std::uint64_t wapi_core_identity_revision(
    const char* user_id,
    const char* photo_url,
    const bool verified
) {
    std::uint64_t hash = 14695981039346656037ULL;
    hashBytes(hash, user_id);
    hash ^= 0xFFU;
    hash *= 1099511628211ULL;
    hashBytes(hash, photo_url);
    hash ^= verified ? 1U : 0U;
    hash *= 1099511628211ULL;
    return hash;
}

const char* wapi_core_version() {
    return "wapi-core/1.0-cxx20";
}
