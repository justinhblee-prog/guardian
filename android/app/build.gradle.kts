plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val guardianVersionName = providers.gradleProperty("guardianVersionName")
    .orElse(providers.environmentVariable("GUARDIAN_VERSION_NAME")).getOrElse("0.7.8-dev")
val guardianVersionCode = providers.gradleProperty("guardianVersionCode")
    .orElse(providers.environmentVariable("GUARDIAN_VERSION_CODE"))
    .map { raw ->
        raw.toIntOrNull()?.takeIf { it > 0 }
            ?: error("GUARDIAN_VERSION_CODE must be a positive integer.")
    }.getOrElse(21)
require(guardianVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9.-]+)?"))) {
    "GUARDIAN_VERSION_NAME must be a semantic version such as 0.7.8 or 0.7.8-rc1."
}

val guardianManifestBaseUrl = providers.gradleProperty("guardianManifestBaseUrl")
    .orElse(providers.environmentVariable("GUARDIAN_MANIFEST_BASE_URL")).getOrElse("")
val guardianManifestPublicKey = providers.gradleProperty("guardianManifestPublicKey")
    .orElse(providers.environmentVariable("GUARDIAN_MANIFEST_PUBLIC_KEY")).getOrElse("")
val guardianLicensePublicKey = providers.gradleProperty("guardianLicensePublicKey")
    .orElse(providers.environmentVariable("GUARDIAN_LICENSE_PUBLIC_KEY")).getOrElse("")
val guardianLicenseApiBaseUrl = providers.gradleProperty("guardianLicenseApiBaseUrl")
    .orElse(providers.environmentVariable("GUARDIAN_LICENSE_API_BASE_URL")).getOrElse("")
val guardianReleaseStoreFile = providers.gradleProperty("guardianReleaseStoreFile")
    .orElse(providers.environmentVariable("GUARDIAN_RELEASE_STORE_FILE")).orNull
val guardianReleaseStorePassword = providers.gradleProperty("guardianReleaseStorePassword")
    .orElse(providers.environmentVariable("GUARDIAN_RELEASE_STORE_PASSWORD")).orNull
val guardianReleaseKeyAlias = providers.gradleProperty("guardianReleaseKeyAlias")
    .orElse(providers.environmentVariable("GUARDIAN_RELEASE_KEY_ALIAS")).orNull
val guardianReleaseKeyPassword = providers.gradleProperty("guardianReleaseKeyPassword")
    .orElse(providers.environmentVariable("GUARDIAN_RELEASE_KEY_PASSWORD")).orNull
fun guardianBuildConfigString(value: String): String {
    val escaped = value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
    return "\"$escaped\""
}

val guardianReleaseSigningConfigured = listOf(
    guardianReleaseStoreFile, guardianReleaseStorePassword,
    guardianReleaseKeyAlias, guardianReleaseKeyPassword
).all { !it.isNullOrBlank() }

val validateGuardianReleaseSigning by tasks.registering {
    doLast {
        check(guardianReleaseSigningConfigured) {
            "Release APK/AAB requires GUARDIAN_RELEASE_STORE_FILE, GUARDIAN_RELEASE_STORE_PASSWORD, GUARDIAN_RELEASE_KEY_ALIAS and GUARDIAN_RELEASE_KEY_PASSWORD."
        }
        check(file(guardianReleaseStoreFile!!).isFile) {
            "Configured Guardian release keystore file does not exist."
        }
    }
}
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }
    .configureEach { dependsOn(validateGuardianReleaseSigning) }
android {
    buildFeatures { buildConfig = true }
    namespace = "com.guardian.adblock"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    defaultConfig {
        applicationId = "com.guardian.adblock"
        minSdk = 26
        targetSdk = 35
        versionCode = guardianVersionCode
        versionName = guardianVersionName
        buildConfigField("String", "FILTER_MANIFEST_BASE_URL", guardianBuildConfigString(guardianManifestBaseUrl))
        buildConfigField("String", "FILTER_MANIFEST_PUBLIC_KEY", guardianBuildConfigString(guardianManifestPublicKey))
        buildConfigField("String", "LICENSE_PUBLIC_KEY", guardianBuildConfigString(guardianLicensePublicKey))
        buildConfigField("String", "LICENSE_API_BASE_URL", guardianBuildConfigString(guardianLicenseApiBaseUrl))
    }
    signingConfigs {
        if (guardianReleaseSigningConfigured) {
            create("guardianRelease") {
                storeFile = file(guardianReleaseStoreFile!!)
                storePassword = guardianReleaseStorePassword!!
                keyAlias = guardianReleaseKeyAlias!!
                keyPassword = guardianReleaseKeyPassword!!
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (guardianReleaseSigningConfigured) {
                signingConfigs.getByName("guardianRelease")
            } else {
                null
            }
        }
    }
    sourceSets.getByName("main").assets.srcDir("../../shared")
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
    testImplementation("junit:junit:4.13.2")
}