# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.experience.virtualos.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.experience.virtualos.**$$serializer { *; }
# WorkManager instantiates workers reflectively by class name.
-keep class com.experience.virtualos.work.** extends androidx.work.ListenableWorker { <init>(...); }
