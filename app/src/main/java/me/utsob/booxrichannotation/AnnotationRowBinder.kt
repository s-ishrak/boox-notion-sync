package me.utsob.booxrichannotation

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Views for one annotation card (item_annotation.xml), minus the selection checkbox. */
class AnnotationRowViews(view: View) {
    val styleChip: TextView = view.findViewById(R.id.annotation_style_chip)
    val page: TextView = view.findViewById(R.id.annotation_page)
    val chapter: TextView = view.findViewById(R.id.annotation_chapter)
    val timestamp: TextView = view.findViewById(R.id.annotation_timestamp)
    val quote: TextView = view.findViewById(R.id.annotation_quote)
    val noteDivider: View = view.findViewById(R.id.annotation_note_divider)
    val note: TextView = view.findViewById(R.id.annotation_note)
}

private val timestampFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

/** Binds one annotation's style/page/chapter/timestamp/quote/note onto an item_annotation.xml row. */
fun bindAnnotationRow(views: AnnotationRowViews, annotation: Annotation) {
    views.styleChip.text = annotation.styleName().replaceFirstChar { it.uppercase() }
    applyChipColor(views.styleChip, annotation.colorHex())

    views.page.text = annotation.pageNumber?.let { "Page $it" } ?: "Page unknown"
    views.chapter.text = annotation.displayChapter() ?: ""
    views.timestamp.text = annotation.createdAt?.let { timestampFormat.format(Date(it)) } ?: ""
    views.quote.text = annotation.quote?.takeIf { it.isNotBlank() } ?: "(No highlighted text)"

    val note = annotation.displayNote()
    if (note != null) {
        views.note.text = "Note: $note"
        views.note.visibility = View.VISIBLE
        views.noteDivider.visibility = View.VISIBLE
    } else {
        views.note.visibility = View.GONE
        views.noteDivider.visibility = View.GONE
    }
}

/** Fills the style chip with the annotation's actual highlight color, picking readable text contrast. */
fun applyChipColor(chip: TextView, colorHex: String?) {
    val colorInt = colorHex?.let {
        try {
            Color.parseColor(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    } ?: Color.WHITE

    (chip.background.mutate() as? GradientDrawable)?.setColor(colorInt)

    val luminance = 0.299 * Color.red(colorInt) + 0.587 * Color.green(colorInt) + 0.114 * Color.blue(colorInt)
    chip.setTextColor(if (luminance > 150) Color.BLACK else Color.WHITE)
}
