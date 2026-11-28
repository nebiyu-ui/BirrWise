# BirrWise

Personal finance and expense tracker for Ethiopia. Built to work offline, with no account required. All amounts are in Ethiopian birr (ETB).

## Status

Early development (version 0.9.0). You can create, edit, and archive accounts, and record income, expenses, fees, refunds, and transfers by hand. Each account shows a tracked balance calculated from your records. The Home tab shows your total balance, a month of income and expenses, your accounts, recent entries, and warnings. The Transactions tab can be searched, filtered, and sorted. The Budgets tab tracks a monthly limit for each expense category, with progress, gentle alerts, pausing, and a month-by-month history. Backup and restore (from the gear button on Home) saves everything to a file you choose, protected by a passphrase, and restores it safely. Imports, SMS reading, and managing your own categories are planned and are not built yet. The People tab is still an empty screen.

## Technology

Kotlin, Jetpack Compose with Material 3, Navigation Compose, Room (local database). Planned: DataStore for settings, WorkManager for background work.

## Building

The APK is built by GitHub Actions (`.github/workflows/android.yml`) on every push to `main`. Open the latest run on the Actions tab and download the `BirrWise-debug-APK` artifact. To build locally, run `./gradlew testDebugUnitTest assembleDebug` with JDK 17 and the Android SDK installed. Debug signing is explained in `docs/SIGNING.md`.

## More

See `IMPLEMENTATION_PLAN.md` for the roadmap, `PRIVACY.md` for how data is handled, and the `docs` folder for the financial and database rules.
