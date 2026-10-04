package me.utsob.booxrichannotation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BookDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK = "extra_book"
        const val EXTRA_ANNOTATIONS = "extra_annotations"
    }

    private lateinit var book: BookMetadata
    private lateinit var annotationAdapter: AnnotationAdapter
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var selectionCountText: TextView
    private lateinit var exportMenuButton: ImageButton
    private lateinit var shareSelectedButton: ImageButton
    private lateinit var saveSelectedButton: ImageButton

    @Suppress("DEPRECATION")
    private fun <T : java.io.Serializable> Intent.serializableExtraCompat(name: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(name, clazz)
        } else {
            getSerializableExtra(name) as? T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setWindowAnimations(0)
        overridePendingTransition(0, 0)

        setContentView(R.layout.activity_book_detail)

        val extraBook = intent.serializableExtraCompat(EXTRA_BOOK, BookMetadata::class.java)
        if (extraBook == null) {
            finish()
            return
        }
        book = extraBook
        @Suppress("UNCHECKED_CAST")
        val annotations = (intent.serializableExtraCompat(EXTRA_ANNOTATIONS, ArrayList::class.java)
            as? ArrayList<Annotation>) ?: arrayListOf()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = book.getDisplayTitle()

        val titleView = findViewById<TextView>(R.id.book_detail_title)
        val deletedChipView = findViewById<TextView>(R.id.book_detail_deleted_chip)
        val authorView = findViewById<TextView>(R.id.book_detail_author)
        val countView = findViewById<TextView>(R.id.book_detail_count)
        val selectionToolbar = findViewById<View>(R.id.selection_toolbar)
        selectAllCheckbox = findViewById(R.id.select_all_checkbox)
        selectionCountText = findViewById(R.id.selection_count_text)
        exportMenuButton = findViewById(R.id.btn_export_menu_selected)
        shareSelectedButton = findViewById(R.id.btn_share_selected)
        saveSelectedButton = findViewById(R.id.btn_save_selected)
        val recyclerView = findViewById<RecyclerView>(R.id.annotations_recycler_view)
        val emptyText = findViewById<TextView>(R.id.annotations_empty_text)

        titleView.text = book.getDisplayTitle()
        deletedChipView.visibility = if (book.isDeleted) View.VISIBLE else View.GONE
        authorView.text = book.getDisplayAuthors()
        countView.text = "${annotations.size} annotation${if (annotations.size != 1) "s" else ""}"

        recyclerView.layoutManager = LinearLayoutManager(this)
        val sortedAnnotations = annotations.sortedWith(
            compareBy({ it.pageNumber ?: Int.MAX_VALUE }, { it.locationBeginInt ?: Int.MAX_VALUE })
        )

        if (sortedAnnotations.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            selectionToolbar.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            selectionToolbar.visibility = View.VISIBLE

            annotationAdapter = AnnotationAdapter(sortedAnnotations) { updateSelectionUi() }
            recyclerView.adapter = annotationAdapter

            selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) annotationAdapter.selectAll() else annotationAdapter.clearSelection()
            }

            exportMenuButton.setOnClickListener {
                val selected = annotationAdapter.getSelectedAnnotations()
                if (selected.isNotEmpty()) {
                    AnnotationExporter.showExportMenu(it, this, lifecycleScope, book, selected)
                }
            }

            shareSelectedButton.setOnClickListener {
                val selected = annotationAdapter.getSelectedAnnotations()
                if (selected.isNotEmpty()) {
                    AnnotationExporter.shareInDefaultFormat(this, lifecycleScope, book, selected)
                }
            }

            saveSelectedButton.setOnClickListener {
                val selected = annotationAdapter.getSelectedAnnotations()
                if (selected.isNotEmpty()) {
                    AnnotationExporter.saveInDefaultFormat(this, lifecycleScope, book, selected)
                }
            }

            updateSelectionUi()
        }
    }

    private fun updateSelectionUi() {
        val selectedCount = annotationAdapter.selectedCount()

        selectAllCheckbox.setOnCheckedChangeListener(null)
        selectAllCheckbox.isChecked = annotationAdapter.isAllSelected()
        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) annotationAdapter.selectAll() else annotationAdapter.clearSelection()
        }

        selectionCountText.text = if (selectedCount > 0) "$selectedCount selected" else "Nothing selected"

        val hasSelection = selectedCount > 0
        exportMenuButton.isEnabled = hasSelection
        shareSelectedButton.isEnabled = hasSelection
        saveSelectedButton.isEnabled = hasSelection
        exportMenuButton.alpha = if (hasSelection) 1.0f else 0.4f
        shareSelectedButton.alpha = if (hasSelection) 1.0f else 0.4f
        saveSelectedButton.alpha = if (hasSelection) 1.0f else 0.4f
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
