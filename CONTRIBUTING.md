# Developer Instructions - Boox Rich Annotation

This file contains instructions for building and releasing this Android app.

## Building a Release

### 1. Update Version Numbers
```bash
make bump VERSION=X.Y.Z
```
This increments `versionCode` by 1 and sets `versionName` to `X.Y.Z` in
`app/build.gradle.kts`. Equivalent to editing the file by hand:
```kotlin
versionCode = X  // Increment by 1
versionName = "X.Y.Z"  // Semantic versioning
```

### 2. Build Release APKs
```bash
./gradlew assembleStandardRelease --no-daemon
```

This generates three APK variants in `app/build/outputs/apk/standard/release/`:
- `app-standard-arm64-v8a-release.apk` - 64-bit ARM (modern devices)
- `app-standard-armeabi-v7a-release.apk` - 32-bit ARM (older devices)
- `app-standard-universal-release.apk` - All architectures (larger size)

The `standard` flavor is used for GitHub releases (includes the in-app update
checker). A separate `fdroid` flavor exists for F-Droid builds, which disables
the update checker since F-Droid is the sole update channel for that build.

### 3. Organize Release Folder
```bash
# Create version folder
mkdir -p releases/X.Y.Z

# Copy APKs with proper naming
cp app/build/outputs/apk/standard/release/app-standard-arm64-v8a-release.apk \
   releases/X.Y.Z/boox-rich-annotation-X.Y.Z-arm64-v8a.apk
cp app/build/outputs/apk/standard/release/app-standard-armeabi-v7a-release.apk \
   releases/X.Y.Z/boox-rich-annotation-X.Y.Z-armeabi-v7a.apk
cp app/build/outputs/apk/standard/release/app-standard-universal-release.apk \
   releases/X.Y.Z/boox-rich-annotation-X.Y.Z-universal.apk
```

### 4. Generate Checksums
```bash
cd releases/X.Y.Z
shasum -a 256 *.apk > SHA256SUMS.txt
```

### 5. Create Release Notes
Create `releases/X.Y.Z/RELEASE_NOTES.md` with:
- **Features** - New functionality
- **Improvements** - Enhancements to existing features
- **Fixes** - Bug fixes
- **Installation** - APK variant descriptions and installation instructions

### 6. Update Documentation
- Update `README.md` with new features and changes
- Ensure screenshots are up to date if UI changed

### 7. Test Installation
```bash
adb install -r releases/X.Y.Z/boox-rich-annotation-X.Y.Z-arm64-v8a.apk
```

## Release Folder Structure
```
releases/
└── X.Y.Z/
    ├── boox-rich-annotation-X.Y.Z-arm64-v8a.apk
    ├── boox-rich-annotation-X.Y.Z-armeabi-v7a.apk
    ├── boox-rich-annotation-X.Y.Z-universal.apk
    ├── SHA256SUMS.txt
    └── RELEASE_NOTES.md
```

## Version Numbering
Follow semantic versioning (MAJOR.MINOR.PATCH):
- **MAJOR** - Breaking changes or major feature rewrites
- **MINOR** - New features, backward compatible
- **PATCH** - Bug fixes only

**Important**: `versionCode` must increment with every release for Android to recognize updates.

## APK Variant Guide
| Variant | Architecture | Use Case | Size |
|---------|-------------|----------|------|
| arm64-v8a | 64-bit ARM | Most modern Boox devices (recommended) | Smallest |
| armeabi-v7a | 32-bit ARM | Older devices (pre-2015) | Small |
| universal | All architectures | Maximum compatibility | Largest |

Most users should use **arm64-v8a** for optimal performance and size.

## Build Configuration
The app is configured for:
- **minSdk**: 24 (Android 7.0)
- **targetSdk**: 36 (Android 14+)
- **Kotlin**: 2.1.0
- **AGP**: 9.1.0

## Dependencies
Key dependencies:
- AndroidX libraries (Core, AppCompat, Material, RecyclerView)
- Pebble Templates: 3.2.2 (for text export templates)

## Signing
Release builds are signed automatically using the debug keystore. For production releases, configure proper signing in `app/build.gradle.kts`.

## E-ink Optimization
This app is specifically designed for e-ink displays:
- Pure black (#000000) and white (#FFFFFF) color scheme
- No animations or transitions
- No elevation or shadows
- 2dp bold borders on all UI elements
- Large touch targets and text sizes

When making UI changes, always test on an actual e-ink device to ensure readability and usability.

## Testing
- Test all three export formats (JSON, CSV, Text) with various books
- Verify template editor validation works correctly
- Test preferences persistence across app restarts
- Check annotation deduplication with edited annotations
- Verify timestamped filenames are generated correctly
