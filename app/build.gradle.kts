import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

val privateSigningFile = rootProject.file("private-signing/signing.properties")
val privateSigning = Properties().apply {
    if (privateSigningFile.exists()) privateSigningFile.inputStream().use { load(it) }
}

android {
    namespace = "app.caiji.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.caiji.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 27
        versionName = "0.5.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions { animationsDisabled = true }

    if (privateSigningFile.exists()) {
        signingConfigs.create("localRelease") {
            storeFile = rootProject.file(privateSigning.getProperty("storeFile"))
            storePassword = privateSigning.getProperty("storePassword")
            keyAlias = privateSigning.getProperty("keyAlias")
            keyPassword = privateSigning.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            if (privateSigningFile.exists()) signingConfig = signingConfigs.getByName("localRelease")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
}

kapt {
    correctErrorTypes = true
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}
