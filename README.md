# BirrWise

Personal finance and expense tracker for Ethiopia. Built to work offline, with no account required.

## Status

Early development (version 0.2.0). The app opens to five main sections: Home, Transactions, Accounts, Budgets, and People. These sections are empty screens for now. Saving transactions, budgets, imports, and SMS reading are planned and are not built yet.

## Technology

Kotlin, Jetpack Compose with Material 3, Navigation Compose. Planned: Room for local storage, DataStore for settings, WorkManager for background work.

## Building

The APK is built by GitHub Actions (`.github/workflows/android.yml`) on every push to `main`. Open the latest run on the Actions tab and download the `BirrWise-debug-APK` artifact. To build locally, run `./gradlew testDebugUnitTest assembleDebug` with JDK 17 and the Android SDK installed.

## More

See `IMPLEMENTATION_PLAN.md` for the roadmap and `PRIVACY.md` for how data is handled.
