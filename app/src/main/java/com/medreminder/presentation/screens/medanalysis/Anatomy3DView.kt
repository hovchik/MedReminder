package com.medreminder.presentation.screens.medanalysis

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.medreminder.ai.BodyRegion
import com.medreminder.presentation.util.translatedName

/**
 * Interactive 3D human anatomy viewer using WebGL (Three.js) rendered in a WebView.
 * Supports touch rotation/zoom and highlights body regions targeted by medication.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Anatomy3DView(
    highlightedRegions: List<BodyRegion>,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    // Update highlights whenever regions change or WebView is ready
    LaunchedEffect(highlightedRegions, webViewRef, isLoading) {
        if (!isLoading && webViewRef != null) {
            val json = highlightedRegions.joinToString(",") { "\"${it.name}\"" }
            webViewRef?.evaluateJavascript("highlightRegions('[$json]')", null)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        mediaPlaybackRequiresUserGesture = false
                    }

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Small delay to let Three.js initialize
                            postDelayed({
                                isLoading = false
                            }, 500)
                        }
                    }

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onModelReady() {
                            post { isLoading = false }
                        }
                    }, "AndroidBridge")

                    loadUrl("file:///android_asset/anatomy3d.html")
                    webViewRef = this
                }
            },
            update = { webView ->
                webViewRef = webView
            }
        )

        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(surfaceColor.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Loading 3D model...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Region labels below the 3D view
        if (highlightedRegions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                highlightedRegions.take(4).forEach { region ->
                    Text(
                        text = region.translatedName(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
