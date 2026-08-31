#pragma once

#include <cstddef>
#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// Writes a digits-only canonical phone number and returns its required length.
// Local numbers are prefixed with the supplied country code while preserving
// the national trunk digit used by WAPI account identifiers.
std::size_t wapi_core_normalize_phone(
    const char* input,
    const char* default_country_code,
    char* output,
    std::size_t output_capacity
);

// Stable cross-platform revision for avatar/profile cache invalidation.
std::uint64_t wapi_core_identity_revision(
    const char* user_id,
    const char* photo_url,
    bool verified
);

const char* wapi_core_version();

#ifdef __cplusplus
}
#endif
