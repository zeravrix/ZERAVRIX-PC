package zeravrix.core.downloads

data class DownloadItem(
    val id: String,
    val url: String,
    val destination: String,
    val referer: String? = null
)

data class DownloadPlan(
    val items: List<DownloadItem>,
    val maxConcurrency: Int = 4
)
