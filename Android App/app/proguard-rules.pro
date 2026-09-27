# Project-specific R8 rules.
# SQLCipher for Android (net.zetetic:sqlcipher-android) uses JNI; keep its classes.
-keep class net.zetetic.database.** { *; }
-keep class androidx.room.** { *; }
