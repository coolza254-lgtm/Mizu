plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// GitHub "owner/repo" whose Releases feed the in-app updater.
val updateRepo = "coolza254-lgtm/mizu"

android {
    namespace = "com.example.mizu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mizu"
        minSdk = 26
        targetSdk = 35
        // CI passes these for releases (tag vX.Y.Z -> versionName, run number -> versionCode).
        versionCode = (findProperty("mizuVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("mizuVersionName") as String?) ?: "1.0.0"
        // Thai is the default language; English and Japanese are switched in-app.
        resourceConfigurations += listOf("th", "en", "ja")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    // Updates only install over the same signing key, so debug and release share one.
    // Override with MIZU_KEYSTORE / MIZU_STORE_PASSWORD / MIZU_KEY_ALIAS / MIZU_KEY_PASSWORD (CI secrets).
    fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
    signingConfigs {
        create("mizu") {
            storeFile = file(env("MIZU_KEYSTORE") ?: "$rootDir/keystore/mizu.jks")
            storePassword = env("MIZU_STORE_PASSWORD") ?: "mizu-personal"
            keyAlias = env("MIZU_KEY_ALIAS") ?: "mizu"
            keyPassword = env("MIZU_KEY_PASSWORD") ?: "mizu-personal"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("mizu")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mizu")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
