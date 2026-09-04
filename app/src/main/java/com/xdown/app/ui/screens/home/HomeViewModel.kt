package com.xdown.app.ui.screens.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdown.app.data.model.*
import com.xdown.app.domain.usecase.DownloadMediaUseCase
import com.xdown.app.domain.usecase.FetchMediaUseCase
import com.xdown.app.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val fetchMediaUseCase: FetchMediaUseCase,
    private val downloadMediaUseCase: DownloadMediaUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val saveDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "XDown"
    ).also {
        if (!it.exists()) it.mkdirs()
    }

    private var notificationCounter = 0

    fun onInputChanged(input: String) {
        _uiState.value = _uiState.value.copy(input = input, error = null)
    }

    fun fetchMedia() {
        val input = _uiState.value.input.trim()
        if (input.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a URL or username")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                mediaItems = emptyList()
            )

            fetchMediaUseCase(input)
                .onSuccess { mediaItems ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        mediaItems = mediaItems,
                        selectedMedia = null,
                        showQualitySheet = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to fetch media. Please try again."
                    )
                }
        }
    }

    fun selectMedia(mediaItem: MediaItem) {
        _uiState.value = _uiState.value.copy(
            selectedMedia = mediaItem,
            showQualitySheet = true
        )
    }

    fun dismissQualitySheet() {
        _uiState.value = _uiState.value.copy(
            showQualitySheet = false,
            selectedMedia = null
        )
    }

    fun startDownload(mediaItem: MediaItem, quality: MediaQuality?) {
        val mediaId = "${mediaItem.id}_${quality?.quality ?: "default"}"
        val notifId = ++notificationCounter

        viewModelScope.launch {
            val context = getApplication<Application>()

            NotificationHelper.showDownloadStarted(
                context,
                "${mediaItem.displayType}_${mediaItem.id}",
                notifId
            )

            val currentProgress = _downloadProgress.value.toMutableMap()
            currentProgress[mediaId] = DownloadProgress(
                mediaId = mediaId,
                state = DownloadState.DOWNLOADING,
                progress = 0f
            )
            _downloadProgress.value = currentProgress

            downloadMediaUseCase(
                mediaItem = mediaItem,
                quality = quality,
                saveDir = saveDir
            ) { progress, downloaded, total ->
                viewModelScope.launch {
                    val updated = _downloadProgress.value.toMutableMap()
                    updated[mediaId] = DownloadProgress(
                        mediaId = mediaId,
                        state = DownloadState.DOWNLOADING,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                    _downloadProgress.value = updated

                    if (progress >= 0f) {
                        NotificationHelper.showDownloadProgress(
                            context,
                            "${mediaItem.displayType}_${mediaItem.id}",
                            (progress * 100).toInt(),
                            notifId
                        )
                    }
                }
            }.onSuccess { file ->
                val updated = _downloadProgress.value.toMutableMap()
                updated[mediaId] = DownloadProgress(
                    mediaId = mediaId,
                    state = DownloadState.COMPLETED,
                    progress = 1f
                )
                _downloadProgress.value = updated

                NotificationHelper.showDownloadComplete(context, file.name, notifId)
                addToHistory(mediaItem, file)
            }.onFailure { e ->
                val updated = _downloadProgress.value.toMutableMap()
                updated[mediaId] = DownloadProgress(
                    mediaId = mediaId,
                    state = DownloadState.ERROR,
                    error = e.message
                )
                _downloadProgress.value = updated

                NotificationHelper.showDownloadError(
                    context,
                    "${mediaItem.displayType}_${mediaItem.id}",
                    e.message ?: "Unknown error",
                    notifId
                )
            }
        }
    }

    private fun addToHistory(mediaItem: MediaItem, file: File) {
        viewModelScope.launch {
            val currentHistory = _uiState.value.downloadHistory.toMutableList()
            currentHistory.add(
                0, DownloadHistoryItem(
                    mediaItem = mediaItem,
                    filePath = file.absolutePath,
                    fileName = file.name,
                    fileSize = file.length(),
                    downloadTime = System.currentTimeMillis()
                )
            )
            _uiState.value = _uiState.value.copy(downloadHistory = currentHistory.take(100))
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearHistory() {
        _uiState.value = _uiState.value.copy(downloadHistory = emptyList())
    }
}

data class HomeUiState(
    val input: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val mediaItems: List<MediaItem> = emptyList(),
    val selectedMedia: MediaItem? = null,
    val showQualitySheet: Boolean = false,
    val downloadHistory: List<DownloadHistoryItem> = emptyList()
)

data class DownloadHistoryItem(
    val mediaItem: MediaItem,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val downloadTime: Long
) {
    val fileSizeFormatted: String
        get() {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1 -> String.format("%.1f GB", gb)
                mb >= 1 -> String.format("%.1f MB", mb)
                kb >= 1 -> String.format("%.1f KB", kb)
                else -> "$fileSize B"
            }
        }
}
