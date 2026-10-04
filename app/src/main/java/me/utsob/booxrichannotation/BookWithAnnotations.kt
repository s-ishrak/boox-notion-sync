package me.utsob.booxrichannotation

data class BookWithAnnotations(
    val book: BookMetadata,
    val annotations: List<Annotation>,
    val annotationCount: Int = annotations.size
)
