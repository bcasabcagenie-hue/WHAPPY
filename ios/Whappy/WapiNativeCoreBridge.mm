#import "WapiNativeCoreBridge.h"

#include "../../shared/native/wapi_core.h"

#include <array>

@implementation WapiNativeCoreBridge

+ (NSString *)normalizePhone:(NSString *)phone
          defaultCountryCode:(NSString *)countryCode {
    std::array<char, 96> output{};
    wapi_core_normalize_phone(
        phone.UTF8String,
        countryCode.UTF8String,
        output.data(),
        output.size()
    );
    return [NSString stringWithUTF8String:output.data()] ?: @"";
}

+ (uint64_t)identityRevisionForUserID:(NSString *)userID
                             photoURL:(NSString *)photoURL
                             verified:(BOOL)verified {
    return wapi_core_identity_revision(userID.UTF8String, photoURL.UTF8String, verified);
}

+ (NSString *)version {
    return [NSString stringWithUTF8String:wapi_core_version()] ?: @"wapi-core/unavailable";
}

@end
