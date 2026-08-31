import Foundation
import FirebaseFirestore
import FirebaseFunctions
import SwiftUI

let whappyFounderPhone = "+242065465808"
let whappyFounderName = "Cyril Bokilo"
let whappyFounderBusinessName = "BCA SA"
let whappyFounderChannelName = "Le Cercle de Cyril"
let whappyFounderChannelTagline = "Idées, projets et annonces publiés directement par Cyril Bokilo."
let whappyFounderBadgeLabel = "Fondateur"
let whappyFounderPaySlug = "whappy-by-bca"
let whappyFounderPhoneNormalized = WhappyPhoneCountry.normalize(whappyFounderPhone) ?? whappyFounderPhone

/// Keeps Firebase, network and media implementation details out of the UI.
func wapiUserFacingError(_ error: Error, action: String) -> String {
    let failure = error as NSError
    if failure.domain == FunctionsErrorDomain, let code = FunctionsErrorCode(rawValue: failure.code) {
        switch code {
        case .notFound:
            return "\(action) est en cours de mise à jour. Réessayez dans quelques instants."
        case .unauthenticated:
            return "Votre session WAPI a expiré. Reconnectez-vous puis réessayez."
        case .permissionDenied:
            return "Votre compte n’est pas autorisé à effectuer cette action."
        case .failedPrecondition:
            return "\(action) ne peut pas continuer dans son état actuel. Actualisez puis réessayez."
        case .alreadyExists:
            return "\(action) est déjà en cours."
        case .resourceExhausted:
            return "Trop de demandes ont été envoyées. Patientez un instant puis réessayez."
        case .unavailable, .deadlineExceeded:
            return "Le réseau WAPI ne répond pas pour le moment. Vérifiez votre connexion puis réessayez."
        default:
            return "\(action) n’a pas abouti. Réessayez dans quelques instants."
        }
    }
    if failure.domain == FirestoreErrorDomain {
        // FIRFirestoreErrorCode uses the canonical gRPC status numbers.
        switch failure.code {
        case 16: // unauthenticated
            return "Votre session WAPI a expiré. Reconnectez-vous puis réessayez."
        case 7: // permissionDenied
            return "WAPI ne peut pas accéder à ces données avec ce compte."
        case 14, 4: // unavailable, deadlineExceeded
            return "La synchronisation WAPI est momentanément indisponible. Vérifiez votre connexion."
        default:
            return "\(action) n’a pas pu être synchronisé. Actualisez puis réessayez."
        }
    }
    if failure.domain == NSURLErrorDomain {
        return "Connexion internet instable. Vérifiez le réseau puis réessayez."
    }
    let local = error.localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines)
    if local.hasPrefix("Autorisez ") || local.hasPrefix("Le contact WAPI ") {
        return local
    }
    return "\(action) n’a pas abouti. Fermez cet écran puis réessayez."
}

func isWhappyFounderPhone(_ rawPhone: String) -> Bool {
    let normalized = WapiNativeCore.normalizePhone(rawPhone)
    return normalized == whappyFounderPhoneNormalized.filter(\.isNumber)
}

enum WapiInterfaceLanguage: String, CaseIterable, Identifiable {
    case automatic = "auto"
    case french = "fr"
    case english = "en"
    case lingala = "ln"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .automatic: return "Automatique (région)"
        case .french: return "Français"
        case .english: return "English"
        case .lingala: return "Lingála"
        }
    }

    var resolved: WapiInterfaceLanguage {
        guard self == .automatic else { return self }
        switch Locale.autoupdatingCurrent.language.languageCode?.identifier.lowercased() {
        case "en": return .english
        case "ln": return .lingala
        default: return .french
        }
    }

    var automaticSummary: String {
        self == .automatic ? "Automatique · \(resolved.label)" : label
    }

    func text(_ french: String, _ english: String, _ lingala: String) -> String {
        switch resolved {
        case .english: return english
        case .lingala: return lingala
        case .automatic, .french: return french
        }
    }
}

struct WapiTranslationLanguage: Identifiable, Hashable {
    let code: String
    let name: String
    let nativeName: String

    var id: String { code }
    var displayName: String { "\(nativeName) · \(name)" }

    static let supported: [WapiTranslationLanguage] = [
        .init(code: "fr", name: "Français", nativeName: "Français"),
        .init(code: "en", name: "Anglais", nativeName: "English"),
        .init(code: "zh-CN", name: "Chinois simplifié", nativeName: "中文"),
        .init(code: "ar", name: "Arabe", nativeName: "العربية"),
        .init(code: "ru", name: "Russe", nativeName: "Русский"),
        .init(code: "es", name: "Espagnol", nativeName: "Español"),
        .init(code: "pt", name: "Portugais", nativeName: "Português"),
        .init(code: "tr", name: "Turc", nativeName: "Türkçe"),
        .init(code: "ja", name: "Japonais", nativeName: "日本語"),
        .init(code: "it", name: "Italien", nativeName: "Italiano"),
        .init(code: "nl", name: "Néerlandais", nativeName: "Nederlands"),
        .init(code: "de", name: "Allemand", nativeName: "Deutsch"),
        .init(code: "ko", name: "Coréen", nativeName: "한국어"),
        .init(code: "hi", name: "Hindi", nativeName: "हिन्दी"),
        .init(code: "sw", name: "Swahili", nativeName: "Kiswahili"),
        .init(code: "ln", name: "Lingála", nativeName: "Lingála")
    ]

