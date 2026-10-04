package me.utsob.booxrichannotation

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

object OnyxContentProvider {
    private const val TAG = "OnyxContentProvider"
    private const val METADATA_URI = "content://com.onyx.content.database.ContentProvider/Metadata"
    private const val ANNOTATION_URI = "content://com.onyx.content.database.ContentProvider/Annotation"
    
    fun queryBookMetadata(context: Context): List<BookMetadata> {
        val books = mutableListOf<BookMetadata>()
        val uri = Uri.parse(METADATA_URI)
        
        Log.d(TAG, "Starting query to $METADATA_URI")
        
        try {
            context.contentResolver.query(
                uri,
                null, // projection (null = all columns)
                null, // selection
                null, // selectionArgs
                null  // sortOrder
            )?.use { cursor ->
                Log.d(TAG, "Query successful! Found ${cursor.count} books")
                
                // Log all column names for debugging
                val columnNames = cursor.columnNames
                Log.d(TAG, "Available columns (${columnNames.size}): ${columnNames.joinToString(", ")}")
                
                var successCount = 0
                var errorCount = 0
                
                while (cursor.moveToNext()) {
                    try {
                        // Helper function to safely get string value
                        fun getStringOrNull(columnName: String): String? {
                            val index = cursor.getColumnIndex(columnName)
                            return if (index >= 0) {
                                val value = cursor.getString(index)
                                if (value.isNullOrBlank() || value == "NULL") null else value
                            } else {
                                Log.w(TAG, "Column '$columnName' not found")
                                null
                            }
                        }
                        
                        // Helper function to safely get long value
                        fun getLongOrNull(columnName: String): Long? {
                            val index = cursor.getColumnIndex(columnName)
                            return if (index >= 0) {
                                try {
                                    cursor.getLong(index)
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                null
                            }
                        }
                        
                        // Helper function to safely get int value
                        fun getIntOrNull(columnName: String): Int? {
                            val index = cursor.getColumnIndex(columnName)
                            return if (index >= 0) {
                                try {
                                    cursor.getInt(index)
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                null
                            }
                        }
                        
                        val uuid = getStringOrNull("uuid") ?: continue
                        
                        // Parse totalPages: prefer the authoritative "total_page" from
                        // extraAttributes JSON. The "progress" field's denominator is only
                        // a real page count when the reader is set to page-number display;
                        // in percentage-display mode it's a fixed 0-10000 scale (e.g.
                        // "10000/10000" for a finished book), which is NOT a page count.
                        val totalPages = getStringOrNull("extraAttributes")?.let { extraAttrStr ->
                            try {
                                // "total_page" lives under the nested "backend" object, not at the top level
                                JSONObject(extraAttrStr).optJSONObject("backend")
                                    ?.optInt("total_page", -1)?.takeIf { it > 0 }
                            } catch (e: Exception) {
                                null
                            }
                        } ?: getStringOrNull("progress")?.let { progressStr ->
                            try {
                                val parts = progressStr.split("/")
                                if (parts.size == 2) {
                                    parts[1].trim().toIntOrNull()?.takeIf { it != 10000 }
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        // Log if we found totalPages for debugging
                        if (totalPages != null && totalPages > 0) {
                            Log.d(TAG, "Found totalPages=$totalPages for book: ${getStringOrNull("title") ?: getStringOrNull("name")}")
                        }
                        
                        val book = BookMetadata(
                            uuid = uuid,
                            title = getStringOrNull("title"),
                            name = getStringOrNull("name"),
                            authors = getStringOrNull("authors"),
                            publisher = getStringOrNull("publisher"),
                            language = getStringOrNull("language"),
                            isbn = getStringOrNull("ISBN"),
                            description = getStringOrNull("description"),
                            location = getStringOrNull("location"),
                            idString = getStringOrNull("idString"),
                            lastAccess = getLongOrNull("lastAccess"),
                            totalPages = totalPages,
                            status = getIntOrNull("status")
                        )
                        
                        books.add(book)
                        successCount++
                        
                        // Log first few books for debugging
                        if (successCount <= 3) {
                            Log.d(TAG, "Book #$successCount: ${book.getDisplayTitle()} by ${book.getDisplayAuthors()}")
                        }
                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "Error parsing book row #${cursor.position}", e)
                    }
                }
                
                Log.d(TAG, "Parsed $successCount books successfully, $errorCount errors")
            } ?: run {
                Log.e(TAG, "Query returned null cursor")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to access content provider", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying content provider", e)
        }
        
        Log.d(TAG, "Returning ${books.size} books")
        return books
    }
    
    
    fun queryAllAnnotations(context: Context, writeDebugFile: Boolean = true): List<Annotation> {
        val annotations = mutableListOf<Annotation>()
        val uri = Uri.parse(ANNOTATION_URI)
        
        Log.d(TAG, "Querying ALL annotations from content provider...")
        
        try {
            // Query all annotations, only status=0 (active annotations)
            // status=1 means deleted/superseded annotations
            context.contentResolver.query(
                uri,
                null,
                "status=0 OR status IS NULL",
                null,
                null
            )?.use { cursor ->
                Log.d(TAG, "Found ${cursor.count} total annotations")
                
                while (cursor.moveToNext()) {
                    try {
                        fun getStringOrNull(columnName: String): String? {
                            val index = cursor.getColumnIndex(columnName)
                            return if (index >= 0) {
                                val value = cursor.getString(index)
                                if (value.isNullOrBlank() || value == "NULL") null else value
                            } else null
                        }
                        
                        fun getIntOrNull(columnName: String): Int? {
                            val index = cursor.getColumnIndex(columnName)
                            return if (index >= 0) {
                                try { cursor.getInt(index) } catch (e: Exception) { null }
                            } else null
                        }
                        
                        fun getLongOrNull(columnName: String): Long? {
                            val index = cursor.getColumnIndex(columnName)
                            return if (index >= 0) {
                                try { cursor.getLong(index) } catch (e: Exception) { null }
                            } else null
                        }
                        
                        val annotation = Annotation(
                            rowNumber = cursor.position,
                            quote = getStringOrNull("quote"),
                            locationBegin = getStringOrNull("locationBegin"),
                            locationEnd = getStringOrNull("locationEnd"),
                            locationBeginInt = getIntOrNull("locationBeginInt"),
                            locationEndInt = getIntOrNull("locationEndInt"),
                            note = getStringOrNull("note"),
                            linkNote = getStringOrNull("linkNote"),
                            application = getStringOrNull("application"),
                            position = getStringOrNull("position"),
                            // Onyx stores pageNumber 0-indexed, but the reader UI displays
                            // pages 1-indexed, so convert here to match what the user sees
                            pageNumber = getIntOrNull("pageNumber")?.plus(1),
                            rectangles = getStringOrNull("rectangles"),
                            color = getIntOrNull("color"),
                            shape = getIntOrNull("shape"),
                            chapter = getStringOrNull("chapter"),
                            uuid = getStringOrNull("uuid"),
                            objId = getStringOrNull("objId"),
                            status = getIntOrNull("status"),
                            pageXpath = getStringOrNull("pageXpath"),
                            startXpath = getStringOrNull("startXpath"),
                            endXpath = getStringOrNull("endXpath"),
                            customAttr = getStringOrNull("customAttr"),
                            id = getIntOrNull("id"),
                            guid = getStringOrNull("guid"),
                            idString = getStringOrNull("idString"),
                            createdAt = getLongOrNull("createdAt"),
                            updatedAt = getLongOrNull("updatedAt")
                        )
                        
                        annotations.add(annotation)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing annotation row #${cursor.position}", e)
                    }
                }
                
                // Debug logging: Write all annotations to a file
                if (writeDebugFile) try {
                    val debugFile = File(context.getExternalFilesDir(null), "all_annotations_debug_${System.currentTimeMillis()}.txt")
                    FileWriter(debugFile).use { writer ->
                        writer.write("ALL ANNOTATIONS QUERY\n")
                        writer.write("Total annotations before dedup: ${annotations.size}\n")
                        writer.write("=".repeat(80) + "\n\n")
                        
                        annotations.forEachIndexed { index, ann ->
                            writer.write("Annotation #$index:\n")
                            writer.write("  rowNumber: ${ann.rowNumber}\n")
                            writer.write("  quote: ${ann.quote?.take(100)}\n")
                            writer.write("  locationBeginInt: ${ann.locationBeginInt}\n")
                            writer.write("  locationEndInt: ${ann.locationEndInt}\n")
                            writer.write("  pageNumber: ${ann.pageNumber}\n")
                            writer.write("  startXpath: ${ann.startXpath}\n")
                            writer.write("  note: ${ann.note}\n")
                            writer.write("  createdAt: ${ann.createdAt}\n")
                            writer.write("  updatedAt: ${ann.updatedAt}\n")
                            writer.write("  uuid: ${ann.uuid}\n")
                            writer.write("  KEY: ${ann.quote?.take(100)}_${ann.locationBeginInt}_${ann.locationEndInt}\n")
                            writer.write("-".repeat(80) + "\n")
                        }
                        
                        // Group and show duplicates
                        val grouped = annotations.groupBy { ann ->
                            val quoteKey = ann.quote?.take(100) ?: ""
                            val locBegin = ann.locationBeginInt ?: 0
                            val locEnd = ann.locationEndInt ?: 0
                            "${quoteKey}_${locBegin}_${locEnd}"
                        }
                        writer.write("\n\nDUPLICATE ANALYSIS:\n")
                        writer.write("=".repeat(80) + "\n")
                        grouped.forEach { (key, group) ->
                            if (group.size > 1) {
                                writer.write("\nKey: $key\n")
                                writer.write("Found ${group.size} duplicates:\n")
                                group.forEach { ann ->
                                    writer.write("  - updatedAt: ${ann.updatedAt}, rowNumber: ${ann.rowNumber}, uuid: ${ann.uuid}\n")
                                }
                            }
                        }
                    }
                    Log.d(TAG, "Debug file written to: ${debugFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing debug file", e)
                }
                
                // Deduplicate annotations using composite key
                val deduplicatedAnnotations = annotations
                    .groupBy {
                        val quoteKey = it.quote?.take(100) ?: ""
                        val locBegin = it.locationBeginInt ?: 0
                        val locEnd = it.locationEndInt ?: 0
                        "${quoteKey}_${locBegin}_${locEnd}"
                    }
                    .mapNotNull { (key, annotationsWithSameKey) ->
                        annotationsWithSameKey.maxByOrNull { it.updatedAt ?: 0L }
                    }
                
                Log.d(TAG, "After deduplication: ${deduplicatedAnnotations.size} annotations (removed ${annotations.size - deduplicatedAnnotations.size} duplicates)")
                return deduplicatedAnnotations
            } ?: run {
                Log.e(TAG, "Query returned null cursor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying all annotations", e)
        }
        
        return annotations
    }
}
