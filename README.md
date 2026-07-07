# Milky Way — Kotlin Multiplatform OpenGL Renderer

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF)](https://kotlinlang.org/)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS-blue)]()
[![License](https://img.shields.io/badge/license-GPL--3.0-green)](LICENSE)

The Milky Way galaxy rendered as ~65,000 point sprites with a GPU dust ray-marching shader — ported from a 2012 Objective-C / GLKit iOS app into a **shared Kotlin Multiplatform renderer** that runs on Android (Adreno / system ANGLE) and iOS (MetalANGLE / Metal).

The GLSL shaders, the 3-pass frame orchestration, the arcball camera, the star-catalog parser, and the Gaia galactic warp are all in one `commonMain` module. GL is isolated behind a ~30-call interface with two thin implementations. Each platform contributes only what it alone can: a surface, a context, and a finger.

```
kotlin/
├── renderer/                       KMP library module
│   ├── commonMain/                 shared Kotlin — the renderer itself
│   │   ├── MilkyWayRenderer.kt     3-pass frame: bg quad → 256² dust FBO → points
│   │   ├── ArcballCamera.kt        quaternion arcball + pinch + roll, momentum decay
│   │   ├── StarCatalog.kt          parse milkyway0.binary + apply Gaia warp
│   │   ├── MilkyWayConventions.kt  single-source constants
│   │   ├── Gl.kt                   the GL façade interface (~30 calls)
│   │   └── resources/              shaders, PNGs, star catalog — one canonical copy
│   ├── androidMain/                GlAndroid (GLES20) + PNG decoder (BitmapFactory)
│   ├── iosMain/                    GlIos (C shim via cinterop) + PNG decoder (CGImage)
│   ├── nativeInterop/cinterop/     MwGl.def — the C shim wrapping OpenGLES/MetalANGLE
│   └── commonTest/                 ArcballCameraTest — no emulator, no GPU
├── androidApp/                     GLSurfaceView host + gestures + overlay toggles
└── iosApp/                         MGLLayer + CADisplayLink host (Swift)
```

## Features

- **3-pass ES2 renderer**: background quad → 256² dust ray-march FBO → 65k point composite
- **Arcball camera** with flick-momentum decay (pan, pinch zoom, two-finger roll)
- **Context-loss survival**: `init()` (CPU-only) vs `onGlContextCreated()` (GL objects, repeatable)
- **Gaia galactic warp**: the outer stellar disk bends into the S-shape Gaia measured
- **Sun marker** (⊙) at 8.2 kpc on the Local Spur
- **Habitable-zone overlay** (✦) showing the 6.5–9.8 kpc annulus
- **ANGLE on both platforms**: MetalANGLE (GL-over-Metal) on iOS, system ANGLE on Android

## Getting started

### Prerequisites

- Kotlin 2.2+, Gradle 8.13+
- Android SDK (compileSdk 36, minSdk 24, NDK 27)
- Xcode 26 (for iOS)
- MetalANGLE framework (see below)

### Build & run (Android)

```sh
cd kotlin
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n de.hanno_rein.mw.app/de.hanno_rein.mw.MainActivity
```

### Build & run (iOS)

Fetch MetalANGLE first (one-time):
```sh
../../tools/fetch_metalangle.sh   # or manually from kakashidinho/metalangle releases
```

Then build the KMP framework + the Xcode app:
```sh
cd kotlin
./gradlew :renderer:linkDebugFrameworkIosSimulatorArm64

cd iosApp
xcodegen generate
xcodebuild -project MilkyWayKMP.xcodeproj -scheme MilkyWayKMP \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

## Architecture

The renderer is shared Kotlin behind a GL façade — the same pattern the [chess series](https://proandroiddev.com/building-a-3d-game-in-compose-multiplatform-f6a983db0e45) used for game rules, just at a different altitude. In chess, Kotlin held the rules and the renderers sat below. Here, Kotlin holds the *renderer*, and only surface + input glue sits below.

The GL façade is a frozen `interface Gl` of ~30 calls covering the exact ES2 slice the renderer touches. `GlAndroid` delegates each to `GLES20`; `GlIos` delegates to a hand-written C shim (`mwgl_*`) compiled via cinterop. On iOS, the shim links against MetalANGLE by default (`useMetalANGLE=true` in the Gradle build), giving GL-over-Metal. Switch to system OpenGLES with `-PuseMetalANGLE=false`.

Camera logic is under `commonTest` (green with no emulator). Context-loss is modeled explicitly: Home → relaunch → `onGlContextCreated()` refires → galaxy reappears with camera state intact.

## Credits

Original app by [Hanno Rein](https://github.com/hannorein). GLSL shaders, the dust ray-marching algorithm, and the star catalog are from the original [OpenGLMilkyWay](https://github.com/hannorein/OpenGLMilkyWay) (GPL-3.0).

MetalANGLE by [Le Hoang Quyen](https://github.com/kakashidinho/metalangle). stb_image by [Sean Barrett](https://github.com/nothings/stb).

## License

GPL-3.0. See [LICENSE](LICENSE).
