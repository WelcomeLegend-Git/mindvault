import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.mindvault"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mindvault"
        minSdk = 28
        targetSdk = 36
        versionCode = 11
        versionName = "3.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // All credentials must be supplied together; absent credentials fall back to debug signing for local/direct installs.
    val signingVariables = listOf(
        "MINDVAULT_RELEASE_STORE_FILE",
        "MINDVAULT_RELEASE_STORE_PASSWORD",
        "MINDVAULT_RELEASE_KEY_ALIAS",
        "MINDVAULT_RELEASE_KEY_PASSWORD"
    )
    val signingValues = signingVariables.associateWith {
        providers.environmentVariable(it).orNull?.takeIf { value -> value.isNotBlank() }
    }
    val hasReleaseSigning = signingValues.values.any { it != null }
    if (hasReleaseSigning) {
        require(signingValues.values.all { it != null }) {
            "Release signing requires all four MINDVAULT_RELEASE_* environment variables."
        }
        signingConfigs.create("release") {
            storeFile = file(requireNotNull(signingValues["MINDVAULT_RELEASE_STORE_FILE"]))
            storePassword = signingValues["MINDVAULT_RELEASE_STORE_PASSWORD"]
            keyAlias = signingValues["MINDVAULT_RELEASE_KEY_ALIAS"]
            keyPassword = signingValues["MINDVAULT_RELEASE_KEY_PASSWORD"]
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            firebaseAppDistribution {
                appId = "1:930728313401:android:bb0046d431682399cbc3e8"
                artifactType = "APK"
                releaseNotes = "v3.4.0: Comprehensive security hardening, type-safe backup recovery, and robust focus enforcement"
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
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Upgrades fragment to fix InvalidFragmentVersionForActivityResult Lint error
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Additional dependencies for focus mode
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation(libs.coil.compose)

    // Room dependencies are retained but not used by current local persistence.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.accompanist.drawablepainter)

    // Compose lifecycle integration
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Google Sign-In and Drive API
    implementation(libs.play.services.auth)
    // implementation("com.google.api-client:google-api-client-android:2.2.0")
    // implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    // implementation("com.google.http-client:google-http-client-gson:1.43.3")

    // Additional authentication
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Resolve actual selected versions (including BOM constraints and transitives), not TOML declarations.
tasks.register("exportDependencyInventory") {
    group = "verification"
    description = "Export selected app/test classpaths for the CI OSV check"
    val inventory = layout.buildDirectory.file("reports/dependencies/maven-inventory.txt")
    outputs.file(inventory)
    outputs.upToDateWhen { false }
    notCompatibleWithConfigurationCache("Resolves project configurations at execution time")
    doLast {
        val coordinates = sortedSetOf<String>()
        listOf(
            "debugCompileClasspath", "debugRuntimeClasspath",
            "releaseCompileClasspath", "releaseRuntimeClasspath",
            "debugUnitTestCompileClasspath", "debugUnitTestRuntimeClasspath",
            "debugAndroidTestCompileClasspath", "debugAndroidTestRuntimeClasspath"
        ).forEach { name ->
            val result = configurations.getByName(name).incoming.resolutionResult
            val unresolved = result.allDependencies.filterIsInstance<
                org.gradle.api.artifacts.result.UnresolvedDependencyResult>()
            check(unresolved.isEmpty()) { "Unresolved dependencies in $name: $unresolved" }
            result.allComponents.forEach { component ->
                val id = component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
                if (id != null) coordinates.add("${id.group}:${id.module}:${id.version}")
            }
        }
        check(coordinates.isNotEmpty()) { "Dependency inventory is empty" }
        inventory.get().asFile.apply {
            parentFile.mkdirs()
            writeText(coordinates.joinToString("\n", postfix = "\n"))
        }
    }
}
