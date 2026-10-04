package me.utsob.booxrichannotation

data class BookMetadata(
    val uuid: String,
    val title: String? = null,
    val name: String? = null,
    val authors: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val isbn: String? = null,
    val description: String? = null,
    val location: String? = null,
    val idString: String? = null,
    val lastAccess: Long? = null,
    val totalPages: Int? = null,
    val allUuids: List<String> = listOf(uuid), // Track all UUIDs for this file
    val status: Int? = null
) : java.io.Serializable {
    // Onyx marks a Metadata row's underlying file as gone (moved/deleted from storage)
    // with status=1, while the row and any annotations tied to it remain in the DB.
    val isDeleted: Boolean
        get() = status == 1

    fun getDisplayTitle(): String {
        return when {
            !title.isNullOrBlank() && title != "NULL" -> title
            !name.isNullOrBlank() && name != "NULL" -> {
                // Clean extension from name
                var cleaned = name
                val extensions = listOf(".epub", ".pdf", ".djvu", ".cbz", ".cbr", ".mobi", ".azw3", ".azw", ".fbz")
                extensions.forEach { ext ->
                    if (cleaned?.endsWith(ext, ignoreCase = true) == true) {
                        cleaned = cleaned?.substring(0, cleaned.length - ext.length)
                    }
                }
                cleaned ?: "Unknown Book"
            }
            else -> "Unknown Book ($uuid)"
        }
    }
    
    fun getDisplayAuthors(): String {
        return if (!authors.isNullOrBlank() && authors != "NULL") authors else "Unknown Author"
    }
}
