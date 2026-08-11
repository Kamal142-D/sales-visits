# kotlinx.serialization: keep generated serializers + companions so release (R8)
# doesn't strip them and crash on the first JSON read.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.sales.visits.** {
    *** Companion;
}
-keepclasseswithmembers class com.sales.visits.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sales.visits.**$$serializer { *; }
