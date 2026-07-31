# Malachite Design Specification

This document provides the definitive technical specification for the Malachite Browser, designed to serve as a blueprint for a clean-sheet rebuild using optimized patterns (MVI/MVVM) and the **Gecko Engine** orchestration layer.

---

## 🏗️ App Architecture & Flow

Malachite is built around an **Infinite Vertical Feed** model, where every "page" is a live, buffered browser session.

### 1. Dual-Buffer System
- **History Buffer:** Maintains 5 (configurable) inactive but "alive" sessions behind the current page for instant recovery.
- **Forward Buffer:** Pre-calculates and pre-loads 3 (configurable) potential next pages based on affinity scores.
- **Active State:** A `sessionsBuffer` (MutableStateList) manages the lifecycle of `SessionEntry` objects, each containing a `GeckoSession`.

### 2. Data Flow (Pass-throughs)
- **UI → Engine:** Gestures and button clicks trigger `BrowserAction` dispatches.
- **Engine → UI:** `GeckoSession` delegates (Progress, Title, Location) update the `sessionsBuffer` state in real-time.
- **State → Persistence:** `PersistenceManager` mirrors the global `BrowserState` to a JSON database on every pause event.

---

## 🏁 Onboarding & Identity Flow

### 1. Welcome Screen (`WelcomePageContent`)
- **Manifesto:** Displays the project mission and a quote from Linus Torvalds.
- **Tutorial:** Explains the RYGB Flow (Feed -> Limit -> Decide).
- **Suggested Library:** A pre-defined list of high-value domains (AI, Dev Tools) that users can bulk-add to their feed.

### 2. Identity Connection
- **Credential Manager Integration:** Uses `IdentityManager` to request verified email and name via Digital Credentials.
- **User Profile:** Stores `UserProfile` (email, display name) in `BrowserState` upon successful connection.

---

## 🎮 Interaction & Bind Point Map

Malachite uses a centralized `dispatchAction` bridge to decouple UI triggers from engine execution.

### 1. User Action Directory (Bind Points)

| Trigger | Input Method | Action | Target / Result |
| :--- | :--- | :--- | :--- |
| **Bottom Bar Slot 1** | Tap | View Favorites | `FavoritesListingActivity` |
| **Bottom Bar Slot 2** | Tap | Snoozed Content | View active snoozes |
| **Bottom Bar Slot 3** | Tap | Add to Feed | `AddActivity` (Prefilled) |
| **Bottom Bar Slot 4** | Tap | History | `HistoryActivity` |
| **Bottom Bar Slot 5** | Tap | Control Center | `SettingsActivity` |
| **Bottom Bar Slot X** | Long-Press | Edit Bookmark | Edit Name/URL Dialog |
| **WebView Area** | Horiz. Swipe (R) | `UPVOTE` | Increments affinity score + **Haptic** |
| **WebView Area** | Horiz. Swipe (L) | `DOWNVOTE` | Decrements affinity score + **Haptic** |
| **WebView Boundary** | Vertical Pull (Up) | `SNOOZE` | Triggers Next Page (at bottom) |
| **Link / Text** | Long-Press | Context Menu | Bitwarden Autofill / Quick Add |
| **Back Button** | System Back | `GO_BACK` | `GeckoSession.goBack()` |

### 2. Landscape Optimized Bindings (Interleaved)
In landscape mode, 4 high-frequency buttons are interleaved between the primary slots:

| Index | Icon | Action | Logic |
| :--- | :--- | :--- | :--- |
| **0** | `ThumbDown` | `DOWNVOTE` | Quick penalty for low-value content |
| **1** | `ArrowDown` | `NEXT_PAGE` | Instant vertical transition |
| **2** | `Star` | `FAVORITE` | One-tap save to Favorites |
| **3** | `ThumbUp` | `UPVOTE` | Quick reward for high-value content |

---

## ⚙️ Control Center (`SettingsActivity`)

The Control Center is a high-density dashboard for fine-tuning the weighted engine.

### 1. Group Channels (RYGB)
- **Controls:** Each group (Red, Yellow, Green, Blue) has a dedicated `multiplier` (Probability) and `snoozeMinutes` (Guard) control.
- **Visuals:** Color-coded cards with P: (Probability) and G: (Guard) precision controls.

### 2. Global Baselines & Navigation
- **Global Settings:** Centralized `modifier` and `snoozeMinutes` that apply to all domains.
- **Nav Tweaks:** Toggles for swipe/tap behaviors and integer controls for buffer counts (Forward/History).
- **Welcome/Setup:** Shortcut to return to the onboarding screen.

