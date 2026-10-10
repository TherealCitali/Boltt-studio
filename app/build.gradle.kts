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
        versionCode = 7
        versionName = "0.5.0"
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
