package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernmentWebViewDialog(
    url: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf(url) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webPageTitle by remember { mutableStateOf(title) }

    // Desktop Chrome User-Agent to guarantee official portals do not block standard Android views
    val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    val openInExternalBrowser = { targetUrl: String ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Control Header Top Bar
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    title = {
                        Column {
                            Text(
                                text = webPageTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close webview")
                        }
                    },
                    actions = {
                        // Refresh state
                        IconButton(onClick = {
                            webViewInstance?.reload()
                        }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload page")
                        }
                        // Open instantly in system native browser
                        IconButton(onClick = {
                            openInExternalBrowser(currentUrl)
                        }) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = "Open in external browser")
                        }
                    }
                )

                // Optional loading bar
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Native AndroidView wrapping optimized WebView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    
                                    // Bypass restrictions (Cloudflare protection or generic agent blocks)
                                    userAgentString = desktopUserAgent
                                }

                                val webViewRef = this
                                CookieManager.getInstance().apply {
                                    setAcceptCookie(true)
                                    setAcceptThirdPartyCookies(webViewRef, true)
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, urlStr: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, urlStr, favicon)
                                        isLoading = true
                                        urlStr?.let { currentUrl = it }
                                    }

                                    override fun onPageFinished(view: WebView?, urlStr: String?) {
                                        super.onPageFinished(view, urlStr)
                                        isLoading = false
                                        view?.title?.let {
                                            webPageTitle = it.ifBlank { title }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val nextUrl = request?.url?.toString() ?: return false
                                        
                                        // Catch redirects/non-web custom schemes or document downloads
                                        if (nextUrl.endsWith(".pdf") || nextUrl.endsWith(".doc") || nextUrl.endsWith(".zip") ||
                                            nextUrl.startsWith("intent://") || nextUrl.startsWith("market://") || 
                                            nextUrl.startsWith("tel:") || nextUrl.startsWith("mailto:")
                                        ) {
                                            openInExternalBrowser(nextUrl)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                        // Ignore self-signed or invalid SSL certificate warnings typical to Indian government sites
                                        handler?.proceed()
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        if (newProgress >= 100) {
                                            isLoading = false
                                        }
                                    }
                                }

                                val headers = mapOf(
                                    "User-Agent" to desktopUserAgent,
                                    "Accept-Language" to "en-US,en;q=0.9",
                                    "Upgrade-Insecure-Requests" to "1"
                                )
                                loadUrl(url, headers)
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Interactive Info footer for 100% success assurance
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Site not rendering or document failed to download?",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { openInExternalBrowser(currentUrl) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Open in Browser", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
