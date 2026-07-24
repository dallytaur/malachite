package com.example

import android.content.Context
import android.content.Intent
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

// Core actions for universal mapping
enum class BrowserAction {
  UPVOTE, DOWNVOTE, SNOOZE, NEXT_PAGE, REFRESH, GO_BACK, GO_FORWARD
}

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

// Retroactive Decay Mechanism
fun decayScore(score: Float, lastUpdated: Long, currentTime: Long): Pair<Float, Long> {
  val hoursPassed = ((currentTime - lastUpdated) / (1000L * 60 * 60)).toInt()
  if (hoursPassed <= 0) return Pair(score, lastUpdated)
  val decayAmount = hoursPassed * 0.01f
  val newScore = if (score > 1.0f) {
    maxOf(1.0f, score - decayAmount)
  } else if (score < 1.0f) {
    minOf(1.0f, score + decayAmount)
  } else {
    1.0f
  }
  return Pair(newScore, currentTime)
}

// Weighted Random Selection Algorithm
fun selectNextPage(
  domains: List<DomainObject>,
  currentTime: Long
): Triple<DomainObject, PageObject, List<DomainObject>>? {
  val updatedDomains = domains.map { domain ->
    // Query domains where snooze is null or expired
    val isSnoozed = domain.snoozeTimestamp != null && domain.snoozeTimestamp > currentTime
    if (isSnoozed) return@map domain
    
    // Decay domain
    val (newDomainScore, newDomainLastUpdated) = decayScore(domain.affinityScore, domain.lastUpdated, currentTime)
    
    // Decay pages
    val updatedPages = domain.pages.map { page ->
      val isPageSnoozed = page.snoozeTimestamp != null && page.snoozeTimestamp > currentTime
      if (isPageSnoozed) return@map page
      
      val (newPageScore, newPageLastUpdated) = decayScore(page.affinityScore, page.lastUpdated, currentTime)
      page.copy(affinityScore = newPageScore, lastUpdated = newPageLastUpdated)
    }
    
    domain.copy(
      affinityScore = newDomainScore,
      lastUpdated = newDomainLastUpdated,
      pages = updatedPages
    )
  }

  // Filter valid unsnoozed domains
  var remainingDomains = updatedDomains.filter { domain ->
    domain.snoozeTimestamp == null || domain.snoozeTimestamp <= currentTime
  }

  while (remainingDomains.isNotEmpty()) {
    // Perform weighted random selection of Domain
    val totalDomainWeight = remainingDomains.sumOf { it.affinityScore.toDouble() }
    if (totalDomainWeight <= 0.0) {
      val selectedDomain = remainingDomains.randomOrNull() ?: break
      val page = pickWeightedPageForDomain(selectedDomain, currentTime)
      if (page != null) {
        return Triple(selectedDomain, page, updatedDomains)
      } else {
        remainingDomains = remainingDomains - selectedDomain
        continue
      }
    }
    
    val randomValue = kotlin.random.Random.nextDouble() * totalDomainWeight
    var accumulatedWeight = 0.0
    var selectedDomain: DomainObject? = null
    for (domain in remainingDomains) {
      accumulatedWeight += domain.affinityScore
      if (randomValue <= accumulatedWeight) {
        selectedDomain = domain
        break
      }
    }
    if (selectedDomain == null) selectedDomain = remainingDomains.last()

    // Query valid child pages for the selected domain
    val page = pickWeightedPageForDomain(selectedDomain, currentTime)
    if (page != null) {
      return Triple(selectedDomain, page, updatedDomains)
    } else {
      // Fallback: loop back and pick a new Domain
      remainingDomains = remainingDomains - selectedDomain
    }
  }
  return null
}

fun pickWeightedPageForDomain(domain: DomainObject, currentTime: Long): PageObject? {
  val validPages = domain.pages.filter { page ->
    page.snoozeTimestamp == null || page.snoozeTimestamp <= currentTime
  }
  if (validPages.isEmpty()) return null
  
  val totalPageWeight = validPages.sumOf { page -> page.affinityScore.toDouble() }
  if (totalPageWeight <= 0.0) {
    return validPages.randomOrNull()
  }
  
  val randomValue = kotlin.random.Random.nextDouble() * totalPageWeight
  var accumulatedWeight = 0.0
  for (page in validPages) {
    accumulatedWeight += page.affinityScore
    if (randomValue <= accumulatedWeight) {
      return page
    }
  }
  return validPages.last()
}

