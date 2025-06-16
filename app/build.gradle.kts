plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.bus" // Sets the namespace for the R class generation
    compileSdk = 34 // Or your desired compile SDK, 34 is the latest stable

    defaultConfig {
        applicationId = "com.example.bus" // Unique ID for your application
        minSdk = 26 // Minimum SDK version (Android 8.0 Oreo)
        targetSdk = 34 // Target SDK version, should generally be the latest stable
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Set to true for production to shrink your app
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
        viewBinding = true // Recommended for easier view access than findViewById
        // compose = true // Uncomment if you plan to use Jetpack Compose
    }
    // composeOptions { // Uncomment if using Jetpack Compose
    //     kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    // }
}

dependencies {
    // Core AndroidX libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) // For AppCompatActivity
    implementation(libs.material)          // Material Design components

    // UI - ConstraintLayout is a common choice for complex layouts if not using Compose
    implementation(libs.androidx.constraintlayout)

    // Lifecycle components (ViewModel, LiveData) - good for managing UI-related data

    implementation(libs.androidx.activity.ktx) // For by viewModels() and other activity extensions

    // Testing libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Example for Jetpack Compose (Uncomment if you add 'compose = true' above and the composeOptions block)
    // val composeBom = platform(libs.androidx.compose.bom)
    // implementation(composeBom)
    // implementation(libs.androidx.compose.ui)
    // implementation(libs.androidx.compose.ui.graphics)
    // implementation(libs.androidx.compose.ui.tooling.preview)
    // implementation(libs.androidx.compose.material3)
    // androidTestImplementation(composeBom)
    // androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // debugImplementation(libs.androidx.compose.ui.tooling)
    // debugImplementation(libs.androidx.compose.ui.test.manifest)
}