# Privacy

## Current version (0.9.0)

- The app stores what you enter in a local database on your phone: accounts (name, bank or wallet, type, opening balance, date, notes) and transactions (type, amount, date, account, category, description, person or business, notes, and whether it needs review), and budgets (category, monthly limit, alert share, and start and pause dates).
- The database is a private file on the phone. It is not encrypted by the app and is not uploaded anywhere.
- The app requests no runtime permissions and does not declare the internet permission.
- Android automatic backup is turned off for the app.
- Deleting the app deletes its data. You can save a backup file with Backup and restore on the Home screen. You choose where it is saved with the system file picker, and BirrWise never uploads it. The backup holds all your financial data. It can be protected with a passphrase (AES-256 encryption), which is recommended. The passphrase is never stored and cannot be recovered. The date of your last backup is kept in the app's private settings.
- Restoring a backup replaces all data in the app.

## Principles for future versions

- Core features work offline and need no account.
- Bank passwords and PINs are never requested.
- No transaction data is uploaded without clear disclosure and your approval.
- This file will be updated whenever stored data, permissions, exports, or network use change.
