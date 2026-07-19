plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

ksp {
    // Commits the Room schema to app/schemas so migrations can be written (and
    // tested) once there's a release to migrate from.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "io.github.artmann.clock"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "io.github.artmann.clock"
        // 28+: Ringtone.setLooping/setVolume are API 28, and an alarm that can
        // neither loop nor honour its volume isn't an alarm.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets {
        // MigrationTestHelper loads the exported schemas as assets.
        getByName("test") { assets.srcDirs(files("$projectDir/schemas")) }
        getByName("androidTest") { assets.srcDirs(files("$projectDir/schemas")) }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}