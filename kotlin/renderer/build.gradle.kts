plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
}

android {
    namespace = "de.hanno_rein.mw.renderer"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    val isMacosHost = System.getProperty("os.name").contains("Mac", ignoreCase = true)

    // iOS GL backend selection (Phase 5):
    //   - Set `useMetalANGLE=true` (default) to use MetalANGLE (GL-over-Metal).
    //   - Set `useMetalANGLE=false` to use Apple's deprecated OpenGLES.
    val useMetalANGLE = (project.findProperty("useMetalANGLE") as? String ?: "true") == "true"
    val maPath = rootProject.projectDir.parentFile.resolve("third_party/metalangle").absolutePath
    val cinteropDir = file("src/nativeInterop/cinterop").absolutePath

    if (isMacosHost) {
        val iosSdk = providers.exec {
            commandLine("xcrun", "--sdk", "iphoneos", "--show-sdk-path")
        }.standardOutput.asText.get().trim()

        val iosArm64Target = iosArm64()
        val iosX64Target = iosX64()
        val iosSimulatorArm64Target = iosSimulatorArm64()
        val iosTargets = listOf(iosArm64Target, iosX64Target, iosSimulatorArm64Target)

        iosTargets.forEach { target ->
            target.compilations.getByName("main").cinterops.create("MwGl") {
                if (useMetalANGLE) {
                    // MetalANGLE: GL-over-Metal. Include its GLES2 headers + framework.
                    this.defFile = file("src/nativeInterop/cinterop/MwGlMetalANGLE.def")
                    packageName("mwgl")
                    val slice = if (target.name.startsWith("iosArm64")) "ios-device" else "ios-sim"
                    compilerOpts(
                        "-isysroot", iosSdk,
                        "-F$maPath/$slice",
                        "-I$maPath/$slice/MetalANGLE.framework/Headers",
                        "-I$cinteropDir"
                    )
                } else {
                    // System OpenGLES (deprecated; for early-phase parity).
                    this.defFile = file("src/nativeInterop/cinterop/MwGl.def")
                    packageName("mwgl")
                    compilerOpts(
                        "-isysroot", iosSdk,
                        "-I$iosSdk/System/Library/Frameworks/OpenGLES.framework/Headers",
                        "-I$cinteropDir"
                    )
                }
            }
        }

        // Export an iOS framework so the Xcode app can link against it.
        // bundleId override: the default derives from the package (de.hanno_rein.mw),
        // and underscores fail Xcode's CFBundleIdentifier validation.
        iosX64Target.binaries.framework {
            baseName = "MilkyWayRenderer"
            binaryOption("bundleId", "de.hanno-rein.mw.MilkyWayRenderer")
            linkerOpts("-F$maPath/ios-sim", "-framework", "MetalANGLE")
        }
        iosArm64Target.binaries.framework {
            baseName = "MilkyWayRenderer"
            binaryOption("bundleId", "de.hanno-rein.mw.MilkyWayRenderer")
            linkerOpts("-F$maPath/ios-device", "-framework", "MetalANGLE")
        }
        iosSimulatorArm64Target.binaries.framework {
            baseName = "MilkyWayRenderer"
            binaryOption("bundleId", "de.hanno-rein.mw.MilkyWayRenderer")
            linkerOpts("-F$maPath/ios-sim", "-framework", "MetalANGLE")
        }
    } else {
        logger.lifecycle("Skipping iOS Kotlin targets on non-macOS host.")
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test")
        }

        if (isMacosHost) {
            val iosArm64Main by getting
            val iosX64Main by getting
            val iosSimulatorArm64Main by getting
            val iosMain by creating {
                dependsOn(commonMain.get())
                iosArm64Main.dependsOn(this)
                iosX64Main.dependsOn(this)
                iosSimulatorArm64Main.dependsOn(this)
            }
        }
    }
}
