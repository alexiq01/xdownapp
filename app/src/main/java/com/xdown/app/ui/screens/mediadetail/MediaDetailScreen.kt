package com.xdown.app.ui.screens.mediadetail

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xdown.app.data.model.*
import com.xdown.app.ui.components.QualityBadge
import com.xdown.app.ui.theme.*
import com.xdown.app.ui.screens.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    mediaItem: MediaItem,
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mediaItem.displayType) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                MediaPreview(mediaItem = mediaItem)
            }

            item {
                MediaInfoSection(mediaItem = mediaItem)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Available Qualities",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(mediaItem.qualities) { quality ->
                val qualityId = "${mediaItem.id}_${quality.quality}"
                val progress = downloadProgress[qualityId]

                QualityCard(
                    mediaItem = mediaItem,
                    quality = quality,
                    progress = progress,
                    onDownload = {
                        viewModel.startDownload(mediaItem, quality)
                    }
                )
            }
        }
    }
}

@Composable
private fun MediaPreview(mediaItem: MediaItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(mediaItem.url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(getMediaTypeColor(mediaItem.type).copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = getMediaTypeIcon(mediaItem.type),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = mediaItem.displayType,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MediaInfoSection(mediaItem: MediaItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Media Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoItem(
                    icon = Icons.Outlined.AspectRatio,
                    label = "Resolution",
                    value = if (mediaItem.width != null && mediaItem.height != null) {
                        "${mediaItem.width} x ${mediaItem.height}"
                    } else "N/A"
                )
                InfoItem(
                    icon = Icons.Outlined.HighQuality,
                    label = "Qualities",
                    value = "${mediaItem.qualities.size}"
                )
                if (mediaItem.duration != null) {
                    InfoItem(
                        icon = Icons.Outlined.Schedule,
                        label = "Duration",
                        value = formatDuration(mediaItem.duration)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(XBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = XBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QualityCard(
    mediaItem: MediaItem,
    quality: MediaQuality,
    progress: DownloadProgress?,
    onDownload: () -> Unit
) {
    val isDownloading = progress?.state == DownloadState.DOWNLOADING
    val isCompleted = progress?.state == DownloadState.COMPLETED
    val isError = progress?.state == DownloadState.ERROR

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = quality.quality,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        QualityBadge(
                            quality = quality.dimensionLabel,
                            color = getQualityColor(quality.quality)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row {
                        if (quality.width != null && quality.height != null) {
                            InfoTag(
                                icon = Icons.Outlined.AspectRatio,
                                text = "${quality.width}x${quality.height}"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        InfoTag(
                            icon = Icons.Outlined.DataUsage,
                            text = quality.fileSize
                        )
                        if (quality.contentType != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            InfoTag(
                                icon = Icons.Outlined.FiberManualRecord,
                                text = quality.contentType.replace("video/", "").uppercase()
                            )
                        }
                    }
                }

                when {
                    isCompleted -> {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Downloaded",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    isDownloading -> {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { progress?.progress ?: 0f },
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp,
                                color = XBlue,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "${((progress?.progress ?: 0f) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = XBlue
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(GradientStart, GradientEnd)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onDownload) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = "Download",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress?.progress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = XBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (isError) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = progress?.error ?: "Download failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getQualityColor(quality: String): Color {
    return when {
        quality.contains("2160") || quality.contains("1440") -> QualityQHD
        quality.contains("1080") -> QualityFHD
        quality.contains("720") -> QualityHD
        quality.contains("Original") -> QualityQHD
        else -> XBlue
    }
}

private fun formatDuration(millis: Int): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "%d:%02d".format(minutes, remainingSeconds)
    } else {
        "${remainingSeconds}s"
    }
}

private fun getMediaTypeColor(type: MediaType): Color {
    return when (type) {
        MediaType.PHOTO -> XBlue
        MediaType.VIDEO -> XPink
        MediaType.GIF -> XYellow
    }
}

private fun getMediaTypeIcon(type: MediaType) = when (type) {
    MediaType.PHOTO -> Icons.Filled.Image
    MediaType.VIDEO -> Icons.Filled.Videocam
    MediaType.GIF -> Icons.Filled.Gif
}
