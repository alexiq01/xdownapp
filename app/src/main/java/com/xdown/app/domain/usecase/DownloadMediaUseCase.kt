package com.xdown.app.domain.usecase

import com.xdown.app.data.model.MediaItem
import com.xdown.app.data.model.MediaQuality
import com.xdown.app.data.repository.MediaRepository
import java.io.File
import javax.inject.Inject

class DownloadMediaUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(
        mediaItem: MediaItem,
        quality: MediaQuality?,
        saveDir: File,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> {
        return repository.downloadMedia(mediaItem, quality, saveDir, onProgress)
    }
}
