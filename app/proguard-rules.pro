# CLIBeats release shrinking rules.
#
# R8 removes unused library code (most of the APK's size), which makes the APK, and so every in-app
# update download, much smaller. These rules are deliberately cautious: shrinking may only drop code
# that nothing can ever reach, never code that is found at run time by name or reflection.

# No renaming anywhere. Code that looks up a class, field or enum by its name (Room, JSON, protobuf,
# saved preferences, YouTube's player JavaScript bridge) keeps working, and crash reports stay readable.
# Unused code is still removed, which is where the size saving comes from.
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable,Exceptions

# CLIBeats' own classes: kept whole (DTOs, Room entities and generated _Impl classes, Hilt, enums).
-keep class com.clibeats.** { *; }

# kotlinx.serialization: generated serializers are found through companion objects.
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class **$$serializer { *; }

# YouTube stream decoding: NewPipeExtractor and everything it loads by name. It runs YouTube's player
# JavaScript with Rhino (reflection), parses JSON with nanojson and HTML with jsoup, and builds request
# parameters with protobuf-lite (which reads message fields by name). All kept whole; same as NewPipe.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn javax.lang.model.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**

# DataStore preferences store settings with a bundled protobuf-lite that reads fields by name.
-keep class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }

# Media3 finds optional parts (extension renderers, session commands) by class name.
-keep class androidx.media3.session.** { *; }
-keepnames class androidx.media3.** { *; }

# Found by comparing the full and shrunk builds: classes named as text that shrinking had removed.
# The legacy media session interfaces are what Bluetooth headsets, car stereos and older lock screens
# use to control playback; notification styles are rebuilt by class name. All small.
-keep class android.support.v4.media.** { *; }
-keep class androidx.core.app.NotificationCompat** { *; }
-keep class com.google.common.base.Strings { *; }
-keep class kotlin.jvm.internal.EnumCompanionObject { *; }
-keep class kotlin.jvm.internal.StringCompanionObject { *; }

# OkHttp / Retrofit ship their own rules; silence optional-dependency warnings.
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
