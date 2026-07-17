package com.example

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        BrowserApp()
      }
    }
  }
}

data class Bookmark(
  val id: Int,
  val name: String,
  val url: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserApp() {
  val context = LocalContext.current
  val sharedPrefs = remember { context.getSharedPreferences("minima_browser_prefs", Context.MODE_PRIVATE) }

  // Load existing bookmarks or use defaults
  var bookmarks by remember {
    mutableStateOf(
      listOf(
        loadBookmark(sharedPrefs, 1, "HOME", "https://www.google.com"),
        loadBookmark(sharedPrefs, 2, "EXPLORE", "https://en.m.wikipedia.org"),
        loadBookmark(sharedPrefs, 3, "SAVED", "https://news.google.com"),
        loadBookmark(sharedPrefs, 4, "FILES", "https://github.com"),
        loadBookmark(sharedPrefs, 5, "PROFILE", "https://dev.to")
      )
    )
  }

  var currentUrl by remember { mutableStateOf("https://www.google.com") }
  var pageTitle by remember { mutableStateOf("Loading...") }
  var progress by remember { mutableStateOf(0) }
  var isPageLoading by remember { mutableStateOf(false) }
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }

  // Selected bookmark index (1-based), if active
  var activeBookmarkId by remember { mutableStateOf(1) }

  // Dialog state for editing a bookmark
  var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }

  // Custom URL sheet state
  var showGoToUrlDialog by remember { mutableStateOf(false) }

  // Settings window visibility state
  var showSettingsWindow by remember { mutableStateOf(false) }

  // Gesture mode visibility/active override state over the WebView
  var isGestureActive by remember { mutableStateOf(true) }

  // Handle system back navigation inside the WebView
  BackHandler(enabled = webViewInstance?.canGoBack() == true) {
    webViewInstance?.goBack()
  }

  // Determine domain for display
  val domainDisplay = remember(currentUrl) {
    try {
      val uri = android.net.Uri.parse(currentUrl)
      uri.host ?: currentUrl
    } catch (e: Exception) {
      currentUrl
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      // Ultra-minimal header bar showing current domain, SSL status, and controls
      Surface(
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
      ) {
        Column {
          Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            // Navigation back/forward buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { webViewInstance?.goBack() },
                enabled = webViewInstance?.canGoBack() == true
              ) {
                Icon(
                  imageVector = Icons.Outlined.ArrowBack,
                  contentDescription = "Navigate Back",
                  tint = if (webViewInstance?.canGoBack() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
              }
              IconButton(
                onClick = { webViewInstance?.goForward() },
                enabled = webViewInstance?.canGoForward() == true
              ) {
                Icon(
                  imageVector = Icons.Outlined.ArrowForward,
                  contentDescription = "Navigate Forward",
                  tint = if (webViewInstance?.canGoForward() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
              }
            }

            Text(
              text = "malachite",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.SansSerif
              ),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f),
              textAlign = TextAlign.Center
            )

            // Right actions row: Gesture toggle, Refresh, Settings
            Row(verticalAlignment = Alignment.CenterVertically) {
              // Gesture mode toggle button
              IconButton(onClick = { 
                isGestureActive = !isGestureActive 
                val status = if (isGestureActive) "Active" else "Bypassed (Browser Interaction On)"
                android.widget.Toast.makeText(context, "Gesture Overrides: $status", android.widget.Toast.LENGTH_SHORT).show()
              }) {
                Icon(
                  imageVector = if (isGestureActive) Icons.Default.Lock else Icons.Outlined.Language,
                  contentDescription = "Toggle Gesture Override Mode",
                  tint = if (isGestureActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
              }

              // Refresh / Reload Button
              IconButton(onClick = { webViewInstance?.reload() }) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = "Refresh Page",
                  tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
              }

              // Settings button
              IconButton(onClick = { showSettingsWindow = true }) {
                Icon(
                  imageVector = Icons.Default.Settings,
                  contentDescription = "Settings",
                  tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
              }
            }
          }

          // Loading Progress Indicator
          if (isPageLoading) {
            LinearProgressIndicator(
              progress = { progress / 100f },
              modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
              color = MaterialTheme.colorScheme.primary,
              trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
          } else {
            Box(modifier = Modifier.height(2.dp))
          }
        }
      }
    },
    bottomBar = {
      // Elegant Slate Carbon themed bottom bar with buttons 1 2 3 4 5
      Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Title/Name indicator of current speed dial
          val activeBookmark = bookmarks.find { it.id == activeBookmarkId }
          Text(
            text = activeBookmark?.name ?: "Speed Dial",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.SemiBold,
              letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
          )

          // Row of buttons 1, 2, 3, 4, 5
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            bookmarks.forEach { bookmark ->
              val isActive = activeBookmarkId == bookmark.id

              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
              ) {
                // Modern Elegant Pill Shape Dial key
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier
                    .width(64.dp)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(
                      if (isActive) {
                        MaterialTheme.colorScheme.primary
                      } else {
                        Color.Transparent
                      }
                    )
                    .combinedClickable(
                      onClick = {
                        activeBookmarkId = bookmark.id
                        currentUrl = bookmark.url
                        webViewInstance?.loadUrl(bookmark.url)
                      },
                      onLongClick = {
                        editingBookmark = bookmark
                      }
                    )
                ) {
                  Text(
                    text = bookmark.id.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontWeight = FontWeight.Bold,
                      fontFamily = FontFamily.SansSerif
                    ),
                    color = if (isActive) {
                      MaterialTheme.colorScheme.onPrimary
                    } else {
                      MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                  )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Clean Elegant sub-label
                Text(
                  text = bookmark.name,
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                  }
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Tip: Long-press a dial to customize its destination",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Embedded WebView component occupying the top weighted space
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        AndroidView(
          factory = { context ->
            object : WebView(context) {
              private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                  android.widget.Toast.makeText(context, "You did Double Tap!", android.widget.Toast.LENGTH_SHORT).show()
                  return true
                }

                override fun onLongPress(e: MotionEvent) {
                  android.widget.Toast.makeText(context, "You did Hold!", android.widget.Toast.LENGTH_SHORT).show()
                }

                override fun onFling(
                  e1: MotionEvent?,
                  e2: MotionEvent,
                  velocityX: Float,
                  velocityY: Float
                ): Boolean {
                  if (e1 != null) {
                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                      if (Math.abs(diffX) > 120 && Math.abs(velocityX) > 150) {
                        if (diffX > 0) {
                          android.widget.Toast.makeText(context, "You did Swipe Right!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                          android.widget.Toast.makeText(context, "You did Swipe Left!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return true
                      }
                    }
                  }
                  return false
                }
              })

              override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (isGestureActive) {
                  val gestureHandled = gestureDetector.onTouchEvent(event)
                  if (gestureHandled) {
                    return true
                  }
                }
                return super.dispatchTouchEvent(event)
              }
            }.apply {
              layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
              settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                // User agent optimization
                userAgentString = userAgentString.replace("wv", "")
              }
              webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                  view: WebView?,
                  request: WebResourceRequest?
                ): Boolean {
                  // Keep all navigations inside our browser viewport
                  return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  super.onPageStarted(view, url, favicon)
                  isPageLoading = true
                  url?.let {
                    currentUrl = it
                    // Check if the navigated URL matches one of our dials
                    bookmarks.find { b -> it.trimEnd('/').equals(b.url.trimEnd('/'), ignoreCase = true) }?.let { b ->
                      activeBookmarkId = b.id
                    }
                  }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                  super.onPageFinished(view, url)
                  isPageLoading = false
                  url?.let {
                    currentUrl = it
                  }
                }
              }
              webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                  super.onProgressChanged(view, newProgress)
                  progress = newProgress
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                  super.onReceivedTitle(view, title)
                  title?.let { pageTitle = it }
                }
              }
              loadUrl(currentUrl)
              webViewInstance = this
            }
          },
          update = { webView ->
            // Make sure WebView updates correctly if loaded URL is dynamically driven
          },
          modifier = Modifier.fillMaxSize()
        )

        // Overlay discreet gesture active label (without intercepting touches itself)
        if (isGestureActive) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
          ) {
            // Elegant badge indicating touch override is active
            Card(
              modifier = Modifier.padding(12.dp),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
              ),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
              )
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = "Gestures Active",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Gesture Overlay Active (Double Tap, Hold, Swipe L/R)",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                  ),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      }
    }
  }

  // Modern Dark Settings Dialog (Blank for now, styled with malachite theme)
  if (showSettingsWindow) {
    AlertDialog(
      onDismissRequest = { showSettingsWindow = false },
      properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      confirmButton = {
        Button(
          onClick = { showSettingsWindow = false },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
          )
        ) {
          Text("Close")
        }
      },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "malachite settings",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp
            )
          )
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(220.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(16.dp)
            ) {
              Text(
                text = "Empty Configurations",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary
                ),
                textAlign = TextAlign.Center
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "This space is currently left blank. Custom settings parameters will appear here in future updates.",
                style = MaterialTheme.typography.bodyMedium.copy(
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ),
                textAlign = TextAlign.Center
              )
            }
          }
        }
      }
    )
  }

  // Modal Dialog to edit quick dials
  editingBookmark?.let { bookmark ->
    var tempName by remember { mutableStateOf(bookmark.name) }
    var tempUrl by remember { mutableStateOf(bookmark.url) }

    AlertDialog(
      onDismissRequest = { editingBookmark = null },
      icon = { Icon(Icons.Default.Edit, contentDescription = "Edit Dial") },
      title = { Text(text = "Customize Quick Dial ${bookmark.id}") },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedTextField(
            value = tempName,
            onValueChange = { tempName = it },
            label = { Text("Display Name") },
            placeholder = { Text("e.g. Google") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = tempUrl,
            onValueChange = { tempUrl = it },
            label = { Text("Website URL") },
            placeholder = { Text("https://example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            // Ensure protocol is attached
            var formattedUrl = tempUrl.trim()
            if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
              formattedUrl = "https://$formattedUrl"
            }

            val updatedBookmark = bookmark.copy(name = tempName.trim(), url = formattedUrl)
            bookmarks = bookmarks.map {
              if (it.id == bookmark.id) updatedBookmark else it
            }
            saveBookmark(sharedPrefs, updatedBookmark)

            // Immediately navigate if the current active button was updated
            if (activeBookmarkId == bookmark.id) {
              currentUrl = formattedUrl
              webViewInstance?.loadUrl(formattedUrl)
            }

            editingBookmark = null
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { editingBookmark = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // Dialog to go to custom URL directly from header
  if (showGoToUrlDialog) {
    var directUrl by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showGoToUrlDialog = false },
      icon = { Icon(Icons.Outlined.Language, contentDescription = "Browse") },
      title = { Text(text = "Go to Website") },
      text = {
        OutlinedTextField(
          value = directUrl,
          onValueChange = { directUrl = it },
          label = { Text("URL or Search Query") },
          placeholder = { Text("google.com or how to bake cookies") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            var target = directUrl.trim()
            if (target.isNotEmpty()) {
              // If it doesn't look like a URL with a dot or has spaces, search Google
              if (!target.contains(".") || target.contains(" ")) {
                target = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(target, "UTF-8")
              } else if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "https://$target"
              }
              currentUrl = target
              webViewInstance?.loadUrl(target)
            }
            showGoToUrlDialog = false
          }
        ) {
          Text("Go")
        }
      },
      dismissButton = {
        TextButton(onClick = { showGoToUrlDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

private fun loadBookmark(prefs: SharedPreferences, id: Int, defaultName: String, defaultUrl: String): Bookmark {
  val name = prefs.getString("bookmark_name_$id", defaultName) ?: defaultName
  val url = prefs.getString("bookmark_url_$id", defaultUrl) ?: defaultUrl
  return Bookmark(id, name, url)
}

private fun saveBookmark(prefs: SharedPreferences, bookmark: Bookmark) {
  prefs.edit().apply {
    putString("bookmark_name_${bookmark.id}", bookmark.name)
    putString("bookmark_url_${bookmark.id}", bookmark.url)
    apply()
  }
}
