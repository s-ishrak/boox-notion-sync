# Obsidian-Style Pebble Templates

These templates replicate the Obsidian Templater script format for annotation exports.

## obsidian-callout-style.pebble

Replicates the original Templater script with:
- Frontmatter (title, aliases, author)
- Chapter grouping
- Page numbers with percentages
- Formatted timestamps (dd MMM yyyy hh:mm:ss a)
- Obsidian callout boxes based on highlight color
- Note formatting (inline if ≤50 chars, block if longer)

### Color to Callout Type Mapping

| Color | Hex | Callout Type | Title |
|-------|-----|--------------|-------|
| Lemon | #f0ff00 | quote | Quotable/Concept/General Idea |
| Blue/Sky-blue | #00aaff | important | Striking/Intense |
| Red | #ffc1c3 | danger | In Discord |
| Green | #00b036 | question | Thought Provoking |
| Chartreuse | #008000 | sceptic | Sceptic |
| Orange | #ffaa00 | warning | Unsound |
| Violet | #ee00ff | stylish | Stylish |

### Differences from Original Script

**Missing features** (Pebble limitations):
- Line break handling toggle (keepBreak) - Not implementable in template
- Default color fallback for unmapped colors - Defaults to first type silently
- Dynamic prompt for PDF line breaks - Must be pre-decided

**Improvements**:
- Cleaner template syntax
- No runtime dependencies (pure template)
- Can be customized per-export without code changes

### Usage in App

1. Copy the template content
2. Open app → Preferences → Edit Template
3. Paste the template
4. Change file extension to "md" in preferences
5. Export with "Save" or "Share" button

### Customization

**Change default callout type:**
Change the initial set commands at the top of the annotation loop:
```
{%- set calloutType = "note" %}
{%- set calloutTitle = "Your Default Title" %}
```

**Add more colors:**
Add more elseif blocks:
```
{%- elseif annotation.color == "#ff0000" %}
{%- set calloutType = "error" %}
{%- set calloutTitle = "Error" %}
```

**Change date format:**
Modify the date filter:
```
{{ annotation.createdAt | date("yyyy-MM-dd HH:mm:ss") }}
```

**Adjust note length threshold:**
Change `50` in the if condition:
```
{% if annotation.note.length <= 100 %}
```

### Template Variables Reference

Available in context:
- `book.title` - Book title
- `book.authors` - Book authors
- `book.format` - File format (epub, pdf, etc.)
- `book.path` - Book file location/path on device
- `book.totalPages` - Total page count
- `book.exportedAt` - Export timestamp (Unix ms)
- `annotations` - List of annotations with:
  - `pageNumber` - Page number (integer)
  - `quote` - Highlighted text
  - `note` - User note (may be null)
  - `chapter` - Chapter name
  - `style` - Annotation style (highlight, underline, etc.)
  - `color` - Color hex code (#rrggbb)
  - `createdAt` - Creation timestamp (Unix ms)

### Obsidian Callout Types

Supported callout types (for reference):
- `note`, `abstract`, `info`, `todo`, `tip`, `success`, `question`, `warning`, `failure`, `danger`, `bug`, `example`, `quote`

Custom types (not standard Obsidian):
- `important`, `sceptic`, `stylish`

These will render as basic callouts unless you have custom CSS snippets in Obsidian.
