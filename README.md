# Malachite Browser

"Use the brainrot to destroy the brainrot."

Malachite is a high-density, professional "command center" browser designed specifically for users with ADHD. It re-directs addictive swipe habits toward productive content through an infinite vertical feed and a weighted probability engine.

## Mission
Most modern platforms are designed to keep you scrolling through low-value content. Malachite flips the script by using those same mechanics (vertical paging, haptic feedback, infinite buffers) to serve you the content you *actually* want to consume—AI tools, documentation, dev-tools, and learning resources.

## Key Features
- **Infinite Vertical Feed:** Powered by GeckoView, managing multiple live sessions simultaneously.
- **Weighted Probability Engine (RYGB):**
    - **BLUE (Best):** 2.0x probability (AI, Dev Tools, Learning).
    - **GREEN (Good):** 1.5x probability (Productivity & Search).
    - **YELLOW (Careful):** 0.7x probability (News & Shopping).
    - **RED (Addictive):** 0.3x probability (Social Media & Gaming).
- **Zero Dead Space UI:** A high-information-density layout with 2.dp corner radii for a sharp, utilitarian look.
- **Dual-Buffer System:** Maintains a 5-page history and 3-page forward buffer for instant swipes and recovery.
- **Precision Controls:** Every setting is tunable with incremental +/- controls for fine-grained affinity adjustment.
- **Data Portability:** Full persistence with JSON export/import functionality.
- **Engine Hardening:** Modern Firefox 151+ identity, tracking protection, and online uBlock Origin installation.

## Tech Stack
- **Engine:** Mozilla GeckoView
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** Single-Activity with global singleton state
- **Persistence:** Moshi JSON Serialization

## Build & Deploy
Malachite supports **x86_64**, **arm64-v8a**, and **armeabi-v7a** architectures.

### Prerequisites
- Android Studio Ladybug+
- JDK 17+

### Steps
1. Clone the repository.
2. Open in Android Studio.
3. Sync Gradle.
4. Build `app:assembleDebug` or `app:assembleRelease`.

## License
This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.
