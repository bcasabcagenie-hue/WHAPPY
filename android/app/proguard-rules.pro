# WebRTC expose plusieurs classes à son moteur JNI. Elles doivent conserver leur
# nom dans les APK release, sinon l'initialisation d'un appel peut fermer le
# processus natif uniquement après minification.
-keep class org.webrtc.** { *; }
-keep class org.webrtc.audio.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.webrtc.**
