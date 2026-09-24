# AUTO-MOVIE ENGINE 2.0 — R6.4 Android Portable

Android starter runtime for AUTO-MOVIE R6.4.

## Cloud APK build
GitHub Actions runs `R6.4 Cloud Build APK` on every push to `main`, or manually with **Run workflow**.
The generated debug APK is uploaded as the artifact `auto-movie-r6-4-debug-apk`.

## Current runtime
- Android native Kotlin app
- R6.4 version metadata
- Thai command input screen
- 8-second scene workflow status
- GitHub Actions APK build

This is a clean installable runtime foundation. External ChatGPT/Flow/Veo service integration requires separately configured APIs/authorized services; ChatGPT Plus credentials are not embedded in the APK.