    static func language(for code: String) -> WapiTranslationLanguage {
        supported.first(where: { $0.code == code }) ?? supported[0]
    }
}

struct WapiTranslationResult: Hashable {
    let text: String
    let detectedLanguage: String
    let targetLanguage: String
}

struct WhappyPhoneCountry: Identifiable, Hashable {
    var id: String { "\(isoCode)-\(code)" }
    let name: String
    let isoCode: String
    let flag: String
    let code: String
    let nationalLength: Int
    let keepsLeadingZero: Bool

    static let supported = [
        WhappyPhoneCountry(name: "Afghanistan", isoCode: "AF", flag: flag(for: "AF"), code: "+93", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Albania", isoCode: "AL", flag: flag(for: "AL"), code: "+355", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Algeria", isoCode: "DZ", flag: flag(for: "DZ"), code: "+213", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "AmericanSamoa", isoCode: "AS", flag: flag(for: "AS"), code: "+1684", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Andorra", isoCode: "AD", flag: flag(for: "AD"), code: "+376", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Angola", isoCode: "AO", flag: flag(for: "AO"), code: "+244", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Anguilla", isoCode: "AI", flag: flag(for: "AI"), code: "+1264", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Antarctica", isoCode: "AQ", flag: flag(for: "AQ"), code: "+672", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Antigua and Barbuda", isoCode: "AG", flag: flag(for: "AG"), code: "+1268", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Argentina", isoCode: "AR", flag: flag(for: "AR"), code: "+54", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Armenia", isoCode: "AM", flag: flag(for: "AM"), code: "+374", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Aruba", isoCode: "AW", flag: flag(for: "AW"), code: "+297", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Australia", isoCode: "AU", flag: flag(for: "AU"), code: "+61", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Austria", isoCode: "AT", flag: flag(for: "AT"), code: "+43", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Azerbaijan", isoCode: "AZ", flag: flag(for: "AZ"), code: "+994", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Bahamas", isoCode: "BS", flag: flag(for: "BS"), code: "+1242", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Bahrain", isoCode: "BH", flag: flag(for: "BH"), code: "+973", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Bangladesh", isoCode: "BD", flag: flag(for: "BD"), code: "+880", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Barbados", isoCode: "BB", flag: flag(for: "BB"), code: "+1246", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Belarus", isoCode: "BY", flag: flag(for: "BY"), code: "+375", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Belgium", isoCode: "BE", flag: flag(for: "BE"), code: "+32", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Belize", isoCode: "BZ", flag: flag(for: "BZ"), code: "+501", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Benin", isoCode: "BJ", flag: flag(for: "BJ"), code: "+229", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Bermuda", isoCode: "BM", flag: flag(for: "BM"), code: "+1441", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Bhutan", isoCode: "BT", flag: flag(for: "BT"), code: "+975", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Bolivia, Plurinational State of Bolivia", isoCode: "BO", flag: flag(for: "BO"), code: "+591", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Bosnia and Herzegovina", isoCode: "BA", flag: flag(for: "BA"), code: "+387", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Botswana", isoCode: "BW", flag: flag(for: "BW"), code: "+267", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Bouvet Island", isoCode: "BV", flag: flag(for: "BV"), code: "+55", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Brazil", isoCode: "BR", flag: flag(for: "BR"), code: "+55", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "British Indian Ocean Territory", isoCode: "IO", flag: flag(for: "IO"), code: "+246", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Brunei Darussalam", isoCode: "BN", flag: flag(for: "BN"), code: "+673", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Bulgaria", isoCode: "BG", flag: flag(for: "BG"), code: "+359", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Burkina Faso", isoCode: "BF", flag: flag(for: "BF"), code: "+226", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Burundi", isoCode: "BI", flag: flag(for: "BI"), code: "+257", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Cambodia", isoCode: "KH", flag: flag(for: "KH"), code: "+855", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Cameroon", isoCode: "CM", flag: flag(for: "CM"), code: "+237", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Canada", isoCode: "CA", flag: flag(for: "CA"), code: "+1", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Cape Verde", isoCode: "CV", flag: flag(for: "CV"), code: "+238", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Cayman Islands", isoCode: "KY", flag: flag(for: "KY"), code: "+1345", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Central African Republic", isoCode: "CF", flag: flag(for: "CF"), code: "+236", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Chad", isoCode: "TD", flag: flag(for: "TD"), code: "+235", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Chile", isoCode: "CL", flag: flag(for: "CL"), code: "+56", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "China", isoCode: "CN", flag: flag(for: "CN"), code: "+86", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Christmas Island", isoCode: "CX", flag: flag(for: "CX"), code: "+61", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Cocos (Keeling) Islands", isoCode: "CC", flag: flag(for: "CC"), code: "+61", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Colombia", isoCode: "CO", flag: flag(for: "CO"), code: "+57", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Comoros", isoCode: "KM", flag: flag(for: "KM"), code: "+269", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Congo", isoCode: "CG", flag: flag(for: "CG"), code: "+242", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Congo, The Democratic Republic of the", isoCode: "CD", flag: flag(for: "CD"), code: "+243", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Cook Islands", isoCode: "CK", flag: flag(for: "CK"), code: "+682", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Costa Rica", isoCode: "CR", flag: flag(for: "CR"), code: "+506", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Ivory Coast", isoCode: "CI", flag: flag(for: "CI"), code: "+225", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Croatia", isoCode: "HR", flag: flag(for: "HR"), code: "+385", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Cuba", isoCode: "CU", flag: flag(for: "CU"), code: "+53", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Cyprus", isoCode: "CY", flag: flag(for: "CY"), code: "+357", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Czech Republic", isoCode: "CZ", flag: flag(for: "CZ"), code: "+420", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Denmark", isoCode: "DK", flag: flag(for: "DK"), code: "+45", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Djibouti", isoCode: "DJ", flag: flag(for: "DJ"), code: "+253", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Dominica", isoCode: "DM", flag: flag(for: "DM"), code: "+1767", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Dominican Republic", isoCode: "DO", flag: flag(for: "DO"), code: "+1849", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Ecuador", isoCode: "EC", flag: flag(for: "EC"), code: "+593", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Egypt", isoCode: "EG", flag: flag(for: "EG"), code: "+20", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "El Salvador", isoCode: "SV", flag: flag(for: "SV"), code: "+503", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Equatorial Guinea", isoCode: "GQ", flag: flag(for: "GQ"), code: "+240", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Eritrea", isoCode: "ER", flag: flag(for: "ER"), code: "+291", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Estonia", isoCode: "EE", flag: flag(for: "EE"), code: "+372", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Ethiopia", isoCode: "ET", flag: flag(for: "ET"), code: "+251", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Falkland Islands", isoCode: "FK", flag: flag(for: "FK"), code: "+500", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Faroe Islands", isoCode: "FO", flag: flag(for: "FO"), code: "+298", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Fiji", isoCode: "FJ", flag: flag(for: "FJ"), code: "+679", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Finland", isoCode: "FI", flag: flag(for: "FI"), code: "+358", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "France", isoCode: "FR", flag: flag(for: "FR"), code: "+33", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "French Polynesia", isoCode: "PF", flag: flag(for: "PF"), code: "+689", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "French Southern and Antarctic Lands", isoCode: "TF", flag: flag(for: "TF"), code: "+262", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Gabon", isoCode: "GA", flag: flag(for: "GA"), code: "+241", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Gambia", isoCode: "GM", flag: flag(for: "GM"), code: "+220", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Georgia", isoCode: "GE", flag: flag(for: "GE"), code: "+995", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Germany", isoCode: "DE", flag: flag(for: "DE"), code: "+49", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Ghana", isoCode: "GH", flag: flag(for: "GH"), code: "+233", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Gibraltar", isoCode: "GI", flag: flag(for: "GI"), code: "+350", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Greece", isoCode: "EL", flag: flag(for: "EL"), code: "+30", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Greenland", isoCode: "GL", flag: flag(for: "GL"), code: "+299", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Grenada", isoCode: "GD", flag: flag(for: "GD"), code: "+1473", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Guadeloupe", isoCode: "GP", flag: flag(for: "GP"), code: "+590", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Guam", isoCode: "GU", flag: flag(for: "GU"), code: "+1671", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Guatemala", isoCode: "GT", flag: flag(for: "GT"), code: "+502", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Guernsey", isoCode: "GG", flag: flag(for: "GG"), code: "+44", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Guinea", isoCode: "GN", flag: flag(for: "GN"), code: "+224", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Guinea-Bissau", isoCode: "GW", flag: flag(for: "GW"), code: "+245", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Guyana", isoCode: "GY", flag: flag(for: "GY"), code: "+592", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Haiti", isoCode: "HT", flag: flag(for: "HT"), code: "+509", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Heard Island and McDonald Islands", isoCode: "HM", flag: flag(for: "HM"), code: "+672", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Vatican City State (Holy See)", isoCode: "VA", flag: flag(for: "VA"), code: "+379", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Honduras", isoCode: "HN", flag: flag(for: "HN"), code: "+504", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Hong Kong", isoCode: "HK", flag: flag(for: "HK"), code: "+852", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Hungary", isoCode: "HU", flag: flag(for: "HU"), code: "+36", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Iceland", isoCode: "IS", flag: flag(for: "IS"), code: "+354", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "India", isoCode: "IN", flag: flag(for: "IN"), code: "+91", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Indonesia", isoCode: "ID", flag: flag(for: "ID"), code: "+62", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Iran, Islamic Republic of", isoCode: "IR", flag: flag(for: "IR"), code: "+98", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Iraq", isoCode: "IQ", flag: flag(for: "IQ"), code: "+964", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Ireland", isoCode: "IE", flag: flag(for: "IE"), code: "+353", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Isle of Man", isoCode: "IM", flag: flag(for: "IM"), code: "+44", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Israel", isoCode: "IL", flag: flag(for: "IL"), code: "+972", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Italy", isoCode: "IT", flag: flag(for: "IT"), code: "+39", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Jamaica", isoCode: "JM", flag: flag(for: "JM"), code: "+1876", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Japan", isoCode: "JP", flag: flag(for: "JP"), code: "+81", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Jersey", isoCode: "JE", flag: flag(for: "JE"), code: "+44", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Jordan", isoCode: "JO", flag: flag(for: "JO"), code: "+962", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Kazakhstan", isoCode: "KZ", flag: flag(for: "KZ"), code: "+7", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Kenya", isoCode: "KE", flag: flag(for: "KE"), code: "+254", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Kiribati", isoCode: "KI", flag: flag(for: "KI"), code: "+686", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "North Korea", isoCode: "KP", flag: flag(for: "KP"), code: "+850", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "South Korea", isoCode: "KR", flag: flag(for: "KR"), code: "+82", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Kuwait", isoCode: "KW", flag: flag(for: "KW"), code: "+965", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Kyrgyzstan", isoCode: "KG", flag: flag(for: "KG"), code: "+996", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Laos", isoCode: "LA", flag: flag(for: "LA"), code: "+856", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Latvia", isoCode: "LV", flag: flag(for: "LV"), code: "+371", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Lebanon", isoCode: "LB", flag: flag(for: "LB"), code: "+961", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Lesotho", isoCode: "LS", flag: flag(for: "LS"), code: "+266", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Liberia", isoCode: "LR", flag: flag(for: "LR"), code: "+231", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Libyan Arab Jamahiriya", isoCode: "LY", flag: flag(for: "LY"), code: "+218", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Liechtenstein", isoCode: "LI", flag: flag(for: "LI"), code: "+423", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Lithuania", isoCode: "LT", flag: flag(for: "LT"), code: "+370", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Luxembourg", isoCode: "LU", flag: flag(for: "LU"), code: "+352", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Macau", isoCode: "MO", flag: flag(for: "MO"), code: "+853", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Macedonia, The Former Yugoslav Republic of", isoCode: "MK", flag: flag(for: "MK"), code: "+389", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Madagascar", isoCode: "MG", flag: flag(for: "MG"), code: "+261", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Malawi", isoCode: "MW", flag: flag(for: "MW"), code: "+265", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Malaysia", isoCode: "MY", flag: flag(for: "MY"), code: "+60", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Maldives", isoCode: "MV", flag: flag(for: "MV"), code: "+960", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Mali", isoCode: "ML", flag: flag(for: "ML"), code: "+223", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Malta", isoCode: "MT", flag: flag(for: "MT"), code: "+356", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Marshall Islands", isoCode: "MH", flag: flag(for: "MH"), code: "+692", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Martinique", isoCode: "MQ", flag: flag(for: "MQ"), code: "+596", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Mauritania", isoCode: "MR", flag: flag(for: "MR"), code: "+222", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Mauritius", isoCode: "MU", flag: flag(for: "MU"), code: "+230", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Mayotte", isoCode: "YT", flag: flag(for: "YT"), code: "+262", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Mexico", isoCode: "MX", flag: flag(for: "MX"), code: "+52", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Micronesia, Federated States of", isoCode: "FM", flag: flag(for: "FM"), code: "+691", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Moldova, Republic of", isoCode: "MD", flag: flag(for: "MD"), code: "+373", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Monaco", isoCode: "MC", flag: flag(for: "MC"), code: "+377", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Mongolia", isoCode: "MN", flag: flag(for: "MN"), code: "+976", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Montenegro", isoCode: "ME", flag: flag(for: "ME"), code: "+382", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Montserrat", isoCode: "MS", flag: flag(for: "MS"), code: "+1664", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Morocco", isoCode: "MA", flag: flag(for: "MA"), code: "+212", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Mozambique", isoCode: "MZ", flag: flag(for: "MZ"), code: "+258", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Myanmar", isoCode: "MM", flag: flag(for: "MM"), code: "+95", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Namibia", isoCode: "NA", flag: flag(for: "NA"), code: "+264", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Nauru", isoCode: "NR", flag: flag(for: "NR"), code: "+674", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Nepal", isoCode: "NP", flag: flag(for: "NP"), code: "+977", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Netherlands", isoCode: "NL", flag: flag(for: "NL"), code: "+31", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Netherlands Antilles", isoCode: "AN", flag: flag(for: "AN"), code: "+599", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "New Caledonia", isoCode: "NC", flag: flag(for: "NC"), code: "+687", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "New Zealand", isoCode: "NZ", flag: flag(for: "NZ"), code: "+64", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Nicaragua", isoCode: "NI", flag: flag(for: "NI"), code: "+505", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Niger", isoCode: "NE", flag: flag(for: "NE"), code: "+227", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Nigeria", isoCode: "NG", flag: flag(for: "NG"), code: "+234", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Niue", isoCode: "NU", flag: flag(for: "NU"), code: "+683", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Norfolk Island", isoCode: "NF", flag: flag(for: "NF"), code: "+672", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Northern Mariana Islands", isoCode: "MP", flag: flag(for: "MP"), code: "+1670", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Norway", isoCode: "NO", flag: flag(for: "NO"), code: "+47", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Oman", isoCode: "OM", flag: flag(for: "OM"), code: "+968", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Pakistan", isoCode: "PK", flag: flag(for: "PK"), code: "+92", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Palau", isoCode: "PW", flag: flag(for: "PW"), code: "+680", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Palestinian Territory, Occupied", isoCode: "PS", flag: flag(for: "PS"), code: "+970", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Panama", isoCode: "PA", flag: flag(for: "PA"), code: "+507", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Papua New Guinea", isoCode: "PG", flag: flag(for: "PG"), code: "+675", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Paraguay", isoCode: "PY", flag: flag(for: "PY"), code: "+595", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Peru", isoCode: "PE", flag: flag(for: "PE"), code: "+51", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Philippines", isoCode: "PH", flag: flag(for: "PH"), code: "+63", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Pitcairn", isoCode: "PN", flag: flag(for: "PN"), code: "+870", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Poland", isoCode: "PL", flag: flag(for: "PL"), code: "+48", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Portugal", isoCode: "PT", flag: flag(for: "PT"), code: "+351", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Puerto Rico", isoCode: "PR", flag: flag(for: "PR"), code: "+1939", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Qatar", isoCode: "QA", flag: flag(for: "QA"), code: "+974", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Réunion", isoCode: "RE", flag: flag(for: "RE"), code: "+262", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Romania", isoCode: "RO", flag: flag(for: "RO"), code: "+40", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Russia", isoCode: "RU", flag: flag(for: "RU"), code: "+7", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Rwanda", isoCode: "RW", flag: flag(for: "RW"), code: "+250", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Saint Helena, Ascension and Tristan Da Cunha", isoCode: "SH", flag: flag(for: "SH"), code: "+290", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Saint Kitts and Nevis", isoCode: "KN", flag: flag(for: "KN"), code: "+1869", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Saint Lucia", isoCode: "LC", flag: flag(for: "LC"), code: "+1758", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Saint Pierre and Miquelon", isoCode: "PM", flag: flag(for: "PM"), code: "+508", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Saint Vincent and the Grenadines", isoCode: "VC", flag: flag(for: "VC"), code: "+1784", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Samoa", isoCode: "WS", flag: flag(for: "WS"), code: "+685", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "San Marino", isoCode: "SM", flag: flag(for: "SM"), code: "+378", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Sao Tome and Principe", isoCode: "ST", flag: flag(for: "ST"), code: "+239", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Saudi Arabia", isoCode: "SA", flag: flag(for: "SA"), code: "+966", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Senegal", isoCode: "SN", flag: flag(for: "SN"), code: "+221", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Serbia", isoCode: "RS", flag: flag(for: "RS"), code: "+381", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Seychelles", isoCode: "SC", flag: flag(for: "SC"), code: "+248", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Sierra Leone", isoCode: "SL", flag: flag(for: "SL"), code: "+232", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Singapore", isoCode: "SG", flag: flag(for: "SG"), code: "+65", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Slovakia", isoCode: "SK", flag: flag(for: "SK"), code: "+421", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Slovenia", isoCode: "SI", flag: flag(for: "SI"), code: "+386", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Solomon Islands", isoCode: "SB", flag: flag(for: "SB"), code: "+677", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Somalia", isoCode: "SO", flag: flag(for: "SO"), code: "+252", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "South Africa", isoCode: "ZA", flag: flag(for: "ZA"), code: "+27", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "South Georgia and the South Sandwich Islands", isoCode: "GS", flag: flag(for: "GS"), code: "+500", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Spain", isoCode: "ES", flag: flag(for: "ES"), code: "+34", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Sri Lanka", isoCode: "LK", flag: flag(for: "LK"), code: "+94", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Sudan", isoCode: "SD", flag: flag(for: "SD"), code: "+249", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Suriname", isoCode: "SR", flag: flag(for: "SR"), code: "+597", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Svalbard and Jan Mayen", isoCode: "SJ", flag: flag(for: "SJ"), code: "+47", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Swaziland", isoCode: "SZ", flag: flag(for: "SZ"), code: "+268", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Sweden", isoCode: "SE", flag: flag(for: "SE"), code: "+46", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Switzerland", isoCode: "CH", flag: flag(for: "CH"), code: "+41", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Syria", isoCode: "SY", flag: flag(for: "SY"), code: "+963", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Taiwan", isoCode: "TW", flag: flag(for: "TW"), code: "+886", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Tajikistan", isoCode: "TJ", flag: flag(for: "TJ"), code: "+992", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Tanzania, United Republic of", isoCode: "TZ", flag: flag(for: "TZ"), code: "+255", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Thailand", isoCode: "TH", flag: flag(for: "TH"), code: "+66", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Timor-Leste", isoCode: "TL", flag: flag(for: "TL"), code: "+670", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Togo", isoCode: "TG", flag: flag(for: "TG"), code: "+228", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Tokelau", isoCode: "TK", flag: flag(for: "TK"), code: "+690", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Tonga", isoCode: "TO", flag: flag(for: "TO"), code: "+676", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Trinidad and Tobago", isoCode: "TT", flag: flag(for: "TT"), code: "+1868", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Tunisia", isoCode: "TN", flag: flag(for: "TN"), code: "+216", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "Turkey", isoCode: "TR", flag: flag(for: "TR"), code: "+90", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Turkmenistan", isoCode: "TM", flag: flag(for: "TM"), code: "+993", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Turks and Caicos Islands", isoCode: "TC", flag: flag(for: "TC"), code: "+1649", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Tuvalu", isoCode: "TV", flag: flag(for: "TV"), code: "+688", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Uganda", isoCode: "UG", flag: flag(for: "UG"), code: "+256", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Ukraine", isoCode: "UA", flag: flag(for: "UA"), code: "+380", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "United Arab Emirates", isoCode: "AE", flag: flag(for: "AE"), code: "+971", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "United Kingdom", isoCode: "GB", flag: flag(for: "GB"), code: "+44", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "United States", isoCode: "US", flag: flag(for: "US"), code: "+1", nationalLength: 0, keepsLeadingZero: true),
        WhappyPhoneCountry(name: "United States Minor Outlying Islands", isoCode: "UM", flag: flag(for: "UM"), code: "+1581", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Uruguay", isoCode: "UY", flag: flag(for: "UY"), code: "+598", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Uzbekistan", isoCode: "UZ", flag: flag(for: "UZ"), code: "+998", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Vanuatu", isoCode: "VU", flag: flag(for: "VU"), code: "+678", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Venezuela, Bolivarian Republic of", isoCode: "VE", flag: flag(for: "VE"), code: "+58", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Vietnam", isoCode: "VN", flag: flag(for: "VN"), code: "+84", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Virgin Islands, British", isoCode: "VG", flag: flag(for: "VG"), code: "+1284", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Virgin Islands, U.S.", isoCode: "VI", flag: flag(for: "VI"), code: "+1340", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Wallis and Futuna", isoCode: "WF", flag: flag(for: "WF"), code: "+681", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Western Sahara", isoCode: "EH", flag: flag(for: "EH"), code: "+732", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Yemen", isoCode: "YE", flag: flag(for: "YE"), code: "+967", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Zambia", isoCode: "ZM", flag: flag(for: "ZM"), code: "+260", nationalLength: 0, keepsLeadingZero: false),
        WhappyPhoneCountry(name: "Zimbabwe", isoCode: "ZW", flag: flag(for: "ZW"), code: "+263", nationalLength: 0, keepsLeadingZero: false),
    ]

    static func flag(for isoCode: String) -> String {
        let normalized = isoCode.uppercased()
        guard normalized.count == 2 else { return "🏳️" }
        let base: UInt32 = 127462
        var scalars: [UnicodeScalar] = []
        for scalar in normalized.unicodeScalars {
            guard scalar.value >= 65 && scalar.value <= 90 else { return "🏳️" }
            let index = scalar.value - 65
            scalars.append(UnicodeScalar(base + index)!)
        }
        return String(String.UnicodeScalarView(scalars))
    }

    static func normalize(_ rawValue: String, selectedCode: String = "+242") -> String? {
        let raw = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        var digits = raw.filter(\.isNumber)
        guard !digits.isEmpty else { return nil }
        var candidate: String
        if raw.hasPrefix("+") { candidate = "+" + digits }
        else if digits.hasPrefix("00") { digits.removeFirst(2); candidate = "+" + digits }
        else {
            let countryDigits = selectedCode.filter(\.isNumber)
            if digits.hasPrefix(countryDigits), digits.count > countryDigits.count { digits.removeFirst(countryDigits.count) }
            let country = supported.first { $0.code == selectedCode }
            if country?.keepsLeadingZero == false && digits.hasPrefix("0") { digits.removeFirst() }
            candidate = selectedCode + digits
        }
        guard candidate.range(of: #"^\+[1-9][0-9]{7,14}$"#, options: .regularExpression) != nil else { return nil }
        if let country = supported.sorted(by: { $0.code.count > $1.code.count }).first(where: { candidate.hasPrefix($0.code) }) {
            if country.nationalLength > 0 && candidate.count - country.code.count != country.nationalLength { return nil }
            return candidate
        }
        return candidate
    }
}

extension String {
    private var whappySearchValue: String { folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "fr_FR")).lowercased().replacingOccurrences(of: #"[^a-z0-9+@]+"#, with: " ", options: .regularExpression) }

    func matchesWhappySearch(_ query: String) -> Bool {
        let numeric = query.filter(\.isNumber)
        if numeric.count >= 3 && !query.contains(where: \.isLetter) { return filter(\.isNumber).contains(numeric) }
        return query.whappySearchValue.split(separator: " ").allSatisfy { whappySearchValue.contains($0) }
    }
}

enum WhappyDeepLink: Equatable {
    case contact(String)
    case channel(UUID)
    case groupCall(String)
    case directCall(String)
    case search(String)

    static func parse(_ value: String) -> WhappyDeepLink? {
        let raw = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let legacyPrefix = "WHAPPY:CONTACT:"
        if raw.lowercased().hasPrefix(legacyPrefix.lowercased()) {
            return WhappyPhoneCountry.normalize(String(raw.dropFirst(legacyPrefix.count))).map(Self.contact)
        }
        return URL(string: raw).flatMap(parse)
    }

    static func parse(_ url: URL) -> WhappyDeepLink? {
        let parts = url.pathComponents.filter { $0 != "/" }
        let action = url.scheme?.lowercased() == "whappy" ? (url.host ?? "").lowercased() : (parts.first ?? "").lowercased()
        let payload = url.scheme?.lowercased() == "whappy" ? parts.first : parts.dropFirst().first
        switch action {
        case "contact":
            let value = payload
                ?? URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name.lowercased() == "phone" })?.value
            return value.flatMap { WhappyPhoneCountry.normalize($0.removingPercentEncoding ?? $0) }.map(Self.contact)
        case "channel", "chaine": return payload.flatMap { UUID(uuidString: $0) }.map(Self.channel)
        case "group-call", "appel-groupe":
            guard let raw = payload?.trimmingCharacters(in: .whitespacesAndNewlines),
                  raw.range(of: "^[A-Za-z0-9_-]{2,160}$", options: .regularExpression) != nil else { return nil }
            return .groupCall(raw)
        case "call", "appel":
            guard let raw = payload?.trimmingCharacters(in: .whitespacesAndNewlines),
                  raw.range(of: "^[A-Za-z0-9_-]{2,160}$", options: .regularExpression) != nil else { return nil }
            return .directCall(raw)
        case "search", "recherche":
            return URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { ["q", "query"].contains($0.name.lowercased()) })?.value.flatMap { value -> WhappyDeepLink? in value.isEmpty ? nil : .search(String(value.prefix(120))) }
        default: return nil
        }
    }
}