// History tracking
private var lastPageStartTime = System.currentTimeMillis()
private var lastNavigationContext = "Direct"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserApp() {
  val context = LocalContext.current
  val sharedPrefs = remember { context.getSharedPreferences("minima_browser_prefs", Context.MODE_PRIVATE) }

  var currentUrl by remember { mutableStateOf("https://www.google.com") }
  var pageTitle by remember { mutableStateOf("Loading...") }
  var progress by remember { mutableIntStateOf(0) }
  var isPageLoading by remember { mutableStateOf(false) }
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var cloudValue by remember { mutableIntStateOf(42) }

  // Observe navigation requests from other activities
  LaunchedEffect(BrowserState.currentUrlToLoad) {
    BrowserState.currentUrlToLoad?.let { url ->
      webViewInstance?.loadUrl(url)
      BrowserState.currentUrlToLoad = null
    }
  }

  // Load existing bookmarks or use defaults
  var bookmarks by remember {
    mutableStateOf(
      listOf(
        loadBookmark(sharedPrefs, 1, "HOME", "https://www.google.com"),
        loadBookmark(sharedPrefs, 2, "EXPLORE", "https://en.m.wikipedia.org"),
        loadBookmark(sharedPrefs, 3, "SAVED", "https://news.google.com"),
        loadBookmark(sharedPrefs, 4, "FILES", "https://github.com"),
        loadBookmark(sharedPrefs, 5, "FEED", "https://old.reddit.com/r/android")
      )
    )
  }

  var activeDomainObject by remember { mutableStateOf<DomainObject?>(null) }
  var activePageObject by remember { mutableStateOf<PageObject?>(null) }

  // --- Universal Dispatcher ---
  val dispatchAction = { action: BrowserAction, contextStr: String ->
    lastNavigationContext = contextStr
    when (action) {
      BrowserAction.UPVOTE -> {
        val now = System.currentTimeMillis()
        activePageObject?.let { activePage ->
          activeDomainObject?.let { activeDomain ->
            BrowserState.domainsList = BrowserState.domainsList.map { domain ->
              if (domain.url.equals(activeDomain.url, ignoreCase = true)) {
                val updatedPages = domain.pages.map { page ->
                  if (page.path.equals(activePage.path, ignoreCase = true)) {
                    page.copy(affinityScore = minOf(2.0f, page.affinityScore + 0.02f), lastUpdated = now)
                  } else page
                }
                val newDomainScore = minOf(2.0f, domain.affinityScore + 0.02f)
                val updatedDomain = domain.copy(affinityScore = newDomainScore, lastUpdated = now, pages = updatedPages)
                activeDomainObject = updatedDomain
                activePageObject = updatedDomain.pages.find { it.path.equals(activePage.path, ignoreCase = true) }
                android.widget.Toast.makeText(context, "Upvoted! Page affinity: %.2f (Domain: %.2f)".format(activePageObject?.affinityScore ?: 0f, updatedDomain.affinityScore), android.widget.Toast.LENGTH_SHORT).show()
                updatedDomain
              } else domain
            }
          }
        } ?: run {
          android.widget.Toast.makeText(context, "Upvoted! (Load Feed to change specific page affinities)", android.widget.Toast.LENGTH_SHORT).show()
        }
      }
      BrowserAction.DOWNVOTE -> {
        val now = System.currentTimeMillis()
        activePageObject?.let { activePage ->
          activeDomainObject?.let { activeDomain ->
            BrowserState.domainsList = BrowserState.domainsList.map { domain ->
              if (domain.url.equals(activeDomain.url, ignoreCase = true)) {
                val updatedPages = domain.pages.map { page ->
                  if (page.path.equals(activePage.path, ignoreCase = true)) {
                    page.copy(affinityScore = maxOf(0.0f, page.affinityScore - 0.02f), lastUpdated = now)
                  } else page
                }
                val newDomainScore = maxOf(0.0f, domain.affinityScore - 0.02f)
                val updatedDomain = domain.copy(affinityScore = newDomainScore, lastUpdated = now, pages = updatedPages)
                activeDomainObject = updatedDomain
                activePageObject = updatedDomain.pages.find { it.path.equals(activePage.path, ignoreCase = true) }
                android.widget.Toast.makeText(context, "Downvoted! Page affinity: %.2f (Domain: %.2f)".format(activePageObject?.affinityScore ?: 0f, updatedDomain.affinityScore), android.widget.Toast.LENGTH_SHORT).show()
                updatedDomain
              } else domain
            }
          }
        } ?: run {
          android.widget.Toast.makeText(context, "Downvoted! (Load Feed to change specific page affinities)", android.widget.Toast.LENGTH_SHORT).show()
        }
      }
      BrowserAction.SNOOZE -> {
        val now = System.currentTimeMillis()
        val snoozeDuration = 1000L * 60 * 60 // 1 hour
        activePageObject?.let { activePage ->
          activeDomainObject?.let { activeDomain ->
            BrowserState.domainsList = BrowserState.domainsList.map { domain ->
              if (domain.url.equals(activeDomain.url, ignoreCase = true)) {
                val updatedPages = domain.pages.map { page ->
                  if (page.path.equals(activePage.path, ignoreCase = true)) {
                    page.copy(snoozeTimestamp = now + snoozeDuration)
                  } else page
                }
                val updatedDomain = domain.copy(pages = updatedPages)
                activeDomainObject = updatedDomain
                activePageObject = updatedDomain.pages.find { it.path.equals(activePage.path, ignoreCase = true) }
                android.widget.Toast.makeText(context, "Snoozed ${activePage.name} for 1 Hour!", android.widget.Toast.LENGTH_SHORT).show()
                updatedDomain
              } else domain
            }
          }
        } ?: run {
          android.widget.Toast.makeText(context, "Snoozed current page for 1 hour!", android.widget.Toast.LENGTH_SHORT).show()
        }
      }
      BrowserAction.NEXT_PAGE -> {
        val now = System.currentTimeMillis()
        val result = selectNextPage(BrowserState.domainsList, now)
        if (result != null) {
          val (selDomain, selPage, updatedDomains) = result
          BrowserState.domainsList = updatedDomains
          activeDomainObject = selDomain
          activePageObject = selPage
          val fullUrl = selDomain.url + selPage.path
          currentUrl = fullUrl
          webViewInstance?.loadUrl(fullUrl)
          android.widget.Toast.makeText(
            context,
            "Feed Selected: ${selPage.name} (Domain: %.2f, Page: %.2f)".format(selDomain.affinityScore, selPage.affinityScore),
            android.widget.Toast.LENGTH_SHORT
          ).show()
        } else {
          BrowserState.domainsList = BrowserState.domainsList.map { d ->
            d.copy(snoozeTimestamp = null, pages = d.pages.map { p -> p.copy(snoozeTimestamp = null) })
          }
          android.widget.Toast.makeText(context, "All feeds are snoozed! Cleared all snoozes. Try again.", android.widget.Toast.LENGTH_SHORT).show()
        }
      }
      BrowserAction.REFRESH -> webViewInstance?.reload()
      BrowserAction.GO_BACK -> webViewInstance?.goBack()
      BrowserAction.GO_FORWARD -> webViewInstance?.goForward()
    }
  }

  // Auto-detect and bind current url to matching domain/page in state
  LaunchedEffect(currentUrl) {
    var foundMatch = false
    for (domain in BrowserState.domainsList) {
      if (currentUrl.startsWith(domain.url, ignoreCase = true)) {
        val path = currentUrl.substring(domain.url.length)
        val matchedPage = domain.pages.find { page -> 
          path.trimEnd('/').equals(page.path.trimEnd('/'), ignoreCase = true) || 
          page.path.trimEnd('/').equals(path.trimEnd('/'), ignoreCase = true) ||
          currentUrl.endsWith(page.path, ignoreCase = true)
        }
        if (matchedPage != null) {
          activeDomainObject = domain
          activePageObject = matchedPage
          foundMatch = true
          break
        }
      }
    }
    if (!foundMatch) {
      activeDomainObject = null
      activePageObject = null
    }
  }

  var activeBookmarkId by remember { mutableIntStateOf(1) }
  var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
  var showGoToUrlDialog by remember { mutableStateOf(false) }
  val frictionThreshold = 250f
  var isGestureActive by remember { mutableStateOf(true) }
  val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
  val scope = rememberCoroutineScope()

  val offsetX = remember { Animatable(0f) }
  val offsetY = remember { Animatable(0f) }
  val rotation = remember { Animatable(0f) }

  val config = LocalConfiguration.current
  val density = LocalDensity.current
  val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
  val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }

  var isAnimating by remember { mutableStateOf(false) }

  // Multi-stage hold logic
  var holdJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
  val startHoldTimer = {
    holdJob?.cancel()
    holdJob = scope.launch {
      delay(500)
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      delay(500)
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      // Add to favorites logic
      currentUrl.let { url ->
        BrowserState.addPageToFeed(url, pageTitle) // Or a specific favorites list
        android.widget.Toast.makeText(context, "Added to Favorites!", android.widget.Toast.LENGTH_SHORT).show()
      }
    }
  }
  val cancelHoldTimer = {
    holdJob?.cancel()
    holdJob = null
  }

  // Animation Sequences
  val triggerNextPageVertical = {
    if (!isAnimating) {
      isAnimating = true
      scope.launch {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Slide OUT top
        offsetY.animateTo(-screenHeightPx, animationSpec = tween(300))
        dispatchAction(BrowserAction.NEXT_PAGE, "Swipe Feed")
        // Prepare at bottom
        offsetY.snapTo(screenHeightPx)
        // Slide IN from bottom
        offsetY.animateTo(0f, animationSpec = tween(300))
        isAnimating = false
      }
    }
  }

  val triggerDownvoteHorizontal = {
    if (!isAnimating) {
      isAnimating = true
      scope.launch {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Fly off Left
        offsetX.animateTo(-screenWidthPx, animationSpec = tween(300))
        rotation.animateTo(-15f, animationSpec = tween(300))
        dispatchAction(BrowserAction.DOWNVOTE, "Gesture Downvote")
        dispatchAction(BrowserAction.NEXT_PAGE, "Swipe Feed")
        // Reset horizontal
        offsetX.snapTo(0f)
        rotation.snapTo(0f)
        // Enter from bottom
        offsetY.snapTo(screenHeightPx)
        offsetY.animateTo(0f, animationSpec = tween(300))
        isAnimating = false
      }
    }
  }

  val triggerUpvoteHorizontal = {
    if (!isAnimating) {
      isAnimating = true
      scope.launch {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        dispatchAction(BrowserAction.UPVOTE, "Gesture Upvote")
        // Spring back to center
        launch { offsetX.animateTo(0f, animationSpec = spring()) }
        launch { rotation.animateTo(0f, animationSpec = spring()) }
        isAnimating = false
      }
    }
  }

  BackHandler(enabled = webViewInstance?.canGoBack() == true) {
    webViewInstance?.goBack()
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      Surface(
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
      ) {
        Column {
          Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
          Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { dispatchAction(BrowserAction.GO_BACK, "Manual Back") }, enabled = webViewInstance?.canGoBack() == true) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
              }
              IconButton(onClick = { dispatchAction(BrowserAction.GO_FORWARD, "Manual Forward") }, enabled = webViewInstance?.canGoForward() == true) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Forward")
              }
            }
            Text(text = "malachite", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { isGestureActive = !isGestureActive }) {
                Icon(imageVector = if (isGestureActive) Icons.Default.Lock else Icons.Default.Language, contentDescription = "Toggle Gestures")
              }
              IconButton(onClick = { dispatchAction(BrowserAction.REFRESH, "Manual Refresh") }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
              }
              IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
              }
            }
          }
          if (isPageLoading) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
          } else {
            Box(modifier = Modifier.height(2.dp))
          }
        }
      }
    },
    bottomBar = {
      Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          bookmarks.forEach { bookmark ->
            val isActive = activeBookmarkId == bookmark.id
            val icon = when (bookmark.id) {
              1 -> Icons.Default.Favorite
              2 -> Icons.Default.Cloud
              3 -> Icons.Default.Add
              4 -> Icons.AutoMirrored.Filled.MenuBook
              5 -> Icons.Default.Settings
              else -> Icons.Default.Warning
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(56.dp).height(36.dp).clip(CircleShape).background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
                  .combinedClickable(
                    onClick = {
                      activeBookmarkId = bookmark.id
                      when (bookmark.id) {
                        1 -> context.startActivity(Intent(context, FavoritesListingActivity::class.java))
                        2 -> { /* Cloud badge info */ }
                        3 -> context.startActivity(Intent(context, AddActivity::class.java))
                        4 -> context.startActivity(Intent(context, HistoryActivity::class.java))
                        5 -> context.startActivity(Intent(context, SettingsActivity::class.java))
                        else -> {
                          lastNavigationContext = "Bookmark ${bookmark.id}"
                          currentUrl = bookmark.url
                          webViewInstance?.loadUrl(bookmark.url)
                        }
                      }
                    },
                    onLongClick = { editingBookmark = bookmark }
                  )
              ) {
                if (bookmark.id == 2) {
                  BadgedBox(badge = { if (cloudValue > 0) Badge { Text(if (cloudValue > 99) "99+" else cloudValue.toString()) } }) {
                    Icon(imageVector = icon, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                  }
                } else {
                  Icon(imageVector = icon, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
              }
            }
          }
        }
      }
    }
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        AndroidView(
          factory = { context ->
            object : WebView(context) {
              private var lastY = 0f
              private var lastX = 0f
              override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (isGestureActive && !isAnimating) {
                  when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                      lastY = event.rawY
                      lastX = event.rawX
                      startHoldTimer()
                    }
                    MotionEvent.ACTION_MOVE -> {
                      val deltaY = lastY - event.rawY
                      val deltaX = event.rawX - lastX
                      lastY = event.rawY
                      lastX = event.rawX

                      if (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10) {
                        cancelHoldTimer()
                      }

                      // Horizontal Swipe (Tinder Card)
                      if (kotlin.math.abs(offsetX.value) > 10 || (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.5)) {
                        scope.launch {
                          offsetX.snapTo(offsetX.value + deltaX)
                          rotation.snapTo(offsetX.value / 25f)
                        }
                        return true
                      }

                      // Vertical Friction (TikTok Transition)
                      if (!canScrollVertically(1) && deltaY > 0) {
                        scope.launch { offsetY.snapTo(offsetY.value - deltaY * 0.6f) }
                        if (kotlin.math.abs(offsetY.value) > frictionThreshold) {
                          triggerNextPageVertical()
                        }
                        return true
                      } else if (offsetY.value < 0 && deltaY < 0) {
                        scope.launch { offsetY.snapTo(minOf(0f, offsetY.value - deltaY)) }
                        return true
                      }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                      cancelHoldTimer()
                      if (offsetX.value > 200) {
                        triggerUpvoteHorizontal()
                      } else if (offsetX.value < -200) {
                        triggerDownvoteHorizontal()
                      } else {
                        scope.launch {
                          launch { offsetX.animateTo(0f, spring()) }
                          launch { rotation.animateTo(0f, spring()) }
                        }
                      }
                      
                      if (kotlin.math.abs(offsetY.value) < frictionThreshold) {
                        scope.launch { offsetY.animateTo(0f, spring()) }
                      }
                    }
                  }
                }
                return super.dispatchTouchEvent(event)
              }
            }.apply {
              layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  super.onPageStarted(view, url, favicon)
                  isPageLoading = true
                  url?.let { newUrl ->
                    BrowserState.lastNavigationUrl?.let { oldUrl ->
                        val duration = System.currentTimeMillis() - lastPageStartTime
                        BrowserState.history.find { it.url == oldUrl && it.duration == 0L }?.let { it.duration = duration }
                    }
                    lastPageStartTime = System.currentTimeMillis()
                    BrowserState.lastNavigationUrl = newUrl
                    BrowserState.history.add(0, HistoryEntry(url = newUrl, title = view?.title ?: "Loading...", timestamp = lastPageStartTime, parentContext = lastNavigationContext, isFromSwipe = lastNavigationContext == "Swipe Feed"))
                    currentUrl = newUrl
                  }
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                  super.onPageFinished(view, url)
                  isPageLoading = false
                  url?.let { finishedUrl ->
                    BrowserState.history.find { it.url == finishedUrl && it.title == "Loading..." }?.let {
                        val index = BrowserState.history.indexOf(it)
                        BrowserState.history[index] = it.copy(title = view?.title ?: finishedUrl)
                    }
                  }
                }
              }
              webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                override fun onReceivedTitle(view: WebView?, title: String?) { title?.let { pageTitle = it } }
              }
              loadUrl(currentUrl)
              webViewInstance = this
            }
          },
          update = { _ -> },
          modifier = Modifier.fillMaxSize().graphicsLayer {
            translationX = offsetX.value
            translationY = offsetY.value
            rotationZ = rotation.value
            cameraDistance = 8 * density.density
          }
        )
      }
    }
  }

  // Edit Dial Dialog
  editingBookmark?.let { bookmark ->
    var tempName by remember { mutableStateOf(bookmark.name) }
    var tempUrl by remember { mutableStateOf(bookmark.url) }
    AlertDialog(
      onDismissRequest = { editingBookmark = null },
      title = { Text("Edit Dial") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = tempName, onValueChange = { tempName = it }, label = { Text("Name") })
          OutlinedTextField(value = tempUrl, onValueChange = { tempUrl = it }, label = { Text("URL") })
        }
      },
      confirmButton = {
        Button(onClick = {
            val updated = bookmark.copy(name = tempName, url = if (tempUrl.startsWith("http")) tempUrl else "https://$tempUrl")
            bookmarks = bookmarks.map { if (it.id == bookmark.id) updated else it }
            saveBookmark(sharedPrefs, updated)
            editingBookmark = null
        }) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { editingBookmark = null }) { Text("Cancel") } }
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
