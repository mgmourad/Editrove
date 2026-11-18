# SPDX-License-Identifier: AGPL-3.0-only
# OpenWire release shrinking rules. Most libraries (Room, OkHttp, Coil, kotlinx-serialization,
# Compose) ship their own consumer rules; these cover what they don't.

# Jsoup optionally references re2j; it is not used.
-dontwarn com.google.re2j.**
# OkHttp optional TLS providers.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx-serialization: keep generated serializers for our @Serializable classes.
-keepclassmembers @kotlinx.serialization.Serializable class news.openwire.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class news.openwire.**$$serializer { *; }

# Home-screen widget buttons: Glance instantiates ActionCallbacks by class name
# (reflection), so R8 must keep their no-arg constructors and onAction.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
    *;
}

# sherpa-onnx's native code reads its Kotlin config classes' fields by name (JNI).
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.objectweb.asm.**
# …and calls back into the audio callback passed to generateWithCallback by name and signature
# (invoke(float[]) returning Integer), so R8 must keep that method on any Function1.
-keep class news.openwire.voice.PcmSink { *; }
