#include <jni.h>

#include <array>
#include <string>

#include "wapi_core.h"

namespace {

std::string fromJava(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* bytes = env->GetStringUTFChars(value, nullptr);
    if (bytes == nullptr) return {};
    std::string result(bytes);
    env->ReleaseStringUTFChars(value, bytes);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_whappy_chat_WapiNativeCore_nativeNormalizePhone(
    JNIEnv* env,
    jclass,
    jstring input,
    jstring defaultCountryCode
) {
    const std::string raw = fromJava(env, input);
    const std::string country = fromJava(env, defaultCountryCode);
    std::array<char, 96> normalized{};
    wapi_core_normalize_phone(raw.c_str(), country.c_str(), normalized.data(), normalized.size());
    return env->NewStringUTF(normalized.data());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_whappy_chat_WapiNativeCore_nativeIdentityRevision(
    JNIEnv* env,
    jclass,
    jstring userId,
    jstring photoUrl,
    jboolean verified
) {
    const std::string user = fromJava(env, userId);
    const std::string photo = fromJava(env, photoUrl);
    return static_cast<jlong>(wapi_core_identity_revision(user.c_str(), photo.c_str(), verified == JNI_TRUE));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_whappy_chat_WapiNativeCore_nativeVersion(JNIEnv* env, jclass) {
    return env->NewStringUTF(wapi_core_version());
}
