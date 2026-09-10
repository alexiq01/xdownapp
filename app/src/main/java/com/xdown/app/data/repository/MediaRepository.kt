package com.xdown.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.xdown.app.data.model.*
import com.xdown.app.data.remote.DownloadService
import com.xdown.app.data.remote.XScraper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val scraper: XScraper,
    private val downloadService: DownloadService,
    @ApplicationContext private val context: Context
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
                return Result.failure(
                    Exception(
                        if (isTweetUrl(cleanedInput) || cleanedInput.matches(Regex("\\d{1,25}"))) {
                            "Unable to extract media from this post. Make sure the post is public and contains downloadable media."
                        } else {
                            "Invalid X URL or username. Use x.com/user/status/POST_ID or a public username."
                        }
                    )
                )
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

            result.fold(
                onSuccess = {
                    publishToGallery(outputFile, mediaItem.type)
                    Result.success(outputFile)
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun publishToGallery(file: java.io.File, type: MediaType) {
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val (collection, relativePath, mimeType) = when (type) {
                    MediaType.PHOTO, MediaType.GIF -> Triple(
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        "${Environment.DIRECTORY_PICTURES}/XDown",
                        if (type == MediaType.GIF) "image/gif" else "image/jpeg"
                    )
                    MediaType.VIDEO -> Triple(
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        "${Environment.DIRECTORY_MOVIES}/XDown",
                        "video/mp4"
                    )
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collection, values)
                if (uri != null) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            FileInputStream(file).use { input -> input.copyTo(output) }
                        }
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(uri, values, null, null)
                    } catch (e: Exception) {
                        context.contentResolver.delete(uri, null, null)
                    }
                }
            } else {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(mimeTypeFor(type)),
                    null
                )
            }
        }
    }

    private fun mimeTypeFor(type: MediaType): String = when (type) {
        MediaType.PHOTO -> "image/jpeg"
        MediaType.GIF -> "image/gif"
        MediaType.VIDEO -> "video/mp4"
    }

    suspend fun getAvailableQualities(mediaItem: MediaItem): List<MediaQuality> {
        return mediaItem.qualities.sortedDescending()
    }

    private fun cleanInput(input: String): String {
        val trimmed = input.trim()
        val withScheme = if (
            trimmed.startsWith("x.com/") || trimmed.startsWith("twitter.com/")
        ) "https://$trimmed" else trimmed
        return withScheme
            .replace("www.", "")
            .replace(Regex("\\s+"), "")
            .removeSuffix("/")
    }

    private fun isTweetUrl(input: String): Boolean {
        if (input.matches(Regex("\\d{1,25}"))) return true
        val patterns = listOf(
            """https?://(?:mobile\.)?(?:x|twitter)\.com/[^/]+/status(?:es)?/\d{1,25}(?:[?#].*)?"""
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
