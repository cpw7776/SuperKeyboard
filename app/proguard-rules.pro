# SuperKeyboard ProGuard rules

# Keep IME service
-keep class io.superkeyboard.ime.KeyboardService { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# kotlinx.serialization (AI Action Engine epic E2 owns these — E4 must not duplicate).
# OkHttp ships its own consumer ProGuard rules; do not add them here.
# Standard keep rules: preserve the generated $$serializer companions and the
# Serializable types' serializer() accessor so reflection-free codegen survives R8.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the runtime serialization classes referenced by generated code.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes' generated serializers and their companions.
-keep,includedescriptorclasses class io.superkeyboard.**$$serializer { *; }
-keepclassmembers class io.superkeyboard.** {
    *** Companion;
}
-keepclasseswithmembers class io.superkeyboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}
