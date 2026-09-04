package com.xdown.app.domain.usecase

import com.xdown.app.data.model.MediaItem
import com.xdown.app.data.repository.MediaRepository
import javax.inject.Inject

class FetchMediaUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(input: String): Result<List<MediaItem>> {
        if (input.isBlank()) {
            return Result.failure(Exception("Please enter a URL or username"))
        }
        return repository.fetchMedia(input)
    }
}
