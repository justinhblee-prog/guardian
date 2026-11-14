plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val guardianManifestBaseUrl = providers.gradleProperty("guardianManifestBaseUrl")
    .orElse(providers.environmentVariable("GUARDIAN_MANIFEST_BASE_URL")).getOrElse("")
val guardianManifestPublicKey = providers.gradleProperty("guardianManifestPublicKey")
    .orElse(providers.environmentVariable("GUARDIAN_MANIFEST_PUBLIC_KEY")).getOrElse("")
fun buildConfigString(value: String): String =
    "\"\u0024{value.replace("\\\\", "\\\\\\\\").replace("\"", "\\\\\"")}\""

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
        versionCode = 21
        versionName = "0.7.8-dev"
        buildConfigField("String", "FILTER_MANIFEST_BASE_URL", buildConfigString(guardianManifestBaseUrl))
        buildConfigField("String", "FILTER_MANIFEST_PUBLIC_KEY", buildConfigString(guardianManifestPublicKey))
    }
    sourceSets.getByName("main").assets.srcDir("../../shared")
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
    testImplementation("junit:junit:4.13.2")
}