struct WapiGroupCallRoute: Identifiable, Equatable {
    let callID: String?
    let groupID: String?
    let groupName: String
    let video: Bool
    let groupSource: String

    init(callID: String?, groupID: String?, groupName: String, video: Bool, groupSource: String = "groups") {
        self.callID = callID
        self.groupID = groupID
        self.groupName = groupName
        self.video = video
        self.groupSource = groupSource
    }

    var id: String { callID ?? "new-\(groupID ?? "group")-\(video ? "video" : "audio")" }
}

/// A direct WAPI call is always tied to a real WAPI account—not a telephone
/// URL. Incoming routes contain a session ID; outgoing routes contain the peer.
struct WapiDirectCallRoute: Identifiable, Equatable {
    let callID: String?
    let peerID: String?
    let peerName: String
    let peerPhotoURL: String
    let video: Bool
    var callerName: String? = nil
    var callerPhotoURL: String? = nil
    var callerBusinessPageID: String? = nil
    /// Set when a personal account calls a public Business identity. The
    /// authenticated callee remains the page owner while the call is routed
    /// to the dedicated Business inbox and history.
    var calleeBusinessPageID: String? = nil

    var id: String { callID ?? "new-\(peerID ?? "contact")-\(video ? "video" : "audio")" }
}

struct WapiGroupMember: Identifiable, Hashable, Codable {
    let uid: String
    let displayName: String
    let phoneNumber: String
    let photoURL: String

