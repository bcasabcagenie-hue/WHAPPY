#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Objective-C++ boundary around WAPI's cross-platform C++20 core.
@interface WapiNativeCoreBridge : NSObject

+ (NSString *)normalizePhone:(NSString *)phone
          defaultCountryCode:(NSString *)countryCode;

+ (uint64_t)identityRevisionForUserID:(NSString *)userID
                             photoURL:(NSString *)photoURL
                             verified:(BOOL)verified;

+ (NSString *)version;

@end

NS_ASSUME_NONNULL_END
