plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile") version "1.2.2"
}

android {
    namespace = "com.example.juicemachine.baselineprofile"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Baseline profile module targets the app module
    targetProjectPath = ":app"
}

dependencies {
    implementation("androidx.test:runner:1.5.2")
    implementation("androidx.test:rules:1.5.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.2")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}