    var id: String { uid }
}

struct Conversation: Identifiable, Hashable, Codable {
    let id: UUID
    var name: String
    var initials: String
    var phoneNumber: String
    var lastMessage: String
    var unread: Bool
    var readAt: Date = .distantPast
    var messages: [Message]
    var remoteID: String? = nil
    var source: String? = nil
    var peerUID: String? = nil
    var photoURL: String? = nil
    /// Canonical certification state from users/{uid}. Optional keeps older
    /// locally cached conversations backward-compatible.
    var peerVerified: Bool? = nil
    var peerIsOnline: Bool? = nil
    var peerLastSeenAt: Date? = nil
    var groupOwnerID: String? = nil
    var groupAdminIDs: [String] = []
    var groupMembers: [WapiGroupMember] = []
    /// The same phone can operate a personal inbox and one or more Business
    /// inboxes. These fields are persisted on the Firestore conversation so
    /// both iOS and Android can apply the same account boundary.
    var profileType: String? = nil
    var businessPageID: String? = nil
    var businessPageName: String? = nil

    var displaysVerifiedBadge: Bool {
        peerVerified == true || isWhappyFounderPhone(phoneNumber)
    }
}

enum CallMode: String, Identifiable, Codable {
    case audio, video
    var id: String { rawValue }
    var title: String { self == .audio ? "Appel audio" : "Appel vidéo" }
    var systemImage: String { self == .audio ? "phone.fill" : "video.fill" }
}

