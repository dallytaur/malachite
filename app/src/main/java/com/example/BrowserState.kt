package com.example

import androidx.compose.runtime.*

// Data structures for tracking and managing user affinity & snooze preferences per domain
data class DomainObject(
  val url: String,
  val affinityScore: Float = 1.0f,
  val snoozeTimestamp: Long? = null,
  val lastUpdated: Long = System.currentTimeMillis(),
  val pages: List<PageObject> = emptyList(),
  val snoozeCount: Int = 0,
  val successiveReadCount: Int = 0
)

data class PageObject(
  val name: String,
  val path: String,
  val affinityScore: Float = 1.0f,
  val snoozeTimestamp: Long? = null,
  val lastUpdated: Long = System.currentTimeMillis(),
  val snoozeCount: Int = 0,
  val successiveReadCount: Int = 0
)

data class HistoryEntry(
    val url: String,
    val title: String,
    val timestamp: Long,
    var duration: Long = 0, // in milliseconds
    val parentContext: String = "Direct",
    val isFromSwipe: Boolean = false
)

object BrowserState {
    var domainsList by mutableStateOf(
      listOf(
        DomainObject(
          url = "https://old.reddit.com",
          affinityScore = 1.0f,
          lastUpdated = System.currentTimeMillis(),
          pages = listOf(
            PageObject("Reddit Android", "/r/android", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Reddit Kotlin", "/r/kotlin", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Reddit Science", "/r/science", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Reddit Today I Learned", "/r/todayilearned", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Reddit Programming", "/r/programming", 1.0f, lastUpdated = System.currentTimeMillis())
          )
        ),
        DomainObject(
          url = "https://en.m.wikipedia.org",
          affinityScore = 1.0f,
          lastUpdated = System.currentTimeMillis(),
          pages = listOf(
            PageObject("Wikipedia Malachite", "/wiki/Malachite", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Wikipedia Kotlin", "/wiki/Kotlin_(programming_language)", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Wikipedia Jetpack Compose", "/wiki/Jetpack_Compose", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Wikipedia Material Design", "/wiki/Material_Design", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Wikipedia Android Dev", "/wiki/Android_software_development", 1.0f, lastUpdated = System.currentTimeMillis())
          )
        ),
        DomainObject(
          url = "https://news.google.com",
          affinityScore = 1.0f,
          lastUpdated = System.currentTimeMillis(),
          pages = listOf(
            PageObject("Google News Tech", "/news/rss/headlines/section/topic/TECHNOLOGY", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Google News Science", "/news/rss/headlines/section/topic/SCIENCE", 1.0f, lastUpdated = System.currentTimeMillis()),
            PageObject("Google News Business", "/news/rss/headlines/section/topic/BUSINESS", 1.0f, lastUpdated = System.currentTimeMillis())
          )
        )
      )
    )

    val history = mutableStateListOf<HistoryEntry>()
    
    var lastNavigationUrl: String? = null
    
    // To be updated by MainActivity
    var currentUrlToLoad by mutableStateOf<String?>(null)

    var activeDomainObject by mutableStateOf<DomainObject?>(null)
    var activePageObject by mutableStateOf<PageObject?>(null)

    fun addPageToFeed(url: String, title: String) {
        val uri = android.net.Uri.parse(url)
        val domainUrl = "${uri.scheme}://${uri.host}"
        val path = uri.path ?: "/"
        
        val existingDomain = domainsList.find { it.url.equals(domainUrl, ignoreCase = true) }
        if (existingDomain != null) {
            val updatedPages = existingDomain.pages.toMutableList()
            if (updatedPages.none { it.path.equals(path, ignoreCase = true) }) {
                updatedPages.add(PageObject(title, path, 1.0f, lastUpdated = System.currentTimeMillis()))
                domainsList = domainsList.map { 
                    if (it.url == domainUrl) it.copy(pages = updatedPages) else it 
                }
            }
        } else {
            val newDomain = DomainObject(
                url = domainUrl,
                pages = listOf(PageObject(title, path, 1.0f, lastUpdated = System.currentTimeMillis()))
            )
            domainsList = domainsList + newDomain
        }
    }
}
