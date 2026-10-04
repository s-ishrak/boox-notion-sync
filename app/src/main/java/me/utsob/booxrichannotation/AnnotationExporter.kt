package me.utsob.booxrichannotation

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.StringWriter

/**
 * Shared annotation export/share logic (JSON/CSV/Text), used by both the book list
 * (export all annotations for a book) and the book detail screen (export a selection).
 */
object AnnotationExporter {
    private const val TAG = "AnnotationExporter"

    fun showExportMenu(view: View, context: Context, scope: CoroutineScope, book: BookMetadata, annotations: List<Annotation>) {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.menu_book_export, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export_json -> {
                    saveAnnotationsAsJson(context, scope, book, annotations)
                    true
                }
                R.id.action_export_csv -> {
                    saveAnnotationsAsCsv(context, scope, book, annotations)
                    true
                }
                R.id.action_export_text -> {
                    saveAnnotationsAsText(context, scope, book, annotations)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /** Same format-choice popup as [showExportMenu], for a selection spanning multiple books. */
    fun showMultiBookExportMenu(view: View, context: Context, scope: CoroutineScope, selections: List<Pair<BookMetadata, List<Annotation>>>) {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.menu_book_export, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export_json -> {
                    saveMultiBookAnnotationsAsJson(context, scope, selections)
                    true
                }
                R.id.action_export_csv -> {
                    saveMultiBookAnnotationsAsCsv(context, scope, selections)
                    true
                }
                R.id.action_export_text -> {
                    saveMultiBookAnnotationsAsText(context, scope, selections)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun defaultFormat(context: Context): String {
        val prefs = context.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PreferencesActivity.KEY_EXPORT_FORMAT, PreferencesActivity.FORMAT_JSON)
            ?: PreferencesActivity.FORMAT_JSON
    }

    /** Saves using whichever format is configured as default in preferences. */
    fun saveInDefaultFormat(context: Context, scope: CoroutineScope, book: BookMetadata, annotations: List<Annotation>) {
        when (defaultFormat(context)) {
            PreferencesActivity.FORMAT_CSV -> saveAnnotationsAsCsv(context, scope, book, annotations)
            PreferencesActivity.FORMAT_TEXT -> saveAnnotationsAsText(context, scope, book, annotations)
            else -> saveAnnotationsAsJson(context, scope, book, annotations)
        }
    }

    /** Shares using whichever format is configured as default in preferences. */
    fun shareInDefaultFormat(context: Context, scope: CoroutineScope, book: BookMetadata, annotations: List<Annotation>) {
        when (defaultFormat(context)) {
            PreferencesActivity.FORMAT_CSV -> shareAnnotationsAsCsv(context, scope, book, annotations)
            PreferencesActivity.FORMAT_TEXT -> shareAnnotationsAsText(context, scope, book, annotations)
            else -> shareAnnotationsAsJson(context, scope, book, annotations)
        }
    }

    /** Saves a selection spanning multiple books, using whichever format is configured as default in preferences. */
    fun saveMultiBookInDefaultFormat(context: Context, scope: CoroutineScope, selections: List<Pair<BookMetadata, List<Annotation>>>) {
        when (defaultFormat(context)) {
            PreferencesActivity.FORMAT_CSV -> saveMultiBookAnnotationsAsCsv(context, scope, selections)
            PreferencesActivity.FORMAT_TEXT -> saveMultiBookAnnotationsAsText(context, scope, selections)
            else -> saveMultiBookAnnotationsAsJson(context, scope, selections)
        }
    }

    /** Shares a selection spanning multiple books, using whichever format is configured as default in preferences. */
    fun shareMultiBookInDefaultFormat(context: Context, scope: CoroutineScope, selections: List<Pair<BookMetadata, List<Annotation>>>) {
        when (defaultFormat(context)) {
            PreferencesActivity.FORMAT_CSV -> shareMultiBookAnnotationsAsCsv(context, scope, selections)
            PreferencesActivity.FORMAT_TEXT -> shareMultiBookAnnotationsAsText(context, scope, selections)
            else -> shareMultiBookAnnotationsAsJson(context, scope, selections)
        }
    }

    fun saveMultiBookAnnotationsAsJson(context: Context, scope: CoroutineScope, selections: List<Pair<BookMetadata, List<Annotation>>>) {
        scope.launch {
            try {
                val jsonContent = buildMultiBookJsonContent(selections)
                val fileName = generateMultiBookFileName()

                val fileUri = withContext(Dispatchers.IO) { saveJsonFileAndGetUri(context, fileName, jsonContent) }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) { showOpenFileDialog(context, fileName, fileUri, "application/json") }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving multi-book annotations", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun shareMultiBookAnnotationsAsJson(context: Context, scope: CoroutineScope, selections: List<Pair<BookMetadata, List<Annotation>>>) {
        scope.launch {
            try {
                val jsonContent = buildMultiBookJsonContent(selections)
                val fileName = generateMultiBookFileName()

                val fileUri = withContext(Dispatchers.IO) { saveToTempAndGetUri(context, fileName, jsonContent) }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Annotations - ${selections.size} books")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share annotations"))
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing multi-book annotations", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun saveMultiBookAnnotationsAsCsv(context: Context, scope: CoroutineScope, selections: List<Pair<BookMetadata, List<Annotation>>>) {
        scope.launch {
            try {
                val csvContent = buildMultiBookCsvContent(selections)
                val fileName = generateMultiBookFileName("csv")

                val fileUri = withContext(Dispatchers.IO) { saveCsvFileAndGetUri(context, fileName, csvContent) }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) { showOpenFileDialog(context, fileName, fileUri, "text/csv") }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to save CSV file", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving multi-book CSV", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun shareMultiBookAnnotationsAsCsv(context: Context, scope: CoroutineScope, selections: List<Pair<BookMetadata, List<Annotation>>>) {
        scope.launch {
            try {
                val csvContent = buildMultiBookCsvContent(selections)
                val fileName = generateMultiBookFileName("csv")

                val fileUri = withContext(Dispatchers.IO) { saveToTempAndGetUri(context, fileName, csvContent) }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Annotations - ${selections.size} books")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share annotations"))
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing multi-book CSV", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun saveMultiBookAnnotationsAsText(context: Context, scope: CoroutineScope, selections: List<Pair<BookMetadata, List<Annotation>>>) {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE)
                val extension = prefs.getString(PreferencesActivity.KEY_TEXT_EXTENSION, PreferencesActivity.DEFAULT_TEXT_EXTENSION)
                    ?: PreferencesActivity.DEFAULT_TEXT_EXTENSION

                val textContent = buildMultiBookTextContent(context, selections)
                val fileName = generateMultiBookFileName(extension)

                val fileUri = withContext(Dispatchers.IO) { saveTextFileAndGetUri(context, fileName, textContent, extension) }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) { showOpenFileDialog(context, fileName, fileUri, "text/plain") }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to save text file", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving multi-book text", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun shareMultiBookAnnotationsAsText(context: Context, scope: CoroutineScope, selections: List<Pair<BookMetadata, List<Annotation>>>) {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE)
                val extension = prefs.getString(PreferencesActivity.KEY_TEXT_EXTENSION, PreferencesActivity.DEFAULT_TEXT_EXTENSION)
                    ?: PreferencesActivity.DEFAULT_TEXT_EXTENSION

                val textContent = buildMultiBookTextContent(context, selections)
                val fileName = generateMultiBookFileName(extension)

                val fileUri = withContext(Dispatchers.IO) { saveToTempAndGetUri(context, fileName, textContent) }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Annotations - ${selections.size} books")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share annotations"))
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing multi-book text", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun saveAnnotationsAsJson(context: Context, scope: CoroutineScope, book: BookMetadata, annotations: List<Annotation>) {
        scope.launch {
            try {
                val jsonContent = buildJsonContent(book, annotations)
                val fileName = generateFileName(book)

                val fileUri = withContext(Dispatchers.IO) {
                    saveJsonFileAndGetUri(context, fileName, jsonContent)
                }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) {
                        showOpenFileDialog(context, fileName, fileUri, "application/json")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving annotations", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showOpenFileDialog(context: Context, fileName: String, fileUri: Uri, mimeType: String = "application/json") {
        val prefs = context.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val customPathUri = prefs.getString(PreferencesActivity.KEY_SAVE_PATH_URI, null)

        val locationText = if (customPathUri != null) {
            try {
                val treeUri = Uri.parse(customPathUri)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                docDir?.name ?: "custom folder"
            } catch (e: Exception) {
                "Downloads folder"
            }
        } else {
            "Downloads folder"
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("File Saved")
            .setMessage("$fileName saved to $locationText.\n\nWould you like to open it?")
            .setPositiveButton("Open") { dialog, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open with"))
                } catch (e: Exception) {
                    Toast.makeText(context, "No app to open JSON files", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog)
        dialog.show()

        // Style the buttons after showing
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_dialog_button)
            setTextColor(android.graphics.Color.BLACK)
            setPadding(32, 16, 32, 16)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_dialog_button)
            setTextColor(android.graphics.Color.BLACK)
            setPadding(32, 16, 32, 16)
        }
    }

    fun shareAnnotationsAsJson(context: Context, scope: CoroutineScope, book: BookMetadata, annotations: List<Annotation>) {
        scope.launch {
            try {
                val jsonContent = buildJsonContent(book, annotations)
                val fileName = generateFileName(book)

                val fileUri = withContext(Dispatchers.IO) {
                    saveToTempAndGetUri(context, fileName, jsonContent)
                }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            putExtra(Intent.EXTRA_SUBJECT, "${book.getDisplayTitle()} - Annotations")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share annotations"))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing annotations", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareAnnotationsAsCsv(context: Context, scope: CoroutineScope, book: BookMetadata, annotations: List<Annotation>) {
        scope.launch {
            try {
                val csvContent = buildCsvContent(book, annotations)
                val fileName = generateFileName(book, "csv")

                val fileUri = withContext(Dispatchers.IO) {
                    saveToTempAndGetUri(context, fileName, csvContent)
                }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            putExtra(Intent.EXTRA_SUBJECT, "${book.getDisplayTitle()} - Annotations")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share annotations"))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing CSV", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buildJsonContent(book: BookMetadata, annotations: List<Annotation>): String {
        val rootObject = buildBookJsonObject(book, annotations)
        // Add export timestamp (same format as annotation timestamps - milliseconds)
        rootObject.put("exportedAt", System.currentTimeMillis())
        return normalizeJson(rootObject.toString(2))
    }

    /** org.json escapes every "/" as "\/"; strip that back out since it's noise for paths/URLs and not required by the JSON spec. */
    private fun normalizeJson(json: String): String = json.replace("\\/", "/")

    /** One book's title/authors/metadata + its annotations array, without an exportedAt field. */
    private fun buildBookJsonObject(book: BookMetadata, annotations: List<Annotation>): JSONObject {
        // Sort annotations by page number first, then by location in document
        val sortedAnnotations = annotations.sortedWith(
            compareBy({ it.pageNumber }, { it.locationBeginInt })
        )

        val jsonArray = JSONArray()

        for (annotation in sortedAnnotations) {
            val jsonObj = JSONObject()

            // Always include all fields, use empty string if null
            jsonObj.put("quote", annotation.quote ?: "")
            jsonObj.put("pageNumber", annotation.pageNumber ?: "")
            jsonObj.put("chapter", if (annotation.chapter != null && annotation.chapter != "NULL") annotation.chapter else "")
            jsonObj.put("createdAt", annotation.createdAt ?: "")

            // Convert color integer to hex, empty string if null
            val colorHex = annotation.color?.let { colorInt ->
                try {
                    val colorLong = colorInt.toLong() and 0xFFFFFFFFL
                    val r = (colorLong shr 16) and 0xFF
                    val g = (colorLong shr 8) and 0xFF
                    val b = colorLong and 0xFF
                    "#%02x%02x%02x".format(r, g, b)
                } catch (e: Exception) {
                    Log.w(TAG, "Error converting color: $colorInt", e)
                    ""
                }
            } ?: ""
            jsonObj.put("color", colorHex)

            // Convert shape to style name, empty string if null
            val style = annotation.shape?.let { shape ->
                when (shape) {
                    0 -> "highlight"
                    1 -> "underline"
                    2 -> "dashed"
                    3 -> "wavy"
                    4 -> "redact"
                    5 -> "mute"
                    else -> "unknown"
                }
            } ?: ""
            jsonObj.put("style", style)

            // Always include note field, empty string if null/blank/NULL
            val noteValue = if (annotation.note != null && annotation.note.isNotBlank() && annotation.note != "NULL") {
                annotation.note
            } else {
                ""
            }
            jsonObj.put("note", noteValue)

            jsonArray.put(jsonObj)
        }

        val bookObject = JSONObject()
        bookObject.put("title", book.getDisplayTitle())
        bookObject.put("authors", book.getDisplayAuthors())

        // Get format from book's name field
        book.name?.let {
            val extension = it.substringAfterLast('.', "").lowercase()
            val format = if (extension == "fbz") "djvu" else extension.ifEmpty { "unknown" }
            bookObject.put("format", format)
        } ?: run {
            bookObject.put("format", "unknown")
        }

        bookObject.put("path", book.location ?: "")

        // Add total pages if available
        book.totalPages?.let {
            bookObject.put("totalPages", it)
        }

        // Add publisher, language, ISBN, and description
        book.publisher?.takeIf { it.isNotBlank() && it != "NULL" }?.let {
            bookObject.put("publisher", it)
        }
        book.language?.takeIf { it.isNotBlank() && it != "NULL" }?.let {
            bookObject.put("language", it)
        }
        book.isbn?.takeIf { it.isNotBlank() && it != "NULL" }?.let {
            bookObject.put("isbn", it)
        }
        book.description?.takeIf { it.isNotBlank() && it != "NULL" }?.let {
            bookObject.put("description", it)
        }

        bookObject.put("annotations", jsonArray)

        return bookObject
    }

    private fun buildMultiBookJsonContent(selections: List<Pair<BookMetadata, List<Annotation>>>): String {
        // Same timestamp at both levels: consumers that only look at one book entry in
        // "books" (rather than the batch root) still get an exportedAt without extra lookup.
        val exportedAt = System.currentTimeMillis()

        val booksArray = JSONArray()
        for ((book, annotations) in selections) {
            val bookObject = buildBookJsonObject(book, annotations)
            bookObject.put("exportedAt", exportedAt)
            booksArray.put(bookObject)
        }

        val rootObject = JSONObject()
        rootObject.put("exportedAt", exportedAt)
        rootObject.put("books", booksArray)
        return normalizeJson(rootObject.toString(2))
    }

    private fun generateFileName(book: BookMetadata, extension: String = "json"): String {
        val sanitizedTitle = book.getDisplayTitle()
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val ext = extension.trim().trimStart('.').ifEmpty { "txt" }
        return "${sanitizedTitle}_${timestamp}_annotations.$ext"
    }

    private fun generateMultiBookFileName(extension: String = "json"): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val ext = extension.trim().trimStart('.').ifEmpty { "txt" }
        return "Annotations_${timestamp}.$ext"
    }

    fun saveAnnotationsAsCsv(context: Context, scope: CoroutineScope, book: BookMetadata, annotations: List<Annotation>) {
        scope.launch {
            try {
                val csvContent = buildCsvContent(book, annotations)
                val fileName = generateFileName(book, "csv")

                val fileUri = withContext(Dispatchers.IO) {
                    saveCsvFileAndGetUri(context, fileName, csvContent)
                }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) {
                        showOpenFileDialog(context, fileName, fileUri, "text/csv")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save CSV file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving CSV", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Stable per-book identifier for joining/filtering exported rows, matching the key used to group annotations to books elsewhere in the app. */
    private fun bookId(book: BookMetadata): String = book.idString ?: book.uuid

    private fun buildCsvContent(book: BookMetadata, annotations: List<Annotation>): String {
        val csv = StringBuilder()
        csv.append("Book,Author,Page,Quote,Chapter,Style,Color,Note,Created At,Path\n")
        val bookTitle = escapeCsvField(book.getDisplayTitle())
        val bookAuthor = escapeCsvField(book.getDisplayAuthors())
        val bookIdField = escapeCsvField(bookId(book))
        sortedForCsv(annotations).forEach {
            csv.append("\"$bookTitle\",\"$bookAuthor\",").append(csvDataRow(it)).append(",\"$bookIdField\"\n")
        }
        return csv.toString()
    }

    private fun buildMultiBookCsvContent(selections: List<Pair<BookMetadata, List<Annotation>>>): String {
        val csv = StringBuilder()
        csv.append("Book,Author,Page,Quote,Chapter,Style,Color,Note,Created At,Path\n")
        for ((book, annotations) in selections) {
            val bookTitle = escapeCsvField(book.getDisplayTitle())
            val bookAuthor = escapeCsvField(book.getDisplayAuthors())
            val bookIdField = escapeCsvField(bookId(book))
            sortedForCsv(annotations).forEach {
                csv.append("\"$bookTitle\",\"$bookAuthor\",").append(csvDataRow(it)).append(",\"$bookIdField\"\n")
            }
        }
        return csv.toString()
    }

    private fun sortedForCsv(annotations: List<Annotation>): List<Annotation> =
        annotations.sortedWith(compareBy({ it.pageNumber }, { it.locationBeginInt }))

    /** One CSV row (Page,Quote,Chapter,Style,Color,Note,Created At) for a single annotation, no trailing newline. */
    private fun csvDataRow(annotation: Annotation): String {
        val page = annotation.pageNumber?.toString() ?: ""
        val quote = escapeCsvField(annotation.quote ?: "")
        val chapter = if (annotation.chapter != null && annotation.chapter != "NULL")
            escapeCsvField(annotation.chapter) else ""

        // Convert shape to style name
        val style = when (annotation.shape) {
            0 -> "highlight"
            1 -> "underline"
            2 -> "dashed"
            3 -> "wavy"
            4 -> "redact"
            5 -> "mute"
            else -> ""
        }

        // Convert color integer to hex
        val color = annotation.color?.let { colorInt ->
            try {
                val colorLong = colorInt.toLong() and 0xFFFFFFFFL
                val r = (colorLong shr 16) and 0xFF
                val g = (colorLong shr 8) and 0xFF
                val b = colorLong and 0xFF
                "#%02x%02x%02x".format(r, g, b)
            } catch (e: Exception) {
                ""
            }
        } ?: ""

        val note = if (annotation.note != null && annotation.note.isNotBlank() && annotation.note != "NULL")
            escapeCsvField(annotation.note) else ""
        val createdAt = annotation.createdAt ?: ""

        return "$page,\"$quote\",$chapter,$style,$color,\"$note\",$createdAt"
    }

    private fun escapeCsvField(field: String): String {
        // Escape double quotes by doubling them
        return field.replace("\"", "\"\"")
    }

    /**
     * Save file to custom directory if set in preferences, otherwise use Downloads
     */
    private fun saveFileToCustomPath(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String
    ): Uri? {
        val prefs = context.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val customPathUri = prefs.getString(PreferencesActivity.KEY_SAVE_PATH_URI, null)

        return if (customPathUri != null) {
            // Use custom directory via DocumentFile API
            try {
                val treeUri = Uri.parse(customPathUri)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)

                // Delete existing file if present
                docDir?.findFile(fileName)?.delete()

                // Create new file
                val docFile = docDir?.createFile(mimeType, fileName)
                docFile?.let { file ->
                    context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                    }
                    file.uri
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving to custom path: ${e.message}")
                null
            }
        } else {
            // Use default Downloads folder
            null
        }
    }

    private fun saveCsvFileAndGetUri(context: Context, fileName: String, content: String): Uri? {
        // Try custom path first
        saveFileToCustomPath(context, fileName, content, "text/csv")?.let { return it }

        // Fallback to Downloads
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use MediaStore API
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(it, contentValues, null, null)
                it
            }
        } else {
            // Android 9 and below: Use direct file access
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            file.writeText(content)
            Uri.fromFile(file)
        }
    }

    private fun saveJsonFileAndGetUri(context: Context, fileName: String, content: String): Uri? {
        // Try custom path first
        saveFileToCustomPath(context, fileName, content, "application/json")?.let { return it }

        // Fallback to Downloads
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use MediaStore API
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(it, contentValues, null, null)
                it
            }
        } else {
            // Android 9 and below: Direct file access
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                file.writeText(content)

                // Return file URI for Android 9 and below
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error saving file on Android 9-", e)
                null
            }
        }
    }

    private fun saveToTempAndGetUri(context: Context, fileName: String, content: String): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "shared_annotations")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val file = File(cacheDir, fileName)
            file.writeText(content)

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating temp file", e)
            null
        }
    }

    // Text export functions
    fun saveAnnotationsAsText(context: Context, scope: CoroutineScope, book: BookMetadata, annotations: List<Annotation>) {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE)
                val extension = prefs.getString(PreferencesActivity.KEY_TEXT_EXTENSION, PreferencesActivity.DEFAULT_TEXT_EXTENSION)
                    ?: PreferencesActivity.DEFAULT_TEXT_EXTENSION

                val textContent = buildTextContent(context, book, annotations)
                val fileName = generateFileName(book, extension)

                val fileUri = withContext(Dispatchers.IO) {
                    saveTextFileAndGetUri(context, fileName, textContent, extension)
                }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) {
                        showOpenFileDialog(context, fileName, fileUri, "text/plain")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save text file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving text", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareAnnotationsAsText(context: Context, scope: CoroutineScope, book: BookMetadata, annotations: List<Annotation>) {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE)
                val extension = prefs.getString(PreferencesActivity.KEY_TEXT_EXTENSION, PreferencesActivity.DEFAULT_TEXT_EXTENSION)
                    ?: PreferencesActivity.DEFAULT_TEXT_EXTENSION

                val textContent = buildTextContent(context, book, annotations)
                val fileName = generateFileName(book, extension)

                val fileUri = withContext(Dispatchers.IO) {
                    saveToTempAndGetUri(context, fileName, textContent)
                }

                if (fileUri != null) {
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            putExtra(Intent.EXTRA_SUBJECT, "${book.getDisplayTitle()} - Annotations")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share annotations"))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing text", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buildTextContent(context: Context, book: BookMetadata, annotations: List<Annotation>): String {
        // Sort annotations by page number first, then by location in document
        val sortedAnnotations = annotations.sortedWith(
            compareBy({ it.pageNumber }, { it.locationBeginInt })
        )

        // Get template from preferences
        val prefs = context.getSharedPreferences(PreferencesActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val template = prefs.getString(PreferencesActivity.KEY_TEXT_TEMPLATE, PreferencesActivity.DEFAULT_TEMPLATE)
            ?: PreferencesActivity.DEFAULT_TEMPLATE

        // Build context for Pebble
        val annotationMaps = sortedAnnotations.map { annotation ->
            mapOf(
                "pageNumber" to annotation.pageNumber,
                "quote" to annotation.quote,
                "note" to annotation.note?.takeIf { it.isNotBlank() && it != "NULL" },
                "chapter" to annotation.chapter?.takeIf { it != "NULL" },
                "style" to when (annotation.shape) {
                    0 -> "highlight"
                    1 -> "underline"
                    2 -> "dashed"
                    3 -> "wavy"
                    4 -> "redact"
                    5 -> "mute"
                    else -> "unknown"
                },
                "color" to annotation.color?.let { colorInt ->
                    try {
                        val colorLong = colorInt.toLong() and 0xFFFFFFFFL
                        val r = (colorLong shr 16) and 0xFF
                        val g = (colorLong shr 8) and 0xFF
                        val b = colorLong and 0xFF
                        "#%02x%02x%02x".format(r, g, b)
                    } catch (e: Exception) {
                        null
                    }
                },
                "createdAt" to annotation.createdAt
            )
        }

        val format = book.name?.let {
            val ext = it.substringAfterLast('.', "").lowercase()
            if (ext == "fbz") "djvu" else ext.ifEmpty { "unknown" }
        } ?: "unknown"

        val exportedAt = System.currentTimeMillis()

        val contextMap = mapOf(
            "book" to mapOf(
                "title" to book.getDisplayTitle(),
                "authors" to book.getDisplayAuthors(),
                "format" to format,
                "path" to book.location,
                "totalPages" to book.totalPages,
                "publisher" to book.publisher?.takeIf { it.isNotBlank() && it != "NULL" },
                "language" to book.language?.takeIf { it.isNotBlank() && it != "NULL" },
                "isbn" to book.isbn?.takeIf { it.isNotBlank() && it != "NULL" },
                "description" to book.description?.takeIf { it.isNotBlank() && it != "NULL" },
                "exportedAt" to exportedAt
            ),
            "annotations" to annotationMaps
        )

        // Render template
        return try {
            val pebbleEngine = PebbleEngine.Builder()
                .strictVariables(false)
                .autoEscaping(false)
                .newLineTrimming(false)
                .extension(CustomPebbleExtension())
                .build()
            val compiledTemplate = pebbleEngine.getLiteralTemplate(template)
            val writer = StringWriter()
            compiledTemplate.evaluate(writer, contextMap)
            writer.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering template", e)
            "Error rendering template: ${e.message}"
        }
    }

    /** Renders each book's annotations through the same per-book template, concatenated with a separator. */
    private fun buildMultiBookTextContent(context: Context, selections: List<Pair<BookMetadata, List<Annotation>>>): String {
        return selections.joinToString(separator = "\n\n---\n\n") { (book, annotations) ->
            buildTextContent(context, book, annotations)
        }
    }

    private fun saveTextFileAndGetUri(context: Context, fileName: String, content: String, extension: String): Uri? {
        // Use a generic MIME type with no canonical extension of its own so the
        // storage provider doesn't "correct" fileName by appending its own extension
        // on top of the one the user configured (e.g. "annotations.md" -> "annotations.md.txt"
        // would happen if we declared "text/plain", since its canonical extension is .txt).
        val mimeType = "application/octet-stream"

        // Try custom path first
        saveFileToCustomPath(context, fileName, content, mimeType)?.let { return it }

        // Fallback to Downloads
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use MediaStore API
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(it, contentValues, null, null)
                it
            }
        } else {
            // Android 9 and below: Use direct file access
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            file.writeText(content)
            Uri.fromFile(file)
        }
    }
}