struct CallRecord: Identifiable, Hashable, Codable {
    let id: UUID
    let name: String
    let phoneNumber: String
    let mode: CallMode
    let date: Date
    let outgoing: Bool
    let missed: Bool
    var profileType: String? = nil
    var businessPageID: String? = nil
}

struct Message: Identifiable, Hashable, Codable {
    let id: UUID
    let text: String
    let mine: Bool
    let sentAt: Date
    var kind: String = "text"
    var mediaPath: String? = nil
    var mediaName: String? = nil
    var mediaSizeBytes: Int64? = nil
    var mediaSha256: String? = nil
    var viewOnce = false
    var viewedByIDs: [String] = []
    var replyToID: UUID? = nil
    var replyText: String? = nil
    var reactions: [String: String] = [:]
    var deleted: Bool = false
    var edited: Bool = false
    var status: String = "sent"
    var remoteID: String? = nil
    var senderID: String? = nil
    var senderName: String? = nil

    init(id: UUID, text: String, mine: Bool, sentAt: Date, kind: String = "text", mediaPath: String? = nil, mediaName: String? = nil, mediaSizeBytes: Int64? = nil, mediaSha256: String? = nil, viewOnce: Bool = false, viewedByIDs: [String] = [], replyToID: UUID? = nil, replyText: String? = nil, reactions: [String: String] = [:], deleted: Bool = false, edited: Bool = false, status: String = "sent", remoteID: String? = nil, senderID: String? = nil, senderName: String? = nil) {
        self.id = id; self.text = text; self.mine = mine; self.sentAt = sentAt; self.kind = kind; self.mediaPath = mediaPath; self.mediaName = mediaName; self.mediaSizeBytes = mediaSizeBytes; self.mediaSha256 = mediaSha256; self.viewOnce = viewOnce; self.viewedByIDs = viewedByIDs; self.replyToID = replyToID; self.replyText = replyText; self.reactions = reactions; self.deleted = deleted; self.edited = edited
        self.status = status
        self.remoteID = remoteID; self.senderID = senderID; self.senderName = senderName
    }

