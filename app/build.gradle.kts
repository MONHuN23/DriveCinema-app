import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.drivecinema.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.drivecinema.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.drivecinema.app"

        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { stream -> localProps.load(stream) }
        }
        val tmdbKey = (localProps.getProperty("TMDB_API_KEY")
            ?: localProps.getProperty("tmdb.api.key")
            ?: System.getenv("TMDB_API_KEY")
            ?: "")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbKey\"")

        // Google sign-in client for phone + TV (Milestone J.5): your own Android OAuth client, registered for
        // com.drivecinema.app + the SHA-1 of the key that signs your build. Falls back to the original developer's
        // client, which only works for builds signed with the developer's keys.
        val androidClientId = (localProps.getProperty("GOOGLE_ANDROID_CLIENT_ID")
            ?: System.getenv("GOOGLE_ANDROID_CLIENT_ID")
            ?: "").ifBlank { "410395579131-nc0jtmvu2uigv8vu82kdiqutv1b8nf7k.apps.googleusercontent.com" }
        buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$androidClientId\"")
    }

    signingConfigs {
        val keystorePath = System.getenv("KEYSTORE_PATH") ?: System.getenv("DRIVECINEMA_KEYSTORE_PATH")
        val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: System.getenv("DRIVECINEMA_KEYSTORE_PASSWORD")
        val keyAlias = System.getenv("KEY_ALIAS") ?: System.getenv("DRIVECINEMA_KEY_ALIAS")
        val keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("DRIVECINEMA_KEY_PASSWORD")

        if (!keystorePath.isNullOrEmpty() && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.org.json)
  testImplementation("org.robolectric:robolectric:4.14.1")
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Media3 ExoPlayer & OkHttp DataSource
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.datasource.okhttp)
  implementation(libs.media3.ui)

  // AppAuth & OkHttp
  implementation(libs.appauth)
  implementation(libs.play.services.auth)
  implementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)

  // Code shared with the desktop app (Milestone F)
  implementation(project(":shared"))

  // Room SQLite
  // Entities, DAOs and the @Database class are in :shared (Room code is generated there).
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)

  // Security & Bouncy Castle
  implementation(libs.bouncycastle)

  // Coil & TV UI
  implementation(libs.coil.compose)
  implementation(libs.androidx.tv.material)
}
