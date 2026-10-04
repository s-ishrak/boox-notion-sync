package me.utsob.booxrichannotation

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.pebbletemplates.pebble.PebbleEngine
import java.io.StringWriter

class TemplateEditorActivity : AppCompatActivity() {
    
    private lateinit var templateInput: EditText
    private lateinit var errorText: TextView
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private val pebbleEngine = PebbleEngine.Builder()
        .strictVariables(false)
        .autoEscaping(false)
        .newLineTrimming(false)
        .extension(CustomPebbleExtension())
        .build()
    
    private var isUpdatingText = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_template_editor)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Template Editor"
        
        templateInput = findViewById(R.id.template_input)
        errorText = findViewById(R.id.error_text)
        btnSave = findViewById(R.id.btn_save)
        btnReset = findViewById(R.id.btn_reset)
        
        // Style the buttons programmatically
        btnSave.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_dialog_button)
            setPadding(48, 48, 48, 48)
        }
        btnReset.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_dialog_button)
            setPadding(48, 48, 48, 48)
        }
        
        // Load saved template
        val prefs = getSharedPreferences(PreferencesActivity.PREFS_NAME, MODE_PRIVATE)
        val template = prefs.getString(
            PreferencesActivity.KEY_TEXT_TEMPLATE,
            PreferencesActivity.DEFAULT_TEMPLATE
        ) ?: PreferencesActivity.DEFAULT_TEMPLATE
        
        templateInput.setText(template)
        
        // Add text watcher for linting and syntax highlighting
        templateInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingText) {
                    validateTemplate(s?.toString() ?: "")
                    applySyntaxHighlighting(s)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Initial validation and highlighting
        validateTemplate(template)
        applySyntaxHighlighting(templateInput.text)
        
        // Save button
        btnSave.setOnClickListener {
            val newTemplate = templateInput.text.toString()
            if (validateTemplate(newTemplate)) {
                prefs.edit().putString(PreferencesActivity.KEY_TEXT_TEMPLATE, newTemplate).apply()
                finish()
            }
        }
        
        // Reset button
        btnReset.setOnClickListener {
            templateInput.setText(PreferencesActivity.DEFAULT_TEMPLATE)
        }
    }
    
    private fun validateTemplate(template: String): Boolean {
        return try {
            // Test compile the template
            pebbleEngine.getLiteralTemplate(template)
            
            // Try rendering with sample data
            val compiledTemplate = pebbleEngine.getLiteralTemplate(template)
            val context = mapOf(
                "book" to mapOf(
                    "title" to "Sample Book",
                    "authors" to "John Doe",
                    "format" to "epub",
                    "path" to "/storage/emulated/0/Books/sample-book.epub",
                    "totalPages" to 300,
                    "publisher" to "Sample Publisher",
                    "language" to "English",
                    "isbn" to "978-0-123456-78-9",
                    "description" to "This is a sample book description.",
                    "exportedAt" to System.currentTimeMillis()
                ),
                "annotations" to listOf(
                    mapOf(
                        "pageNumber" to 42,
                        "quote" to "Sample quote from the book.",
                        "note" to "This is a sample note.",
                        "chapter" to "Chapter 1",
                        "style" to "highlight",
                        "color" to "#ffff00",
                        "createdAt" to System.currentTimeMillis()
                    )
                )
            )
            compiledTemplate.evaluate(StringWriter(), context)
            
            errorText.text = "✓ Template is valid"
            errorText.setTextColor(android.graphics.Color.parseColor("#000000"))
            btnSave.isEnabled = true
            true
        } catch (e: Exception) {
            // Truncate error message to avoid showing entire template
            val errorMsg = e.message?.take(200) ?: "Unknown error"
            errorText.text = "✗ Error: $errorMsg"
            errorText.setTextColor(android.graphics.Color.parseColor("#000000"))
            btnSave.isEnabled = false
            false
        }
    }
    
    private fun applySyntaxHighlighting(editable: Editable?) {
        if (editable == null || editable.isEmpty()) return
        
        // Save cursor position
        val cursorPos = templateInput.selectionStart
        
        isUpdatingText = true
        
        // Remove existing spans
        val spans = editable.getSpans(0, editable.length, StyleSpan::class.java)
        spans.forEach { editable.removeSpan(it) }
        
        val text = editable.toString()
        
        // Keywords to highlight in bold
        val keywords = listOf(
            "for", "endfor", "if", "endif", "else", "elseif", "elif",
            "set", "block", "endblock", "extends", "include",
            "macro", "endmacro", "import", "from", "in"
        )
        
        // Pattern to match template tags: {% keyword ... %}
        val tagPattern = Regex("""\{%-?\s*(\w+)""")
        tagPattern.findAll(text).forEach { match ->
            val keyword = match.groupValues[1]
            if (keyword in keywords) {
                val start = match.range.first + match.value.indexOf(keyword)
                val end = start + keyword.length
                editable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        
        // Pattern to match filters and functions: | keyword or keyword(
        val filterPattern = Regex("""\|\s*(\w+)|\b(percentage|date)\s*\(""")
        filterPattern.findAll(text).forEach { match ->
            val keyword = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (keyword.isNotEmpty()) {
                val start = match.range.first + match.value.indexOf(keyword)
                val end = start + keyword.length
                editable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        
        // Pattern to match variables: word.word or word.word.word, etc.
        // Matches: book.title, annotation.pageNumber, book.authors, etc.
        val variablePattern = Regex("""\b([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)+)\b""")
        variablePattern.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            editable.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Pattern to match standalone collection variables in loops: "annotations", "items", etc.
        // Matches variables after "for" and "in" keywords
        val loopVarPattern = Regex("""\{%-?\s*for\s+(\w+)\s+in\s+(\w+)""")
        loopVarPattern.findAll(text).forEach { match ->
            // Highlight loop variable (e.g., "annotation" in "for annotation in annotations")
            val loopVar = match.groupValues[1]
            val start1 = match.range.first + match.value.indexOf(loopVar)
            val end1 = start1 + loopVar.length
            editable.setSpan(
                StyleSpan(Typeface.ITALIC),
                start1,
                end1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Highlight collection variable (e.g., "annotations")
            val collectionVar = match.groupValues[2]
            val start2 = match.range.first + match.value.lastIndexOf(collectionVar)
            val end2 = start2 + collectionVar.length
            editable.setSpan(
                StyleSpan(Typeface.ITALIC),
                start2,
                end2,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        isUpdatingText = false
        
        // Restore cursor position
        if (cursorPos >= 0 && cursorPos <= editable.length) {
            templateInput.setSelection(cursorPos)
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
