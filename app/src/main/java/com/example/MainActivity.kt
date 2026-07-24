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
 import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoRuntime
import android.webkit.CookieManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import android.app.Activity

// Core actions for universal mapping
enum class BrowserAction {
  UPVOTE, DOWNVOTE, SNOOZE, NEXT_PAGE, REFRESH, GO_BACK, GO_FORWARD, REMOVE_PAGE, FAVORITE_PAGE
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
    
    // Load buffer settings
    BrowserState.forwardBufferCount = prefs.getInt("forward_buffer_count", 3)
    BrowserState.historyBufferCount = prefs.getInt("history_buffer_count", 5)

    // Load persisted database
    PersistenceManager.load(this)

    setContent {
      MyApplicationTheme {
        BrowserApp(isFirstLaunch = isFirstLaunch)
      }
    }
  }
  override fun onPause() {
    super.onPause()
    PersistenceManager.save(this)
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
  currentTime: Long,
  excludeUrl: String? = null
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
    // Calculate Effective Weights for Domains
    val weights = remainingDomains.map { domain ->
        maxOf(0.01f, domain.affinityScore + domain.settings.modifier + BrowserState.globalSettings.modifier)
    }
    
    val totalDomainWeight = weights.sum().toDouble()
    if (totalDomainWeight <= 0) break
    
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
    val page = pickWeightedPageForDomain(selectedDomain, currentTime, excludeUrl)
    if (page != null) {
      return Triple(selectedDomain, page, updatedDomains)
    } else {
      // Fallback: loop back and pick a new Domain
      remainingDomains = remainingDomains - selectedDomain
    }
  }
  return null
}

