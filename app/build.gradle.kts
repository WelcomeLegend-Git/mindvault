plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.firebase.appdistribution)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.mindvault"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mindvault"
        minSdk = 28
        targetSdk = 36
        versionCode = 9
        versionName = "3.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            firebaseAppDistribution {
                appId = "1:930728313401:android:bb0046d431682399cbc3e8"
                artifactType = "APK"
                releaseNotes = "v3.3.1: Responsive Home Screen layout fix, updated Accessibility self-protection logic"
                groups = "testers" // This is the group name in Firebase console
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
    }
}

dependencies {
    // Remove Google Drive dependencies
    // implementation("com.google.android.gms:play-services-drive:17.0.0")
    // implementation("com.google.api-client:google-api-client-android:2.2.0")
    // implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    // implementation("com.google.http-client:google-http-client-gson:1.43.3")

    // Firebase
    implementation("com.google.firebase:firebase-firestore-ktx:24.10.3")
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Upgrades fragment to fix InvalidFragmentVersionForActivityResult Lint error
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Additional dependencies for focus mode
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.coil-kt:coil-compose:2.4.0")
    
    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.32.0")
    
    // Additional WorkManager and coroutines
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Google Sign-In and Drive API
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    // implementation("com.google.api-client:google-api-client-android:2.2.0")
    // implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    // implementation("com.google.http-client:google-http-client-gson:1.43.3")
    
    // Additional authentication
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}