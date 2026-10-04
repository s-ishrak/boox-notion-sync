package me.utsob.booxrichannotation

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal Notion REST client (no extra dependencies). */
class NotionClient(private val token: String) {

    class NotionException(val code: Int, message: String) : IOException("Notion $code: $message")

    /** Returns the page id of the book with this Book ID in the database, or null. */
    fun findBookPage(databaseId: String, bookId: String): String? {
        val body = JSONObject().put(
            "filter", JSONObject()
                .put("property", "Book ID")
                .put("rich_text", JSONObject().put("equals", bookId))
        ).put("page_size", 1)
        val results = request("POST", "databases/$databaseId/query", body).getJSONArray("results")
        return if (results.length() > 0) results.getJSONObject(0).getString("id") else null
    }

    fun createBookPage(databaseId: String, title: String, author: String, bookId: String): String {
        val props = JSONObject()
            .put("Name", JSONObject().put("title", richText(title)))
            .put("Author", JSONObject().put("rich_text", richText(author)))
            .put("Book ID", JSONObject().put("rich_text", richText(bookId)))
        val body = JSONObject()
            .put("parent", JSONObject().put("database_id", databaseId))
            .put("properties", props)
        return request("POST", "pages", body).getString("id")
    }

    /** Appends blocks to a page, 100 per request (Notion's limit). */
    fun appendBlocks(pageId: String, blocks: List<JSONObject>) {
        blocks.chunked(100).forEach { chunk ->
            val body = JSONObject().put("children", JSONArray(chunk))
            request("PATCH", "blocks/$pageId/children", body)
        }
    }

    fun setLastSynced(pageId: String, isoDate: String) {
        val props = JSONObject().put("Last synced", JSONObject().put("date", JSONObject().put("start", isoDate)))
        request("PATCH", "pages/$pageId", JSONObject().put("properties", props))
    }

    private fun request(method: String, path: String, body: JSONObject, retried: Boolean = false): JSONObject {
        val req = Request.Builder()
            .url("https://api.notion.com/v1/$path")
            .header("Authorization", "Bearer $token")
            .header("Notion-Version", NOTION_VERSION)
            .method(method, body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (resp.code == 429 && !retried) {
                val waitSec = resp.header("Retry-After")?.toLongOrNull() ?: 2L
                Thread.sleep(waitSec * 1000)
                return request(method, path, body, retried = true)
            }
            if (!resp.isSuccessful) {
                val msg = try { JSONObject(text).optString("message", text) } catch (e: Exception) { text }
                throw NotionException(resp.code, msg)
            }
            return JSONObject(text)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        const val NOTION_VERSION = "2022-06-28"
        private const val MAX_TEXT = 2000 // Notion's per-text-object limit

        /** Rich text array, split into 2000-char pieces. */
        fun richText(text: String, bold: Boolean = false, italic: Boolean = false, color: String = "default"): JSONArray {
            val arr = JSONArray()
            text.chunked(MAX_TEXT).forEach { piece ->
                arr.put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", JSONObject().put("content", piece))
                        .put("annotations", JSONObject().put("bold", bold).put("italic", italic).put("color", color))
                )
            }
            return arr
        }
    }
}
