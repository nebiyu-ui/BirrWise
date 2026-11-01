# Implementation plan

A phase counts as done only when it compiles, its tests pass, and the GitHub Actions build is green.

| Phase | Scope | Status |
|---|---|---|
| 1 | Repository stabilization: working build and installable debug APK | Done |
| 2 | App foundation: theme, five-section navigation, empty screens | In progress (waiting for build result) |
| 3 | Room database and financial calculation engine, with tests | Planned |
| 4 | Accounts and manual transactions | Planned |
| 5 | Home overview, transaction history, search, filters | Planned |
| 6 | Categories and monthly budgets | Planned |
| 7 | CSV import and export | Planned |
| 8 | SMS message parsing | Planned |
| 9 | PDF statements and account reconciliation | Planned |
| 10 | Savings goals, recurring transactions, reports, People section | Planned |
| 11 | Amharic localization and polish | Planned |
| 12 | Security and release testing | Planned |

## Notes

- The People section lists people who often appear in transactions. It needs the database from Phase 3 before it can show anything.
- Reports move to a Reports screen opened from Home.
