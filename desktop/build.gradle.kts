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
  // libsecret (Linux keyring), Windows Credential Manager (Advapi32), libVLC (vlcj)
  implementation(libs.jna)
  // Video player: libVLC through vlcj (VLC 3.x installed by the user: apt/dnf on Linux, winget or videolan.org on Windows)
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
      targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi)
      packageName = "DriveCinema"
      packageVersion = "1.1.0"
      description = "Stream your movie and TV library from Google Drive"
      vendor = "DriveCinema"
      // jlink keeps only these JDK modules: Swing/AWT (Compose), the loopback sign-in/stream servers,
      // TLS with EC ciphers (Google APIs), Unsafe (JNA, coroutines), logging/naming/sql used by libraries.
      modules(
        "java.desktop", "java.logging", "java.naming", "java.net.http", "java.sql",
        "jdk.crypto.ec", "jdk.httpserver", "jdk.unsupported",
      )

      linux {
        // .deb and .rpm install to /opt/drivecinema with a menu entry. VLC is not bundled on Linux (apt: vlc; Fedora: vlc from RPM Fusion).
        // The .rpm needs `rpmbuild` on the build machine (Ubuntu: `sudo apt install rpm`).
        packageName = "drivecinema"
        menuGroup = "AudioVideo;Video"
        appCategory = "video"
        shortcut = true
        iconFile.set(project.file("icons/drivecinema.png"))
      }

      windows {
        // .msi, built on Windows only (`.\gradlew.bat :desktop:packageMsi`). Per-user install into %LOCALAPPDATA%,
        // so no admin rights; app data stays in %APPDATA%\DriveCinema and survives an uninstall. VLC is not bundled:
        // the player offers "Install VLC" (winget) or the download page (F.11 decision, user 2026-10-19).
        // Never change upgradeUuid: it is what lets a newer .msi replace the installed one instead of adding a second.
        upgradeUuid = "a016f5b8-2f1d-43b9-ad15-152e57c30f22"
        perUserInstall = true
        dirChooser = true
        menu = true
        menuGroup = "DriveCinema"
        shortcut = true
        iconFile.set(project.file("icons/drivecinema.ico"))
      }
    }
  }
}
