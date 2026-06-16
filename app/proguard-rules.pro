# Spares — ProGuard rules
# Keep Room entities
-keep class com.spares.app.db.** { *; }
# Keep Lifecycle
-keep class * extends androidx.lifecycle.ViewModel { *; }
