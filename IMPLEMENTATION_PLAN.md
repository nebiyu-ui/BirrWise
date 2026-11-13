# Implementation plan

A phase counts as done only when it compiles, its tests pass, and the GitHub Actions build is green.

| Phase | Scope | Status |
|---|---|---|
| 1 | Repository stabilization: working build and installable debug APK | Done |
| 2 | App foundation: theme, five-section navigation, empty screens | Done (checked on a phone) |
| 3a | Financial calculation engine: money, periods, balances, reports, budget math, with tests | Done |
| 3b | Room database: entities, DAOs, repositories, database tests | Done |
| 3c | Fixed debug signing key so new builds install over old ones and keep data | Done |
| 4a | Accounts: list, create, edit, archive, tracked balance | Done (checked on a phone) |
| 4b | Manual transactions: income, expense, transfer, fee, refund, review state | Done (checked on a phone) |
| 5a | Home overview: month totals, balance, accounts, recent entries, warnings, quick actions | Done (checked on a phone) |
| 5b | Transactions: search, filters, sorting, grouping by day | In progress (waiting for build result) |
| 6 | Categories and monthly budgets | Planned |
| 7 | CSV import and export, duplicate detection | Planned |
| 8 | SMS message parsing | Planned |
| 9 | PDF statements and account reconciliation | Planned |
| 10 | Savings goals, recurring transactions, reports, People section | Planned |
| 11 | Amharic localization and polish | Planned |
| 12 | Security and release testing | Planned |

## Required tests not covered yet

- Duplicate imports do not change balances: needs duplicate detection (Phase 7).
- Database migrations: there is only version 1 so far. The first schema change must add a migration and a test (see `docs/DATABASE.md`).

## Notes

- The calculation rules are in `docs/FINANCIAL_RULES.md`, the database rules are in `docs/DATABASE.md`, and signing is described in `docs/SIGNING.md`.
- The People section lists people who often appear in transactions. The database can store people now; the screen comes in Phase 10.
- Reports move to a Reports screen opened from Home.
