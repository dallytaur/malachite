# Project Map - malachite 🏛️

Welcome to the **malachite** project map. This document provides a high-level overview of the project structure, classes, and UI components with direct links for easy navigation.

---

## 📂 Project Structure

```text
root/
├── 📁 app/
│   └── 📁 src/main/
│       ├── 📁 java/com/example/        <-- Core Logic & UI
│       │   ├── 📄 MainActivity.kt      <-- Entry point & Main logic
│       │   └── 📁 ui/theme/            <-- Design System
│       │       ├── 📄 Theme.kt
│       │       ├── 📄 Color.kt
│       │       └── 📄 Type.kt
│       └── 📁 res/                     <-- Android Resources
│           ├── 📁 values/              <-- Strings, Colors, Themes (XML)
│           └── 📁 drawable/            <-- Icons & Graphics
├── 📁 docs/
│   └── 📄 map.md                       <-- You are here
└── 📁 gradle/                          <-- Build Configuration
```

---

## 🧩 Classes & Data Structures

### Core Logic ([MainActivity.kt](file:///home/nadia/StudioProjects/malachite/app/src/main/java/com/example/MainActivity.kt))

| Symbol | Type | Description |
| :--- | :--- | :--- |
| `MainActivity` | `class` | The main entry point activity that initializes the Compose UI. |
| `Bookmark` | `data class` | Represents a quick-dial bookmark with an ID, name, and URL. |
| `DomainObject` | `data class` | Stores domain-level affinity scores and snooze preferences. |
| `PageObject` | `data class` | Stores page-level affinity scores and snooze preferences. |
| `decayScore` | `fun` | Implements the retroactive decay algorithm for affinity scores. |
| `selectNextPage` | `fun` | Weighted random selection algorithm for the "Feed" functionality. |
| `pickWeightedPageForDomain` | `fun` | Helper to pick a page within a domain based on weights. |

---

## 📱 Screens & UI Components

### Main Browser Screen
The entire UI is currently contained within the `BrowserApp` Composable.

- **Composable:** `BrowserApp` ([MainActivity.kt](file:///home/nadia/StudioProjects/malachite/app/src/main/java/com/example/MainActivity.kt))
- **Key Features:**
    - **Header Bar:** Shows current domain, navigation controls, and gesture toggle.
    - **WebView:** The core browser component with a custom gesture overlay.
    - **Bottom Navigation (Speed Dial):** Five programmable buttons for quick navigation.
    - **Settings Dialog:** Dashboard for monitoring the Affinity Engine and resetting scores.

---

## 🎨 Theme & Styling

- **Theme Definition:** `MyApplicationTheme` ([Theme.kt](file:///home/nadia/StudioProjects/malachite/app/src/main/java/com/example/ui/theme/Theme.kt))
- **Colors:** Primary carbon/slate palette ([Color.kt](file:///home/nadia/StudioProjects/malachite/app/src/main/java/com/example/ui/theme/Color.kt))
- **Typography:** Custom font settings for the minimalist aesthetic ([Type.kt](file:///home/nadia/StudioProjects/malachite/app/src/main/java/com/example/ui/theme/Type.kt))

---

## 🛠️ Resources

- [Strings](file:///home/nadia/StudioProjects/malachite/app/src/main/res/values/strings.xml) - App name and static labels.
- [AndroidManifest.xml](file:///home/nadia/StudioProjects/malachite/app/src/main/AndroidManifest.xml) - App configuration and permissions (INTERNET).
