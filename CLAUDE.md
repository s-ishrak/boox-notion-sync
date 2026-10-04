# Claude Instructions - Boox Rich Annotation

## Project Overview
Android app for exporting annotations from Onyx Boox e-readers. Optimized for e-ink displays with strict black/white design constraints.

## Critical Design Rules

### E-ink Display Requirements (NON-NEGOTIABLE)
- **Only black (#000000) and white (#FFFFFF)** - No gray, no colors
- **All text must be black** - Gray text (#666666) has poor e-ink contrast
- **Zero animations** - E-ink refresh rates make animations unusable
- **No shadows/elevation** - Don't render properly on e-ink
- **Bold 2dp borders** - Use for all UI element separation
- **Large text sizes** - 17-22sp range for main UI, 16sp minimum for inputs
- **Large touch targets** - Minimum 48dp for buttons and interactive elements

When modifying UI, always verify these constraints are maintained.

### Button Styling Pattern
Material3 buttons require programmatic styling:
```kotlin
button.apply {
    backgroundTintList = null  // Remove Material tint
    setBackgroundResource(R.drawable.bg_dialog_button)  // Apply custom drawable
    setPadding(48, 48, 48, 48)  // Large touch target
}
```
Never use gray text for hints - use black (#000000) instead.

## Architecture

### Content Provider Integration
- Authority: `com.onyx.content.database.ContentProvider`
- Tables: `Metadata` (books), `Annotation` (highlights/notes)
- Deduplication: Groups by quote+location, keeps most recent `updatedAt`

### Export System
Three formats supported:
1. **JSON** - Structured data with book metadata and annotations array
2. **CSV** - Spreadsheet format: Page, Quote, Chapter, Style, Color, Note, Created At
3. **Text** - Customizable Pebble templates (default: Markdown)

All exports include:
- `exportedAt` field (Unix timestamp in milliseconds)
- Timestamped filename: `Title_YYYYMMDD_HHMMSS_annotations.ext`

### Pebble Template Engine
Configuration:
```kotlin
PebbleEngine.Builder()
    .strictVariables(false)
    .autoEscaping(false)       // Don't escape quotes/HTML
    .newLineTrimming(false)    // Preserve template newlines
    .build()
```

Available variables:
- `book.title`, `book.authors`, `book.format`, `book.path`, `book.totalPages`, `book.exportedAt`
- `annotations` (list): `pageNumber`, `quote`, `note`, `chapter`, `style`, `color`, `createdAt`

Whitespace control:
- `{%- tag %}` - Strip whitespace before tag
- `{% tag -%}` - Strip whitespace after tag

Date formatting:
- `{{ timestamp | date("yyyy-MM-dd HH:mm:ss") }}`

## Release Process

Full instructions in `CONTRIBUTING.md`. Quick reference:

1. **Update version** in `app/build.gradle.kts`:
   - Increment `versionCode` by 1
   - Set `versionName` to semantic version (e.g., "1.3.0")

2. **Build release**:
   ```bash
   ./gradlew assembleRelease --no-daemon
   ```

3. **Organize release** in `releases/X.Y.Z/`:
   ```bash
   mkdir -p releases/X.Y.Z
   cp app/build/outputs/apk/release/app-arm64-v8a-release.apk \
      releases/X.Y.Z/boox-rich-annotation-X.Y.Z-arm64-v8a.apk
   cp app/build/outputs/apk/release/app-armeabi-v7a-release.apk \
      releases/X.Y.Z/boox-rich-annotation-X.Y.Z-armeabi-v7a.apk
   cp app/build/outputs/apk/release/app-universal-release.apk \
      releases/X.Y.Z/boox-rich-annotation-X.Y.Z-universal.apk
   ```

4. **Generate checksums**:
   ```bash
   cd releases/X.Y.Z
   shasum -a 256 *.apk > SHA256SUMS.txt
   ```

5. **Create release notes** (`releases/X.Y.Z/RELEASE_NOTES.md`):
   - Features (new functionality)
   - Improvements (enhancements)
   - Fixes (bug fixes)
   - Installation instructions

6. **Update README.md** with new features

7. **Test on device**:
   ```bash
   adb install -r releases/X.Y.Z/boox-rich-annotation-X.Y.Z-arm64-v8a.apk
   ```

## Code Patterns

### Export Function Template
```kotlin
private fun build{Format}Content(book: BookMetadata, annotations: List<Annotation>): String {
    // 1. Sort annotations
    val sortedAnnotations = annotations.sortedWith(
        compareBy({ it.pageNumber }, { it.locationBeginInt })
    )
    
    // 2. Build content
    // ...
    
    return content
}
```

### Preferences Access
```kotlin
val prefs = context.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE)
val format = prefs.getString(PreferencesActivity.KEY_EXPORT_FORMAT, PreferencesActivity.FORMAT_JSON)
```

### Filename Generation
```kotlin
val sanitizedTitle = book.getDisplayTitle()
    .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
    .replace(Regex("\\s+"), "_")
    .take(50)
val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
return "${sanitizedTitle}_${timestamp}_annotations.$extension"
```

## Testing Requirements

Before any release:
- [ ] All three export formats work with real data
- [ ] Template editor validation catches errors
- [ ] Preferences persist across app restarts
- [ ] Annotation deduplication works correctly
- [ ] Timestamped filenames generate properly
- [ ] UI tested on actual e-ink device
- [ ] All text is black (no gray)
- [ ] No animations are present

## File Structure Reference
```
app/src/main/
├── java/.../booxrichannotation/
│   ├── MainActivity.kt              # Book list, search, refresh
│   ├── BookDetailActivity.kt        # Annotation viewer with export
│   ├── PreferencesActivity.kt       # Format selection, text settings
│   ├── TemplateEditorActivity.kt    # Pebble template editor
│   ├── BookAdapter.kt               # Export logic (733 lines)
│   ├── OnyxContentProvider.kt       # Content provider queries
│   ├── BookMetadata.kt              # Book data class
│   └── Annotation.kt                # Annotation data class
└── res/
    ├── layout/                      # All activities + item layouts
    ├── menu/                        # Main menu, book export menu
    ├── drawable/                    # Custom backgrounds, icons
    └── values/                      # Theme, strings, colors
```

## Common Issues

### Gray Text Appearing
**Problem**: Hint text or labels showing gray (#666666)
**Solution**: Set `android:textColorHint="#000000"` in XML or programmatically

### Buttons Have Black Background
**Problem**: Material3 applies default background tint
**Solution**: Set `backgroundTintList = null` before applying custom drawable

### Template Newlines Not Working
**Problem**: Extra blank lines or missing spacing
**Solution**: Use `{%-` for whitespace stripping, ensure `newLineTrimming(false)` in engine

### Text Escaping in Export
**Problem**: Quotes show as `&quot;`, brackets as `&lt;`
**Solution**: Set `autoEscaping(false)` in Pebble engine configuration

## Dependencies
```kotlin
implementation("io.pebbletemplates:pebble:3.2.2")  // Template engine
// Standard AndroidX libraries for UI
```

## When Making Changes

**Always consider**:
1. E-ink display constraints (black/white only, no animations)
2. Export format consistency (JSON/CSV/Text all need same data)
3. Template variable availability (update validation sample data)
4. Filename timestamp format (YYYYMMdd_HHmmss)
5. Testing on actual hardware before release

**Never**:
- Use gray text or colors
- Add animations or transitions
- Reduce text sizes below 16sp
- Make touch targets smaller than 48dp
- Skip testing on e-ink device
