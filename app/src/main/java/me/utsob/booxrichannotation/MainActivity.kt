package me.utsob.booxrichannotation

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var searchView: SearchView
    private lateinit var toolbar: MaterialToolbar

    private lateinit var tabBooks: TextView
    private lateinit var tabAnnotations: TextView
    private lateinit var annotationsRecyclerView: RecyclerView
    private lateinit var annotationsByBookAdapter: AnnotationsByBookAdapter
    private lateinit var annotationsSelectionToolbar: View
    private lateinit var annotationsSelectionToolbarDivider: View
    private lateinit var annotationsSelectAllCheckbox: CheckBox
    private lateinit var annotationsSelectionCountText: TextView
    private lateinit var btnAnnotationsExportMenu: ImageButton
    private lateinit var btnAnnotationsShareSelected: ImageButton
    private lateinit var btnAnnotationsSaveSelected: ImageButton

    private enum class Tab { BOOKS, ANNOTATIONS }
    private var currentTab = Tab.BOOKS

    private var allBooksWithAnnotations: List<BookWithAnnotations> = emptyList()
    private var filteredBooksWithAnnotations: List<BookWithAnnotations> = emptyList()

    // Update checking
    private var updateMenuItem: MenuItem? = null
    private var updateInfo: UpdateChecker.UpdateInfo? = null

    // Sorting preference
    private enum class SortMode {
        LAST_READ, ALPHABETICAL
    }
    private var currentSortMode = SortMode.LAST_READ

    // Filtering: whether to include books whose underlying file was deleted
    // (Metadata.status == 1) but which still have annotations lingering in the DB
    private var includeDeletedBooks = false

    companion object {
        private const val PREFS_NAME = "BooxRichAnnotationPrefs"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_INCLUDE_DELETED = "include_deleted_books"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved sort preference
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentSortMode = SortMode.valueOf(
            prefs.getString(KEY_SORT_MODE, SortMode.LAST_READ.name) ?: SortMode.LAST_READ.name
        )
        includeDeletedBooks = prefs.getBoolean(KEY_INCLUDE_DELETED, false)

        // Keep the hourly Notion sync scheduled (no-op until set up)
        SyncWorker.schedule(this)

        // Disable animations for e-ink
        window.setWindowAnimations(0)
        overridePendingTransition(0, 0)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.books_recycler_view)
        progressBar = findViewById(R.id.progress_bar)
        emptyText = findViewById(R.id.empty_text)
        searchView = findViewById(R.id.search_view)

        tabBooks = findViewById(R.id.tab_books)
        tabAnnotations = findViewById(R.id.tab_annotations)
        annotationsRecyclerView = findViewById(R.id.annotations_recycler_view)
        annotationsSelectionToolbar = findViewById(R.id.annotations_selection_toolbar)
        annotationsSelectionToolbarDivider = findViewById(R.id.annotations_selection_toolbar_divider)
        annotationsSelectAllCheckbox = findViewById(R.id.annotations_select_all_checkbox)
        annotationsSelectionCountText = findViewById(R.id.annotations_selection_count_text)
        btnAnnotationsExportMenu = findViewById(R.id.btn_annotations_export_menu_selected)
        btnAnnotationsShareSelected = findViewById(R.id.btn_annotations_share_selected)
        btnAnnotationsSaveSelected = findViewById(R.id.btn_annotations_save_selected)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        annotationsRecyclerView.layoutManager = LinearLayoutManager(this)
        annotationsByBookAdapter = AnnotationsByBookAdapter { updateAnnotationsSelectionUi() }
        annotationsRecyclerView.adapter = annotationsByBookAdapter

        setupTabs()
        setupAnnotationsSelectionToolbar()
        setupSearch()
        loadBooks()
        checkForUpdates()
    }

    private fun setupTabs() {
        // Plain views instead of TabLayout: TabLayout's HorizontalScrollView base enforces
        // its own minimum-height logic (getDefaultHeight()) that we can't fully override,
        // leaving unwanted empty space below the tabs. A plain LinearLayout + two TextViews
        // gives full, predictable control over sizing - selection is shown by inverting the
        // pill (black-on-white vs white-on-black), no color, no animation.
        tabBooks.setOnClickListener { selectTab(Tab.BOOKS) }
        tabAnnotations.setOnClickListener { selectTab(Tab.ANNOTATIONS) }
        selectTab(Tab.BOOKS)
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        styleTabPill(tabBooks, selected = tab == Tab.BOOKS)
        styleTabPill(tabAnnotations, selected = tab == Tab.ANNOTATIONS)
        updateContentVisibility()
    }

    private fun styleTabPill(label: TextView, selected: Boolean) {
        if (selected) {
            label.setBackgroundResource(R.drawable.bg_pill_selected)
            label.setTextColor(android.graphics.Color.WHITE)
        } else {
            label.setBackgroundResource(R.drawable.bg_pill_unselected)
            label.setTextColor(android.graphics.Color.BLACK)
        }
    }

    private fun setupAnnotationsSelectionToolbar() {
        annotationsSelectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) annotationsByBookAdapter.selectAll() else annotationsByBookAdapter.clearSelection()
        }

        btnAnnotationsExportMenu.setOnClickListener {
            val selected = annotationsByBookAdapter.getSelectedByBook()
            if (selected.isNotEmpty()) {
                AnnotationExporter.showMultiBookExportMenu(it, this, lifecycleScope, selected)
            }
        }

        btnAnnotationsShareSelected.setOnClickListener {
            val selected = annotationsByBookAdapter.getSelectedByBook()
            if (selected.isNotEmpty()) {
                AnnotationExporter.shareMultiBookInDefaultFormat(this, lifecycleScope, selected)
            }
        }

        btnAnnotationsSaveSelected.setOnClickListener {
            val selected = annotationsByBookAdapter.getSelectedByBook()
            if (selected.isNotEmpty()) {
                AnnotationExporter.saveMultiBookInDefaultFormat(this, lifecycleScope, selected)
            }
        }

        updateAnnotationsSelectionUi()
    }

    private fun updateAnnotationsSelectionUi() {
        val selectedCount = annotationsByBookAdapter.selectedCount()

        annotationsSelectAllCheckbox.setOnCheckedChangeListener(null)
        annotationsSelectAllCheckbox.isChecked = annotationsByBookAdapter.isAllSelected()
        annotationsSelectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) annotationsByBookAdapter.selectAll() else annotationsByBookAdapter.clearSelection()
        }

        annotationsSelectionCountText.text = if (selectedCount > 0) "$selectedCount selected" else "Nothing selected"

        val hasSelection = selectedCount > 0
        btnAnnotationsExportMenu.isEnabled = hasSelection
        btnAnnotationsShareSelected.isEnabled = hasSelection
        btnAnnotationsSaveSelected.isEnabled = hasSelection
        btnAnnotationsExportMenu.alpha = if (hasSelection) 1.0f else 0.4f
        btnAnnotationsShareSelected.alpha = if (hasSelection) 1.0f else 0.4f
        btnAnnotationsSaveSelected.alpha = if (hasSelection) 1.0f else 0.4f
    }

    private fun checkForUpdates() {
        if (!BuildConfig.ENABLE_UPDATE_CHECK) return
        lifecycleScope.launch {
            try {
                val updateChecker = UpdateChecker(this@MainActivity)
                val info = updateChecker.checkForUpdate()

                if (info != null && info.updateAvailable) {
                    updateInfo = info
                    updateMenuItem?.isVisible = true
                    Log.d("MainActivity", "Update available: ${info.latestVersion}")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking for updates", e)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        updateMenuItem = menu.findItem(R.id.action_update)
        // Update visibility if we already have update info
        updateInfo?.let {
            if (it.updateAvailable) {
                updateMenuItem?.isVisible = true
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_update -> {
                updateInfo?.let { info ->
                    showUpdateDialog(info)
                }
                true
            }
            R.id.action_sort -> {
                showSortMenu(item)
                true
            }
            R.id.action_filter -> {
                showFilterMenu(item)
                true
            }
            R.id.action_refresh -> {
                loadBooks()
                true
            }
            R.id.action_notion_sync -> {
                startActivity(Intent(this, NotionSettingsActivity::class.java))
                true
            }
            R.id.action_preferences -> {
                startActivity(Intent(this, PreferencesActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("A new version (${info.latestVersion}) is available.\n\nYour current version: ${info.currentVersion}\n\nWould you like to download it?")
            .setPositiveButton("Download") { _, _ ->
                // Open GitHub releases page
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .create()

        // Apply e-ink styling
        dialog.window?.setWindowAnimations(0)
        dialog.show()
    }

    private fun showSortMenu(menuItem: MenuItem) {
        val popup = android.widget.PopupMenu(this, toolbar.findViewById(R.id.action_sort))
        popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)

        // Check current sort mode
        when (currentSortMode) {
            SortMode.LAST_READ -> popup.menu.findItem(R.id.sort_last_read)?.isChecked = true
            SortMode.ALPHABETICAL -> popup.menu.findItem(R.id.sort_alphabetical)?.isChecked = true
        }

        popup.setOnMenuItemClickListener { item ->
            val newSortMode = when (item.itemId) {
                R.id.sort_last_read -> SortMode.LAST_READ
                R.id.sort_alphabetical -> SortMode.ALPHABETICAL
                else -> return@setOnMenuItemClickListener false
            }

            if (newSortMode != currentSortMode) {
                currentSortMode = newSortMode
                item.isChecked = true

                // Save preference
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SORT_MODE, currentSortMode.name)
                    .apply()

                // Resort and update display
                sortAndFilterBooks()
            }
            true
        }

        popup.show()
    }

    private fun showFilterMenu(menuItem: MenuItem) {
        val popup = android.widget.PopupMenu(this, toolbar.findViewById(R.id.action_filter))
        popup.menuInflater.inflate(R.menu.menu_filter, popup.menu)
        popup.menu.findItem(R.id.filter_include_deleted)?.isChecked = includeDeletedBooks

        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.filter_include_deleted) {
                includeDeletedBooks = !includeDeletedBooks

                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_INCLUDE_DELETED, includeDeletedBooks)
                    .apply()

                filterBooks(searchView.query.toString())
                true
            } else {
                false
            }
        }

        popup.show()
    }

    private fun setupSearch() {
        // Set hint text color to black for better contrast
        val searchText = searchView.findViewById<android.widget.EditText>(androidx.appcompat.R.id.search_src_text)
        searchText?.setHintTextColor(android.graphics.Color.parseColor("#666666"))
        searchText?.setTextColor(android.graphics.Color.BLACK)

        // Set icon tints to black
        val searchIcon = searchView.findViewById<android.widget.ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchIcon?.setColorFilter(android.graphics.Color.BLACK)

        val closeIcon = searchView.findViewById<android.widget.ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeIcon?.setColorFilter(android.graphics.Color.BLACK)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterBooks(newText ?: "")
                return true
            }
        })
    }

    private fun filterBooks(query: String) {
        val searchQuery = query.trim().lowercase()

        filteredBooksWithAnnotations = allBooksWithAnnotations.filter { bookWithAnnotations ->
            val matchesDeletedFilter = includeDeletedBooks || !bookWithAnnotations.book.isDeleted
            val matchesSearch = searchQuery.isEmpty() || run {
                val title = bookWithAnnotations.book.getDisplayTitle().lowercase()
                val author = bookWithAnnotations.book.getDisplayAuthors().lowercase()
                title.contains(searchQuery) || author.contains(searchQuery)
            }
            matchesDeletedFilter && matchesSearch
        }

        updateRecyclerView()
    }

    private fun sortAndFilterBooks() {
        android.util.Log.d("MainActivity", "sortAndFilterBooks: currentSortMode=$currentSortMode, allBooksWithAnnotations.size=${allBooksWithAnnotations.size}")

        // Sort all books first
        allBooksWithAnnotations = when (currentSortMode) {
            SortMode.LAST_READ -> {
                android.util.Log.d("MainActivity", "Sorting by LAST_READ")
                // Sort by lastAccess descending (most recent first), null values last
                allBooksWithAnnotations.sortedWith(
                    compareByDescending<BookWithAnnotations> { it.book.lastAccess != null }
                        .thenByDescending { it.book.lastAccess ?: 0 }
                )
            }
            SortMode.ALPHABETICAL -> {
                android.util.Log.d("MainActivity", "Sorting by ALPHABETICAL")
                // Sort alphabetically by display title
                allBooksWithAnnotations.sortedBy { it.book.getDisplayTitle().lowercase() }
            }
        }

        android.util.Log.d("MainActivity", "After sorting, first 3 books:")
        allBooksWithAnnotations.take(3).forEachIndexed { i, bookWithAnnotations ->
            android.util.Log.d("MainActivity", "  #$i: ${bookWithAnnotations.book.getDisplayTitle()}")
        }

        // Reapply current search filter
        filterBooks(searchView.query.toString())
    }

    private fun updateRecyclerView() {
        recyclerView.adapter = BookAdapter(filteredBooksWithAnnotations, lifecycleScope)
        annotationsByBookAdapter.submitList(filteredBooksWithAnnotations)
        updateContentVisibility()
    }

    private fun updateContentVisibility() {
        if (filteredBooksWithAnnotations.isEmpty()) {
            recyclerView.visibility = View.GONE
            annotationsRecyclerView.visibility = View.GONE
            annotationsSelectionToolbar.visibility = View.GONE
            annotationsSelectionToolbarDivider.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = if (searchView.query.isEmpty()) {
                "No books with annotations found"
            } else {
                "No books found matching \"${searchView.query}\""
            }
        } else {
            emptyText.visibility = View.GONE
            val showBooks = currentTab == Tab.BOOKS
            recyclerView.visibility = if (showBooks) View.VISIBLE else View.GONE
            annotationsRecyclerView.visibility = if (showBooks) View.GONE else View.VISIBLE
            annotationsSelectionToolbar.visibility = if (showBooks) View.GONE else View.VISIBLE
            annotationsSelectionToolbarDivider.visibility = if (showBooks) View.GONE else View.VISIBLE
        }
    }

    private fun loadBooks() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        annotationsRecyclerView.visibility = View.GONE
        emptyText.visibility = View.GONE

        lifecycleScope.launch {
            val (books, allAnnotations) = withContext(Dispatchers.IO) {
                val books = OnyxContentProvider.queryBookMetadata(this@MainActivity)
                val annotations = OnyxContentProvider.queryAllAnnotations(this@MainActivity)
                Pair(books, annotations)
            }

            // Group by idString and collect all UUIDs for each file
            val uniqueBooks = books.groupBy { it.idString }
                .map { (_, booksWithSameFile) ->
                    val firstBook = booksWithSameFile.first()
                    // Collect all UUIDs for this file and keep the most recent lastAccess time
                    val allUuids = booksWithSameFile.map { it.uuid }
                    val latestAccessTime = booksWithSameFile.mapNotNull { it.lastAccess }.maxOrNull()
                    // Only treat the merged entry as deleted if every version of this file is marked deleted
                    val mergedStatus = if (booksWithSameFile.all { it.status == 1 }) 1 else firstBook.status
                    firstBook.copy(allUuids = allUuids, lastAccess = latestAccessTime, status = mergedStatus)
                }

            // Map annotations to books by idString
            val annotationsByIdString = allAnnotations.groupBy { it.idString }

            android.util.Log.d("MainActivity", "Total unique books loaded: ${uniqueBooks.size}")
            android.util.Log.d("MainActivity", "Total annotations loaded: ${allAnnotations.size}")
            android.util.Log.d("MainActivity", "Unique annotation idStrings: ${annotationsByIdString.keys.size}")

            // Create BookWithAnnotations list and filter to only books with annotations
            allBooksWithAnnotations = uniqueBooks.mapNotNull { book ->
                // Find annotations that match any of this book's UUIDs
                val bookAnnotations = book.allUuids.flatMap { uuid ->
                    annotationsByIdString[uuid] ?: emptyList()
                }.distinctBy { "${it.quote?.take(100)}_${it.locationBeginInt}_${it.locationEndInt}" }

                // Only include books with at least 1 annotation
                if (bookAnnotations.isNotEmpty()) {
                    BookWithAnnotations(book, bookAnnotations)
                } else {
                    // Log books that have no annotations matched
                    if (book.getDisplayTitle().contains("godel", ignoreCase = true) ||
                        book.getDisplayTitle().contains("escher", ignoreCase = true)) {
                        android.util.Log.w("MainActivity", "Book '${book.getDisplayTitle()}' has 0 annotations matched. UUIDs: ${book.allUuids}")
                    }
                    null
                }
            }

            android.util.Log.d("MainActivity", "Loaded ${allBooksWithAnnotations.size} books with annotations (filtered out books with 0 annotations)")
            android.util.Log.d("MainActivity", "Total annotations across all books: ${allBooksWithAnnotations.sumOf { it.annotationCount }}")

            // Apply sorting based on current mode
            sortAndFilterBooks()

            progressBar.visibility = View.GONE
        }
    }
}
