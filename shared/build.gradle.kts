// Non-UI code shared by the Android app (:app) and the desktop app (Milestone F, see DESKTOP.md).
// Plain Kotlin/JVM: no Android, no Compose.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ksp)
}

kotlin {
  jvmToolchain(17)
}

// Room schema export: one JSON per database version, checked into git as the migration baseline.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  api(libs.okhttp)
  api(libs.kotlinx.coroutines.core)
  implementation(libs.bouncycastle)
  // Room: the Android app resolves the Android variant, the desktop app the JVM variant.
  api(libs.room.runtime)
  ksp(libs.room.compiler)
  // Android has org.json built in; bundling it into the APK would clash with the platform copy.
  // The desktop app adds it as a normal dependency.
  compileOnly(libs.org.json)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.org.json)
  testImplementation(libs.sqlite.bundled)
}
