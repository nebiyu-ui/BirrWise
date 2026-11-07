# Database

BirrWise keeps its data in a Room (SQLite) database named `birrwise.db`, stored in the app's private storage on the phone. Nothing is sent anywhere. The app does not encrypt the file itself, so it relies on the phone's lock screen and storage encryption.

## Tables

| Table | Holds |
|---|---|
| accounts | Bank, wallet, cash, and savings accounts, with an opening balance and date |
| transactions | Every money movement, with its type, amount, time, category, person, and source |
| categories | Built-in and custom categories, with optional parent category |
| people | People and businesses that appear in transactions |
| import_batches | One row per statement import |
| budgets | Monthly spending limits per category |
| category_rules | Rules that suggest a category |
| savings_goals | Savings targets |

Money is a whole number of minor units (cents) in a `Long`. Times are UTC epoch milliseconds. Ids are text and never change.

## What happens when a row is deleted

| Deleting | Rows that point to it |
|---|---|
| An account | Refused while it has transactions or import batches. Archive it instead. |
| A category | Refused while transactions or subcategories use it. Its budgets and rules are deleted with it. |
| A transaction that was reversed | Refused while a reversal points to it. |
| A person | Their transactions stay and lose the link. |
| An import batch | Its transactions stay and lose the link. |

The app also deletes both legs of a transfer together, so no half transfer is left behind.

## Changing the schema

- Every change to the tables raises the database version and adds a migration in `BirrWiseDatabase.MIGRATIONS` with a test.
- Destructive migration is never used. If the database cannot be opened or upgraded, the app must stop with an error and must not replace the file.
- Room exports a JSON description of each version to `app/schemas`. Commit those files so migrations can be tested.
