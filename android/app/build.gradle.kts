import java.io.FileInputStream
import java.net.URI
import java.security.KeyStore
import java.security.PrivateKey
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val guardianVersionName = providers.gradleProperty("guardianVersionName")
    .orElse(providers.environmentVariable("GUARDIAN_VERSION_NAME")).getOrElse("0.7.42-dev")
val guardianVersionCode = providers.gradleProperty("guardianVersionCode")
    .orElse(providers.environmentVariable("GUARDIAN_VERSION_CODE"))
    .map { raw ->
        raw.toIntOrNull()?.takeIf { it > 0 }
            ?: error("GUARDIAN_VERSION_CODE must be a positive integer.")
    }.getOrElse(55)
require(guardianVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9.-]+)?"))) {
    "GUARDIAN_VERSION_NAME must be a semantic version such as 0.7.8 or 0.7.8-rc1."
}

val guardianManifestBaseUrl = providers.gradleProperty("guardianManifestBaseUrl")
    .orElse(providers.environmentVariable("GUARDIAN_MANIFEST_BASE_URL")).getOrElse("")
val guardianManifestPublicKey = providers.gradleProperty("guardianManifestPublicKey")
    .orElse(providers.environmentVariable("GUARDIAN_MANIFEST_PUBLIC_KEY")).getOrElse("")
val guardianLicensePublicKey = providers.gradleProperty("guardianLicensePublicKey")
    .orElse(providers.environmentVariable("GUARDIAN_LICENSE_PUBLIC_KEY")).getOrElse("MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA0ylSU/4ZONpBVJwXrK9BxxT9yFgqbzbTnInZKKMo2OuyxTvVHlxTzMSgmkjO7Mzpb+UcMbDIw/xILLG/O8Hb5mqldfs80BRPpbnvacvcjZ8swGIWkxFXuSo+/+SzgRKQUbEnVVPBvg416hdKSIq1VKGEte4CLXbG2ztfAr051jmbyUSlDDIyYsFi6pFwEKL/MHN823SOOGbwM9wXL71ur5Ss6cqmHsodugvnT1BBZR9sUzYhzN6bJp/Fa8HCVl9agHVop4JJC8P9Cf/E/2HWKxNnz0z2fKnaWp4w489pDvgW5ivfA6YxstY8pI7/ud0kWAGueYlfab3hb1jlSJaJSyZ89aQtL4k2szxaL3y7ZWpWLpGdo8SFuzsrCTih2nB5FqfuPmRiEbX8IONSQeOipfzA+AaMn4ZfWvGPN5YPTGcoesYquZYdm6l+pPWLdISZEnnPmhhsxgGM9VSnqGkeaE+lgMfCpvG0W0SrixUrUA4GAKf2IyakIeo+Vjv+rFmTAgMBAAE=")
val guardianLicenseApiBaseUrl = providers.gradleProperty("guardianLicenseApiBaseUrl")
    .orElse(providers.environmentVariable("GUARDIAN_LICENSE_API_BASE_URL")).getOrElse("https://br-shiny-mountain-bi4ia7g2-guardianapi.compute.c-5.ap-southeast-1.aws.neon.tech")
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
        val keystoreFile = file(guardianReleaseStoreFile!!)
        check(keystoreFile.isFile) {
            "Configured Guardian release keystore file does not exist."
        }

        // Validate the actual signer entry, not just the presence of four non-empty
        // environment variables. Accept the two standard Android keystore formats.
        val storePassword = guardianReleaseStorePassword!!.toCharArray()
        val keyPassword = guardianReleaseKeyPassword!!.toCharArray()
        try {
            var loadedKeyStore: KeyStore? = null
            for (type in listOf("PKCS12", "JKS")) {
                try {
                    val candidate = KeyStore.getInstance(type)
                    FileInputStream(keystoreFile).use { input ->
                        candidate.load(input, storePassword)
                    }
                    loadedKeyStore = candidate
                    break
                } catch (_: Exception) {
                    // Try the other supported keystore format without logging secrets.
                }
            }
            val keyStore = loadedKeyStore
                ?: throw GradleException(
                    "Configured Guardian release keystore is unreadable with the supplied password or is not JKS/PKCS12."
                )
            check(keyStore.containsAlias(guardianReleaseKeyAlias!!)) {
                "Configured Guardian release key alias does not exist in the keystore."
            }
            check(keyStore.isKeyEntry(guardianReleaseKeyAlias!!)) {
                "Configured Guardian release key alias is not a private-key entry."
            }
            check(keyStore.getKey(guardianReleaseKeyAlias!!, keyPassword) is PrivateKey) {
                "Configured Guardian release key alias does not contain an accessible private key."
            }
            check(keyStore.getCertificate(guardianReleaseKeyAlias!!) != null) {
                "Configured Guardian release key alias has no signing certificate."
            }
        } finally {
            storePassword.fill('\u0000')
            keyPassword.fill('\u0000')
        }
    }
}

