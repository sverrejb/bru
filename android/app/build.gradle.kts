import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val baseVersionCode = 1

android {
    namespace = "works.bru"
    compileSdk = 36

    defaultConfig {
        applicationId = "works.bru"
        minSdk = 26
        targetSdk = 36
        versionCode = baseVersionCode
        versionName = "1.0.0"
    }

    flavorDimensions += "abi"

    productFlavors {
        create("armv7") {
            dimension = "abi"
            ndk {
                abiFilters += "armeabi-v7a"
            }
            versionCode = 100 * baseVersionCode + 1
        }
        create("arm64") {
            dimension = "abi"
            ndk {
                abiFilters += "arm64-v8a"
            }
            versionCode = 100 * baseVersionCode + 2
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += listOf("darwin-*/**", "linux-*/**", "win32-*/**")
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val room = "2.8.4"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("computer.iroh:iroh-android:1.1.0")

    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
