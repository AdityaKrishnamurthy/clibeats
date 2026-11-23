# CLIBeats release shrinking rules.
#
# R8 removes unused library code (most of the APK's size), which makes the APK, and so every in-app
# update download, much smaller. CLIBeats' own classes are kept whole: they are a small part of the
# size, and this avoids surprises from names stored as text (enum names in preferences, Room, JSON).
-keep class com.clibeats.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# kotlinx.serialization DTOs (the library ships its own rules; this covers custom serializers).
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# NewPipeExtractor (YouTube stream decoding) runs YouTube's player JavaScript with Rhino, which
# loads its own classes by reflection. Same rules the NewPipe app ships.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn javax.lang.model.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**

# OkHttp / Retrofit ship their own rules; silence optional-dependency warnings.
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
