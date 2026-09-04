package com.xdown.app.util

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object FileUtils {
    suspend fun getSaveDirectory(context: Context): File {
        return withContext(Dispatchers.IO) {
            val dir = File(
                context.getExternalFilesDir(null),
                "XDown"
            )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            dir
        }
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

    fun getFileExtension(url: String): String {
        return try {
            val path = URL(url).path
            val lastDot = path.lastIndexOf('.')
            if (lastDot != -1) {
                path.substring(lastDot)
            } else {
                ".unknown"
            }
        } catch (e: Exception) {
            ".unknown"
        }
    }

    fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    fun showShortToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showLongToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
