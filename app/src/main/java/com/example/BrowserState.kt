package com.example

import androidx.compose.runtime.*

import org.mozilla.geckoview.GeckoSession

import com.squareup.moshi.JsonClass

enum class AppColorGroup {
    RED, YELLOW, GREEN, BLUE
}

@JsonClass(generateAdapter = true)
data class AffinitySettings(
    var modifier: Float = 0.0f,
    var snoozeMinutes: Int = 5
)

// Data structures for tracking and managing user affinity & snooze preferences per domain
@JsonClass(generateAdapter = true)
data class DomainObject(
  val url: String,
  var affinityScore: Float = 1.0f, // Soft votes
  val settings: AffinitySettings = AffinitySettings(),
  var defaultColorGroup: AppColorGroup = AppColorGroup.BLUE,
  val pages: List<PageObject> = emptyList(),
  val snoozeTimestamp: Long? = null,
  val lastUpdated: Long = System.currentTimeMillis(),
  val customTimeLimitSeconds: Int? = null,
  val customScrollLimitPx: Int? = null
)

@JsonClass(generateAdapter = true)
data class PageObject(
  val name: String,
  val path: String,
  var affinityScore: Float = 1.0f, // Soft votes
  val settings: AffinitySettings = AffinitySettings(),
  var assignedColorGroup: AppColorGroup = AppColorGroup.BLUE,
  val snoozeTimestamp: Long? = null,
  val snoozeCount: Int = 0,
  val successiveReadCount: Int = 0,
  val lastUpdated: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class HistoryEntry(
    val url: String,
    val title: String,
    val timestamp: Long,
    var duration: Long = 0,
    val parentContext: String = "Direct",
    val isFromSwipe: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    val email: String,
    val displayName: String
)

@JsonClass(generateAdapter = true)
data class GroupSettings(
    val multiplier: Float,
    val snoozeMinutes: Int
)

@JsonClass(generateAdapter = true)
data class BrowserDatabase(
    val domainsList: List<DomainObject>,
    val globalSettings: AffinitySettings,
    val history: List<HistoryEntry>,
    val swipeRightNext: Boolean,
    val swipeLeftNext: Boolean,
    val doubleTapNext: Boolean,
    val groupSettings: Map<AppColorGroup, GroupSettings>
)

data class SessionEntry(
    val url: String,
    val session: GeckoSession,
    var title: String = "Loading...",
    var progress: Int = 0,
    var isLoading: Boolean = false
)

object BrowserState {
    // Identity State
    var userProfile by mutableStateOf<UserProfile?>(null)

    // Global Defaults
    var globalSettings by mutableStateOf(AffinitySettings(modifier = 0f, snoozeMinutes = 5))
    
    // Global Limits
    var globalTimeLimitSeconds by mutableIntStateOf(300)
    var globalScrollLimitPx by mutableIntStateOf(5000)
    var exitSnoozeMinutes by mutableIntStateOf(5)

    // Buffer Settings
    var forwardBufferCount by mutableIntStateOf(3)
    var historyBufferCount by mutableIntStateOf(5)
    
    // Live Sessions Buffer
    val sessionsBuffer = mutableStateListOf<SessionEntry>()

    // UI Tweaks
    var swipeRightNext by mutableStateOf(false)
    var swipeLeftNext by mutableStateOf(true)
    var doubleTapNext by mutableStateOf(false)
    
    // Enforcement State
    var isLimitReached by mutableStateOf(false)
    var rootPageUrl by mutableStateOf<String?>(null)

    // Group / Channel Settings (Multipliers and Snooze)
    // Blue (Best), Green (Good), Yellow (Careful), Red (Addictive)
    var groupSettings by mutableStateOf(mapOf(
        AppColorGroup.BLUE to GroupSettings(2.0f, 1),    // Best: 2.0x probability, 1m guard
        AppColorGroup.GREEN to GroupSettings(1.5f, 5),   // Good: 1.5x probability, 5m guard
        AppColorGroup.YELLOW to GroupSettings(0.7f, 15), // Careful: 0.7x probability, 15m guard
        AppColorGroup.RED to GroupSettings(0.3f, 60)     // Addictive: 0.3x probability, 60m guard
    ))

    var domainsList by mutableStateOf(
      listOf(
        // --- Essential BLUE ---
        createDomain("https://chatgpt.com", AppColorGroup.BLUE, listOf(createPage("ChatGPT", ""))),
        createDomain("https://claude.ai", AppColorGroup.BLUE, listOf(createPage("Claude", ""))),
        
        // --- Essential GREEN ---
        createDomain("https://google.com", AppColorGroup.GREEN, listOf(createPage("Google", "")))
      )
    )

    val SuggestedLibrary = listOf(
        // --- MALACHITE SYSTEM ---
        createDomain("malachite://welcome", AppColorGroup.BLUE, listOf(createPage("Malachite Setup", ""))),

        // --- BLUE: BEST (AI, Dev, Reference) ---
        createDomain("https://gemini.google.com", AppColorGroup.BLUE, listOf(createPage("Gemini", ""))),
        createDomain("https://perplexity.ai", AppColorGroup.BLUE, listOf(createPage("Perplexity", ""))),
        createDomain("https://github.com", AppColorGroup.BLUE, listOf(createPage("GitHub", ""))),
        createDomain("https://gitlab.com", AppColorGroup.BLUE, listOf(createPage("GitLab", ""))),
        createDomain("https://stackoverflow.com", AppColorGroup.BLUE, listOf(createPage("Stack Overflow", ""))),
        createDomain("https://wikipedia.org", AppColorGroup.BLUE, listOf(createPage("Wikipedia", ""))),
        createDomain("https://archive.org", AppColorGroup.BLUE, listOf(createPage("Internet Archive", ""))),
        createDomain("https://khanacademy.org", AppColorGroup.BLUE, listOf(createPage("Khan Academy", ""))),
        createDomain("https://coursera.org", AppColorGroup.BLUE, listOf(createPage("Coursera", ""))),
        createDomain("https://duolingo.com", AppColorGroup.BLUE, listOf(createPage("Duolingo", ""))),
        createDomain("https://huggingface.co", AppColorGroup.BLUE, listOf(createPage("Hugging Face", ""))),
        createDomain("https://runwayml.com", AppColorGroup.BLUE, listOf(createPage("Runway", ""))),

        // --- GREEN: GOOD (Productivity, Search, Design) ---
        createDomain("https://notion.so", AppColorGroup.GREEN, listOf(createPage("Notion", ""))),
        createDomain("https://obsidian.md", AppColorGroup.GREEN, listOf(createPage("Obsidian", ""))),
        createDomain("https://figma.com", AppColorGroup.GREEN, listOf(createPage("Figma", ""))),
        createDomain("https://canva.com", AppColorGroup.GREEN, listOf(createPage("Canva", ""))),
        createDomain("https://proton.me", AppColorGroup.GREEN, listOf(createPage("Proton Mail", ""))),
        createDomain("https://office.com", AppColorGroup.GREEN, listOf(createPage("Microsoft 365", ""))),

        // --- YELLOW: CAREFUL (Shopping, News, Anime, Streaming) ---
        createDomain("https://amazon.com", AppColorGroup.YELLOW, listOf(createPage("Amazon", ""))),
        createDomain("https://ebay.com", AppColorGroup.YELLOW, listOf(createPage("eBay", ""))),
        createDomain("https://nytimes.com", AppColorGroup.YELLOW, listOf(createPage("NYT", ""))),
        createDomain("https://thetimes.com", AppColorGroup.YELLOW, listOf(createPage("The Times", ""))),
        createDomain("https://youtube.com", AppColorGroup.YELLOW, listOf(createPage("YouTube", ""))),
        createDomain("https://netflix.com", AppColorGroup.YELLOW, listOf(createPage("Netflix", ""))),
        createDomain("https://crunchyroll.com", AppColorGroup.YELLOW, listOf(createPage("Crunchyroll", ""))),
        createDomain("https://webtoons.com", AppColorGroup.YELLOW, listOf(createPage("Webtoon", ""))),

        // --- RED: ADDICTIVE (Social, Gaming, Furry, Fanfic) ---
        createDomain("https://reddit.com", AppColorGroup.RED, listOf(
            createPage("Reddit", ""),
            createPage("r/android", "/r/android"),
            createPage("r/anime", "/r/anime"),
            createPage("r/furry", "/r/furry"),
            createPage("r/FanFiction", "/r/FanFiction")
        )),
        createDomain("https://x.com", AppColorGroup.RED, listOf(createPage("X / Twitter", ""))),
        createDomain("https://instagram.com", AppColorGroup.RED, listOf(createPage("Instagram", ""))),
        createDomain("https://facebook.com", AppColorGroup.RED, listOf(createPage("Facebook", ""))),
        createDomain("https://twitch.tv", AppColorGroup.RED, listOf(createPage("Twitch", ""))),
        createDomain("https://furaffinity.net", AppColorGroup.RED, listOf(createPage("Fur Affinity", ""))),
        createDomain("https://inkbunny.net", AppColorGroup.RED, listOf(createPage("InkBunny", ""))),
        createDomain("https://archiveofourown.org", AppColorGroup.RED, listOf(createPage("AO3", ""))),
        createDomain("https://fanfiction.net", AppColorGroup.RED, listOf(createPage("FanFiction.net", ""))),
        createDomain("https://store.steampowered.com", AppColorGroup.RED, listOf(createPage("Steam", "")))
    )

    val history = mutableStateListOf<HistoryEntry>()
    var lastNavigationUrl: String? = null
    var currentUrlToLoad by mutableStateOf<String?>(null)
    var activeDomainObject by mutableStateOf<DomainObject?>(null)
    var activePageObject by mutableStateOf<PageObject?>(null)

    private fun createDomain(url: String, group: AppColorGroup, pages: List<PageObject> = emptyList()): DomainObject {
        return DomainObject(url = url, defaultColorGroup = group, pages = pages)
    }

    private fun createPage(name: String, path: String, group: AppColorGroup? = null): PageObject {
        // Correcting: Pages should inherit their group from domain if not specified.
        // We'll use BLUE as a placeholder if nothing else exists.
        return PageObject(name = name, path = path, assignedColorGroup = group ?: AppColorGroup.BLUE)
    }

    fun addPageToFeed(
        url: String, 
        title: String, 
        group: AppColorGroup? = null,
        modifier: Float = 0.0f,
        snooze: Int = 5
    ) {
        val uri = android.net.Uri.parse(url)
        val domainUrl = "${uri.scheme}://${uri.host}"
        val path = uri.path ?: "/"
        
        val existingDomain = domainsList.find { it.url.equals(domainUrl, ignoreCase = true) }
        val targetGroup = group ?: existingDomain?.defaultColorGroup ?: AppColorGroup.BLUE
        
        val newPage = PageObject(
            name = title, 
            path = path, 
            assignedColorGroup = targetGroup,
            settings = AffinitySettings(modifier = modifier, snoozeMinutes = snooze)
        )

        if (existingDomain != null) {
            val updatedPages = existingDomain.pages.toMutableList()
            if (updatedPages.none { it.path.equals(path, ignoreCase = true) }) {
                updatedPages.add(newPage)
                domainsList = domainsList.map { 
                    if (it.url == domainUrl) it.copy(pages = updatedPages) else it 
                }
            }
        } else {
            val newDomain = DomainObject(
                url = domainUrl,
                defaultColorGroup = targetGroup,
                pages = listOf(newPage)
            )
            domainsList = domainsList + newDomain
        }
    }
}
