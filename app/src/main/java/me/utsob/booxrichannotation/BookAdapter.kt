package me.utsob.booxrichannotation

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope

class BookAdapter(
    private val booksWithAnnotations: List<BookWithAnnotations>,
    private val lifecycleScope: CoroutineScope
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    class BookViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.book_title)
        val deletedChip: TextView = view.findViewById(R.id.book_deleted_chip)
        val authorTextView: TextView = view.findViewById(R.id.book_author)
        val annotationCountTextView: TextView = view.findViewById(R.id.annotation_count)
        val menuButton: ImageButton = view.findViewById(R.id.btn_menu)
        val shareButton: ImageButton = view.findViewById(R.id.btn_share)
        val saveButton: ImageButton = view.findViewById(R.id.btn_save)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val bookWithAnnotations = booksWithAnnotations[position]
        val book = bookWithAnnotations.book
        val annotations = bookWithAnnotations.annotations

        holder.titleTextView.text = book.getDisplayTitle()
        holder.deletedChip.visibility = if (book.isDeleted) View.VISIBLE else View.GONE
        holder.authorTextView.text = book.getDisplayAuthors()

        // Show annotation count
        val count = annotations.size
        holder.annotationCountTextView.text = if (count > 0) {
            "$count annotation${if (count != 1) "s" else ""}"
        } else {
            "No annotations"
        }

        // Tap the row to view annotations in detail
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, BookDetailActivity::class.java).apply {
                putExtra(BookDetailActivity.EXTRA_BOOK, book)
                putExtra(BookDetailActivity.EXTRA_ANNOTATIONS, ArrayList(annotations))
            }
            holder.itemView.context.startActivity(intent)
        }

        // Disable buttons if no annotations
        val hasAnnotations = count > 0
        holder.menuButton.isEnabled = hasAnnotations
        holder.shareButton.isEnabled = hasAnnotations
        holder.saveButton.isEnabled = hasAnnotations
        holder.menuButton.alpha = if (hasAnnotations) 1.0f else 0.4f
        holder.shareButton.alpha = if (hasAnnotations) 1.0f else 0.4f
        holder.saveButton.alpha = if (hasAnnotations) 1.0f else 0.4f

        // Menu button - shows export options
        holder.menuButton.setOnClickListener {
            if (hasAnnotations) {
                AnnotationExporter.showExportMenu(it, holder.itemView.context, lifecycleScope, book, annotations)
            }
        }

        // Share button - shares in default format from preferences
        holder.shareButton.setOnClickListener {
            if (hasAnnotations) {
                AnnotationExporter.shareInDefaultFormat(holder.itemView.context, lifecycleScope, book, annotations)
            }
        }

        // Save button - saves in default format from preferences
        holder.saveButton.setOnClickListener {
            if (hasAnnotations) {
                AnnotationExporter.saveInDefaultFormat(holder.itemView.context, lifecycleScope, book, annotations)
            }
        }
    }

    override fun getItemCount() = booksWithAnnotations.size
}
