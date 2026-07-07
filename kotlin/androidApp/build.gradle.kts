plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.hanno_rein.milkyway"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.hanno_rein.milkyway"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Render the galaxy at the device's native pixel density, like the iOS app
        // (which premultiplies pointSize by retina scale). Handed to nativeInit.
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                // stb_image + the core need C++17; exceptions/rtti off (we use neither).
                cppFlags("-std=c++17", "-fexceptions", "-frtti")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "27.1.12297006"

    // Share the canonical assets with the iOS build — no duplication. The native
    // AssetProvider reads these via AAssetManager at runtime.
    sourceSets {
        getByName("main") {
            assets.srcDirs("../../core/assets")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
