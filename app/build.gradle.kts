import java.security.KeyStore
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------- Secrets resilience: nothing below may ever throw on missing secrets ----------
fun secret(name: String): String =
    (System.getenv(name) ?: (project.findProperty(name) as String?) ?: "").trim()

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseKeyAlias = secret("RELEASE_KEYALIAS")
val releaseKeyPassword = secret("RELEASE_KEY_PASSWORD")
val releaseStorePassword = secret("RELEASE_STORE_PASSWORD")

// Decode the keystore and verify it really opens with the supplied credentials.
// Any failure (missing / malformed / wrong password) silently falls back to debug signing.
val releaseKeystore: File? = runCatching {
    val b64 = secret("KEYSTORE_BASE64")
    if (b64.isEmpty() || releaseKeyAlias.isEmpty() || releaseKeyPassword.isEmpty() || releaseStorePassword.isEmpty()) {
        null
    } else {
        val out = layout.buildDirectory.file("signing/release.keystore").get().asFile
        out.parentFile.mkdirs()
        out.writeBytes(Base64.getMimeDecoder().decode(b64))
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        out.inputStream().use { ks.load(it, releaseStorePassword.toCharArray()) }
        if (ks.containsAlias(releaseKeyAlias)) out else null
    }
}.getOrNull()
val hasReleaseSigning = releaseKeystore != null

// Reproducible builds: both values come from gradle properties ONLY (never from CI environment
// variables), so a GitHub build and an F-Droid build embed identical BuildConfig constants.
// The client ID is public and committed in gradle.properties; Android OAuth clients have no secret.
val googleClientId = (project.findProperty("GOOGLE_CLIENT_ID") as String?)?.trim().orEmpty()
val googleClientSecret = (project.findProperty("GOOGLE_CLIENT_SECRET") as String?)?.trim().orEmpty()

android {
    namespace = "com.dev.svn.psbdx"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dev.svn.psbdx"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.2"

        buildConfigField("String", "GOOGLE_CLIENT_ID", quoted(googleClientId))
        buildConfigField("String", "GOOGLE_CLIENT_SECRET", quoted(googleClientSecret))
        buildConfigField("boolean", "DEV_BUILD", "false")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // single line on purpose: F-Droid's key-stripping removes this whole line
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
        }
        debug {
            buildConfigField("boolean", "DEV_BUILD", "true")
        }
        // Developer build: debug-signed, debuggable, internal logging / testing hooks enabled.
        create("dev") {
            initWith(getByName("debug"))
            isDebuggable = true
            versionNameSuffix = "-dev"
            buildConfigField("boolean", "DEV_BUILD", "true")
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST",
            "META-INF/*.kotlin_module"
        )
    }

    // F-Droid: no dependency metadata blob signed for Google
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Pure-Java SVN client. Native / SSH helpers are excluded to keep it Android-friendly.
    implementation("org.tmatesoft.svnkit:svnkit:1.10.11") {
        exclude(module = "jna")
        exclude(module = "jna-platform")
        exclude(module = "sshd-core")
        exclude(module = "sshd-common")
        exclude(module = "eddsa")
        exclude(module = "trilead-ssh2")
        exclude(module = "jsch.agentproxy.connector-factory")
        exclude(module = "jsch.agentproxy.svnkit-trilead-ssh2")
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

// Reproducible builds: ART baseline-profile generation is not deterministic across machines
// (assets/dexopt/baseline.prof differed between the GitHub and F-Droid builds).
tasks.configureEach {
    if (name.contains("ArtProfile")) enabled = false
}
