package com.xdown.app.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

object DownloadUtils {

    fun getFileExtension(url: String, type: com.xdown.app.data.model.MediaType): String {
        return when (type) {
            com.xdown.app.data.model.MediaType.PHOTO -> ".jpg"
            com.xdown.app.data.model.MediaType.VIDEO -> ".mp4"
            com.xdown.app.data.model.MediaType.GIF -> ".gif"
        }
    }

    fun generateFilename(
        id: String,
        type: com.xdown.app.data.model.MediaType,
        quality: String? = null
    ): String {
        val timestamp = System.currentTimeMillis()
        val typePrefix = when (type) {
            com.xdown.app.data.model.MediaType.PHOTO -> "IMG"
            com.xdown.app.data.model.MediaType.VIDEO -> "VID"
            com.xdown.app.data.model.MediaType.GIF -> "GIF"
        }
        val qualitySuffix = quality?.let { "_$it" } ?: ""
        return "${typePrefix}_X_${id}${qualitySuffix}_$timestamp"
    }

    fun getSaveDirectory(context: Context): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "XDown"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format("%.1f GB", gb)
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun getMediaTypeEmoji(type: com.xdown.app.data.model.MediaType): String {
        return when (type) {
            com.xdown.app.data.model.MediaType.PHOTO -> "\uD83D\uDCF7"
            com.xdown.app.data.model.MediaType.VIDEO -> "\uD83C\uDFAC"
            com.xdown.app.data.model.MediaType.GIF -> "\uD83D\uDCDD"
        }
    }
}
