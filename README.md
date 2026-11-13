# Pantau Data

Lightweight Android home-screen widget that shows **Wi-Fi and mobile data usage** (total and per app), so you can track your quota and spot apps that send data in the background.

**[Download the latest APK](https://github.com/Muh-Asad-Habib/data-usage-widget/releases/latest)**

## Features
- Usage for today, yesterday, last 7 days, this month, last month, or a custom range
- Wi-Fi / mobile split and per-app breakdown (including background vs. foreground)
- Flags apps with heavy background traffic, which can help detect spyware
- Widget sizes: 2x1, 2x2, 4x1 (total + Wi-Fi + mobile) and 4x2 (full, with donut chart and top apps)
- Customizable widget: period, network, background color, transparency, text color, visible items
- Private: no internet permission, all data stays on your phone

## Install
1. Download and install the APK (allow "Install unknown apps").
2. Open the app and grant **Usage access**.
3. Long-press the home screen → Widgets → **Pantau Data**, or add it from the app's Widget screen.

## Build
```
./gradlew assembleDebug
```
Requires Android 10+ (minSdk 29). Built with Kotlin, Jetpack Compose and RemoteViews.

## License
MIT