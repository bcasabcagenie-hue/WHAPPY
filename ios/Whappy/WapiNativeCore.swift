import Foundation

/// Swift boundary for the C++20 core also loaded by Android through JNI.
enum WapiNativeCore {
    static func normalizePhone(_ value: String, defaultCountryCode: String = "242") -> String {
        let result = WapiNativeCoreBridge.normalizePhone(value, defaultCountryCode: defaultCountryCode)
        if !result.isEmpty { return result }
        let digits = value.filter(\.isNumber)
        return digits.hasPrefix("00") ? String(digits.dropFirst(2)) : digits
    }

    static func identityRevision(userID: String, photoURL: String, verified: Bool) -> UInt64 {
        WapiNativeCoreBridge.identityRevision(forUserID: userID, photoURL: photoURL, verified: verified)
    }

    static var version: String { WapiNativeCoreBridge.version() }
}
