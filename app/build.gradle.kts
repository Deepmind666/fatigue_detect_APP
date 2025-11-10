plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.googleDevtoolsKsp)
    alias(libs.plugins.kotlinSerialization)
    id("androidx.room") version "2.6.1"
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.example.juicemachine"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.juicemachine"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.coreKtx)
    implementation(libs.lifecycleRuntimeKtx)
    implementation(libs.activityCompose)
    implementation(libs.appcompat)
    implementation(platform(libs.composeBom))
    implementation(libs.bundles.composeBundle)
    implementation(libs.composeMaterialIconsExtended)
    implementation(libs.coreSplashscreen)

    // Room and Database
    implementation(libs.bundles.roomBundle)
    ksp(libs.roomCompiler)

    // Serialization
    implementation(libs.kotlinxSerializationJson)

    // USB Serial
    implementation(libs.usbSerialForAndroid)

    // Navigation
    implementation(libs.navigationCompose)

    // ViewModel and Coil
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Android Keystore + Crypto for encrypting sensitive preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Install baseline profiles from libraries at app install to speed cold start
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.extJunit)
    androidTestImplementation(libs.espressoCore)
    androidTestImplementation(platform(libs.composeBom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.licheedev:android-serialport:2.1.4")
}