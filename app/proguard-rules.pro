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
