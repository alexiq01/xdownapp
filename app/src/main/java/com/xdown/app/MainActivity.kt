package com.xdown.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import com.xdown.app.ui.navigation.XDownNavGraph
import com.xdown.app.ui.theme.XDownTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XDownTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    XDownNavGraph(initialInput = intent.extractSharedText())
                }
            }
        }
    }

    private fun Intent.extractSharedText(): String? {
        return when (action) {
            Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> dataString
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }
}
