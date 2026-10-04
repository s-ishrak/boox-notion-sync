package me.utsob.booxrichannotation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Flattened, collapsible "all annotations grouped by book" list. Selection is tracked by
 * Annotation value (data class structural equality) rather than list position, since rows
 * shift around whenever a book group is collapsed/expanded.
 */
class AnnotationsByBookAdapter(
    private val onSelectionChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val book: BookMetadata, val annotations: List<Annotation>, val isCollapsed: Boolean) : Row()
        data class Item(val annotation: Annotation) : Row()
    }

    private var booksWithAnnotations: List<BookWithAnnotations> = emptyList()
    private val collapsedBookKeys = mutableSetOf<String>()
    private val selectedAnnotations = mutableSetOf<Annotation>()
    private var rows: List<Row> = emptyList()

    private fun bookKey(book: BookMetadata) = book.idString ?: book.uuid

    fun submitList(newBooksWithAnnotations: List<BookWithAnnotations>) {
        booksWithAnnotations = newBooksWithAnnotations
        // Drop selections for annotations no longer present (e.g. after a search filter change)
        val stillPresent = newBooksWithAnnotations.flatMap { it.annotations }.toSet()
        selectedAnnotations.retainAll(stillPresent)
        rebuildRows()
        onSelectionChanged?.invoke()
    }

    private fun rebuildRows() {
        val result = mutableListOf<Row>()
        for (bw in booksWithAnnotations) {
            val collapsed = bookKey(bw.book) in collapsedBookKeys
            result.add(Row.Header(bw.book, bw.annotations, collapsed))
            if (!collapsed) {
                bw.annotations
                    .sortedWith(compareBy({ it.pageNumber ?: Int.MAX_VALUE }, { it.locationBeginInt ?: Int.MAX_VALUE }))
                    .forEach { result.add(Row.Item(it)) }
            }
        }
        rows = result
        notifyDataSetChanged()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Item -> TYPE_ITEM
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.group_header_checkbox)
        val title: TextView = view.findViewById(R.id.group_header_title)
        val deletedChip: TextView = view.findViewById(R.id.group_header_deleted_chip)
        val author: TextView = view.findViewById(R.id.group_header_author)
        val count: TextView = view.findViewById(R.id.group_header_count)
        val toggle: ImageView = view.findViewById(R.id.group_header_toggle)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.annotation_checkbox)
        val row = AnnotationRowViews(view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_annotation_group_header, parent, false))
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_annotation, parent, false)
            // Indent child annotation cards so they read as nested under their book's
            // header rather than as independent, equally-weighted cards.
            val density = view.resources.displayMetrics.density
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.marginStart = (28 * density).toInt()
                params.topMargin = (2 * density).toInt()
                view.layoutParams = params
            }
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> {
                holder as HeaderViewHolder
                holder.title.text = row.book.getDisplayTitle()
                holder.deletedChip.visibility = if (row.book.isDeleted) View.VISIBLE else View.GONE
                holder.author.text = row.book.getDisplayAuthors()
                holder.count.text = "${row.annotations.size} annotation${if (row.annotations.size != 1) "s" else ""}"
                holder.toggle.rotation = if (row.isCollapsed) -90f else 0f

                val toggleCollapse = View.OnClickListener {
                    val key = bookKey(row.book)
                    if (!collapsedBookKeys.add(key)) collapsedBookKeys.remove(key)
                    rebuildRows()
                }
                holder.itemView.setOnClickListener(toggleCollapse)
                holder.toggle.setOnClickListener(toggleCollapse)

                holder.checkbox.setOnCheckedChangeListener(null)
                holder.checkbox.isChecked = row.annotations.isNotEmpty() && row.annotations.all { it in selectedAnnotations }
                holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedAnnotations.addAll(row.annotations) else selectedAnnotations.removeAll(row.annotations.toSet())
                    notifyDataSetChanged()
                    onSelectionChanged?.invoke()
                }
            }
            is Row.Item -> {
                holder as ItemViewHolder
                val annotation = row.annotation
                holder.checkbox.setOnCheckedChangeListener(null)
                holder.checkbox.isChecked = annotation in selectedAnnotations
                holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedAnnotations.add(annotation) else selectedAnnotations.remove(annotation)
                    notifyDataSetChanged()
                    onSelectionChanged?.invoke()
                }
                bindAnnotationRow(holder.row, annotation)
            }
        }
    }

    fun selectAll() {
        selectedAnnotations.clear()
        booksWithAnnotations.forEach { selectedAnnotations.addAll(it.annotations) }
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun clearSelection() {
        selectedAnnotations.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun isAllSelected(): Boolean {
        val total = booksWithAnnotations.sumOf { it.annotations.size }
        return total > 0 && selectedAnnotations.size == total
    }

    fun selectedCount(): Int = selectedAnnotations.size

    /** Selected annotations grouped by their source book, in book display order. */
    fun getSelectedByBook(): List<Pair<BookMetadata, List<Annotation>>> {
        return booksWithAnnotations.mapNotNull { bw ->
            val selected = bw.annotations.filter { it in selectedAnnotations }
            if (selected.isEmpty()) null else bw.book to selected
        }
    }

    override fun getItemCount() = rows.size
}
