plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val personalKeystore = rootProject.file("signing/personal.jks")

android {
    namespace = "com.myhealth.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myhealth.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (personalKeystore.exists()) {
            create("personal") {
                storeFile = personalKeystore
                storePassword = (findProperty("signingPassword") as String?) ?: "myhealth"
                keyAlias = "myhealth"
                keyPassword = (findProperty("signingPassword") as String?) ?: "myhealth"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("personal") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // 화면 캡처 테스트: -Pshots=<폴더> 를 주면 PNG를 그 폴더에 저장
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("shots.dir", (findProperty("shots") as String?) ?: "${layout.buildDirectory.get()}/shots")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            // Robolectric이 실행 중에 직접 내려받지 않도록 Gradle이 받아 둔 런타임을 쓴다
            it.dependsOn("robolectricRuntime")
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", "${layout.buildDirectory.get()}/robolectric")
            it.maxHeapSize = "2g"
        }
    }
}

val androidAll: Configuration by configurations.creating

tasks.register<Copy>("robolectricRuntime") {
    from(androidAll)
    into(layout.buildDirectory.dir("robolectric"))
}

dependencies {
    androidAll("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
}
