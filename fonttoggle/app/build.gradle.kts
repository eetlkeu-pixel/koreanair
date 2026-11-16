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
        versionCode = 5
        versionName = "1.4"
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

    buildFeatures { buildConfig = true }

    // basic: 접근성 없음(Play 프로텍트에 안 막힘) / tg: 텔레그램 동기화 포함
    flavorDimensions += "v"
    productFlavors {
        create("basic") { dimension = "v"; buildConfigField("boolean", "TG", "false") }
        create("tg") { dimension = "v"; buildConfigField("boolean", "TG", "true") }
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