    private enum CodingKeys: String, CodingKey { case id, text, mine, sentAt, kind, mediaPath, mediaName, mediaSizeBytes, mediaSha256, viewOnce, viewedByIDs, replyToID, replyText, reactions, deleted, edited, status, remoteID, senderID, senderName }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(UUID.self, forKey: .id)
        text = try values.decode(String.self, forKey: .text)
        mine = try values.decode(Bool.self, forKey: .mine)
        sentAt = try values.decode(Date.self, forKey: .sentAt)
        kind = try values.decodeIfPresent(String.self, forKey: .kind) ?? "text"
        mediaPath = try values.decodeIfPresent(String.self, forKey: .mediaPath)
        mediaName = try values.decodeIfPresent(String.self, forKey: .mediaName)
        mediaSizeBytes = try values.decodeIfPresent(Int64.self, forKey: .mediaSizeBytes)
        mediaSha256 = try values.decodeIfPresent(String.self, forKey: .mediaSha256)
        viewOnce = try values.decodeIfPresent(Bool.self, forKey: .viewOnce) ?? false
        viewedByIDs = try values.decodeIfPresent([String].self, forKey: .viewedByIDs) ?? []
        replyToID = try values.decodeIfPresent(UUID.self, forKey: .replyToID)
        replyText = try values.decodeIfPresent(String.self, forKey: .replyText)
        reactions = try values.decodeIfPresent([String: String].self, forKey: .reactions) ?? [:]
        deleted = try values.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
        edited = try values.decodeIfPresent(Bool.self, forKey: .edited) ?? false
        status = try values.decodeIfPresent(String.self, forKey: .status) ?? "sent"
        remoteID = try values.decodeIfPresent(String.self, forKey: .remoteID)
        senderID = try values.decodeIfPresent(String.self, forKey: .senderID)
        senderName = try values.decodeIfPresent(String.self, forKey: .senderName)
    }
}

