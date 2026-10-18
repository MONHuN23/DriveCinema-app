# ProGuard & R8 Rules for DriveCinema

# Bouncy Castle SRP & Cryptography
-keep class org.bouncycastle.crypto.agreement.srp.** { *; }
-keep class org.bouncycastle.crypto.digests.** { *; }
-keep class org.bouncycastle.crypto.generators.** { *; }
-keep class org.bouncycastle.crypto.params.** { *; }
-keep class org.bouncycastle.util.** { *; }

# Room SQLite
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); *; }
-keep class com.drivecinema.app.core.database.**_Impl { *; }
-keep class com.drivecinema.app.core.database.entities.** { *; }
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.ui.** { *; }

# Google Play Services & Auth
-keep class com.google.android.gms.auth.api.signin.** { *; }
-dontwarn com.google.android.gms.**

# AppAuth & OkHttp
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn okhttp3.**
-dontwarn okio.**

# Android Keystore & Security
-keep class android.security.keystore.** { *; }
-keep class androidx.security.crypto.** { *; }

# Kotlinx Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keep class * implements kotlinx.serialization.KSerializer { *; }
