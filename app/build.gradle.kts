plugins {
    // AGP 9 has built-in Kotlin: no separate kotlin-android plugin is needed (or allowed).
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mareco.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mareco.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Personal-use convenience: sign the release APK with the debug key so it installs
            // right away. For publishing, create your own keystore instead.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // Mareco itself has ZERO runtime dependencies (plain Android framework APIs only).
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