### 3. Domain Hierarchy & Analytics
- **Hierarchy:** Expandable cards showing Domain -> Page relationships.
- **Analytics:** 
    - **G%:** Global weight percentage (probability of this domain appearing in the next random draw).
    - **In-Domain%:** Probability of a specific page appearing once its domain is selected.

---

## 📜 History & Analytics

### 1. Duration Tracking
- `HistoryEntry` captures the exact `duration` (milliseconds) spent on each page.
- Displays "Spent: Xm Ys" in the history list.

### 2. Navigation Context
- Tracks how a page was reached (e.g., "Direct", "Bookmark X", "Swipe Up").
- Displays "via [Context]" to help users understand their navigation patterns.

---

## 🧠 Core Algorithms (The "Flow Rate")

### 1. RYGB Tiered Multipliers
| Tier | Logic | Multiplier | Usage |
| :--- | :--- | :--- | :--- |
| **BLUE** | Best | 2.0x | AI, Dev Documentation, Learning |
| **GREEN** | Good | 1.5x | Productivity, Search, Design |
| **YELLOW** | Careful | 0.7x | News, Shopping, Anime |
| **RED** | Addictive | 0.3x | Social Media, Gaming, "Brainrot" |

### 2. Weighted Random Selection
1.  **Domain Selection:** Pick a domain from `domainsList` where `randomValue <= accumulatedWeight`.
2.  **Page Selection:** Within the domain, pick a page using `multiplier * (affinityScore + modifier)`.
3.  **Snooze Check:** Skip domains/pages where `snoozeTimestamp > currentTime`.

### 3. Session Guarding & Limit Enforcement
- **Time Limits:** Each domain/page can have a `customTimeLimitSeconds`. If null, it defaults to `globalTimeLimitSeconds * multiplier`.
- **Lock Overlay:** When a limit is reached, a high-opacity (85%) overlay blocks the page, forcing the user to either "Follow Site", "Save for Later", or "Swipe UP" to the next feed item.
- **Scroll Limits:** Infrastructure exists for `customScrollLimitPx` to trigger similar enforcement.

### 4. Retroactive Decay
- Affinity scores decay at **0.01f per hour**.
- Scores > 1.0f decay downwards; scores < 1.0f decay upwards.
- Goal: Systematically return all content to a neutral "1.0f" base weight over time.

---

## 🛠️ System Integrations

### 1. Gecko Engine (Orchestration Layer)
- **Runtime:** `GeckoRuntime` singleton with remote debugging options.
- **Privacy Hardening:**
    - **uBlock Origin:** Auto-installed from assets or downloaded from AMO to eliminate distractions and trackers.
    - **Tracking Protection:** Force-enabled at the session level.
    - **Media Suspension:** Media is automatically suspended when a page is not active in the feed.
- **Identity:** `Firefox rv:151.0` User-Agent spoofing for Desktop/Tablet modes to bypass mobile-only limitations.
- **Configuration:** Deep access via `about:config`.

### 2. Identity & Persistence
- **IdentityManager:** Uses Android **Credential Manager** for verified email retrieval (SD-JWT parsing).
- **PersistenceManager:** Handles **Moshi JSON** serialization and supports Export/Import for data portability.
- **AutoFill:** Full support for system Autofill Services (e.g., Bitwarden) via the contextual long-press menu.

---

## 🚀 Rebuild Requirements

1.  **MVI Architecture:** Move all logic from `MainActivity` and `SettingsActivity` into structured ViewModels.
2.  **Dependency Injection:** Use Hilt to provide engine and persistence services.
3.  **Database Upgrade:** Migrate from flat JSON to **Room** for high-performance history and domain analytics.
4.  **Shared Components:** Extract `PrecisionControl`, `AffinityControlBlock`, and `RYGB Selector` into a shared UI module.

---

## 🗺️ Design Roadmap (Rebuild Strategy)

The following sequence defines the priority for the clean-sheet implementation:

1.  **Gecko Core:** Initialize `GeckoRuntime` and ensure basic URI loading in a single session.
2.  **Tab Management:** Implement the `sessionsBuffer` with the dual-buffer (History/Forward) pre-loading logic.
3.  **Action Dispatcher:** Formalize the `BrowserAction` system and bind core navigation functions.
4.  **Universal UI Base:** Build the navigation bar and layout logic for both Portrait and Landscape (interleaved) modes.
5.  **Gesture Integration:** Implement vertical pull-to-next and horizontal affinity swipe listeners.
6.  **Persistence Layer:** Implement the **Room** database schema for domains, pages, and history.
7.  **Control Center:** Build the Settings UI with live RYGB multipliers and global baseline controls.
8.  **History Engine:** Integrate the duration tracking and navigation context recording.
9.  **Affinity Logic (Likes):** Finalize the weighted random selection and "Upvote/Downvote" score adjustment logic.
