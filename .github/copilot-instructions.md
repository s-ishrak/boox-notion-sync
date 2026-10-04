# GitHub Copilot Instructions - Boox Rich Annotation

## Project Context
This is an Android app for extracting and exporting annotations from Onyx Boox e-readers. The app is specifically optimized for e-ink displays with a pure black/white theme and no animations.

## Key Design Principles

### E-ink Optimization (CRITICAL)
- **Colors**: Only pure black (#000000) and white (#FFFFFF)
- **No gray text** - All text must be black for e-ink contrast
- **No animations** - Disable all animations and transitions
- **No shadows/elevation** - E-ink doesn't render these well
- **Bold borders** - Use 2dp black borders on all UI elements
- **Large text** - Minimum 16sp, prefer 18-22sp for main UI

### Architecture
- **Content Provider Access**: Queries `com.onyx.content.database.ContentProvider`
- **Export Formats**: JSON, CSV, Text (with Pebble templates)
- **Deduplication**: Groups annotations by quote+location, keeps most recent

## Release Process

When asked to create a release, follow `CONTRIBUTING.md`:

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`
2. Build: `./gradlew assembleRelease --no-daemon`
3. Organize APKs into `releases/X.Y.Z/` with proper naming
4. Generate SHA256 checksums: `shasum -a 256 *.apk > SHA256SUMS.txt`
5. Create `RELEASE_NOTES.md` with features, improvements, fixes
6. Update `README.md` with new features
7. Test installation on device

**APK variants required**:
- arm64-v8a (most modern devices)
- armeabi-v7a (older devices)
- universal (all architectures)

## Code Style

### UI Components
- All buttons must use `bg_dialog_button` drawable (white background, black border)
- Material3 buttons need `backgroundTintList = null` set programmatically
- Text sizes: Headers 22sp, body 17-19sp, inputs 16-17sp
- Use `tools:ignore` for hardcoded text in e-ink optimized layouts

### Export Functions
- Always sort annotations by page number, then location
- Use Pebble engine with `autoEscaping(false)` and `newLineTrimming(false)`
- Include `exportedAt` timestamp (Unix milliseconds) in all exports
- Filename format: `Title_YYYYMMDD_HHMMSS_annotations.ext`

### Template System
- Available variables: `book.{title, authors, format, totalPages, exportedAt}`
- Annotation fields: `pageNumber, quote, note, chapter, style, color, createdAt`
- Use `{%-` tags for whitespace stripping in templates
- Date filter: `{{ createdAt | date("yyyy-MM-dd HH:mm:ss") }}`

## File Structure
```
app/src/main/java/.../booxrichannotation/
├── MainActivity.kt              # Book list
├── BookDetailActivity.kt        # Annotation viewer
├── PreferencesActivity.kt       # Settings
├── TemplateEditorActivity.kt    # Template editor
├── BookAdapter.kt               # Export logic (JSON/CSV/Text)
└── OnyxContentProvider.kt       # Content provider helper
```

## Testing Checklist
- Test all three export formats with real book data
- Verify template validation works correctly
- Check preferences persist across restarts
- Test deduplication with edited annotations
- Verify on actual e-ink device for readability

## Common Tasks

### Adding a New Export Format
1. Add format constant in `PreferencesActivity`
2. Implement `build{Format}Content()` in `BookAdapter`
3. Add save and share functions
4. Update preferences UI and menu items
5. Add to export menu (`menu_book_export.xml`)

### Modifying UI
- Always test on e-ink device
- Verify all text is black (#000000)
- Check touch target sizes (minimum 48dp)
- Ensure no animations are introduced
- Use 2dp borders for visual separation

### Template Changes
- Update both `PreferencesActivity.DEFAULT_TEMPLATE` and validation sample data
- Test with real book data before releasing
- Document available variables in release notes
