package com.example

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.View
import android.view.autofill.AutofillManager
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

    // First launch check
    val prefs = getSharedPreferences("minima_browser_prefs", Context.MODE_PRIVATE)
    val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
    if (isFirstLaunch) {
      prefs.edit().putBoolean("is_first_launch", false).apply()
    }

    setContent {
      MyApplicationTheme {
        BrowserApp(isFirstLaunch = isFirstLaunch)
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
    // Calculate Effective Weights for Domains: Soft + Domain Hard + Global Offset
    val weights = remainingDomains.map { domain ->
        maxOf(0.01f, domain.affinityScore + domain.settings.modifier + BrowserState.globalSettings.modifier)
    }
    
    val totalDomainWeight = weights.sum().toDouble()
    
    val randomValue = kotlin.random.Random.nextDouble() * totalDomainWeight
    var accumulatedWeight = 0.0
    var selectedDomain: DomainObject? = null
    for (i in remainingDomains.indices) {
      accumulatedWeight += weights[i]
      if (randomValue <= accumulatedWeight) {
        selectedDomain = remainingDomains[i]
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
  
  // Calculate Effective Weights for Pages: (Soft + Page Hard + Global Offset) * Group Multiplier
  val weights = validPages.map { page ->
      val multiplier = BrowserState.groupSettings[page.assignedColorGroup]?.first ?: 1.0f
      maxOf(0.01f, (page.affinityScore + page.settings.modifier + BrowserState.globalSettings.modifier) * multiplier)
  }
  
  val totalPageWeight = weights.sum().toDouble()
  
  val randomValue = kotlin.random.Random.nextDouble() * totalPageWeight
  var accumulatedWeight = 0.0
  for (i in validPages.indices) {
    accumulatedWeight += weights[i]
    if (randomValue <= accumulatedWeight) {
      return validPages[i]
    }
  }
  return validPages.last()
}

// History tracking constants
private var lastPageStartTime = System.currentTimeMillis()
private var lastNavigationContext = "Direct"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserApp(isFirstLaunch: Boolean = false) {
  val context = LocalContext.current
  val sharedPrefs = remember { context.getSharedPreferences("minima_browser_prefs", Context.MODE_PRIVATE) }

  var currentUrl by remember { mutableStateOf(if (isFirstLaunch) "malachite://welcome" else "https://www.google.com") }
  var pageTitle by remember { mutableStateOf("Loading...") }
  var progress by remember { mutableIntStateOf(0) }
  var isPageLoading by remember { mutableStateOf(false) }
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var cloudValue by remember { mutableIntStateOf(42) }
  var ignoreReadTrackingForPage by remember { mutableStateOf<String?>(null) }

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

  var activeBookmarkId by remember { mutableIntStateOf(1) }
  var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
  var contextMenuData by remember { mutableStateOf<String?>(null) } // URL if it's a link

  // --- Universal Dispatcher ---
  val dispatchAction = { action: BrowserAction, contextStr: String, overrideSnoozeMinutes: Int? ->
    lastNavigationContext = contextStr
    when (action) {
      BrowserAction.UPVOTE -> {
        val now = System.currentTimeMillis()
        BrowserState.activePageObject?.let { activePage ->
          BrowserState.activeDomainObject?.let { activeDomain ->
            BrowserState.domainsList = BrowserState.domainsList.map { domain ->
              if (domain.url.equals(activeDomain.url, ignoreCase = true)) {
                val updatedPages = domain.pages.map { page ->
                  if (page.path.equals(activePage.path, ignoreCase = true)) {
                    page.copy(affinityScore = minOf(2.0f, page.affinityScore + 0.02f), lastUpdated = now)
                  } else page
                }
                val updatedDomain = domain.copy(affinityScore = minOf(2.0f, domain.affinityScore + 0.02f), lastUpdated = now, pages = updatedPages)
                BrowserState.activeDomainObject = updatedDomain
                BrowserState.activePageObject = updatedDomain.pages.find { it.path.equals(activePage.path, ignoreCase = true) }
                android.widget.Toast.makeText(context, "Upvoted! Page: %.2f".format(BrowserState.activePageObject?.affinityScore ?: 0f), android.widget.Toast.LENGTH_SHORT).show()
                updatedDomain
              } else domain
            }
          }
        }
      }
      BrowserAction.DOWNVOTE -> {
        val now = System.currentTimeMillis()
        BrowserState.activePageObject?.let { activePage ->
          BrowserState.activeDomainObject?.let { activeDomain ->
            BrowserState.domainsList = BrowserState.domainsList.map { domain ->
              if (domain.url.equals(activeDomain.url, ignoreCase = true)) {
                val updatedPages = domain.pages.map { page ->
                  if (page.path.equals(activePage.path, ignoreCase = true)) {
                    page.copy(affinityScore = maxOf(0.0f, page.affinityScore - 0.02f), lastUpdated = now)
                  } else page
                }
                val updatedDomain = domain.copy(affinityScore = maxOf(0.0f, domain.affinityScore - 0.02f), lastUpdated = now, pages = updatedPages)
                BrowserState.activeDomainObject = updatedDomain
                BrowserState.activePageObject = updatedDomain.pages.find { it.path.equals(activePage.path, ignoreCase = true) }
                android.widget.Toast.makeText(context, "Downvoted! Page: %.2f".format(BrowserState.activePageObject?.affinityScore ?: 0f), android.widget.Toast.LENGTH_SHORT).show()
                updatedDomain
              } else domain
            }
          }
        }
      }
      BrowserAction.SNOOZE -> {
        val now = System.currentTimeMillis()
        BrowserState.activePageObject?.let { activePage ->
          val groupSnooze = BrowserState.groupSettings[activePage.assignedColorGroup]?.second ?: BrowserState.globalSettings.snoozeMinutes
          val minutes = overrideSnoozeMinutes ?: activePage.settings.snoozeMinutes
          val durationMs = minutes.toLong() * 60 * 1000
          
          ignoreReadTrackingForPage = activePage.path
          BrowserState.activeDomainObject?.let { activeDomain ->
            BrowserState.domainsList = BrowserState.domainsList.map { domain ->
              if (domain.url.equals(activeDomain.url, ignoreCase = true)) {
                val updatedPages = domain.pages.map { page ->
                  if (page.path.equals(activePage.path, ignoreCase = true)) {
                    page.copy(
                        snoozeTimestamp = now + durationMs,
                        snoozeCount = page.snoozeCount + 1,
                        successiveReadCount = 0
                    )
                  } else page
                }
                val updatedDomain = domain.copy(pages = updatedPages)
                BrowserState.activeDomainObject = updatedDomain
                BrowserState.activePageObject = updatedDomain.pages.find { it.path.equals(activePage.path, ignoreCase = true) }
                if (overrideSnoozeMinutes == null) {
                    android.widget.Toast.makeText(context, "Snoozed for ${minutes}m (Count: ${BrowserState.activePageObject?.snoozeCount})", android.widget.Toast.LENGTH_SHORT).show()
                }
                updatedDomain
              } else domain
            }
          }
        }
      }
      BrowserAction.NEXT_PAGE -> {
        val now = System.currentTimeMillis()
        val result = selectNextPage(BrowserState.domainsList, now)
        if (result != null) {
          val (selDomain, selPage, updatedDomains) = result
          BrowserState.domainsList = updatedDomains
          BrowserState.activeDomainObject = selDomain
          BrowserState.activePageObject = selPage
          val fullUrl = selDomain.url + selPage.path
          currentUrl = fullUrl
          webViewInstance?.loadUrl(fullUrl)
        } else {
          BrowserState.domainsList = BrowserState.domainsList.map { d ->
            d.copy(snoozeTimestamp = null, pages = d.pages.map { p -> p.copy(snoozeTimestamp = null) })
          }
          android.widget.Toast.makeText(context, "Cleared all snoozes.", android.widget.Toast.LENGTH_SHORT).show()
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
          BrowserState.activeDomainObject = domain
          BrowserState.activePageObject = matchedPage
          foundMatch = true
          break
        }
      }
    }
    if (!foundMatch) {
      BrowserState.activeDomainObject = null
      BrowserState.activePageObject = null
    }
  }

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
      currentUrl.let { url ->
        BrowserState.addPageToFeed(url, pageTitle)
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
        dispatchAction(BrowserAction.SNOOZE, "Swipe Feed", BrowserState.exitSnoozeMinutes)
        offsetY.animateTo(-screenHeightPx, animationSpec = tween(300))
        dispatchAction(BrowserAction.NEXT_PAGE, "Swipe Feed", null)
        offsetY.snapTo(screenHeightPx)
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
        dispatchAction(BrowserAction.SNOOZE, "Gesture Downvote", BrowserState.exitSnoozeMinutes)
        offsetX.animateTo(-screenWidthPx, animationSpec = tween(300))
        rotation.animateTo(-15f, animationSpec = tween(300))
        dispatchAction(BrowserAction.DOWNVOTE, "Gesture Downvote", null)
        dispatchAction(BrowserAction.NEXT_PAGE, "Swipe Feed", null)
        offsetX.snapTo(0f)
        rotation.snapTo(0f)
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
        dispatchAction(BrowserAction.UPVOTE, "Gesture Upvote", null)
        if (BrowserState.swipeRightNext) {
            dispatchAction(BrowserAction.SNOOZE, "Gesture Upvote", BrowserState.exitSnoozeMinutes)
            offsetX.animateTo(screenWidthPx, animationSpec = tween(300))
            dispatchAction(BrowserAction.NEXT_PAGE, "Swipe Feed", null)
            offsetX.snapTo(0f)
            offsetY.snapTo(screenHeightPx)
            offsetY.animateTo(0f, animationSpec = tween(300))
        } else {
            launch { offsetX.animateTo(0f, animationSpec = spring()) }
            launch { rotation.animateTo(0f, animationSpec = spring()) }
        }
        isAnimating = false
      }
    }
  }

  var currentUrlTimeSeconds by remember { mutableIntStateOf(0) }
  var currentUrlScrollPx by remember { mutableIntStateOf(0) }

  LaunchedEffect(isPageLoading) {
    if (!isPageLoading) {
        BrowserState.isLimitReached = false
        while (true) {
            delay(1000)
            currentUrlTimeSeconds += 1
            BrowserState.activeDomainObject?.let { domain ->
                val multiplier = BrowserState.groupSettings[domain.defaultColorGroup]?.first ?: 1.0f
                val effectiveTimeLimit = domain.customTimeLimitSeconds ?: (BrowserState.globalTimeLimitSeconds * multiplier).toInt()
                
                if (currentUrlTimeSeconds >= effectiveTimeLimit) {
                    BrowserState.isLimitReached = true
                }
            }
        }
    }
  }

  BackHandler(enabled = webViewInstance?.canGoBack() == true) {
    webViewInstance?.goBack()
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      Surface(tonalElevation = 4.dp, shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Column {
          Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
          Row(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { dispatchAction(BrowserAction.GO_BACK, "Manual Back", null) }, enabled = webViewInstance?.canGoBack() == true) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
              }
              IconButton(onClick = { dispatchAction(BrowserAction.GO_FORWARD, "Manual Forward", null) }, enabled = webViewInstance?.canGoForward() == true) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Forward")
              }
            }
            Text(text = "malachite", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { isGestureActive = !isGestureActive }) {
                Icon(imageVector = if (isGestureActive) Icons.Default.Lock else Icons.Default.Language, contentDescription = "Toggle Gestures")
              }
              IconButton(onClick = { dispatchAction(BrowserAction.REFRESH, "Manual Refresh", null) }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
              }
              IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
              }
            }
          }
          if (isPageLoading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
          else Box(modifier = Modifier.height(2.dp))
        }
      }
    },
    bottomBar = {
      Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
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
                } else if (bookmark.id == 5) {
                  val snoozeCount = BrowserState.activePageObject?.snoozeCount ?: 0
                  BadgedBox(badge = { if (snoozeCount > 0) Badge { Text(snoozeCount.toString()) } }) {
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
        if (currentUrl == "malachite://welcome") {
          WelcomePageContent(onNavigate = { url ->
            currentUrl = url
            webViewInstance?.loadUrl(url)
          })
        } else {
          AndroidView(
            factory = { context ->
            object : WebView(context) {
              private var lastY = 0f
              private var lastX = 0f
              private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                  if (BrowserState.doubleTapNext) { triggerNextPageVertical(); return true }
                  return false
                }
              })
              override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (BrowserState.isLimitReached) {
                    // Only allow vertical movement for the exit swipe
                    gestureDetector.onTouchEvent(event)
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { lastY = event.rawY }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaY = lastY - event.rawY
                            lastY = event.rawY
                            if (deltaY > 0) { // Swiping UP
                                scope.launch { offsetY.snapTo(offsetY.value - deltaY * 0.8f) }
                                if (kotlin.math.abs(offsetY.value) > frictionThreshold) triggerNextPageVertical()
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (kotlin.math.abs(offsetY.value) < frictionThreshold) scope.launch { offsetY.animateTo(0f, spring()) }
                        }
                    }
                    return true // Lock internal scrolling
                }
                
                if (isGestureActive && !isAnimating) {
                  gestureDetector.onTouchEvent(event)
                  when (event.action) {
                    MotionEvent.ACTION_DOWN -> { lastY = event.rawY; lastX = event.rawX; startHoldTimer() }
                    MotionEvent.ACTION_MOVE -> {
                      val deltaY = lastY - event.rawY
                      val deltaX = event.rawX - lastX
                      lastY = event.rawY
                      lastX = event.rawX
                      if (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10) cancelHoldTimer()
                      if (kotlin.math.abs(offsetX.value) > 10 || (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.5)) {
                        scope.launch { offsetX.snapTo(offsetX.value + deltaX); rotation.snapTo(offsetX.value / 25f) }
                        return true
                      }
                      if (!canScrollVertically(1) && deltaY > 0) {
                        scope.launch { offsetY.snapTo(offsetY.value - deltaY * 0.6f) }
                        if (kotlin.math.abs(offsetY.value) > frictionThreshold) triggerNextPageVertical()
                        return true
                      } else if (offsetY.value < 0 && deltaY < 0) {
                        scope.launch { offsetY.snapTo(minOf(0f, offsetY.value - deltaY)) }
                        return true
                      }
                      if (deltaY > 0) {
                          currentUrlScrollPx += deltaY.toInt()
                          BrowserState.activeDomainObject?.let { domain ->
                              val multiplier = BrowserState.groupSettings[domain.defaultColorGroup]?.first ?: 1.0f
                              val effectiveScrollLimit = domain.customScrollLimitPx ?: (BrowserState.globalScrollLimitPx * multiplier).toInt()
                              if (currentUrlScrollPx >= effectiveScrollLimit && !isAnimating) {
                                  BrowserState.isLimitReached = true
                              }
                          }
                      }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                      cancelHoldTimer()
                      if (offsetX.value > 200) triggerUpvoteHorizontal()
                      else if (offsetX.value < -200) triggerDownvoteHorizontal()
                      else scope.launch { launch { offsetX.animateTo(0f, spring()) }; launch { rotation.animateTo(0f, spring()) } }
                      if (kotlin.math.abs(offsetY.value) < frictionThreshold) scope.launch { offsetY.animateTo(0f, spring()) }
                    }
                  }
                }
                return super.dispatchTouchEvent(event)
              }
            }.apply {
              layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                  importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
              }
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              
              setOnLongClickListener { 
                  val hitTestResult = hitTestResult
                  val url = hitTestResult.extra
                  contextMenuData = url ?: "" // Empty string means "general page"
                  true
              }

              webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  super.onPageStarted(view, url, favicon)
                  isPageLoading = true
                  url?.let { newUrl ->
                    val isFromSwipe = lastNavigationContext == "Swipe Feed"
                    if (isFromSwipe) {
                        BrowserState.rootPageUrl = newUrl
                        currentUrlTimeSeconds = 0
                        currentUrlScrollPx = 0
                    }
                    
                    BrowserState.activePageObject?.let { prevPage ->
                        BrowserState.activeDomainObject?.let { prevDomain ->
                            if (ignoreReadTrackingForPage != prevPage.path) {
                                BrowserState.domainsList = BrowserState.domainsList.map { d ->
                                    if (d.url.equals(prevDomain.url, ignoreCase = true)) {
                                        val updatedPages = d.pages.map { p ->
                                            if (p.path.equals(prevPage.path, ignoreCase = true)) {
                                                val newReadCount = p.successiveReadCount + 1
                                                val newSnoozeCount = if (newReadCount >= 2) 0 else maxOf(0, p.snoozeCount - 1)
                                                p.copy(successiveReadCount = if (newReadCount >= 2) 0 else newReadCount, snoozeCount = newSnoozeCount)
                                            } else p
                                        }
                                        d.copy(pages = updatedPages)
                                    } else d
                                }
                            }
                        }
                    }
                    ignoreReadTrackingForPage = null
                    BrowserState.lastNavigationUrl?.let { oldUrl ->
                        val duration = System.currentTimeMillis() - lastPageStartTime
                        BrowserState.history.find { it.url == oldUrl && it.duration == 0L }?.let { it.duration = duration }
                    }
                    lastPageStartTime = System.currentTimeMillis()
                    BrowserState.lastNavigationUrl = newUrl
                    BrowserState.history.add(0, HistoryEntry(url = newUrl, title = view?.title ?: "Loading...", timestamp = lastPageStartTime, parentContext = lastNavigationContext, isFromSwipe = isFromSwipe))
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

        // Lock Overlay
        if (BrowserState.isLimitReached) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Icon(Icons.Default.TimerOff, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    Text("Session Limit Reached", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { 
                                BrowserState.addPageToFeed(
                                    url = currentUrl, 
                                    title = pageTitle, 
                                    group = BrowserState.activeDomainObject?.defaultColorGroup ?: AppColorGroup.BLUE
                                )
                                BrowserState.isLimitReached = false
                                android.widget.Toast.makeText(context, "Added to Feed!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                        ) {
                            Text("Follow Site")
                        }
                        Button(
                            onClick = { 
                                // For now, just clear the lock and toast
                                BrowserState.isLimitReached = false
                                android.widget.Toast.makeText(context, "Saved for Later!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Save for Later")
                        }
                    }
                    
                    Text("Swipe UP to load next item", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
      }
    }
  }
}

  editingBookmark?.let { bookmark ->
    var tempName by remember { mutableStateOf(bookmark.name) }
    var tempUrl by remember { mutableStateOf(bookmark.url) }
    AlertDialog(onDismissRequest = { editingBookmark = null }, title = { Text("Edit Dial") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = tempName, onValueChange = { tempName = it }, label = { Text("Name") })
          OutlinedTextField(value = tempUrl, onValueChange = { tempUrl = it }, label = { Text("URL") })
        }
      },
      confirmButton = { Button(onClick = {
            val updated = bookmark.copy(name = tempName, url = if (tempUrl.startsWith("http")) tempUrl else "https://$tempUrl")
            bookmarks = bookmarks.map { if (it.id == bookmark.id) updated else it }
            saveBookmark(sharedPrefs, updated); editingBookmark = null
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editingBookmark = null }) { Text("Cancel") } }
    )
  }

  // Context Menu for Long-Press (Bitwarden & Feed)
  contextMenuData?.let { url ->
      AlertDialog(
          onDismissRequest = { contextMenuData = null },
          title = { Text("Quick Actions", fontWeight = FontWeight.Bold) },
          text = { 
              Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                  if (url.isNotEmpty()) {
                      Text("Link: $url", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                  } else {
                      Text("Active Page: $currentUrl", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                  }
              }
          },
          confirmButton = {
              Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                      onClick = {
                          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                              val afm = context.getSystemService(AutofillManager::class.java)
                              afm?.requestAutofill(webViewInstance!!)
                          } else {
                              android.widget.Toast.makeText(context, "AutoFill requires Android 8.0+", android.widget.Toast.LENGTH_SHORT).show()
                          }
                          contextMenuData = null
                      },
                      modifier = Modifier.fillMaxWidth()
                  ) {
                      Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Text("Fill with Bitwarden")
                  }
                  
                  if (url.isNotEmpty()) {
                      OutlinedButton(
                          onClick = {
                              context.startActivity(Intent(context, AddActivity::class.java).apply {
                                  putExtra("PREFILL_URL", url)
                              })
                              contextMenuData = null
                          },
                          modifier = Modifier.fillMaxWidth()
                      ) {
                          Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                          Spacer(modifier = Modifier.width(8.dp))
                          Text("Add Link to Feed")
                      }
                  }
              }
          },
          dismissButton = {
              TextButton(onClick = { contextMenuData = null }) {
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
