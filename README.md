Matrix Wallpaper
================

A modernized fork of Hacker Live Wallpaper.

An Android live wallpaper of bit sequences streaming down the screen.

This is a personal fork of [gsingh93/hacker-live-wallpaper](https://github.com/gsingh93/hacker-live-wallpaper), updated to build and run on modern Android devices. All credit for the original app goes to Gulshan Singh.

> **Note:** This fork is maintained for personal use only. It is provided as-is, with no support, no guarantees, and no plans for ongoing maintenance. If you find it useful, feel free to use it, but issues and feature requests will likely go unanswered.

Modernization changes in this fork
----------------------------------

The original project was last updated around 2015. This fork brings it up to date:

- **Build system**: Upgraded from Gradle 2.2.1 / Android Gradle Plugin 1.0.0 to Gradle 8.13 / AGP 8.13.0, with Java 17 and current `google()`/`mavenCentral()` repositories (JCenter is dead).
- **SDK targets**: Now compiles against and targets Android 16 (API 36), up from API 21. Minimum supported version is Android 5.0 (API 21).
- **Android 12+ install fix**: Added the required `android:exported` attributes; the original APK could not be installed on modern Android.
- **Android crash fix**: The settings screen used an incomplete theme overlay that crashes on recent Android versions; it now uses a full Material dark theme.
- **Rendering rewrite**: The draw loop was an unthrottled busy loop with per-column background threads (including a data race on the animation state). It is now single-threaded and vsync-driven via `Choreographer`, with time-based animation.
- **GPU rendering**: Frames are drawn on a hardware-accelerated canvas on Android 9+, falling back to software rendering on older devices.
- **High refresh rate displays**: The animation is capped at 60fps and the surface requests a 60Hz frame rate on Android 11+, so 120Hz displays don't waste battery.
- Migrated to AndroidX annotations and removed dead test scaffolding.

Building
--------

Open the project in Android Studio, or build from the command line:

    ./gradlew assembleDebug

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`. Requires JDK 17 and an Android SDK with API 36.

License
-------

MIT License, you can see the license [here](LICENSE). Original copyright (C) 2013 Gulshan Singh.
