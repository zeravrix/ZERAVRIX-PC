package zeravrix.reader

enum class PageMode { AUTO, SINGLE, DOUBLE }
enum class ReadingDirection { LTR, RTL }

data class ReaderSettings(
    val pageMode: PageMode = PageMode.AUTO,
    val direction: ReadingDirection = ReadingDirection.LTR,
    val zoom: Float = 1.0f,
    val fitToPage: Boolean = true,
    val fullscreen: Boolean = false
)

data class ReaderPosition(
    val chapterId: String,
    val pageIndex: Int,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val zoom: Float = 1f
)

data class ReaderPage(
    val index: Int,
    val sourceUrl: String,
    val localPath: String? = null,
    val width: Int = 0,
    val height: Int = 0
)
