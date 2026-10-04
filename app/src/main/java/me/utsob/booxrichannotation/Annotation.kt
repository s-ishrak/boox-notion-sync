package me.utsob.booxrichannotation

data class Annotation(
    val rowNumber: Int,
    val quote: String? = null,
    val locationBegin: String? = null,
    val locationEnd: String? = null,
    val locationBeginInt: Int? = null,
    val locationEndInt: Int? = null,
    val note: String? = null,
    val linkNote: String? = null,
    val application: String? = null,
    val position: String? = null,
    val pageNumber: Int? = null,
    val rectangles: String? = null,
    val color: Int? = null,
    val shape: Int? = null,
    val chapter: String? = null,
    val uuid: String? = null,
    val objId: String? = null,
    val status: Int? = null,
    val pageXpath: String? = null,
    val startXpath: String? = null,
    val endXpath: String? = null,
    val customAttr: String? = null,
    val id: Int? = null,
    val guid: String? = null,
    val idString: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
) : java.io.Serializable {

    /** Lowercase style name derived from [shape], matching export output. */
    fun styleName(): String = when (shape) {
        0 -> "highlight"
        1 -> "underline"
        2 -> "dashed"
        3 -> "wavy"
        4 -> "redact"
        5 -> "mute"
        else -> "unknown"
    }

    /** [color] as a "#rrggbb" hex string, or null if unavailable. */
    fun colorHex(): String? = color?.let { colorInt ->
        try {
            val colorLong = colorInt.toLong() and 0xFFFFFFFFL
            val r = (colorLong shr 16) and 0xFF
            val g = (colorLong shr 8) and 0xFF
            val b = colorLong and 0xFF
            "#%02x%02x%02x".format(r, g, b)
        } catch (e: Exception) {
            null
        }
    }

    /** [note], or null if blank/absent/the literal string "NULL". */
    fun displayNote(): String? = note?.takeIf { it.isNotBlank() && it != "NULL" }

    /** [chapter], or null if blank/absent/the literal string "NULL". */
    fun displayChapter(): String? = chapter?.takeIf { it.isNotBlank() && it != "NULL" }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "rowNumber" to rowNumber,
            "quote" to quote,
            "locationBegin" to locationBegin,
            "locationEnd" to locationEnd,
            "locationBeginInt" to locationBeginInt,
            "locationEndInt" to locationEndInt,
            "note" to note,
            "linkNote" to linkNote,
            "application" to application,
            "position" to position,
            "pageNumber" to pageNumber,
            "rectangles" to rectangles,
            "color" to color,
            "shape" to shape,
            "chapter" to chapter,
            "uuid" to uuid,
            "objId" to objId,
            "status" to status,
            "pageXpath" to pageXpath,
            "startXpath" to startXpath,
            "endXpath" to endXpath,
            "customAttr" to customAttr,
            "id" to id,
            "guid" to guid,
            "idString" to idString,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        ).filterValues { it != null }
    }
}
