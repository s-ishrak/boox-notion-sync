package me.utsob.booxrichannotation

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One-way sync: NeoReader highlights -> Notion, one database page per book.
 * Only highlights not sent before are appended. Edits/deletions are not mirrored.
 */
object NotionSync {
    private const val TAG = "NotionSync"
    const val PREFS = "notion_sync"
    const val KEY_TOKEN = "token"
    const val KEY_DATABASE_ID = "database_id"
    const val KEY_LAST_STATUS = "last_status"
    private const val KEY_SYNCED = "synced_keys"
    private const val KEY_BOOK_PAGES = "book_pages"

    fun isConfigured(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !p.getString(KEY_TOKEN, null).isNullOrBlank() && !p.getString(KEY_DATABASE_ID, null).isNullOrBlank()
    }

    /** Accepts a raw ID or a full Notion URL; returns the 32-char ID, or null. */
    fun normalizeDatabaseId(input: String): String? {
        val path = input.trim().substringBefore("?")
        return Regex("[0-9a-fA-F]{32}").findAll(path.replace("-", "")).lastOrNull()?.value
    }

    /** Runs a sync and returns a human-readable status. Throws on network/API failure. */
    @Synchronized
    fun run(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        val databaseId = prefs.getString(KEY_DATABASE_ID, null)
        if (token.isNullOrBlank() || databaseId.isNullOrBlank()) return "Not set up: add token and database ID"

        val client = NotionClient(token)
        val synced = prefs.getStringSet(KEY_SYNCED, emptySet())!!.toMutableSet()
        val bookPages = JSONObject(prefs.getString(KEY_BOOK_PAGES, "{}")!!)

        // Same book/annotation grouping as MainActivity: books share a file idString,
        // annotations point at any of that book's metadata uuids.
        val books = OnyxContentProvider.queryBookMetadata(context)
        val annotations = OnyxContentProvider.queryAllAnnotations(context, writeDebugFile = false)
        val annotationsByBookUuid = annotations.groupBy { it.idString }

        var newCount = 0
        var bookCount = 0
        books.groupBy { it.idString ?: it.uuid }.forEach { (bookKey, rows) ->
            val book = rows.first()
            val newOnes = rows.flatMap { annotationsByBookUuid[it.uuid] ?: emptyList() }
                .filter { !it.quote.isNullOrBlank() }
                .distinctBy { keyOf(bookKey, it) }
                .filter { keyOf(bookKey, it) !in synced }
                .sortedWith(compareBy({ it.pageNumber ?: Int.MAX_VALUE }, { it.locationBeginInt ?: 0 }, { it.createdAt ?: 0L }))
            if (newOnes.isEmpty()) return@forEach

            fun lookUpOrCreate() = client.findBookPage(databaseId, bookKey)
                ?: client.createBookPage(databaseId, book.getDisplayTitle(), book.getDisplayAuthors(), bookKey)

            var pageId = bookPages.optString(bookKey).ifBlank { null } ?: lookUpOrCreate()
            val blocks = newOnes.flatMap { blocksFor(it) }
            try {
                client.appendBlocks(pageId, blocks)
            } catch (e: NotionClient.NotionException) {
                // Cached page was deleted/archived in Notion: find or recreate it once.
                if (e.code != 404 && e.code != 400) throw e
                pageId = lookUpOrCreate()
                client.appendBlocks(pageId, blocks)
            }
            bookPages.put(bookKey, pageId)
            client.setLastSynced(pageId, isoNow())

            // Record progress per book so a later failure doesn't resend these.
            newOnes.forEach { synced.add(keyOf(bookKey, it)) }
            prefs.edit()
                .putStringSet(KEY_SYNCED, HashSet(synced))
                .putString(KEY_BOOK_PAGES, bookPages.toString())
                .apply()
            newCount += newOnes.size
            bookCount++
        }

        val status = "Last sync ${displayNow()}: $newCount new highlight(s) in $bookCount book(s)"
        Log.d(TAG, status)
        return status
    }

    private fun keyOf(bookKey: String, a: Annotation) =
        "$bookKey|${a.quote?.take(100)}|${a.locationBeginInt}|${a.locationEndInt}"

    private fun blocksFor(a: Annotation): List<JSONObject> {
        val blocks = mutableListOf(block("quote", NotionClient.richText(a.quote!!.trim())))
        a.displayNote()?.let { note ->
            val rt = NotionClient.richText("Note: ", bold = true)
            val noteRt = NotionClient.richText(note.trim())
            for (i in 0 until noteRt.length()) rt.put(noteRt.get(i))
            blocks.add(block("paragraph", rt))
        }
        val meta = listOfNotNull(
            a.pageNumber?.let { "p. $it" },
            a.displayChapter(),
            a.createdAt?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)) }
        ).joinToString(" · ")
        if (meta.isNotEmpty()) blocks.add(block("paragraph", NotionClient.richText(meta, italic = true, color = "gray")))
        return blocks
    }

    private fun block(type: String, richText: JSONArray) = JSONObject()
        .put("object", "block")
        .put("type", type)
        .put(type, JSONObject().put("rich_text", richText))

    private fun isoNow() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
    private fun displayNow() = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date())
}
