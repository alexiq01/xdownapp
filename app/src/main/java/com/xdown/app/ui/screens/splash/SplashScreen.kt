package com.xdown.app.ui.screens.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xdown.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val iconRotation by animateFloatAsState(
        targetValue = if (startAnimation) 360f else 0f,
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "rotation"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(
            durationMillis = 500,
            delayMillis = 300
        ),
        label = "textAlpha"
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(
            durationMillis = 500,
            delayMillis = 500
        ),
        label = "subtitleAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(400)
        showContent = true
        delay(1800)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(GradientStart, GradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(56.dp)
                        .rotate(iconRotation)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "XDown",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = textAlpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Download X Media",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = subtitleAlpha)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .graphicsLayer(alpha = subtitleAlpha)
        ) {
            Text(
                text = "v1.0.0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