struct WhappyChannel: Identifiable, Hashable, Codable {
    let id: UUID
    var name: String
    var description: String
    var category: String
    var ownerName: String
    var owner: Bool
    var subscribed: Bool
    var memberCount: Int
    var verified: Bool
    var posts: [WhappyChannelPost]
}

struct WhappyChannelPost: Identifiable, Hashable, Codable {
    let id: UUID
    var text: String
    let authorName: String
    let createdAt: Date
    var reactions: [String: String] = [:]
    var pinned: Bool = false
    var deleted: Bool = false
}

struct WhappyMoment: Identifiable, Hashable, Codable {
    let id: UUID
    let title: String
    let text: String
    let createdAt: Date
}

struct WapiStory: Identifiable, Hashable, Codable {
    let id: String
    let authorID: String
    let authorName: String
    let authorPhotoURL: String
    let caption: String
    let mediaURL: String
    let mediaType: String
    let createdAt: Date
    let expiresAt: Date
    var viewCount: Int
    var viewed: Bool
}

struct Listing: Identifiable, Hashable, Codable {
    let id: UUID
    let title: String
    let price: String
    let place: String
    let seller: String
    let icon: String
    let acceptsTrade: Bool
    var saved: Bool = false
    /// Marketplace fields are optional so listings cached by older WAPI builds
    /// keep decoding safely after the Business-only marketplace migration.
    var photoURL: String? = nil
    var description: String? = nil
    var mode: String? = nil
    var businessPageID: String? = nil
    var boostStatus: String? = nil
}

