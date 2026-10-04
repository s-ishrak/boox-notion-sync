package me.utsob.booxrichannotation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView

class AnnotationAdapter(
    private val annotations: List<Annotation>,
    private val onSelectionChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<AnnotationAdapter.AnnotationViewHolder>() {

    private val selectedPositions = mutableSetOf<Int>()

    class AnnotationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.annotation_checkbox)
        val row = AnnotationRowViews(view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnotationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_annotation, parent, false)
        return AnnotationViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnnotationViewHolder, position: Int) {
        val annotation = annotations[position]

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = position in selectedPositions
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedPositions.add(position) else selectedPositions.remove(position)
            onSelectionChanged?.invoke()
        }

        bindAnnotationRow(holder.row, annotation)
    }

    fun selectAll() {
        selectedPositions.clear()
        selectedPositions.addAll(annotations.indices)
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun clearSelection() {
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    fun isAllSelected(): Boolean = annotations.isNotEmpty() && selectedPositions.size == annotations.size

    fun selectedCount(): Int = selectedPositions.size

    fun getSelectedAnnotations(): List<Annotation> = annotations.filterIndexed { index, _ -> index in selectedPositions }

    override fun getItemCount() = annotations.size
}
