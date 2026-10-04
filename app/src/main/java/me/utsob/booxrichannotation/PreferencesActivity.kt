package me.utsob.booxrichannotation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile

class PreferencesActivity : AppCompatActivity() {
    
    private lateinit var formatRadioGroup: RadioGroup
    private lateinit var jsonRadio: RadioButton
    private lateinit var csvRadio: RadioButton
    private lateinit var textRadio: RadioButton
    private lateinit var textExtensionInput: EditText
    private lateinit var btnEditTemplate: Button
    private lateinit var savePathDisplay: TextView
    private lateinit var btnChooseFolder: Button
    private lateinit var btnResetFolder: Button
    
    // Folder picker launcher
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist URI permission
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            
            // Save URI to preferences
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit().putString(KEY_SAVE_PATH_URI, it.toString()).apply()
            
            // Update display
            updateSavePathDisplay()
        }
    }
    
    companion object {
        const val PREFS_NAME = "BooxRichAnnotationPrefs"
        const val KEY_EXPORT_FORMAT = "export_format"
        const val KEY_TEXT_EXTENSION = "text_extension"
        const val KEY_TEXT_TEMPLATE = "text_template"
        const val KEY_SAVE_PATH_URI = "save_path_uri"
        const val FORMAT_JSON = "json"
        const val FORMAT_CSV = "csv"
        const val FORMAT_TEXT = "text"
        const val DEFAULT_TEXT_EXTENSION = "md"
        const val DEFAULT_TEMPLATE = """# {{ book.title }}
##### by {{ book.authors }}

{%- set prevChapter = "" %}
{%- for annotation in annotations %}
{%- if annotation.chapter != prevChapter %}

## {{ annotation.chapter }}
{%- set prevChapter = annotation.chapter %}
{%- endif %}
### Page: {{ annotation.pageNumber }} @ {{ annotation.createdAt | date("yyyy-MM-dd HH:mm:ss") }}
{{ annotation.quote }}

---
{{ annotation.note }}

**Color**: {{ annotation.color }}
**Style**: {{ annotation.style }}
{% endfor %}

---

| Metadata | Value |
|----------|-------|
| Total Pages | {{ book.totalPages }} |
| Annotations | {{ annotations.size }} |
{%- if book.publisher %}
| Publisher | {{ book.publisher }} |
{%- endif %}
{%- if book.language %}
| Language | {{ book.language }} |
{%- endif %}
{%- if book.isbn %}
| ISBN | {{ book.isbn }} |
{%- endif %}
{%- if book.description %}
| Description | {{ book.description }} |
{%- endif %}
| Exported At | {{ book.exportedAt | date("yyyy-MM-dd HH:mm:ss") }} |
"""
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)
        
        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Preferences"
        
        formatRadioGroup = findViewById(R.id.format_radio_group)
        jsonRadio = findViewById(R.id.radio_json)
        csvRadio = findViewById(R.id.radio_csv)
        textRadio = findViewById(R.id.radio_text)
        textExtensionInput = findViewById(R.id.text_extension_input)
        btnEditTemplate = findViewById(R.id.btn_edit_template)
        savePathDisplay = findViewById(R.id.save_path_display)
        btnChooseFolder = findViewById(R.id.btn_choose_folder)
        btnResetFolder = findViewById(R.id.btn_reset_folder)
        
        // Style the buttons programmatically
        btnEditTemplate.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_dialog_button)
            setPadding(48, 48, 48, 48)
        }
        btnChooseFolder.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_dialog_button)
            setPadding(48, 48, 48, 48)
        }
        btnResetFolder.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_dialog_button)
            setPadding(48, 48, 48, 48)
        }
        
        // Load saved preferences
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentFormat = prefs.getString(KEY_EXPORT_FORMAT, FORMAT_JSON) ?: FORMAT_JSON
        val textExtension = prefs.getString(KEY_TEXT_EXTENSION, DEFAULT_TEXT_EXTENSION) ?: DEFAULT_TEXT_EXTENSION
        
        when (currentFormat) {
            FORMAT_JSON -> jsonRadio.isChecked = true
            FORMAT_CSV -> csvRadio.isChecked = true
            FORMAT_TEXT -> textRadio.isChecked = true
        }
        
        textExtensionInput.setText(textExtension)
        
        // Update save path display
        updateSavePathDisplay()
        
        // Save preference when changed
        formatRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val format = when (checkedId) {
                R.id.radio_json -> FORMAT_JSON
                R.id.radio_csv -> FORMAT_CSV
                R.id.radio_text -> FORMAT_TEXT
                else -> FORMAT_JSON
            }
            
            prefs.edit().putString(KEY_EXPORT_FORMAT, format).apply()
        }
        
        // Save extension when changed
        textExtensionInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val extension = textExtensionInput.text.toString().trim()
                if (extension.isNotEmpty()) {
                    prefs.edit().putString(KEY_TEXT_EXTENSION, extension).apply()
                }
            }
        }
        
        // Open template editor
        btnEditTemplate.setOnClickListener {
            startActivity(Intent(this, TemplateEditorActivity::class.java))
        }
        
        // Choose custom folder
        btnChooseFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
        
        // Reset to default Downloads folder
        btnResetFolder.setOnClickListener {
            prefs.edit().remove(KEY_SAVE_PATH_URI).apply()
            updateSavePathDisplay()
        }
    }
    
    private fun updateSavePathDisplay() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedUri = prefs.getString(KEY_SAVE_PATH_URI, null)
        
        if (savedUri != null) {
            try {
                val uri = Uri.parse(savedUri)
                val docFile = DocumentFile.fromTreeUri(this, uri)
                savePathDisplay.text = docFile?.name ?: "Custom Folder"
            } catch (e: Exception) {
                savePathDisplay.text = "Downloads (default)"
            }
        } else {
            savePathDisplay.text = "Downloads (default)"
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
