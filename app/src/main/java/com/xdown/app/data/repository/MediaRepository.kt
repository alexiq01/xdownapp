package com.xdown.app.data.repository

import com.xdown.app.data.model.*
import com.xdown.app.data.remote.DownloadService
import com.xdown.app.data.remote.XScraper
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val scraper: XScraper,
    private val downloadService: DownloadService
) {
    suspend fun fetchMedia(input: String): Result<List<MediaItem>> {
        return try {
            val cleanedInput = cleanInput(input)

            val tweetResponse = if (isTweetUrl(cleanedInput)) {
                scraper.scrapeTweet(cleanedInput)
            } else {
                val username = extractUsername(cleanedInput)
                val tweets = scraper.scrapeProfile(username)
                tweets.firstOrNull()
            }

            if (tweetResponse == null) {
                return Result.failure(Exception("Could not fetch media. Please check the URL or username."))
            }

            val mediaItems = parseMediaItems(tweetResponse)
            if (mediaItems.isEmpty()) {
                Result.failure(Exception("No media found in this post."))
            } else {
                Result.success(mediaItems)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMedia(
        mediaItem: MediaItem,
        quality: MediaQuality?,
        saveDir: java.io.File,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<java.io.File> {
        return try {
            val url = quality?.url ?: mediaItem.url
            val extension = getFileExtension(url, mediaItem.type)
            val filename = generateFilename(mediaItem, extension)
            val outputFile = java.io.File(saveDir, filename)

            if (outputFile.exists()) {
                outputFile.delete()
            }

            val result = downloadService.downloadFile(url, outputFile) { progress, downloaded, total ->
                onProgress(progress, downloaded, total)
            }

            result.map { outputFile }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAvailableQualities(mediaItem: MediaItem): List<MediaQuality> {
        return mediaItem.qualities.sortedDescending()
    }

    private fun cleanInput(input: String): String {
        return input.trim()
            .replace("www.", "")
            .replace("https://", "http://")
            .removeSuffix("/")
    }

    private fun isTweetUrl(input: String): Boolean {
        val patterns = listOf(
            """https?://x\.com/\w+/status/\d+.*""",
            """https?://twitter\.com/\w+/status/\d+.*""",
            """https?://mobile\.x\.com/\w+/status/\d+.*""",
            """https?://mobile\.twitter\.com/\w+/status/\d+.*"""
        )
        return patterns.any { input.matches(Regex(it)) }
    }

    private fun extractUsername(input: String): String {
        val cleaned = input.removePrefix("@").trim()
        val usernamePattern = """^(\w{1,15})$""".toRegex()
        val match = usernamePattern.find(cleaned)
        return match?.groupValues?.get(1) ?: cleaned
    }

    private fun parseMediaItems(response: TweetResponse): List<MediaItem> {
        val tweetResult = response.data?.tweetResult?.result ?: return emptyList()
        val mediaEntities = tweetResult.mediaDetails
            ?: tweetResult.legacy?.extendedEntities?.media
            ?: tweetResult.legacy?.entities?.media
            ?: return emptyList()

        return mediaEntities.mapNotNull { entity ->
            val id = entity.idStr ?: return@mapNotNull null
            val type = parseMediaType(entity.type)

            when (type) {
                MediaType.PHOTO -> parsePhotoMedia(entity, id)
                MediaType.VIDEO, MediaType.GIF -> parseVideoMedia(entity, id, type)
            }
        }
    }

    private fun parsePhotoMedia(entity: MediaEntity, id: String): MediaItem? {
        val url = entity.mediaUrlHttps ?: return null
        val baseUrl = cleanUrl(url)

        val qualities = buildPhotoQualities(baseUrl, entity.originalInfo)
        val bestUrl = "$baseUrl?format=jpg&name=4096x4096"

        return MediaItem(
            id = id,
            url = bestUrl,
            type = MediaType.PHOTO,
            width = entity.originalInfo?.width,
            height = entity.originalInfo?.height,
            qualities = qualities,
            thumbnailUrl = "$baseUrl?format=jpg&name=small"
        )
    }

    private fun parseVideoMedia(entity: MediaEntity, id: String, type: MediaType): MediaItem? {
        val variants = entity.videoInfo?.variants ?: return null
        val mp4Variants = variants
            .filter { it.contentType?.contains("video/mp4") == true && it.url != null }
            .sortedByDescending { it.bitrate ?: 0 }

        if (mp4Variants.isEmpty()) return null

        val bestVariant = mp4Variants.first()
        val qualities = mp4Variants.mapNotNull { variant ->
            val variantUrl = variant.url ?: return@mapNotNull null
            MediaQuality(
                quality = getQualityLabel(variant.height),
                url = cleanUrl(variantUrl),
                width = variant.width,
                height = variant.height,
                bitrate = variant.bitrate,
                contentType = variant.contentType
            )
        }.distinctBy { it.quality }

        return MediaItem(
            id = id,
            url = cleanUrl(bestVariant.url ?: ""),
            type = type,
            width = bestVariant.width,
            height = bestVariant.height,
            duration = entity.videoInfo.durationMillis,
            qualities = qualities,
            thumbnailUrl = entity.mediaUrlHttps
        )
    }

    private fun cleanUrl(url: String): String {
        return try {
            val parsed = URL(url)
            "${parsed.protocol}://${parsed.host}${parsed.path}"
        } catch (e: Exception) {
            url.split("?").first()
        }
    }

    private fun buildPhotoQualities(baseUrl: String, originalInfo: OriginalInfo?): List<MediaQuality> {
        val qualities = mutableListOf<MediaQuality>()

        qualities.add(MediaQuality(
            quality = "Original",
            url = "$baseUrl?format=jpg&name=4096x4096",
            width = originalInfo?.width,
            height = originalInfo?.height,
            contentType = "image/jpeg"
        ))

        if (originalInfo?.large != null) {
            qualities.add(MediaQuality(
                quality = "Large",
                url = "$baseUrl?format=jpg&name=large",
                width = originalInfo.large.w,
                height = originalInfo.large.h,
                contentType = "image/jpeg"
            ))
        }

        if (originalInfo?.medium != null) {
            qualities.add(MediaQuality(
                quality = "Medium",
                url = "$baseUrl?format=jpg&name=medium",
                width = originalInfo.medium.w,
                height = originalInfo.medium.h,
                contentType = "image/jpeg"
            ))
        }

        if (originalInfo?.small != null) {
            qualities.add(MediaQuality(
                quality = "Small",
                url = "$baseUrl?format=jpg&name=small",
                width = originalInfo.small.w,
                height = originalInfo.small.h,
                contentType = "image/jpeg"
            ))
        }

        return qualities
    }

    private fun parseMediaType(type: String?): MediaType {
        return when (type) {
            "photo" -> MediaType.PHOTO
            "animated_gif" -> MediaType.GIF
            "video" -> MediaType.VIDEO
            else -> MediaType.PHOTO
        }
    }

    private fun getQualityLabel(height: Int?): String {
        return when {
            height == null -> "Unknown"
            height >= 2160 -> "2160p"
            height >= 1440 -> "1440p"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            height >= 480 -> "480p"
            height >= 360 -> "360p"
            else -> "${height}p"
        }
    }

    private fun getFileExtension(url: String, type: MediaType): String {
        return when (type) {
            MediaType.PHOTO -> ".jpg"
            MediaType.VIDEO -> ".mp4"
            MediaType.GIF -> ".gif"
        }
    }

    private fun generateFilename(mediaItem: MediaItem, extension: String): String {
        val timestamp = System.currentTimeMillis()
        val typePrefix = when (mediaItem.type) {
            MediaType.PHOTO -> "IMG"
            MediaType.VIDEO -> "VID"
            MediaType.GIF -> "GIF"
        }
        return "${typePrefix}_X_${mediaItem.id}_$timestamp$extension"
    }
}
