# BirrWise

Personal finance and expense tracker for Ethiopia. Built to work offline, with no account required.

## Status

Early development (version 0.4.0). You can create, edit, and archive accounts and see each account's tracked balance. Adding transactions, budgets, imports, and SMS reading are planned and are not built yet. The Home, Transactions, Budgets, and People tabs are still empty screens.

## Technology

Kotlin, Jetpack Compose with Material 3, Navigation Compose, Room (local database). Planned: DataStore for settings, WorkManager for background work.

## Building

The APK is built by GitHub Actions (`.github/workflows/android.yml`) on every push to `main`. Open the latest run on the Actions tab and download the `BirrWise-debug-APK` artifact. To build locally, run `./gradlew testDebugUnitTest assembleDebug` with JDK 17 and the Android SDK installed. Debug signing is explained in `docs/SIGNING.md`.

## More

See `IMPLEMENTATION_PLAN.md` for the roadmap, `PRIVACY.md` for how data is handled, and the `docs` folder for the financial and database rules.
