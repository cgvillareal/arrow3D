# Release builds are minified with R8.
#
# kotlinx.serialization ships its own rules for the runtime; these keep the generated serializers of
# the ranking protocol (core/online), which are looked up through their companion objects.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.arrow3d.online.** {
    *** Companion;
    *** serializer(...);
}
-keepclasseswithmembers class com.arrow3d.online.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.arrow3d.online.**$$serializer { *; }
