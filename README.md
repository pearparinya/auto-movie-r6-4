# DIRECTOR X

Android app for planning stories, sharing character references with ChatGPT, and preparing scene packages for Flow.

The launcher opens `StudioActivity`. The channel picker shows the saved กวิน, รินลดา and มายด์ MASTER portraits for the main channel, and a fictional cast image for other channels.

Version metadata lives in `app/build.gradle.kts`. GitHub Actions runs unit tests, builds and signs the release APK, and publishes the current release for the in-app updater. The old build-time source transformations have been removed; builds compile the committed source directly.
