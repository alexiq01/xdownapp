package com.xdown.app.data.model

data class MediaItem(
    val id: String,
    val url: String,
    val type: MediaType,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Int? = null,
    val qualities: List<MediaQuality> = emptyList(),
    val thumbnailUrl: String? = null
) {
    val displayType: String
        get() = when (type) {
            MediaType.PHOTO -> "Image"
            MediaType.VIDEO -> "Video"
            MediaType.GIF -> "GIF"
        }
}

enum class MediaType {
    PHOTO, VIDEO, GIF
}

data class MediaQuality(
    val quality: String,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val contentType: String? = null
) : Comparable<MediaQuality> {
    val fileSize: String
        get() {
            if (bitrate == null) return "Unknown"
            return when {
                bitrate >= 5_000_000 -> "> 5 MB"
                bitrate >= 2_000_000 -> "~2-5 MB"
                bitrate >= 1_000_000 -> "~1-2 MB"
                bitrate >= 500_000 -> "~0.5-1 MB"
                else -> "< 0.5 MB"
            }
        }

    val dimensionLabel: String
        get() = if (width != null && height != null) "${width}x${height}" else quality

    override fun compareTo(other: MediaQuality): Int {
        return (bitrate ?: 0) - (other.bitrate ?: 0)
    }
}

data class UserProfile(
    val username: String,
    val name: String,
    val profileImageUrl: String? = null,
    val mediaItems: List<MediaItem> = emptyList()
)

enum class DownloadState {
    IDLE, DOWNLOADING, PAUSED, COMPLETED, ERROR
}

data class DownloadProgress(
    val mediaId: String,
    val progress: Float = 0f,
    val state: DownloadState = DownloadState.IDLE,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val speed: Long = 0,
    val error: String? = null
)
