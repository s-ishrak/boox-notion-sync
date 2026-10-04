package me.utsob.booxrichannotation

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

/** Notion token + database ID, a "Sync now" button and the last sync result. */
class NotionSettingsActivity : AppCompatActivity() {

    private lateinit var tokenInput: EditText
    private lateinit var databaseInput: EditText
    private lateinit var statusText: TextView
    private lateinit var syncButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Notion Sync"

        val prefs = getSharedPreferences(NotionSync.PREFS, Context.MODE_PRIVATE)
        val pad = dp(16)

        tokenInput = input("Notion integration token (secret_… or ntn_…)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString(NotionSync.KEY_TOKEN, ""))
        }
        databaseInput = input("Notion database link or ID").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString(NotionSync.KEY_DATABASE_ID, ""))
        }
        val saveButton = button("Save") { save() }
        syncButton = button("Sync now") { syncNow() }
        statusText = label("")

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(label("Token"))
            addView(tokenInput)
            addView(label("Database"))
            addView(databaseInput)
            addView(saveButton)
            addView(syncButton)
            addView(statusText)
            addView(label("Syncs automatically about once an hour when online."))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFFFFFFFF.toInt()); addView(column) })
    }

    override fun onResume() {
        super.onResume()
        statusText.text = getSharedPreferences(NotionSync.PREFS, Context.MODE_PRIVATE)
            .getString(NotionSync.KEY_LAST_STATUS, "Not synced yet")
    }

    private fun save(): Boolean {
        val token = tokenInput.text.toString().trim()
        val dbId = NotionSync.normalizeDatabaseId(databaseInput.text.toString())
        if (token.isEmpty() || dbId == null) {
            Toast.makeText(this, "Enter a token and a valid database link or ID", Toast.LENGTH_LONG).show()
            return false
        }
        getSharedPreferences(NotionSync.PREFS, Context.MODE_PRIVATE).edit()
            .putString(NotionSync.KEY_TOKEN, token)
            .putString(NotionSync.KEY_DATABASE_ID, dbId)
            .apply()
        databaseInput.setText(dbId)
        SyncWorker.schedule(this)
        Toast.makeText(this, "Saved. Hourly sync is on.", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun syncNow() {
        if (!save()) return
        syncButton.isEnabled = false
        statusText.text = "Syncing…"
        Thread {
            val status = try {
                NotionSync.run(applicationContext)
            } catch (e: IOException) {
                "Last sync failed: ${e.message}"
            } catch (e: Exception) {
                "Last sync failed: ${e.javaClass.simpleName}: ${e.message}"
            }
            getSharedPreferences(NotionSync.PREFS, Context.MODE_PRIVATE).edit()
                .putString(NotionSync.KEY_LAST_STATUS, status).apply()
            runOnUiThread {
                statusText.text = status
                syncButton.isEnabled = true
            }
        }.start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // --- E-ink friendly views: black on white, large text, 48dp+ targets ---

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFF000000.toInt())
        textSize = 18f
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun input(hint: String) = EditText(this).apply {
        this.hint = hint
        setTextColor(0xFF000000.toInt())
        setHintTextColor(0xFF000000.toInt())
        textSize = 18f
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundResource(R.drawable.bg_card)
        isSingleLine = true
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(0xFF000000.toInt())
        textSize = 18f
        backgroundTintList = null
        setBackgroundResource(R.drawable.bg_dialog_button)
        minHeight = dp(48)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) }
        setOnClickListener { onClick() }
    }
}
