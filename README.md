# Smoke Grid

A home-screen widget that shows a GitHub-style grid of your days. Ember-shaded box = a day you smoked (darker for 1, brightest for 4+). The goal is an empty grid.

## Install on your phone
1. Install **Android Studio** on your laptop and open this `smoke-grid` folder.
2. If it offers to **upgrade the Android Gradle Plugin**, say **no / don't remind me**. The project is pinned to versions that work together.
3. On your phone: Settings → About phone → tap **Build number** 7 times, then Developer options → turn on **USB debugging** (or **Wireless debugging**).
4. Connect the phone, pick it in the device dropdown, and press **Run ▶**.
5. On your home screen: long-press → **Widgets** → **Smoke Grid** → drag it onto the screen. Resize it wider to see more weeks.

## No Android Studio? Build it on GitHub
1. Create a new GitHub repo and upload everything in this folder, including the `.github` folder (drag-and-drop works from a laptop browser).
2. Open the repo's **Actions** tab. The "Build APK" run starts on every upload and takes about 5 minutes.
3. Open the finished run and download **smoke-grid-apk** from Artifacts. It's a zip with `app-debug.apk` inside.
4. On your phone, open the APK and allow "Install unknown apps" when asked.

## Using it
- The widget is just the grid. Tap it to open the app, then press **I smoked today**. After the first tap the button becomes a − / + stepper for how many you had.
- The day changes at **4am**, so a late-night cigarette counts for the day before.
- Today's box has a cream ring. Dimmed boxes are from before you started tracking.
- The widget follows the phone's light / dark mode.

## How it's built
| File | What it does |
|---|---|
| `SmokeLog.kt` | Stores a count per day on the phone (SharedPreferences). |
| `HeatmapRenderer.kt` | Draws the grid as a bitmap in the Ember palettes (dark + light). |
| `SmokeWidget.kt` | The widget (Jetpack Glance): just the grid; tapping opens the app. |
| `MainActivity.kt` | The app screen: streak, 13-week grid, today's button / stepper. |

## Ideas for v2
- Money saved, best streak, worst day of the week.