fun pickWeightedPageForDomain(domain: DomainObject, currentTime: Long, excludeUrl: String? = null): PageObject? {
  val validPages = domain.pages.filter { page ->
    val fullUrl = domain.url + page.path
    (page.snoozeTimestamp == null || page.snoozeTimestamp <= currentTime) && 
    (excludeUrl == null || !fullUrl.equals(excludeUrl, ignoreCase = true))
  }
  if (validPages.isEmpty()) return null
  
  // Calculate Effective Weights for Pages
  val weights = validPages.map { page ->
      val multiplier = BrowserState.groupSettings[page.assignedColorGroup]?.multiplier ?: 1.0f
      maxOf(0.01f, (page.affinityScore + page.settings.modifier + BrowserState.globalSettings.modifier) * multiplier)
  }
  
  val totalPageWeight = weights.sum().toDouble()
  if (totalPageWeight <= 0) return validPages.random()
  
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

fun getNextSessionEntry(context: Context, excludeUrl: String? = null): SessionEntry? {
    val result = selectNextPage(BrowserState.domainsList, System.currentTimeMillis(), excludeUrl)
    return result?.let { (selDomain, selPage, _) ->
        val fullUrl = selDomain.url + selPage.path
        val session = GeckoEngineManager.createSession(context)
        session.loadUri(fullUrl)
        SessionEntry(url = fullUrl, session = session)
    }
}

// History tracking constants
private var lastPageStartTime = System.currentTimeMillis()
private var lastNavigationContext = "Direct"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserApp(isFirstLaunch: Boolean = false) {
  val context = LocalContext.current
  val sharedPrefs = remember { context.getSharedPreferences("minima_browser_prefs", Context.MODE_PRIVATE) }
  val scope = rememberCoroutineScope()
  val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

  // Buffer State
  val pagerState = rememberPagerState(pageCount = { BrowserState.sessionsBuffer.size })
  
  // Current page indicators derived from buffer
  val currentEntry = if (pagerState.currentPage < BrowserState.sessionsBuffer.size) {
    BrowserState.sessionsBuffer[pagerState.currentPage]
  } else null

  val pageTitle = currentEntry?.title ?: "Loading..."
  val progress = currentEntry?.progress ?: 0
  val isPageLoading = currentEntry?.isLoading ?: false
  
  var cloudValue by remember { mutableIntStateOf(0) }
  LaunchedEffect(BrowserState.domainsList) {
      while(true) {
          cloudValue = BrowserState.domainsList.sumOf { d -> 
              d.pages.count { it.snoozeTimestamp != null && it.snoozeTimestamp > System.currentTimeMillis() } 
          }
          delay(1000)
      }
  }
  
  var ignoreReadTrackingForPage by remember { mutableStateOf<String?>(null) }
  var currentUrlTimeSeconds by remember { mutableIntStateOf(0) }
  var currentUrlScrollPx by remember { mutableIntStateOf(0) }

  // Initial Buffer Population
  LaunchedEffect(Unit) {
    if (BrowserState.sessionsBuffer.isEmpty()) {
      // 1. History Buffer (Placeholders as requested)
      repeat(BrowserState.historyBufferCount) {
        val lastUrl = BrowserState.sessionsBuffer.lastOrNull()?.url
        getNextSessionEntry(context, excludeUrl = lastUrl)?.let { BrowserState.sessionsBuffer.add(it) }
      }
      
      // 2. Current Page
      val initialUrl = if (isFirstLaunch) "malachite://welcome" else "https://www.google.com"
      val initialSession = GeckoEngineManager.createSession(context)
      initialSession.loadUri(initialUrl)
      BrowserState.sessionsBuffer.add(SessionEntry(url = initialUrl, session = initialSession))
      
      // 3. Forward Buffer
      repeat(BrowserState.forwardBufferCount) {
        val lastUrl = BrowserState.sessionsBuffer.lastOrNull()?.url
        getNextSessionEntry(context, excludeUrl = lastUrl)?.let { BrowserState.sessionsBuffer.add(it) }
      }
      
      pagerState.scrollToPage(BrowserState.historyBufferCount)
    }
  }

  // Buffer Maintenance
  LaunchedEffect(pagerState.currentPage) {
    val remainingForward = BrowserState.sessionsBuffer.size - 1 - pagerState.currentPage
    if (remainingForward < BrowserState.forwardBufferCount) {
      repeat(BrowserState.forwardBufferCount - remainingForward) {
        val lastUrl = BrowserState.sessionsBuffer.lastOrNull()?.url
        getNextSessionEntry(context, excludeUrl = lastUrl)?.let { BrowserState.sessionsBuffer.add(it) }
      }
    }
    
    // Sync active objects for voting on current page
    if (pagerState.currentPage < BrowserState.sessionsBuffer.size) {
        val currentUrl = BrowserState.sessionsBuffer[pagerState.currentPage].url
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
  }

  // Observe navigation requests
  LaunchedEffect(BrowserState.currentUrlToLoad) {
    BrowserState.currentUrlToLoad?.let { url ->
      if (pagerState.currentPage < BrowserState.sessionsBuffer.size) {
        BrowserState.sessionsBuffer[pagerState.currentPage].session.loadUri(url)
      }
      BrowserState.currentUrlToLoad = null
    }
  }

  // Bookmarks
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
  var contextMenuData by remember { mutableStateOf<String?>(null) }

  // Dispatcher
  val dispatchAction = { action: BrowserAction, contextStr: String, overrideSnoozeMinutes: Int? ->
    lastNavigationContext = contextStr
    val session = if (pagerState.currentPage < BrowserState.sessionsBuffer.size) BrowserState.sessionsBuffer[pagerState.currentPage].session else null
    
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
                android.widget.Toast.makeText(context, "Upvoted!", android.widget.Toast.LENGTH_SHORT).show()
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
                android.widget.Toast.makeText(context, "Downvoted!", android.widget.Toast.LENGTH_SHORT).show()
                updatedDomain
              } else domain
            }
          }
        }
      }
      BrowserAction.SNOOZE -> {
        val now = System.currentTimeMillis()
        BrowserState.activePageObject?.let { activePage ->
          val minutes = overrideSnoozeMinutes ?: activePage.settings.snoozeMinutes
          val durationMs = minutes.toLong() * 60 * 1000
          ignoreReadTrackingForPage = activePage.path
          BrowserState.activeDomainObject?.let { activeDomain ->
            BrowserState.domainsList = BrowserState.domainsList.map { domain ->
              if (domain.url.equals(activeDomain.url, ignoreCase = true)) {
                val updatedPages = domain.pages.map { page ->
                  if (page.path.equals(activePage.path, ignoreCase = true)) {
                    page.copy(snoozeTimestamp = now + durationMs, snoozeCount = page.snoozeCount + 1, successiveReadCount = 0)
                  } else page
                }
                val updatedDomain = domain.copy(pages = updatedPages)
                BrowserState.activeDomainObject = updatedDomain
                BrowserState.activePageObject = updatedDomain.pages.find { it.path.equals(activePage.path, ignoreCase = true) }
                updatedDomain
              } else domain
            }
          }
        }
      }
      BrowserAction.NEXT_PAGE -> {
        scope.launch {
          if (pagerState.currentPage < BrowserState.sessionsBuffer.size - 1) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
          }
        }
      }
      BrowserAction.REFRESH -> session?.reload()
      BrowserAction.GO_BACK -> session?.goBack()
      BrowserAction.GO_FORWARD -> session?.goForward()
      BrowserAction.REMOVE_PAGE -> {
        BrowserState.activePageObject?.let { activePage ->
          BrowserState.activeDomainObject?.let { activeDomain ->
             BrowserState.domainsList = BrowserState.domainsList.map { domain ->
               if (domain.url.equals(activeDomain.url, ignoreCase = true)) {
                 val updatedPages = domain.pages.filter { !it.path.equals(activePage.path, ignoreCase = true) }
                 domain.copy(pages = updatedPages)
               } else domain
             }
             android.widget.Toast.makeText(context, "Removed from feed.", android.widget.Toast.LENGTH_SHORT).show()
             scope.launch {
               if (pagerState.currentPage < BrowserState.sessionsBuffer.size - 1) {
                 pagerState.animateScrollToPage(pagerState.currentPage + 1)
               }
             }
          }
        }
      }
      BrowserAction.FAVORITE_PAGE -> {
        val currentUrl = if (pagerState.currentPage < BrowserState.sessionsBuffer.size) BrowserState.sessionsBuffer[pagerState.currentPage].url else ""
        BrowserState.addPageToFeed(currentUrl, pageTitle, AppColorGroup.BLUE, modifier = 1.0f)
        android.widget.Toast.makeText(context, "Saved to Favorites!", android.widget.Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Horizontal Swipe Animation States
  val offsetX = remember { Animatable(0f) }
  val rotation = remember { Animatable(0f) }
  val config = LocalConfiguration.current
  val screenWidthPx = with(LocalDensity.current) { config.screenWidthDp.dp.toPx() }

  // Enforcement Timer
  LaunchedEffect(isPageLoading) {
    if (!isPageLoading) {
        BrowserState.isLimitReached = false
        while (true) {
            delay(1000)
            currentUrlTimeSeconds += 1
            BrowserState.activeDomainObject?.let { domain ->
                val multiplier = BrowserState.groupSettings[domain.defaultColorGroup]?.multiplier ?: 1.0f
                val effectiveTimeLimit = domain.customTimeLimitSeconds ?: (BrowserState.globalTimeLimitSeconds * multiplier).toInt()
                if (currentUrlTimeSeconds >= effectiveTimeLimit) {
                    BrowserState.isLimitReached = true
                }
            }
        }
    }
  }

  BackHandler(enabled = true) {
    if (pagerState.currentPage < BrowserState.sessionsBuffer.size) {
      BrowserState.sessionsBuffer[pagerState.currentPage].session.goBack()
    }
  }

  val isWide = config.screenWidthDp > config.screenHeightDp * 1.5

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
      Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
          bookmarks.forEachIndexed { index, bookmark ->
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
                modifier = Modifier.width(36.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
                  .combinedClickable(
                    onClick = {
                      activeBookmarkId = bookmark.id
                      when (bookmark.id) {
                        1 -> context.startActivity(Intent(context, FavoritesListingActivity::class.java))
                        3 -> {
                          val url = if (pagerState.currentPage < BrowserState.sessionsBuffer.size) BrowserState.sessionsBuffer[pagerState.currentPage].url else ""
                          context.startActivity(Intent(context, AddActivity::class.java).apply {
                              putExtra("PREFILL_URL", url)
                              putExtra("PREFILL_TITLE", pageTitle)
                          })
                        }
                        4 -> context.startActivity(Intent(context, HistoryActivity::class.java))
                        5 -> context.startActivity(Intent(context, SettingsActivity::class.java))
                        else -> {
                          lastNavigationContext = "Bookmark ${bookmark.id}"
                          if (pagerState.currentPage < BrowserState.sessionsBuffer.size) {
                            BrowserState.sessionsBuffer[pagerState.currentPage].session.loadUri(bookmark.url)
                          }
                        }
                      }
                    },
                    onLongClick = { editingBookmark = bookmark }
                  )
              ) {
                if (bookmark.id == 2) {
                  BadgedBox(badge = { if (cloudValue > 0) Badge { Text(if (cloudValue > 99) "99+" else cloudValue.toString(), fontSize = 7.sp) } }) {
                    Icon(imageVector = icon, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                  }
                } else if (bookmark.id == 5) {
                  val snoozeCount = BrowserState.activePageObject?.snoozeCount ?: 0
                  BadgedBox(badge = { if (snoozeCount > 0) Badge { Text(snoozeCount.toString(), fontSize = 7.sp) } }) {
                    Icon(imageVector = icon, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                  }
                } else {
                  Icon(imageVector = icon, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
              }
            }
            
            // Interleave Secondary Buttons in Landscape
            if (isWide && index < bookmarks.size - 1) {
                val secIcon = when(index) {
                    0 -> Icons.Default.ThumbDown
                    1 -> Icons.Default.KeyboardArrowDown
                    2 -> Icons.Default.Star
                    3 -> Icons.Default.ThumbUp
                    else -> null
                }
                val secAction = when(index) {
                    0 -> BrowserAction.DOWNVOTE
                    1 -> BrowserAction.NEXT_PAGE
                    2 -> BrowserAction.FAVORITE_PAGE
                    3 -> BrowserAction.UPVOTE
                    else -> null
                }
                
                if (secIcon != null && secAction != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.6f)) {
                        IconButton(onClick = { dispatchAction(secAction, "Landscape Bar", null) }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = secIcon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
          }
        }
      }
    }
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding).windowInsetsPadding(WindowInsets.statusBars)) {
      VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = BrowserState.historyBufferCount // Keep history pages alive
      ) { pageIndex ->
        val entry = BrowserState.sessionsBuffer[pageIndex]
        
        Box(
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
              if (pageIndex == pagerState.currentPage) {
                translationX = offsetX.value
                rotationZ = rotation.value
              }
            }
            .pointerInput(Unit) {
              detectHorizontalDragGestures(
                onDragEnd = {
                  if (offsetX.value > 200) {
                    scope.launch {
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                      dispatchAction(BrowserAction.UPVOTE, "Gesture Upvote", null)
                      if (BrowserState.swipeRightNext) {
                        offsetX.animateTo(screenWidthPx)
                        dispatchAction(BrowserAction.NEXT_PAGE, "Swipe Right", null)
                        offsetX.snapTo(0f)
                        rotation.snapTo(0f)
                      } else {
                        offsetX.animateTo(0f, spring())
                        rotation.animateTo(0f, spring())
                      }
                    }
                  } else if (offsetX.value < -200) {
                    scope.launch {
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                      dispatchAction(BrowserAction.DOWNVOTE, "Gesture Downvote", null)
                      if (BrowserState.swipeLeftNext) {
                        offsetX.animateTo(-screenWidthPx)
                        dispatchAction(BrowserAction.NEXT_PAGE, "Swipe Left", null)
                        offsetX.snapTo(0f)
                        rotation.snapTo(0f)
                      } else {
                        offsetX.animateTo(0f, spring())
                        rotation.animateTo(0f, spring())
                      }
                    }
                  } else {
                    scope.launch {
                      offsetX.animateTo(0f, spring())
                      rotation.animateTo(0f, spring())
                    }
                  }
                },
                onHorizontalDrag = { change, dragAmount ->
                  change.consume()
                  scope.launch {
                    offsetX.snapTo(offsetX.value + dragAmount)
                    rotation.snapTo(offsetX.value / 40f)
                  }
                }
              )
            }
        ) {
          if (entry.url == "malachite://welcome") {
            WelcomePageContent(onNavigate = { url ->
              entry.session.loadUri(url)
            })
          } else {
            AndroidView(
              factory = { context ->
                GeckoView(context).apply {
                  setSession(entry.session)
                  
                  // Task 1: Vertical Swipe Fix (Swipe UP at bottom for Next Page)
                  var startY = 0f
                  setOnTouchListener { v, event ->
                    when (event.action) {
                      MotionEvent.ACTION_DOWN -> {
                        startY = event.y
                        false
                      }
                      MotionEvent.ACTION_UP -> {
                        val deltaY = startY - event.y
                        if (deltaY > 150 && !v.canScrollVertically(1)) {
                          scope.launch {
                            dispatchAction(BrowserAction.SNOOZE, "Swipe Up", null)
                            if (pagerState.currentPage < BrowserState.sessionsBuffer.size - 1) {
                              pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                          }
                          true
                        } else {
                          false
                        }
                      }
                      else -> false
                    }
                  }
                  
                  entry.session.progressDelegate = object : GeckoSession.ProgressDelegate {
                    override fun onProgressChange(session: GeckoSession, p: Int) {
                      BrowserState.sessionsBuffer[pageIndex] = BrowserState.sessionsBuffer[pageIndex].copy(progress = p)
                    }
                    override fun onPageStart(session: GeckoSession, url: String) {
                      BrowserState.sessionsBuffer[pageIndex] = BrowserState.sessionsBuffer[pageIndex].copy(isLoading = true)
                    }
                    override fun onPageStop(session: GeckoSession, success: Boolean) {
                      BrowserState.sessionsBuffer[pageIndex] = BrowserState.sessionsBuffer[pageIndex].copy(isLoading = false)
                    }
                  }
                  
                  entry.session.contentDelegate = object : GeckoSession.ContentDelegate {
                    override fun onTitleChange(session: GeckoSession, title: String?) {
                      title?.let {
                        BrowserState.sessionsBuffer[pageIndex] = BrowserState.sessionsBuffer[pageIndex].copy(title = it)
                      }
                    }
                  }
                  
                  entry.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
                    override fun onLoadRequest(session: GeckoSession, request: org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadRequest): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                        if (request.uri.startsWith("about:") || request.uri.startsWith("malachite:")) {
                            return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
                        }
                        return null
                    }

                    override fun onLocationChange(session: GeckoSession, url: String?, permissions: List<org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission>) {
                      url?.let { newUrl ->
                        if (newUrl == "about:blank") return@let
                        
                        // Update entry in buffer
                        BrowserState.sessionsBuffer[pageIndex] = entry.copy(url = newUrl)
                        
                        if (pageIndex == pagerState.currentPage) {
                            val isFromSwipe = lastNavigationContext.startsWith("Swipe")
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
                            BrowserState.history.add(0, HistoryEntry(url = newUrl, title = pageTitle, timestamp = lastPageStartTime, parentContext = lastNavigationContext, isFromSwipe = isFromSwipe))
                        }
                      }
                    }
                  }
                }
              },
              modifier = Modifier.fillMaxSize(),
              update = { /* session is stable */ }
            )
          }
        }
      }
      
      // Progress indicator overlay
      if (isPageLoading) {
        LinearProgressIndicator(
          progress = { progress / 100f },
          modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter)
        )
      }

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
                              val currentUrl = if (pagerState.currentPage < BrowserState.sessionsBuffer.size) BrowserState.sessionsBuffer[pagerState.currentPage].url else ""
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
      val currentUrl = if (pagerState.currentPage < BrowserState.sessionsBuffer.size) BrowserState.sessionsBuffer[pagerState.currentPage].url else ""
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
                              // Note: geckoViewInstance might need to be tracked per page now, but keeping simple for now
                              contextMenuData = null
                          } else {
                              android.widget.Toast.makeText(context, "AutoFill requires Android 8.0+", android.widget.Toast.LENGTH_SHORT).show()
                          }
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
