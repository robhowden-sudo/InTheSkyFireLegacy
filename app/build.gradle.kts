plugins {
    id("com.android.application")
}

android {
    namespace = "org.inthesky.firelegacy"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.inthesky.firelegacy"
        minSdk = 22
        targetSdk = 28
        versionCode = 1
        versionName = "1.0-fire-alpha1"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
