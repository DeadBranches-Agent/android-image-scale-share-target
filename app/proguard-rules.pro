# kotlinx.serialization — keep serializers for our models
-keepclassmembers class ca.urbanlight.imagescale.** {
    *** Companion;
}
-keepclasseswithmembers class ca.urbanlight.imagescale.** {
    kotlinx.serialization.KSerializer serializer(...);
}
