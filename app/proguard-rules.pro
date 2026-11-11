# FitifySync Proguard / R8 Optimization Rules

# Preserve reflection metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Jetpack Lifecycle & ViewModel reflection (ensures AndroidViewModelFactory finds constructors)
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
    <init>(...);
}

# AndroidX WorkManager Worker reflection
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Health Connect Client SDK records & contracts
-keep class androidx.health.connect.client.** { *; }
-keep interface androidx.health.connect.client.** { *; }
-keep class androidx.health.platform.client.** { *; }
-keep class * extends androidx.health.connect.client.records.Record { *; }

# Domain models
-keep class com.fitifysync.app.data.model.** { *; }
