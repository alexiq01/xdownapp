package com.xdown.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://x.com/")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun downloadFile(
        url: String,
        outputFile: File,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response"))
            val contentLength = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val progress = if (contentLength > 0) {
                            downloaded.toFloat() / contentLength
                        } else {
                            -1f
                        }
                        onProgress(progress, downloaded, contentLength)
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileSize(url: String): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).head().build()
            val response = client.newCall(request).execute()
            val contentLength = response.body?.contentLength() ?: -1
            response.close()
            contentLength
        } catch (e: Exception) {
            -1
        }
    }
}
