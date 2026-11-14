plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
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
    }
    sourceSets.getByName("main").assets.srcDir("../../shared")
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
    testImplementation("junit:junit:4.13.2")
}