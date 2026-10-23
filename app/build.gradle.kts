plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.arrow3d.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arrow3d.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing comes from Gradle properties or environment variables, never from the repo:
    // arrow3d.keystore / ARROW3D_KEYSTORE (path), arrow3d.storePassword / ARROW3D_STORE_PASSWORD,
    // arrow3d.keyAlias / ARROW3D_KEY_ALIAS, arrow3d.keyPassword / ARROW3D_KEY_PASSWORD.
    // Without them the release build is unsigned (fine for CI checks, not for the store).
    fun secret(property: String, env: String): String? = providers.gradleProperty(property).orNull ?: System.getenv(env)
    val keystorePath = secret("arrow3d.keystore", "ARROW3D_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = secret("arrow3d.storePassword", "ARROW3D_STORE_PASSWORD")
                keyAlias = secret("arrow3d.keyAlias", "ARROW3D_KEY_ALIAS")
                keyPassword = secret("arrow3d.keyPassword", "ARROW3D_KEY_PASSWORD")
            }
        }
    }

    // Online ranking server. Set it with -Parrow3d.rankingUrl=https://... or in gradle.properties.
    // Debug builds default to the emulator's view of a server on the host (http://10.0.2.2:8080);
    // release builds have the ranking turned off until a URL is given.
    val rankingUrl = providers.gradleProperty("arrow3d.rankingUrl")

    buildTypes {
        debug {
            buildConfigField("String", "RANKING_URL", "\"${rankingUrl.getOrElse("http://10.0.2.2:8080")}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "RANKING_URL", "\"${rankingUrl.getOrElse("")}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:online"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
