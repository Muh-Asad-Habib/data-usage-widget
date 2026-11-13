# Data Watch

A lightweight Android app and home-screen widget that shows your **Wi-Fi and mobile data usage**: in total, per app and per Wi-Fi network. Handy for keeping an eye on your quota and spotting apps that quietly send data in the background.

**[Download the latest APK](https://github.com/Muh-Asad-Habib/data-usage-widget/releases/latest)**

## Features
- Home shows today at a glance; tap the total to open the **Dashboard**, swipe through weeks and pick any day, or open last 7 days, this month, last month or any date range with full details
- Weekly stacked charts for total, Wi-Fi and mobile data, with top apps per day
- Per-app usage (foreground vs. background) and usage per Wi-Fi network (SSID)
- Highlights apps with heavy background uploads, which can help detect spyware
- Widgets in 2x1, 2x2, 4x1 and 4x2, with custom period, network, colors, gradient, image, transparency and content
- Clean One UI-style design, light and dark theme
- English by default, follows the system language (Indonesian included)
- Private: no internet permission, nothing leaves your phone

## Requirements
- Android 8.0 or newer (minSdk 26), about 2 GB RAM or more
- APK size around 1.5 MB
- Location permission is only needed to read Wi-Fi names

## Install
1. Download and install the APK (allow "Install unknown apps").
2. Open the app and grant **Usage access**.
3. Long-press the home screen, open Widgets and add **Data Watch**.

## About the icon
An eye made of two arcs, one for Wi-Fi and one for mobile data, with a keyhole in the middle: you watch your own data, and only you hold the key.

## Build
```
./gradlew assembleRelease
```
Kotlin, Jetpack Compose and RemoteViews.

## License
MIT