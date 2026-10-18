import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

// DriveCinema desktop app (Milestone F, see DESKTOP.md): Linux first, then Windows.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(17)
}

// Desktop OAuth client + TMDB key from local.properties (or environment variables), bundled as the
// desktop-config.properties resource. Never committed: local.properties is git-ignored.
val localProperties = Properties().apply {
  rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localValue(key: String): String = localProperties.getProperty(key) ?: System.getenv(key) ?: ""

val generateDesktopConfig by tasks.registering {
  val keys = listOf("GOOGLE_DESKTOP_CLIENT_ID", "GOOGLE_DESKTOP_CLIENT_SECRET", "TMDB_API_KEY")
  val values = keys.associateWith { localValue(it) }
  val outputDir = layout.buildDirectory.dir("generated/desktopConfig")
  inputs.property("values", values)
  outputs.dir(outputDir)
  doLast {
    val file = outputDir.get().file("desktop-config.properties").asFile
    file.parentFile.mkdirs()
    file.writeText(values.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
  }
}
sourceSets.main { resources.srcDir(generateDesktopConfig) }

dependencies {
  implementation(project(":shared"))
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  // Android has org.json built in; on the desktop it is a normal dependency (used by :shared).
  implementation(libs.org.json)
  // libsecret (Linux keyring) now, libVLC (vlcj) in F.7
  implementation(libs.jna)
  // Video player: libVLC through vlcj (VLC 3.x installed on Linux, bundled on Windows in F.11)
  implementation(libs.vlcj)
  // SQLite for the shared Room database on the desktop
  implementation(libs.sqlite.bundled)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)
}

compose.desktop {
  application {
    mainClass = "com.drivecinema.desktop.MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Deb, TargetFormat.Msi)
      packageName = "DriveCinema"
      packageVersion = "1.0.0"
      description = "Stream your movie and TV library from Google Drive"
      vendor = "DriveCinema"
      // jlink keeps only these JDK modules: Swing/AWT (Compose), the loopback sign-in/stream servers,
      // TLS with EC ciphers (Google APIs), Unsafe (JNA, coroutines), logging/naming/sql used by libraries.
      modules(
        "java.desktop", "java.logging", "java.naming", "java.net.http", "java.sql",
        "jdk.crypto.ec", "jdk.httpserver", "jdk.unsupported",
      )

      linux {
        // Installs to /opt/drivecinema with a menu entry. VLC is not bundled on Linux: `sudo apt install vlc`.
        packageName = "drivecinema"
        menuGroup = "AudioVideo;Video"
        appCategory = "video"
        shortcut = true
        iconFile.set(project.file("icons/drivecinema.png"))
      }
    }
  }
}
