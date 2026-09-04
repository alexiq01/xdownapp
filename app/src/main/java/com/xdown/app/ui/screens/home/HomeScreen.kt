package com.xdown.app.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xdown.app.data.model.*
import com.xdown.app.ui.components.ErrorCard
import com.xdown.app.ui.components.QualityBadge
import com.xdown.app.ui.screens.history.HistoryScreen
import com.xdown.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetail: (MediaItem) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        HistoryScreen(
            historyItems = uiState.downloadHistory,
            onClearHistory = { viewModel.clearHistory() },
            onBack = { showHistory = false }
        )
        return
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = XBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "XDown",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).text.toString()
                            if (text.contains("x.com") || text.contains("twitter.com") || text.startsWith("@")) {
                                viewModel.onInputChanged(text)
                                Toast.makeText(context, "Link pasted!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = "Paste from clipboard"
                        )
                    }

                    IconButton(onClick = { showHistory = true }) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "History"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SearchBar(
                input = uiState.input,
                onInputChange = viewModel::onInputChanged,
                onSearch = {
                    keyboardController?.hide()
                    viewModel.fetchMedia()
                },
                focusRequester = focusRequester,
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                uiState.error?.let { error ->
                    ErrorCard(
                        message = error,
                        onDismiss = viewModel::clearError
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (uiState.mediaItems.isNotEmpty()) {
                SectionHeader(
                    title = "Media Found",
                    subtitle = "${uiState.mediaItems.size} items"
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(
                        items = uiState.mediaItems,
                        key = { it.id }
                    ) { mediaItem ->
                        MediaCard(
                            mediaItem = mediaItem,
                            onClick = { viewModel.selectMedia(mediaItem) }
                        )
                    }
                }
            } else if (!uiState.isLoading && uiState.input.isNotBlank()) {
                EmptyState()
            } else if (!uiState.isLoading && uiState.input.isBlank()) {
                WelcomeContent(onPasteFromClipboard = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text.toString()
                        viewModel.onInputChanged(text)
                    }
                })
            }

            if (uiState.isLoading) {
                LoadingOverlay()
            }
        }

        if (uiState.showQualitySheet && uiState.selectedMedia != null) {
            com.xdown.app.ui.components.QualityBottomSheet(
                mediaItem = uiState.selectedMedia!!,
                onQualitySelected = { quality ->
                    viewModel.startDownload(uiState.selectedMedia!!, quality)
                    viewModel.dismissQualitySheet()
                },
                onDismiss = viewModel::dismissQualitySheet
            )
        }
    }
}

@Composable
private fun SearchBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = XBlue.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = XBlue,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(22.dp)
            )

            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        "Paste URL or @username...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = XBlue
                )
            )

            AnimatedVisibility(visible = input.isNotEmpty()) {
                IconButton(onClick = { onInputChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(GradientStart, GradientEnd)
                        )
                    )
                    .clickable(enabled = !isLoading) { onSearch() },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    mediaItem: MediaItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(mediaItem.thumbnailUrl ?: mediaItem.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(getMediaTypeColor(mediaItem.type).copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getMediaTypeIcon(mediaItem.type),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaItem.displayType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (mediaItem.width != null && mediaItem.height != null) {
                    Text(
                        text = "${mediaItem.width} x ${mediaItem.height}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    QualityBadge(
                        quality = getBestQualityLabel(mediaItem),
                        color = getMediaTypeColor(mediaItem.type)
                    )

                    if (mediaItem.qualities.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${mediaItem.qualities.size} qualities",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WelcomeContent(onPasteFromClipboard: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(GradientStart.copy(alpha = 0.1f), GradientEnd.copy(alpha = 0.1f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                tint = XBlue,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Download X Media",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Paste a tweet URL or enter a username\nto download photos, videos, and GIFs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureChip(icon = Icons.Outlined.Image, label = "Photos")
            FeatureChip(icon = Icons.Outlined.Videocam, label = "Videos")
            FeatureChip(icon = Icons.Outlined.Gif, label = "GIFs")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPasteFromClipboard() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = XBlue.copy(alpha = 0.08f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    tint = XBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Paste from Clipboard",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = XBlue
                )
            }
        }
    }
}

@Composable
private fun FeatureChip(
    icon: ImageVector,
    label: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = XBlue,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No media found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Try a different URL or username",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = XBlue,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Fetching media...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

fun getMediaTypeColor(type: MediaType): Color {
    return when (type) {
        MediaType.PHOTO -> XBlue
        MediaType.VIDEO -> XPink
        MediaType.GIF -> XYellow
    }
}

fun getMediaTypeIcon(type: MediaType): ImageVector {
    return when (type) {
        MediaType.PHOTO -> Icons.Filled.Image
        MediaType.VIDEO -> Icons.Filled.Videocam
        MediaType.GIF -> Icons.Filled.Gif
    }
}

fun getBestQualityLabel(mediaItem: MediaItem): String {
    if (mediaItem.qualities.isEmpty()) {
        return when (mediaItem.type) {
            MediaType.PHOTO -> "HD"
            MediaType.VIDEO -> "HD"
            MediaType.GIF -> "GIF"
        }
    }
    return mediaItem.qualities.maxByOrNull { it.bitrate ?: 0 }?.quality ?: "HD"
}
