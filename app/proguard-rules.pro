# Most rules come from the libraries (Hilt, Room, Compose, Glance, WorkManager, SQLCipher,
# RevenueCat, kotlinx-serialization). Checked against R8's mapping output: classes created by
# name (receivers, worker, Glance action) keep their names.

# Glance stores ActionCallback classes by name in the widget's pending intents. Pin them
# explicitly so a library rule change can never break the widget start/stop button.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Backup DTOs: field names in JSON come from @SerialName/descriptor strings, so renaming the
# classes is safe. Keep the generated serializers reachable (library rules also do this).
-keepclassmembers class com.fastingshift.domain.backup.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
