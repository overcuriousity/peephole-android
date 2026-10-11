plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes only from the environment the release workflow sets
// up from the owner's secrets. Without it the release APK stays unsigned and
// the workflow refuses to publish it.
val keystoreFile = providers.environmentVariable("ANDROID_KEYSTORE_FILE")

android {
    namespace = "io.github.overcuriousity.peephole"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.overcuriousity.peephole"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.environmentVariable("PEEPHOLE_VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.environmentVariable("PEEPHOLE_VERSION_NAME").orNull ?: "0.2.0-dev"
    }

    signingConfigs {
        if (keystoreFile.isPresent) {
            create("release") {
                storeFile = file(keystoreFile.get())
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    // No Google-encrypted dependency blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.okhttp.mockwebserver)
}
