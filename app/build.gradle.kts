plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.reserve"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.reserve"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    /**
     * The key is IN THE REPO on purpose, and that is a deliberate trade.
     *
     * Android will not replace an installed app with one signed by a different key. Debug builds
     * are normally signed with `~/.android/debug.keystore`, which every machine generates for
     * itself - so the CI runner's APK, a maintainer's APK and yours from source were three
     * different identities. Shipping one of those over another fails at install time, which is
     * how a perfectly valid APK came to report "There was a problem parsing the package".
     *
     * A committed key therefore proves nothing about WHO built an APK - anyone can sign one.
     * That is acceptable here precisely because the app has no network permission, no account
     * and no data worth taking, and the alternative is an app that can never be updated in
     * place. It is not Play-signed and should not be treated as if it were.
     */
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("reserve.keystore")
            storePassword = "reserve"
            keyAlias = "reserve"
            keyPassword = "reserve"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
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
        viewBinding = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged manifest and resources to stand the app up.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":logic"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
