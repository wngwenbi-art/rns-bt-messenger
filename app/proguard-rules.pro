# Reticulum-kt
-keep class network.reticulum.** { *; }
-keepclassmembers class network.reticulum.** { *; }

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep RNS interface classes
-keep class com.example.rnsbtmessenger.BluetoothSppInterface { *; }
-keep class com.example.rnsbtmessenger.KissFramer { *; }
-keep class com.example.rnsbtmessenger.RnsService { *; }

# Keep model classes
-keep class com.example.rnsbtmessenger.** { *; }