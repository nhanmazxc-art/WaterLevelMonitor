plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.nhom6.waterlevelmonitor"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.nhom6.waterlevelmonitor"

        minSdk = 26
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }
}

dependencies {

    // ==========================================
    // ANDROID
    // ==========================================

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.appcompat)

    implementation(libs.material)

    implementation(libs.androidx.activity)

    implementation(libs.androidx.constraintlayout)
    implementation("androidx.work:work-runtime-ktx:2.10.0")


    // ==========================================
    // TEST
    // ==========================================

    testImplementation(libs.junit)

    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )


    // ==========================================
    // FIREBASE
    // ==========================================

    implementation(
        platform(
            "com.google.firebase:firebase-bom:34.17.0"
        )
    )

    // Firebase Authentication
    implementation(
        "com.google.firebase:firebase-auth"
    )

    // Firebase Realtime Database
    implementation(
        "com.google.firebase:firebase-database"
    )


    // ==========================================
    // GOOGLE SIGN-IN
    // ==========================================

    implementation(
        "com.google.android.gms:play-services-auth:21.4.0"
    )
}