struct CartLine: Identifiable, Hashable, Codable {
    var id: UUID { listing.id }
    let listing: Listing
    var quantity: Int
}

struct WhappyOrder: Identifiable, Hashable, Codable {
    let id: UUID
    let reference: String
    let lines: [CartLine]
    let delivery: String
    let createdAt: Date
    var status: String
}

struct LiveRoom: Identifiable, Hashable, Codable {
    let id: UUID
    let host: String
    let title: String
    let category: String
    var viewers: Int
    let icon: String
    var live: Bool = true
}

struct WalletTransaction: Identifiable, Hashable, Codable {
    let id: UUID
    let label: String
    let amount: Int
    let date: Date
}

struct WhappyServiceRequest: Identifiable, Hashable, Codable {
    let id: UUID
    let type: String
    let details: String
    let createdAt: Date
    var status: String
}

struct WhappyBusiness: Identifiable, Hashable, Codable {
    let id: UUID
    /// Firestore document id. The local UUID remains stable for older installs.
    var remoteID: String = ""
    var name: String
    var category: String
    var bio: String
    var city: String
    var phone: String = ""
    var website: String = ""
    /// Public logo for the separate Business identity.
    var logoURL: String = ""

    init(id: UUID, remoteID: String = "", name: String, category: String, bio: String, city: String, phone: String = "", website: String = "", logoURL: String = "") {
        self.id = id; self.remoteID = remoteID; self.name = name; self.category = category; self.bio = bio; self.city = city; self.phone = phone; self.website = website; self.logoURL = logoURL
    }

    private enum CodingKeys: String, CodingKey { case id, remoteID, name, category, bio, city, phone, website, logoURL }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(UUID.self, forKey: .id)
        remoteID = try values.decodeIfPresent(String.self, forKey: .remoteID) ?? ""
        name = try values.decode(String.self, forKey: .name)
        category = try values.decode(String.self, forKey: .category)
        bio = try values.decode(String.self, forKey: .bio)
        city = try values.decode(String.self, forKey: .city)
        phone = try values.decodeIfPresent(String.self, forKey: .phone) ?? ""
        website = try values.decodeIfPresent(String.self, forKey: .website) ?? ""
        logoURL = try values.decodeIfPresent(String.self, forKey: .logoURL) ?? ""
    }

    func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        try values.encode(id, forKey: .id); try values.encode(remoteID, forKey: .remoteID); try values.encode(name, forKey: .name); try values.encode(category, forKey: .category); try values.encode(bio, forKey: .bio); try values.encode(city, forKey: .city); try values.encode(phone, forKey: .phone); try values.encode(website, forKey: .website); try values.encode(logoURL, forKey: .logoURL)
    }
}

enum WhappyTab: String, Hashable {
    case home, messages, calls, actus, wia, market, live, games, services, profile
}
