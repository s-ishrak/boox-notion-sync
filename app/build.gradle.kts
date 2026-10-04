plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "me.utsob.booxrichannotation"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "me.utsob.booxrichannotation.notion"
        minSdk = 24
        targetSdk = 36
        versionCode = 19
        versionName = "1.12.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use debug signing for easier distribution
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            // GitHub releases: checks for and prompts about new releases on GitHub
            buildConfigField("boolean", "ENABLE_UPDATE_CHECK", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            // F-Droid is the sole update channel; never prompt users off-store
            buildConfigField("boolean", "ENABLE_UPDATE_CHECK", "false")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            // Common ARM architectures in Boox devices
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    
    // Pebble templating engine
    implementation("io.pebbletemplates:pebble:3.2.2")

    // Notion sync
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}