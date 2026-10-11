import java.time.Instant

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.citali.bolttstudio"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.citali.bolttstudio"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 14
        versionName = "0.7.4"
        val sha = providers.environmentVariable("GITHUB_SHA").orNull
            ?.takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) }?.take(12) ?: "local"
        buildConfigField("String", "BUILD_COMMIT", "\"$sha\"")
        buildConfigField("String", "BUILD_DATE", "\"${Instant.now()}\"")
    }
    buildTypes {
        debug { versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets.getByName("test").resources.srcDir("src/main/assets/google-fonts")
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}
dependencies {
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.12.0-beta02")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.0-beta02")
    // Same elastic edge effect as ShadowRPC; no Miuix theme or widgets.
    implementation(libs.miuix.ui)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.material3)
    implementation(libs.coroutines.android)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