fun validateGuardianRsaPublicKey(encoded: String, variableName: String) {
    val decoded = try {
        Base64.getDecoder().decode(encoded)
    } catch (error: IllegalArgumentException) {
        throw GradleException("$variableName must be a Base64-encoded X.509 RSA public key.", error)
    }
    val key = try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoded)) as? RSAPublicKey
    } catch (error: Exception) {
        throw GradleException("$variableName must be a valid X.509 RSA public key.", error)
    } ?: throw GradleException("$variableName must be an RSA public key.")
    check(key.modulus.bitLength() >= 2048) {
        "$variableName must use an RSA key of at least 2048 bits."
    }
}

val validateGuardianReleaseServices by tasks.registering {
    doLast {
        check(guardianManifestBaseUrl.isNotBlank()) {
            "Release APK/AAB requires GUARDIAN_MANIFEST_BASE_URL for signed filter delivery."
        }
        val manifestUri = try {
            URI(guardianManifestBaseUrl.trimEnd('/'))
        } catch (error: Exception) {
            throw GradleException("GUARDIAN_MANIFEST_BASE_URL must be a valid HTTPS URL.", error)
        }
        check(manifestUri.scheme.equals("https", ignoreCase = true) &&
            !manifestUri.host.isNullOrBlank() && manifestUri.userInfo == null &&
            manifestUri.query == null && manifestUri.fragment == null) {
            "GUARDIAN_MANIFEST_BASE_URL must be an HTTPS origin/path without credentials, query or fragment."
        }
        check(guardianManifestPublicKey.isNotBlank()) {
            "Release APK/AAB requires GUARDIAN_MANIFEST_PUBLIC_KEY."
        }
        check(guardianLicenseApiBaseUrl.isNotBlank()) {
            "Release APK/AAB requires GUARDIAN_LICENSE_API_BASE_URL."
        }
        val licenseUri = try {
            URI(guardianLicenseApiBaseUrl.trimEnd('/'))
        } catch (error: Exception) {
            throw GradleException("GUARDIAN_LICENSE_API_BASE_URL must be a valid HTTPS URL.", error)
        }
        check(licenseUri.scheme.equals("https", ignoreCase = true) &&
            !licenseUri.host.isNullOrBlank() && licenseUri.userInfo == null &&
            licenseUri.query == null && licenseUri.fragment == null) {
            "GUARDIAN_LICENSE_API_BASE_URL must be an HTTPS origin/path without credentials, query or fragment."
        }
        check(guardianLicensePublicKey.isNotBlank()) {
            "Release APK/AAB requires GUARDIAN_LICENSE_PUBLIC_KEY."
        }
        validateGuardianRsaPublicKey(guardianManifestPublicKey, "GUARDIAN_MANIFEST_PUBLIC_KEY")
        validateGuardianRsaPublicKey(guardianLicensePublicKey, "GUARDIAN_LICENSE_PUBLIC_KEY")
    }
}
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "assembleDirect", "packageDirect") }
    .configureEach {
        dependsOn(validateGuardianReleaseSigning)
        dependsOn(validateGuardianReleaseServices)
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
        versionCode = guardianVersionCode
        versionName = guardianVersionName
        buildConfigField("boolean", "GUARDIAN_STABLE_UPDATER_ENABLED", "false")
        buildConfigField("String", "FILTER_MANIFEST_BASE_URL", guardianBuildConfigString(guardianManifestBaseUrl))
        buildConfigField("String", "FILTER_MANIFEST_PUBLIC_KEY", guardianBuildConfigString(guardianManifestPublicKey))
        buildConfigField("String", "LICENSE_PUBLIC_KEY", guardianBuildConfigString(guardianLicensePublicKey))
        buildConfigField("String", "LICENSE_API_BASE_URL", guardianBuildConfigString(guardianLicenseApiBaseUrl))
    }
    signingConfigs {
        // A fixed development-only certificate is required so successive debug APKs
        // can update an existing installation. Never reuse this key for production.
        getByName("debug") {
            storeFile = rootProject.file("guardian-dev.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
            buildConfigField("boolean", "GUARDIAN_STABLE_UPDATER_ENABLED", "false")
            signingConfig = if (guardianReleaseSigningConfigured) {
                signingConfigs.getByName("guardianRelease")
            } else {
                null
            }
        }
        create("direct") {
            // Sideloaded distribution uses the same production signer and stable
            // GitHub updater, while the Play Store release variant remains store-managed.
            initWith(getByName("release"))
            isMinifyEnabled = false
            buildConfigField("boolean", "GUARDIAN_STABLE_UPDATER_ENABLED", "true")
            signingConfig = if (guardianReleaseSigningConfigured) {
                signingConfigs.getByName("guardianRelease")
            } else {
                null
            }
            matchingFallbacks += listOf("release")
        }
    }
    sourceSets.getByName("main").assets.srcDir("../../shared")
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
    testImplementation("junit:junit:4.13.2")
}
