plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.me.fonttoggle"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.me.fonttoggle"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // 고정 서명 키: 새 버전을 지우지 않고 덮어 설치할 수 있게
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
